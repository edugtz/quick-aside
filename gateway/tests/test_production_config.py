import os
import subprocess
import sys

import pytest

from quickaside_gateway.admin_cli import DEFAULT_AUTH_DB
from quickaside_gateway.app import create_app
from quickaside_gateway.config import GatewaySettings


def test_from_env_requires_explicit_auth_db(
    monkeypatch,
):
    monkeypatch.delenv(
        "QUICKASIDE_AUTH_DB",
        raising=False,
    )

    with pytest.raises(
        ValueError,
        match="QUICKASIDE_AUTH_DB is required",
    ):
        GatewaySettings.from_env()


def test_create_app_rejects_missing_auth_db(tmp_path):
    settings = GatewaySettings(
        codex_bin="codex",
        codex_home=tmp_path,
        workspace=tmp_path,
        schema_path=tmp_path / "schema.json",
        auth_db_path=None,
    )

    with pytest.raises(
        ValueError,
        match="auth_db_path is required",
    ):
        create_app(
            settings=settings,
            runner=object(),
        )


def test_admin_cli_default_is_persistent_service_state():
    assert DEFAULT_AUTH_DB == (
        "/var/lib/quickaside/auth.db"
    )


def test_importing_app_has_no_runtime_side_effect(
    tmp_path,
):
    env = os.environ.copy()
    env.pop(
        "QUICKASIDE_AUTH_DB",
        None,
    )

    result = subprocess.run(
        [
            sys.executable,
            "-c",
            "import quickaside_gateway.app; print('import-ok')",
        ],
        cwd=tmp_path,
        env=env,
        capture_output=True,
        text=True,
        timeout=10,
        check=False,
    )

    assert result.returncode == 0, result.stderr
    assert result.stdout.strip() == "import-ok"
