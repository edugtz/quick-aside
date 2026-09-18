# Quick Aside Gateway

Repository-owned private interpretation gateway.

The gateway provides the server-side AI runtime used by Quick Aside while
keeping provider credentials and runtime state off the Android device.

Current production deployment work is tracked by:

    docs/changes/QAG-003R-private-tailnet-deployment/

The active ingress architecture is defined by ADR-0005.

## Runtime boundary

The gateway provides:

- HTTP interpretation contract;
- trusted capture instant plus IANA timezone;
- `codex exec --ephemeral`;
- GPT-5.6 Luna with explicit Low reasoning;
- strict provider output;
- timeout and cancellation cleanup;
- provider concurrency limited to one;
- health/readiness endpoints;
- QA1 request authentication;
- privacy-safe failure responses and logs.

Production FastAPI remains bound only to:

    127.0.0.1:2588

Private ingress is provided separately through:

    Tailscale
      -> svc:quickaside
      -> Tailscale Serve HTTPS
      -> 127.0.0.1:2588

Tailscale Funnel and public Quick Aside ingress are not part of the active
architecture.

Detailed deployment, verification and rollback procedures live in:

    gateway/deploy/README.md

Android/client integration remains a separate change after the VPS
deployment independently passes its production gates.

## Local verification

Create an isolated venv from this directory, install `.[test]`, then run:

    python -m compileall -q quickaside_gateway
    python -m pytest -q

## Runtime configuration

Production paths are defined by the reviewed deployment artifacts.

- `QUICKASIDE_AUTH_DB` — QA1 authentication database
- `QUICKASIDE_CODEX_BIN` — Codex executable path
- `QUICKASIDE_CODEX_HOME` — isolated Quick Aside Codex home
- `QUICKASIDE_WORKSPACE` — isolated Quick Aside provider workspace

Without the required isolated runtime paths, `/healthz` remains live while
`/readyz` reports not ready.

Quick Aside must not reuse Hermes or Personal Admin credentials, runtime
state or application configuration.
