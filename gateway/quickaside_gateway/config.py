from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


DEFAULT_PAIRING_CODE_TTL_SECONDS = 600
MAX_PAIRING_CODE_TTL_SECONDS = 900


@dataclass(frozen=True)
class GatewaySettings:
    codex_bin: str
    codex_home: Path | None
    workspace: Path | None
    schema_path: Path
    auth_db_path: Path | None = None
    expected_codex_version: str = "0.154.0"
    model: str = "gpt-5.6-luna"
    reasoning_effort: str = "low"
    timeout_seconds: float = 15.0
    request_timeout_seconds: float = 20.0
    max_concurrency: int = 1
    max_request_body_bytes: int = 32 * 1024
    max_provider_output_bytes: int = 64 * 1024
    auth_timestamp_skew_seconds: int = 120
    replay_nonce_ttl_seconds: int = 300
    pairing_code_ttl_seconds: int = DEFAULT_PAIRING_CODE_TTL_SECONDS
    pair_rate_limit_count: int = 10
    pair_rate_limit_window_seconds: float = 60.0
    auth_global_rate_limit_count: int = 600
    auth_global_rate_limit_window_seconds: float = 60.0
    auth_attempt_rate_limit_count: int = 300
    auth_attempt_rate_limit_window_seconds: float = 60.0
    interpret_rate_limit_count: int = 60
    interpret_rate_limit_window_seconds: float = 60.0

    @classmethod
    def from_env(cls) -> "GatewaySettings":
        package_dir = Path(__file__).resolve().parent
        codex_home = os.environ.get("QUICKASIDE_CODEX_HOME")
        workspace = os.environ.get("QUICKASIDE_WORKSPACE")
        auth_db = os.environ.get("QUICKASIDE_AUTH_DB")
        if not auth_db:
            raise ValueError(
                "QUICKASIDE_AUTH_DB is required"
            )
        return cls(
            codex_bin=os.environ.get("QUICKASIDE_CODEX_BIN", "codex"),
            codex_home=Path(codex_home) if codex_home else None,
            workspace=Path(workspace) if workspace else None,
            schema_path=package_dir / "provider_output_schema.json",
            auth_db_path=Path(auth_db),
        )
