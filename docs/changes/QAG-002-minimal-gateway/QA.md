# QAG-002 — Minimal Private Gateway — QA

Status: **COMPLETE — PASS_WITH_NOTES**

## Deterministic local evidence

Environment:

- macOS development host
- Python 3.14.7
- isolated `.venv` under `gateway/`

Final deterministic verification:

- clean editable package install: PASS
- `python -m compileall -q quickaside_gateway`: PASS
- pytest: **33 passed, 2 upstream deprecation warnings**
- `git diff --check`: PASS

The two warnings originate from FastAPI/Starlette test-client dependencies:

- Starlette `TestClient` / `httpx` deprecation;
- AnyIO `BlockingPortal` alias deprecation.

They are recorded as NOTE findings and do not block QAG-2.

## Real provider evidence

Runtime:

- isolated Codex CLI: `codex-cli 0.154.0`
- isolated QAG-2 `CODEX_HOME` authentication: PASS
- model: `gpt-5.6-luna`
- reasoning effort: explicit `low`
- invocation: `codex exec --ephemeral` through repository `CodexRunner`

Representative successful cases:

- `Agrega leche al mandado` -> `AddListItem(mandado, leche)`
- `Mañana revisa el PR` -> `CreateTask(TRABAJO, Revisar el PR, 2026-09-14)`
- explicit note -> `CreateNote`
- `Hoy hice 210 lbs en press inclinado` -> `CreateStructuredLog` with gateway map conversion
- `Deshaz lo último` -> `UndoLast`

Targeted semantic routing smoke after remediation:

- `Revisar ticket de Jira mañana` -> `TRABAJO`
- `Preparar demo para el cliente el lunes` -> `TRABAJO`
- `Arreglar bug de mi app personal` -> `PERSONAL`
- workplace-specific check-in / 1:1 phrasing -> `TRABAJO`
- workplace-specific deploy-communication phrasing -> `TRABAJO`

Result: **5/5 PASS**.

Residual QAG-2 Codex process check after live requests: **NONE**.

## Failures found during environment QA

Two real-provider failures were discovered and fixed before the first full
review:

1. `Mañana revisa el PR` initially routed to `PERSONAL`. The prompt routing
   rule was tightened to encode the accepted project rule that software/work
   context maps to `TRABAJO` unless explicitly personal.
2. A subsequent real run produced `CreateTask` with `title: null`. The
   gateway correctly rejected it. The prompt now defines required and
   non-applicable fields per action type, and deterministic regression
   coverage protects the contract.

These failures remain documented because they directly improved the runtime
contract.

## First full-review findings and remediation

The first full diff review returned `BLOCKED` with:

- MAJOR: request/provider output and total queue+execution wall clock were not fully bounded;
- MAJOR: non-applicable non-null provider fields could be silently discarded during normalization;
- MINOR: `capturedAt` accepted non-RFC3339 wire representations;
- MINOR: QA evidence had not yet been persisted.

Remediation verified:

- raw HTTP body bound: 32 KiB;
- capture text bound: 4,000 characters;
- action count bound: 16;
- structured-log fields bound: 32;
- provider result-file bound: 64 KiB;
- provider-process timeout: 15 seconds;
- total request timeout including semaphore wait: 20 seconds;
- non-applicable provider fields rejected before conversion;
- strict RFC3339 string wire format enforced for `capturedAt`;
- provider output is treated as untrusted and invalid output maps to the
  stable provider-invalid-output failure path.

## Final-review follow-up

The second review found one additional MINOR edge case: non-UTF-8 provider
output could escape the stable invalid-output contract. The runner now maps
`UnicodeDecodeError` to `ProviderInvalidOutputError`, with deterministic
regression coverage. Final pytest count after that fix is **33 passed**.

The same review also identified stale closure documentation as a MAJOR source-
of-truth issue. The QAG-2 closure reconciles `ACTIVE_WORK.md`,
`PROJECT_SPEC.md`, `ARCHITECTURE.md`, `ROADMAP.md`, and `AI_WORKFLOW.md` so
future work resumes at QAG-3 rather than incorrectly restarting QAG-2.

## Final verdict

**PASS_WITH_NOTES**

`BLOCKER 0 / MAJOR 0 / MINOR 0`

Notes:

- two upstream FastAPI/Starlette deprecation warnings remain non-blocking;
- QAG-2 intentionally stops before live VPS/network/systemd deployment;
- QAG-3 remains a separate HIGH-ASSURANCE change requiring explicit user
  approval;
- no new ADR is required for QAG-2.
