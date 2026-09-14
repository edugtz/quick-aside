import asyncio
import json
import os
from dataclasses import replace
from pathlib import Path

import pytest

from quickaside_gateway.codex_runner import (
    CodexRunner,
    ProviderInvalidOutputError,
    ProviderTimeoutError,
    ReadinessError,
)
from quickaside_gateway.config import GatewaySettings
from quickaside_gateway.contracts import InterpretRequest


def fake_codex_script(
    mode: str = "ok",
    invocation_marker: Path | None = None,
    pid_marker: Path | None = None,
    version: str = "0.154.0",
) -> str:
    invocation_literal = repr(str(invocation_marker)) if invocation_marker is not None else "None"
    pid_literal = repr(str(pid_marker)) if pid_marker is not None else "None"
    return f"""#!/usr/bin/env python3
import json
import os
import pathlib
import sys
import time

if "--version" in sys.argv:
    print("codex-cli {version}")
    raise SystemExit(0)

args = sys.argv[1:]
output = pathlib.Path(args[args.index("--output-last-message") + 1])
invocation_marker = {invocation_literal}
pid_marker = {pid_literal}
if pid_marker:
    pathlib.Path(pid_marker).write_text(str(os.getpid()))
prompt = sys.stdin.read()
if invocation_marker:
    pathlib.Path(invocation_marker).write_text(json.dumps({{"argv": args, "prompt": prompt}}))
mode = {mode!r}
if mode == "sleep":
    time.sleep(10)
elif mode == "invalid":
    output.write_text("not json")
elif mode == "non_utf8":
    output.write_bytes(bytes([255, 254]))
else:
    output.write_text(json.dumps({{"actions": [{{
        "type": "AddListItem",
        "listDefinitionId": "mandado",
        "text": "leche",
        "space": None,
        "title": None,
        "dueDate": None,
        "fields": None
    }}]}}))
"""


def make_settings(
    tmp_path: Path,
    timeout: float = 2.0,
    mode: str = "ok",
    invocation_marker: Path | None = None,
    pid_marker: Path | None = None,
    version: str = "0.154.0",
) -> GatewaySettings:
    codex_home = tmp_path / "codex-home"
    workspace = tmp_path / "workspace"
    codex_home.mkdir()
    workspace.mkdir()
    schema = tmp_path / "schema.json"
    schema.write_text("{}")
    executable = tmp_path / "codex"
    executable.write_text(
        fake_codex_script(
            mode=mode,
            invocation_marker=invocation_marker,
            pid_marker=pid_marker,
            version=version,
        )
    )
    executable.chmod(0o755)
    return GatewaySettings(
        codex_bin=str(executable),
        codex_home=codex_home,
        workspace=workspace,
        schema_path=schema,
        timeout_seconds=timeout,
    )


def request() -> InterpretRequest:
    return InterpretRequest(
        inputText="Agrega leche al mandado",
        capturedAt="2026-09-13T21:24:00Z",
        timeZone="America/Mexico_City",
    )


def process_exists(pid: int) -> bool:
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    return True


def test_readiness_checks_version(tmp_path):
    asyncio.run(CodexRunner(make_settings(tmp_path)).readiness())


def test_readiness_rejects_wrong_version(tmp_path):
    runner = CodexRunner(make_settings(tmp_path, version="0.155.0"))
    with pytest.raises(ReadinessError):
        asyncio.run(runner.readiness())


def test_runner_uses_ephemeral_luna_low_stdin_and_non_git_workspace(tmp_path):
    marker = tmp_path / "invocation.json"
    settings = make_settings(tmp_path, invocation_marker=marker)
    response = asyncio.run(CodexRunner(settings).interpret(request()))
    assert response.actions[0].text == "leche"

    invocation = json.loads(marker.read_text())
    argv = invocation["argv"]
    assert argv[0] == "exec"
    assert "--ephemeral" in argv
    assert "--ignore-user-config" in argv
    assert "--ignore-rules" in argv
    assert "--skip-git-repo-check" in argv
    assert argv[argv.index("-m") + 1] == "gpt-5.6-luna"
    assert argv[argv.index("-s") + 1] == "read-only"
    assert 'model_reasoning_effort="low"' in argv
    assert argv[-1] == "-"
    assert "Agrega leche al mandado" in invocation["prompt"]
    assert "Agrega leche al mandado" not in " ".join(argv)


