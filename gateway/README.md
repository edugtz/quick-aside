# Quick Aside Gateway

Repository-owned private interpretation gateway.

QAG-2 scope is local implementation and verification only:

- HTTP interpretation contract;
- trusted capture instant plus IANA timezone;
- `codex exec --ephemeral`;
- GPT-5.6 Luna with explicit Low reasoning;
- strict provider output;
- timeout and cancellation cleanup;
- provider concurrency limited to one;
- health/readiness endpoints;
- privacy-safe failure responses and logs.

Do not deploy this service, modify systemd/Tailscale/UFW, or connect Android
until later gated changes explicitly authorize those steps.

## Local verification

Create an isolated venv from this directory, install `.[test]`, then run:

    python -m compileall -q quickaside_gateway
    python -m pytest -q

## Runtime configuration

The actual deployment paths are deliberately not frozen in QAG-2.

- `QUICKASIDE_CODEX_BIN` — optional; defaults to `codex`
- `QUICKASIDE_CODEX_HOME` — required for readiness/provider calls
- `QUICKASIDE_WORKSPACE` — required for readiness/provider calls

Without the required isolated paths, `/healthz` remains live while `/readyz`
reports not ready.
