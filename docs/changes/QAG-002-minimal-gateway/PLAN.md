# QAG-002 — Minimal Private Gateway — PLAN

Governance: **STANDARD**

## Implementation approach

Build one isolated Python service under `gateway/`.

Proposed structure:

```text
gateway/
  pyproject.toml
  .gitignore
  README.md

  quickaside_gateway/
    __init__.py
    app.py
    config.py
    contracts.py
    codex_runner.py
    prompt.py
    provider_output_schema.json

  tests/
    test_app.py
    test_contracts.py
    test_prompt.py
    test_runner.py
```

## Server stack

Use FastAPI/Pydantic/Uvicorn because:

- the target host already has a supported Python runtime;
- the service is small and JSON-contract heavy;
- Pydantic gives deterministic boundary validation;
- asyncio subprocess handling supports timeout/cancellation without a
  persistent provider daemon;
- it avoids reusing Hermes' Node environment.

Direct dependencies are pinned during QAG-2.

## Contract strategy

Keep the HTTP result aligned with the existing Android provider-neutral
draft-action boundary.

Add only the missing trusted temporal transport:

- `capturedAt`
- `timeZone`

Do not transport `sourceCaptureId`.

Bound the public request before provider execution: stream at most 32 KiB, allow at most 4,000 capture characters, and require an RFC3339 timestamp string plus a valid IANA timezone.

## Provider schema strategy

Use a committed JSON Schema for Codex output.

Keep dynamic structured-log keys out of the provider's strict object shape
by using a temporary provider-only list of `{key,value}` pairs.

Convert and revalidate before producing the HTTP response.

## Process strategy

Use `asyncio.create_subprocess_exec`.

Start each provider request in a new process session so timeout/cancellation
can terminate the entire process group.

Send the capture prompt through stdin.

Send normal provider stdout/stderr to DEVNULL.

Read only the dedicated `--output-last-message`/`-o` result file and reject it before parsing if it exceeds 64 KiB.

## Environment strategy

Pass only a small explicit environment to Codex:

- `CODEX_HOME`
- `HOME`
- `PATH`
- locale
- explicit system TLS certificate paths when present.

Do not blindly inherit service secrets or arbitrary environment variables.

## Concurrency

Wrap the provider runner in an asyncio semaphore.

Default capacity: 1.

Wrap semaphore wait plus provider execution in a 20-second total request deadline; retain the 15-second provider-process timeout inside that bound.

This is deliberately conservative for the first personal deployment.

## Verification

Local deterministic checks:

```text
python -m compileall
pytest
```

Tests use a fake executable provider and must cover:

- exact command flags;
- successful result;
- timeout;
- invalid output;
- readiness;
- API status mapping;
- temporal validation including strict RFC3339 wire format;
- request/provider output bounds;
- provider field applicability validation;
- serialization;
- max provider concurrency and bounded semaphore wait.

External integration check before QAG-2 closes:

- run the final schema/prompt through the isolated authenticated Codex
  runtime;
- verify Luna Low;
- verify representative current action types;
- verify no residual Codex process.

## Documentation alignment

During QAG-2:

- reconcile stale QAG-1 wording in `PROJECT_SPEC.md`;
- point `ACTIVE_WORK.md` at QAG-2;
- update architecture/roadmap only after implementation evidence supports
  the final gateway contract;
- create a new ADR only if QAG-2 establishes a durable new architecture
  decision worth preserving.

## Rollback

Before QAG-3 there is no production runtime state to roll back.

QAG-2 rollback is simply removal/revert of the repository gateway package.

Personal Admin/Hermes remains untouched.