def test_child_environment_does_not_inherit_secret(monkeypatch, tmp_path):
    monkeypatch.setenv("QUICKASIDE_FAKE_SECRET", "do-not-forward")
    runner = CodexRunner(make_settings(tmp_path))
    env = runner._child_env(ReadinessError)
    assert "QUICKASIDE_FAKE_SECRET" not in env
    assert env["CODEX_HOME"].endswith("codex-home")


def test_invalid_provider_output_rejected(tmp_path):
    runner = CodexRunner(make_settings(tmp_path, mode="invalid"))
    with pytest.raises(ProviderInvalidOutputError):
        asyncio.run(runner.interpret(request()))


def test_non_utf8_provider_output_rejected(tmp_path):
    runner = CodexRunner(make_settings(tmp_path, mode="non_utf8"))
    with pytest.raises(ProviderInvalidOutputError):
        asyncio.run(runner.interpret(request()))


def test_timeout_terminates_and_reaps_process(tmp_path):
    pid_marker = tmp_path / "pid.txt"
    runner = CodexRunner(
        make_settings(tmp_path, timeout=1.5, mode="sleep", pid_marker=pid_marker)
    )
    with pytest.raises(ProviderTimeoutError):
        asyncio.run(runner.interpret(request()))
    pid = int(pid_marker.read_text())
    assert not process_exists(pid)


def test_cancellation_terminates_and_reaps_process(tmp_path):
    async def scenario():
        pid_marker = tmp_path / "pid.txt"
        runner = CodexRunner(
            make_settings(tmp_path, timeout=5.0, mode="sleep", pid_marker=pid_marker)
        )
        task = asyncio.create_task(runner.interpret(request()))
        deadline = asyncio.get_running_loop().time() + 2.0
        while not pid_marker.exists():
            if asyncio.get_running_loop().time() >= deadline:
                raise AssertionError("provider process never started")
            await asyncio.sleep(0.01)
        pid = int(pid_marker.read_text())
        task.cancel()
        with pytest.raises(asyncio.CancelledError):
            await task
        return pid

    pid = asyncio.run(scenario())
    assert not process_exists(pid)


def test_provider_concurrency_is_serialized(tmp_path):
    async def scenario():
        runner = CodexRunner(make_settings(tmp_path, timeout=3.0))
        active = 0
        peak = 0
        original = runner._interpret_locked

        async def wrapped(req):
            nonlocal active, peak
            active += 1
            peak = max(peak, active)
            await asyncio.sleep(0.05)
            try:
                return await original(req)
            finally:
                active -= 1

        runner._interpret_locked = wrapped
        await asyncio.gather(runner.interpret(request()), runner.interpret(request()))
        return peak

    assert asyncio.run(scenario()) == 1

def test_total_request_timeout_includes_semaphore_wait(tmp_path):
    async def scenario():
        settings = replace(
            make_settings(tmp_path, timeout=5.0),
            request_timeout_seconds=0.05,
        )
        runner = CodexRunner(settings)
        await runner._semaphore.acquire()
        try:
            with pytest.raises(ProviderTimeoutError):
                await runner.interpret(request())
        finally:
            runner._semaphore.release()

    asyncio.run(scenario())


def test_provider_output_file_size_is_bounded(tmp_path):
    settings = replace(
        make_settings(tmp_path),
        max_provider_output_bytes=8,
    )
    runner = CodexRunner(settings)
    with pytest.raises(ProviderInvalidOutputError):
        asyncio.run(runner.interpret(request()))
