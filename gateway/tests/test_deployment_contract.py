from pathlib import Path

DEPLOY = Path(__file__).resolve().parents[1] / "deploy"


def _read(name: str) -> str:
    return (DEPLOY / name).read_text()


def test_caddy_deployment_artifacts_are_absent():
    obsolete = [
        "Caddyfile.quickaside",
        "caddy-quickaside-env.conf",
        "caddy.env.example",
        "reviewed-versions.env",
    ]

    for name in obsolete:
        assert not (DEPLOY / name).exists()


def test_systemd_unit_fixes_local_runtime_boundary():
    unit = _read("quickaside-gateway.service")

    required = [
        "User=quickaside",
        "Group=quickaside",
        "WorkingDirectory=/opt/quickaside-gateway/gateway",
        "EnvironmentFile=/etc/quickaside-gateway/gateway.env",
        "--factory",
        "--host 127.0.0.1",
        "--port 2588",
        "--workers 1",
        "--limit-concurrency 16",
        "--proxy-headers",
        "--forwarded-allow-ips 127.0.0.1",
        "--timeout-keep-alive 5",
        "--no-server-header",
        "--no-access-log",
    ]

    for value in required:
        assert value in unit

    assert "--host 0.0.0.0" not in unit
    assert "--forwarded-allow-ips *" not in unit


def test_systemd_hardening_contract_is_preserved():
    unit = _read("quickaside-gateway.service")

    required = [
        "UMask=0077",
        "NoNewPrivileges=true",
        "PrivateTmp=true",
        "ProtectSystem=strict",
        "ProtectHome=true",
        "ReadWritePaths=/var/lib/quickaside",
        "ReadOnlyPaths=/opt/quickaside-gateway",
        "ReadOnlyPaths=/etc/quickaside-gateway",
        "ProtectKernelTunables=true",
        "ProtectKernelModules=true",
        "ProtectControlGroups=true",
        "RestrictAddressFamilies=AF_UNIX AF_INET AF_INET6",
        "LockPersonality=true",
        "CapabilityBoundingSet=",
        "AmbientCapabilities=",
        "SystemCallArchitectures=native",
    ]

    for value in required:
        assert value in unit


def test_gateway_environment_uses_quickaside_owned_state():
    env = _read("gateway.env.example")

    assert "QUICKASIDE_AUTH_DB=/var/lib/quickaside/auth.db" in env
    assert "QUICKASIDE_CODEX_BIN=/opt/quickaside-gateway/bin/codex" in env
    assert "QUICKASIDE_CODEX_HOME=/var/lib/quickaside/codex-home" in env
    assert "QUICKASIDE_WORKSPACE=/var/lib/quickaside/workspace" in env

    assert "/home/hermes" not in env
    assert "tailscale" not in env.lower()
    assert "caddy" not in env.lower()


def test_uvicorn_access_logging_is_disabled():
    assert "--no-access-log" in _read("quickaside-gateway.service")


def _command_lines(text: str) -> list[str]:
    commands = []
    pending = ""

    for raw_line in text.splitlines():
        line = raw_line.strip()

        if pending:
            pending += " " + line
        elif line.startswith(
            ("tailscale ", "sudo ", "systemctl ", "ss ", "curl ", "ufw ")
        ):
            pending = line
        else:
            continue

        if pending.endswith("\\"):
            pending = pending[:-1].rstrip()
            continue

        command = " ".join(pending.split())

        if command.startswith("sudo "):
            command = command[5:].lstrip()

        commands.append(command)
        pending = ""

    return commands

def test_tailscale_service_contract_is_explicit():
    readme = _read("README.md")

    assert "svc:quickaside" in readme
    assert "--service=svc:quickaside" in readme
    assert "--https=443" in readme
    assert "http://127.0.0.1:2588" in readme
    assert "quickaside.taildc9db9.ts.net" in readme


def test_personal_admin_service_is_preserved():
    readme = _read("README.md")

    assert "svc:personal-admin-ack" in readme
    assert "http://127.0.0.1:2587" in readme
    assert "Never clear, reset, replace or otherwise mutate that Service" in readme


