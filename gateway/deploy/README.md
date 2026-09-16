# Quick Aside gateway deployment contract

These files define the reviewed production boundary for QAG-003.
They are deployment inputs, not evidence that deployment has occurred.

## Required runtime shape

Public Internet
-> Caddy :443
-> 127.0.0.1:2588
-> one Uvicorn worker
-> FastAPI
-> bounded Codex provider

Port 2588 must never be publicly exposed.

Tailscale is not part of the Quick Aside runtime request path.

## Caddy control plane

Caddy's Admin API must not listen on localhost:2019.

The reviewed deployment uses:

    unix//run/caddy-admin/admin.sock

The Caddy systemd drop-in must create:

    RuntimeDirectory=caddy-admin
    RuntimeDirectoryMode=0700

The admin socket itself is configured as mode 0600.

The `quickaside` service user must not be able to traverse the runtime
directory or connect to the Caddy Admin API.

Graceful Caddy reloads must target the reviewed admin address, for example:

    sudo systemctl reload caddy

The Caddy systemd drop-in overrides `ExecReload` so reload runs with the
same `EnvironmentFile=/etc/quickaside-gateway/caddy.env` used by the
service and targets the permissioned Unix Admin socket.

Do not run `caddy reload` directly from an operator shell for this
deployment. The Caddyfile contains `{$QUICKASIDE_PUBLIC_HOST}`, so
adaptation must occur within the controlled caddy.service environment.

Gate F/G runtime verification must explicitly prove that a process running
as `quickaside` cannot connect to the admin socket.

## Caddy requirements

- Caddy security version contract:

- QAG-003 reviewed version: `v2.11.4`.
- The exact reviewed value is stored in `deploy/reviewed-versions.env`.
- Gate E must verify that the installed Caddy version equals that value.
- Gate E must also independently verify against the official Caddy
  security policy/releases that the reviewed version is still `2.latest`.
- If a newer stable `2.latest` exists, deployment is BLOCKED until the
  reviewed version is updated and the version delta is re-reviewed.
- Older Caddy releases are not accepted merely because they satisfy a
  minimum semantic version.
- Only these public application routes are allowed:
  - GET /healthz
  - POST /v1/pair
  - POST /v1/interpret
- /readyz is not public.
- All other paths/methods return 404.
- Request bodies are capped at 32 KiB before FastAPI.
- Request headers are capped at 16 KiB.
- Header/body/client-write timeouts are finite.
- Upstream timeouts are finite.
- Quick Aside access logging is intentionally disabled.
- X-QA-Signature and X-QA-Nonce must never be access logged.
- The public hostname is provided through QUICKASIDE_PUBLIC_HOST.
- Caddy must proxy only to 127.0.0.1:2588.

## Uvicorn requirements

The systemd unit fixes:

- --factory
- --host 127.0.0.1
- --port 2588
- --workers 1
- --limit-concurrency 16
- --proxy-headers
- --forwarded-allow-ips 127.0.0.1

Do not replace forwarded-allow-ips with "*".

Application rate limiting is process-local, therefore production must remain
single-worker unless rate limiting is redesigned around shared state.

## Persistent state

- /var/lib/quickaside/auth.db
- /var/lib/quickaside/codex-home
- /var/lib/quickaside/workspace

The service may write only under /var/lib/quickaside.

## Isolation

The deployment must not read, write, restart, reconfigure, or reuse:

- /home/hermes
- Personal Admin state
- Hermes credentials
- Hermes systemd units
- Personal Admin ACK
- Tailscale configuration

### Caddy version gate

From the deployed gateway checkout:

    expected_version="$(sed -n 's/^CADDY_VERSION=//p' gateway/deploy/reviewed-versions.env)"
    actual_version="$(caddy version | awk '{print $1}')"
    test "$actual_version" = "$expected_version"

This local equality check is necessary but not sufficient. Gate E must also
verify from the official Caddy security policy and releases that
`expected_version` is still the supported stable `2.latest`.

Do not activate the public ingress if those checks disagree.

## Validation before activation

Before any Caddy reload:

    caddy validate \
      --config /etc/caddy/Caddyfile \
      --adapter caddyfile \
      --envfile /etc/quickaside-gateway/caddy.env

Before starting the gateway service:

    systemd-analyze verify \
      /etc/systemd/system/quickaside-gateway.service

Actual mutation commands belong to Gate E and require explicit user approval.
