from pathlib import Path


DEPLOY = Path(__file__).resolve().parents[1] / "deploy"


def test_caddy_public_boundary_is_explicit():
    config = (DEPLOY / "Caddyfile.quickaside").read_text()

    assert "{$QUICKASIDE_PUBLIC_HOST}" in config
    assert "max_size 32KB" in config
    assert "max_header_size 16KB" in config
    assert "read_body   15s" in config
    assert "read_header 5s" in config

    assert "path /healthz" in config
    assert "path /v1/pair" in config
    assert "path /v1/interpret" in config

    active_lines = [
        line.strip()
        for line in config.splitlines()
        if not line.strip().startswith("#")
    ]

    assert not any(
        "/readyz" in line
        for line in active_lines
    )

    assert "reverse_proxy 127.0.0.1:2588" in config
    assert "respond 404" in config


def test_caddy_does_not_enable_quickaside_access_logging():
    config = (DEPLOY / "Caddyfile.quickaside").read_text()

    lines = [
        line.strip()
        for line in config.splitlines()
        if not line.strip().startswith("#")
    ]

    assert not any(
        line == "log"
        or line.startswith("log ")
        or line.startswith("log {")
        for line in lines
    )


def test_systemd_unit_fixes_runtime_boundary():
    unit = (
        DEPLOY / "quickaside-gateway.service"
    ).read_text()

    required = [
        "User=quickaside",
        "Group=quickaside",
        "--factory",
        "--host 127.0.0.1",
        "--port 2588",
        "--workers 1",
        "--limit-concurrency 16",
        "--proxy-headers",
        "--forwarded-allow-ips 127.0.0.1",
        "EnvironmentFile=/etc/quickaside-gateway/gateway.env",
        "NoNewPrivileges=true",
        "ProtectSystem=strict",
        "ProtectHome=true",
        "ReadWritePaths=/var/lib/quickaside",
        "UMask=0077",
    ]

    for value in required:
        assert value in unit

    assert "--forwarded-allow-ips *" not in unit
    assert "--host 0.0.0.0" not in unit


def test_gateway_environment_uses_quickaside_owned_state():
    env = (DEPLOY / "gateway.env.example").read_text()

    assert (
        "QUICKASIDE_AUTH_DB=/var/lib/quickaside/auth.db"
        in env
    )
    assert (
        "QUICKASIDE_CODEX_HOME="
        "/var/lib/quickaside/codex-home"
        in env
    )

    assert "/home/hermes" not in env
    assert "tailscale" not in env.lower()


def test_caddy_hostname_is_explicit_environment_contract():
    env = (DEPLOY / "caddy.env.example").read_text()
    drop_in = (
        DEPLOY / "caddy-quickaside-env.conf"
    ).read_text()

    assert "QUICKASIDE_PUBLIC_HOST=" in env
    assert (
        "EnvironmentFile=/etc/quickaside-gateway/caddy.env"
        in drop_in
    )



def _extract_server_block(
    config: str,
    listener: str,
) -> str:
    marker = f"servers {listener} {{"
    start = config.index(marker)
    brace_start = config.index("{", start)

    depth = 0

    for index in range(brace_start, len(config)):
        character = config[index]

        if character == "{":
            depth += 1
        elif character == "}":
            depth -= 1

            if depth == 0:
                return config[start:index + 1]

    raise AssertionError(
        f"unterminated server block: {listener}"
    )


def test_http_and_https_listeners_are_both_hardened():
    config = (
        DEPLOY / "Caddyfile.quickaside"
    ).read_text()

    assert "http:// {" in config

    http = _extract_server_block(
        config,
        ":80",
    )

    https = _extract_server_block(
        config,
        ":443",
    )

    assert "read_header 5s" in http
    assert "read_body   10s" in http
    assert "write       15s" in http
    assert "idle        15s" in http
    assert "max_header_size 16KB" in http

    assert "read_header 5s" in https
    assert "read_body   15s" in https
    assert "write       60s" in https
    assert "idle        30s" in https
    assert "max_header_size 16KB" in https


def test_uvicorn_access_logging_is_disabled():
    unit = (
        DEPLOY / "quickaside-gateway.service"
    ).read_text()

    assert "--no-access-log" in unit



def test_caddy_admin_control_plane_is_permissioned():
    config = (
        DEPLOY / "Caddyfile.quickaside"
    ).read_text()

    drop_in = (
        DEPLOY / "caddy-quickaside-env.conf"
    ).read_text()

    gateway_unit = (
        DEPLOY / "quickaside-gateway.service"
    ).read_text()

    assert (
        "admin unix//run/caddy-admin/admin.sock|0600"
        in config
    )

    assert "localhost:2019" not in config

    assert (
        "RuntimeDirectory=caddy-admin"
        in drop_in
    )
    assert (
        "RuntimeDirectoryMode=0700"
        in drop_in
    )

    assert "User=quickaside" in gateway_unit
    assert "Group=quickaside" in gateway_unit


def test_http_catch_all_returns_404():
    config = (
        DEPLOY / "Caddyfile.quickaside"
    ).read_text()

    marker = "http:// {"
    start = config.index(marker)
    end = config.index("}", start)

    block = config[start:end + 1]

    assert "respond 404" in block



def test_caddy_reload_uses_service_environment_contract():
    drop_in = (
        DEPLOY / "caddy-quickaside-env.conf"
    ).read_text()

    readme = (
        DEPLOY / "README.md"
    ).read_text()

    assert (
        "EnvironmentFile=/etc/quickaside-gateway/caddy.env"
        in drop_in
    )

    assert "ExecReload=" in drop_in

    assert (
        "ExecReload=/usr/bin/caddy reload "
        "--address unix//run/caddy-admin/admin.sock "
        "--config /etc/caddy/Caddyfile "
        "--adapter caddyfile --force"
        in drop_in
    )

    assert "sudo systemctl reload caddy" in readme
    assert "sudo caddy reload" not in readme



def test_caddy_version_is_explicitly_reviewed():
    versions = (
        DEPLOY / "reviewed-versions.env"
    ).read_text()

    readme = (
        DEPLOY / "README.md"
    ).read_text()

    assert "CADDY_VERSION=v2.11.4" in versions
    assert "CADDY_REVIEWED_DATE=2026-09-15" in versions

    assert "QAG-003 reviewed version: `v2.11.4`" in readme
    assert "still `2.latest`" in readme
    assert "deployment is BLOCKED" in readme

    # A loose historical minimum must not be the production contract.
    assert "Caddy >= 2.10" not in readme


def test_caddy_version_is_checked_before_activation():
    readme = (
        DEPLOY / "README.md"
    ).read_text()

    assert "expected_version=" in readme
    assert "actual_version=" in readme
    assert 'test "$actual_version" = "$expected_version"' in readme

    assert "official Caddy security policy" in readme