def test_funnel_is_never_used_for_activation():
    commands = _command_lines(_read("README.md"))

    funnel_commands = [
        command
        for command in commands
        if command.startswith("tailscale funnel ")
    ]

    assert funnel_commands
    assert all(
        command == "tailscale funnel status"
        for command in funnel_commands
    )


def test_global_serve_reset_is_not_an_executable_command():
    commands = _command_lines(_read("README.md"))

    assert "tailscale serve reset" not in commands
    assert not any(
        command.startswith("tailscale serve reset ")
        for command in commands
    )


def test_serve_clear_commands_are_scoped_to_quickaside():
    commands = _command_lines(_read("README.md"))

    clear_commands = [
        command
        for command in commands
        if command.startswith("tailscale serve clear")
    ]

    assert clear_commands
    assert all(
        command == "tailscale serve clear svc:quickaside"
        for command in clear_commands
    )


def test_control_plane_preflight_precedes_activation():
    readme = _read("README.md")

    preflight = readme.index("## Gate E — read-only preflight")
    control_plane = readme.index(
        "In the Tailscale control plane, verify before activation:"
    )
    activation = readme.index("## Gate E — private Service activation")

    assert preflight < control_plane < activation


def test_local_health_precedes_private_activation():
    readme = _read("README.md")

    local = readme.index("## Gate E — local gateway first")
    health = readme.index(
        "curl --fail --silent http://127.0.0.1:2588/healthz"
    )
    activation = readme.index("## Gate E — private Service activation")

    assert local < health < activation


def test_failure_path_log_verification_is_required():
    readme = _read("README.md")

    assert "## Failure-path log verification" in readme
    assert "journalctl -u tailscaled" in readme
    assert "journalctl -u quickaside-gateway" in readme
    assert "signature, nonce and capture canaries" in readme
    assert "gateway is healthy" in readme
    assert "backend" in readme
    assert "unavailable" in readme


def test_normal_rollback_drains_before_clear():
    readme = _read("README.md")

    rollback = readme.index("## Normal rollback")
    drain = readme.index(
        "tailscale serve drain svc:quickaside",
        rollback,
    )
    clear = readme.index(
        "tailscale serve clear svc:quickaside",
        rollback,
    )

    assert rollback < drain < clear


def test_no_public_quickaside_ingress_is_required():
    readme = _read("README.md")

    assert "Tailscale Funnel is prohibited" in readme
    assert "No public Quick Aside UFW rule is required." in readme
    assert "no public Quick Aside listener/UFW rule was introduced" in readme


def test_serve_drain_commands_are_scoped_to_quickaside():
    commands = _command_lines(_read("README.md"))

    drain_commands = [
        command
        for command in commands
        if command.startswith("tailscale serve drain")
    ]

    assert drain_commands
    assert all(
        command == "tailscale serve drain svc:quickaside"
        for command in drain_commands
    )


def test_firewall_commands_are_read_only():
    commands = _command_lines(_read("README.md"))

    firewall_commands = [
        command
        for command in commands
        if command.startswith("ufw ")
    ]

    assert firewall_commands
    assert all(
        command == "ufw status numbered"
        for command in firewall_commands
    )


def test_command_parser_detects_wrapped_and_multiline_security_mutations():
    commands = _command_lines(
        """\
sudo tailscale serve reset
sudo tailscale funnel \\
  --bg=443 \\
  http://127.0.0.1:2588
sudo tailscale serve clear svc:personal-admin-ack
sudo tailscale serve drain svc:personal-admin-ack
sudo ufw allow 443/tcp
"""
    )

    assert "tailscale serve reset" in commands
    assert any(
        command.startswith("tailscale funnel ")
        and command != "tailscale funnel status"
        for command in commands
    )
    assert "tailscale serve clear svc:personal-admin-ack" in commands
    assert "tailscale serve drain svc:personal-admin-ack" in commands
    assert "ufw allow 443/tcp" in commands


def test_existing_tailnet_policy_is_preserved_and_qa1_is_authorization_boundary():
    readme = _read("README.md")

    required = [
        "preserves the existing tailnet-wide access policy",
        "Do not modify tailnet grants or Access Controls",
        "unreachable from outside the tailnet",
        "tailnet connectivity follows the existing tailnet policy",
        "QA1 remains the application authorization boundary",
    ]

    for value in required:
        assert value in readme
