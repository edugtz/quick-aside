# QAG-002 — Minimal Private Gateway — SPEC

Governance: **STANDARD**
Status: **ACTIVE**
Branch: `qag-002-minimal-gateway`

## Objective

Implement the smallest testable Quick Aside-owned HTTP gateway that exposes
the accepted provider-neutral interpretation boundary and invokes the QAG-1
runtime:

```text
HTTP request
    -> Quick Aside gateway
    -> bounded codex exec --ephemeral
    -> GPT-5.6 Luna / Low
    -> strict provider schema
    -> provider-neutral actions
```

This change proves the gateway implementation locally and in an isolated
integration environment.

It does **not** deploy the service to production, expose it through
Tailscale, modify systemd/UFW, or connect Android.

## Proven baseline

- QAG-0: COMPLETE — PASS.
- QAG-1: COMPLETE — PASS.
- ADR-0001/0002/0003 remain accepted.
- Primary provider runtime:
  `gpt-5.6-luna` with explicit Low reasoning.
- Provider invocation:
  `codex exec --ephemeral`.
- Validated Codex CLI version:
  `0.154.0`.
- Android provider output remains untrusted.
- `sourceCaptureId` remains Android-owned.
- Current Android `AIInterpretationRequest` contains only `inputText`.
- Current supported draft actions are:
  - AddListItem
  - CreateTask
  - CreateNote
  - CreateStructuredLog
  - UndoLast

## Gateway implementation

The gateway will be a dedicated Python package under:

`gateway/`

Initial server stack:

- Python 3.12–3.14 compatible package;
- FastAPI `0.141.1`;
- Pydantic `2.13.5`;
- Uvicorn `0.52.4`.

The target VPS remains Python 3.14.4.

This does not reuse the Hermes Node/Python environment.

## HTTP contract

### POST `/v1/interpret`

Request:

```json
{
  "inputText": "Mañana revisa el PR",
  "capturedAt": "2026-09-13T21:24:00Z",
  "timeZone": "America/Mexico_City"
}
```

Contract rules:

- `inputText` must be non-blank and at most 4,000 characters.
- `capturedAt` must be an offset-aware RFC3339 timestamp.
- `timeZone` must be a valid IANA timezone.
- the raw HTTP request body is capped at 32 KiB;
- the gateway normalizes the trusted instant to UTC;
- relative-date interpretation receives both the trusted instant and local
  time derived from the supplied timezone;
- `sourceCaptureId` is deliberately absent.

Successful response:

```json
{
  "actions": [
    {
      "type": "CreateTask",
      "space": "TRABAJO",
      "title": "Revisar el PR",
      "dueDate": "2026-09-14"
    }
  ]
}
```

The HTTP action shape mirrors the existing Android draft-action boundary.

## Structured-log wire adaptation

Android uses:

```text
Map<String, String>
```

The strict Codex provider schema may represent structured-log fields
internally as:

```json
[
  {"key": "exercise", "value": "press inclinado"},
  {"key": "weight", "value": "210 lbs"}
]
```

The gateway converts that provider-only representation back to the existing
HTTP/domain shape:

```json
{
  "exercise": "press inclinado",
  "weight": "210 lbs"
}
```

Duplicate provider keys are invalid provider output.

This provider-specific workaround must not alter the Android/domain model.

## Health endpoints

### GET `/healthz`

Process liveness only.

It must not invoke Codex.

### GET `/readyz`

Verifies local gateway/provider prerequisites including:

- configured Codex executable exists and is executable;
- configured Quick Aside `CODEX_HOME` exists;
- configured isolated workspace exists;
- provider output schema exists;
- Codex reports the expected pinned version.

QAG-2 readiness does not claim OAuth validity. Live OAuth/provider
availability is verified by integration evidence and later QAG-3 deployment
checks.

## Provider process lifecycle

Each interpretation request must:

