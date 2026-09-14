from __future__ import annotations

import asyncio
import json
import os
import shutil
import signal
import ssl
from pathlib import Path
from tempfile import TemporaryDirectory

from pydantic import ValidationError

from .config import GatewaySettings
from .contracts import (
    InterpretRequest,
    InterpretResponse,
    ProviderCandidate,
    ProviderOutputError,
    to_interpret_response,
)
from .prompt import build_prompt


class ProviderTimeoutError(RuntimeError):
    pass


class ProviderUnavailableError(RuntimeError):
    pass


class ProviderInvalidOutputError(RuntimeError):
    pass


class ReadinessError(RuntimeError):
    pass


class CodexRunner:
    def __init__(self, settings: GatewaySettings):
        if settings.max_concurrency < 1:
            raise ValueError("max_concurrency must be positive")
        self.settings = settings
        self._semaphore = asyncio.Semaphore(settings.max_concurrency)

    async def readiness(self) -> None:
        self._validate_local_prerequisites(ReadinessError)
        executable = self._resolve_executable(ReadinessError)

        try:
            process = await asyncio.create_subprocess_exec(
                executable,
                "--version",
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.DEVNULL,
                env=self._child_env(ReadinessError),
                start_new_session=True,
            )
        except OSError as exc:
            raise ReadinessError("codex version check failed") from exc

        try:
            stdout, _ = await asyncio.wait_for(process.communicate(), timeout=3.0)
        except asyncio.TimeoutError as exc:
            await self._terminate_process_group(process)
            raise ReadinessError("codex version check failed") from exc

        if process.returncode != 0:
            raise ReadinessError("codex version check failed")

        version_output = stdout.decode("utf-8", errors="replace").strip()
        expected = f"codex-cli {self.settings.expected_codex_version}"
        if version_output != expected:
            raise ReadinessError("unexpected codex version")

    async def interpret(self, request: InterpretRequest) -> InterpretResponse:
        try:
            async with asyncio.timeout(self.settings.request_timeout_seconds):
                async with self._semaphore:
                    return await self._interpret_locked(request)
        except TimeoutError as exc:
            raise ProviderTimeoutError("request timed out") from exc

    async def _interpret_locked(self, request: InterpretRequest) -> InterpretResponse:
        self._validate_local_prerequisites(ProviderUnavailableError)
        executable = self._resolve_executable(ProviderUnavailableError)
        prompt = build_prompt(request)

        with TemporaryDirectory(prefix="quickaside-codex-") as tmp_dir:
            output_path = Path(tmp_dir) / "last-message.json"
            command = [
                executable,
                "exec",
                "--ephemeral",
                "--ignore-user-config",
                "--ignore-rules",
                "--skip-git-repo-check",
                "--output-schema",
                str(self.settings.schema_path),
                "--output-last-message",
                str(output_path),
                "-m",
                self.settings.model,
                "-s",
                "read-only",
                "-C",
                str(self.settings.workspace),
                "-c",
                f'model_reasoning_effort="{self.settings.reasoning_effort}"',
                "-",
            ]

            try:
                process = await asyncio.create_subprocess_exec(
                    *command,
                    stdin=asyncio.subprocess.PIPE,
                    stdout=asyncio.subprocess.DEVNULL,
                    stderr=asyncio.subprocess.DEVNULL,
                    env=self._child_env(ProviderUnavailableError),
                    start_new_session=True,
                )
            except OSError as exc:
                raise ProviderUnavailableError("provider process could not start") from exc

            try:
                await asyncio.wait_for(
                    process.communicate(prompt.encode("utf-8")),
                    timeout=self.settings.timeout_seconds,
                )
            except asyncio.TimeoutError as exc:
                await self._terminate_process_group(process)
                raise ProviderTimeoutError("provider timed out") from exc
            except asyncio.CancelledError:
                await self._terminate_process_group(process)
                raise

            if process.returncode != 0:
                raise ProviderUnavailableError("provider process failed")
            if not output_path.is_file():
                raise ProviderInvalidOutputError("provider result missing")

            try:
                if output_path.stat().st_size > self.settings.max_provider_output_bytes:
                    raise ProviderInvalidOutputError("provider result too large")
                payload = json.loads(output_path.read_text(encoding="utf-8"))
                candidate = ProviderCandidate.model_validate(payload)
                return to_interpret_response(candidate)
            except (
                OSError,
                UnicodeDecodeError,
                json.JSONDecodeError,
                ValidationError,
                ProviderOutputError,
            ) as exc:
                raise ProviderInvalidOutputError("provider result invalid") from exc

    async def _terminate_process_group(self, process: asyncio.subprocess.Process) -> None:
        if process.returncode is not None:
            return

        try:
            os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass

        try:
            await asyncio.wait_for(process.wait(), timeout=1.0)
            return
        except asyncio.TimeoutError:
            pass

        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        await process.wait()

    def _validate_local_prerequisites(self, error_type: type[RuntimeError]) -> None:
        if self.settings.codex_home is None or not self.settings.codex_home.is_dir():
            raise error_type("codex home missing")
        if self.settings.workspace is None or not self.settings.workspace.is_dir():
            raise error_type("workspace missing")
        if not self.settings.schema_path.is_file():
            raise error_type("provider schema missing")

    def _resolve_executable(self, error_type: type[RuntimeError]) -> str:
        candidate = self.settings.codex_bin
        if os.path.sep in candidate:
            path = Path(candidate)
            if path.is_file() and os.access(path, os.X_OK):
                return str(path)
            raise error_type("codex executable missing")

        resolved = shutil.which(candidate, path=os.environ.get("PATH"))
        if not resolved:
            raise error_type("codex executable missing")
        return resolved

    def _child_env(self, error_type: type[RuntimeError]) -> dict[str, str]:
        if self.settings.codex_home is None:
            raise error_type("codex home missing")

        lang = os.environ.get("LANG") or "C.UTF-8"
        env = {
            "CODEX_HOME": str(self.settings.codex_home),
            "HOME": str(self.settings.codex_home),
            "PATH": os.environ.get("PATH", "/usr/local/bin:/usr/bin:/bin"),
            "LANG": lang,
            "LC_ALL": os.environ.get("LC_ALL") or lang,
        }

        for key in ("SSL_CERT_FILE", "SSL_CERT_DIR"):
            value = os.environ.get(key)
            if value:
                env[key] = value

        verify_paths = ssl.get_default_verify_paths()
        if (
            "SSL_CERT_FILE" not in env
            and verify_paths.cafile
            and Path(verify_paths.cafile).is_file()
        ):
            env["SSL_CERT_FILE"] = verify_paths.cafile
        if (
            "SSL_CERT_DIR" not in env
            and verify_paths.capath
            and Path(verify_paths.capath).is_dir()
        ):
            env["SSL_CERT_DIR"] = verify_paths.capath
        return env
