# QAG-001 — Runtime / Protocol Decision — TASKS

## Preflight and runtime discovery

- [x] Confirm QAG-0 target-VPS baseline.
- [x] Create isolated temporary QAG-1 workspace and `CODEX_HOME`.
- [x] Verify Python 3.14 virtual environment and pip.
- [x] Install/verify official `openai-codex` SDK in the isolated venv.
- [x] Authenticate through ChatGPT/Codex OAuth without exposing credentials.
- [x] Discover live models.
- [x] Confirm `gpt-5.6-luna` and explicit Low reasoning support.

## SDK/app-server evidence

- [x] Verify installed SDK version/signatures.
- [x] Execute one strict-schema Luna Low smoke test.
- [x] Execute representative warm structured-output cases.
- [x] Measure cold initialization and resident memory.
- [x] Confirm memory growth across fresh ephemeral threads.
- [x] Confirm app-server exits cleanly when the SDK client closes.

## CLI control

- [x] Verify bundled Codex CLI and `exec` capabilities.
- [x] Execute three `codex exec --ephemeral` controls.
- [x] Confirm strict JSON Schema result.
- [x] Confirm explicit Luna Low configuration.
- [x] Measure elapsed time and peak RSS.
- [x] Confirm no Codex process remains after each request.
- [x] Compare CLI token/context usage with the equivalent SDK request.

## Decision and documentation

- [x] Select `codex exec --ephemeral` for the first gateway runtime.
- [x] Preserve SDK/app-server as a future alternative rather than deleting
  the abstraction.
- [x] Record QAG-0 prerequisite evidence and QAG-1 measurements in `QA.md`.
- [x] Record durable runtime-protocol choice in ADR-0003.
- [x] Reconcile `AGENTS.md`, `ARCHITECTURE.md`, `ROADMAP.md`,
  `AI_WORKFLOW.md`, and `ACTIVE_WORK.md`.
- [x] Review the actual branch diff and close documentation review before
  commit/push.

## Stop state

QAG-1 is technically complete.

Do not begin live deployment, Tailscale/systemd changes, or Android
networking in this branch.

The next independent gate is QAG-2 — minimal gateway implementation.