1. build a bounded prompt from request data;
2. invoke one fresh `codex exec --ephemeral`;
3. use:
   - `--ignore-user-config`
   - `--ignore-rules`
   - Luna
   - Low reasoning
   - read-only sandbox
   - isolated workspace
   - strict output schema
4. capture the final response in a temporary file;
5. validate provider JSON;
6. convert it to provider-neutral HTTP actions;
7. terminate with no persistent Codex process.

Provider stdout/stderr must not be forwarded into normal application logs.

## Resource policy

Initial maximum provider concurrency:

**1**

Rationale:

- personal single-user service;
- target VPS has 2 vCPU / ~3.7 GiB RAM / no swap;
- QAG-1 observed roughly ~200 MiB peak per ephemeral Codex invocation;
- serialization is sufficient for the first usable runtime.

Requests may wait on the single provider slot, but the complete request wall clock is bounded.

Provider output is also bounded: at most 16 actions, at most 32 structured-log fields per action, and at most 64 KiB in the provider result file.

No concurrency=2 requirement exists for QAG-2.

## Timeout

Initial provider-process timeout:

**15 seconds**

Initial total interpretation timeout, including semaphore wait:

**20 seconds**

This is a safety cutoff, not the final UX latency SLO.

QAG-1 normal requests were substantially below this threshold.

On timeout or coroutine cancellation, the gateway must terminate the Codex
process group and reap the process.

## Failure contract

Provider/runtime failures return stable error classes without raw provider
output or capture content:

- HTTP 504 — `provider_timeout`
- HTTP 503 — `provider_unavailable`
- HTTP 502 — `provider_invalid_output`
- HTTP 413 — `request_too_large`

Invalid HTTP input remains request validation failure.

## Privacy / logging

Normal logs may contain:

- generated request ID;
- duration;
- action count;
- stable failure code.

Normal logs must not contain:

- raw capture text;
- provider prompt;
- provider response;
- OAuth credentials;
- auth headers;
- provider stderr/stdout;
- Android sourceCaptureId.

The provider child process receives an allowlisted environment rather than
inheriting arbitrary gateway environment variables.

## Supported semantic scope

QAG-2 implements only the current action families already represented by the
Android provider-neutral boundary.

QAG-2 does not add:

- events;
- reminders;
- queries;
- clarification outcomes;
- unsupported outcomes;
- action execution.

Those remain separate product/runtime work.

## Acceptance scenarios

1. Valid request timestamp + timezone produce trusted local temporal context.
2. Invalid/blank input or invalid timezone is rejected.
3. All five current action families can be decoded.
4. Structured-log provider pairs convert deterministically to a field map.
5. Duplicate structured-log keys and non-applicable non-null provider fields are rejected.
6. Correct Luna/Low/ephemeral Codex command is constructed.
7. Provider timeout terminates/reaps the process.
8. Provider cancellation terminates/reaps the process.
9. Invalid provider JSON returns `provider_invalid_output`.
10. Provider process failure returns `provider_unavailable`.
11. Health does not invoke the provider.
12. Readiness rejects missing/wrong provider prerequisites.
13. Concurrency is bounded to one by default and total request wall clock is bounded.
14. HTTP input, semantic input, action count, structured-log field count, and provider output size are bounded.
15. `capturedAt` rejects non-RFC3339 wire representations before provider execution.
16. Error responses/logging do not expose raw capture/provider data.
17. Deterministic automated tests pass.
18. Before final QAG-2 PASS, the final provider schema and prompt are
    exercised against the real isolated Luna Low runtime.
19. QAG-2 does not modify production VPS/network/systemd/Android state.

## Explicit exclusions

- No QAG-3 deployment.
- No production Linux user.
- No systemd unit.
- No Tailscale changes.
- No UFW changes.
- No public listener.
- No Android INTERNET permission.
- No Android HTTP client.
- No DeepSeek fallback.
- No Google Tasks/Calendar.
- No Personal Admin/Hermes changes.
