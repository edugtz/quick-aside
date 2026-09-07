# Change 015 — Structured Logs UI — PLAN

Governance: **STANDARD**  
Status: **IN PROGRESS**

## Preflight

- Confirm the `chg-015-structured-logs-ui` branch and clean worktree.
- Read accepted project, architecture, roadmap, acceptance, naming, UX, and
  Changes 013–014 guidance.
- Inspect the canonical v3 visual reference before implementation.
- Inspect the current Memoria/Capture History UI, Notes route and padding,
  app-scoped `MemoryStore`, StructuredLog domain/persistence, and existing
  UI/integration test patterns.
- Confirm Room version 4, schemas 1–4, and existing dependencies remain the
  baseline.

## Implementation approach

1. Add `StructuredLogs` beside `History` and `Notes` in the local Memory route.
   Keep History as default, reset it on bottom-navigation selection, and
   route toolbar/system back from Registros to History.
2. Add a restrained `Registros` action to Capture History beside `Notas`.
3. Add focused `StructuredLogsScreen.kt` state, retry, editor, validation,
   result handling, and read-only record cards. Call only `MemoryStore` from
   Compose.
4. Reuse the existing `NoteTimestampFormatter`, whose semantics are identical
   for Notes and Structured Logs, with device defaults in production and
   fixed zone/locale/clock inputs in tests.
5. Preserve exact field strings, pass `sourceCaptureId = null`, prevent blank,
   partial, duplicate-key, and duplicate-in-flight submissions, propagate
   cancellation, and locally insert `Saved(log)` deterministically.
6. Increase Notes LazyColumn bottom content padding by the smallest focused
   amount needed to clear the existing global FAB; do not redesign Notes.
7. Add deterministic fake-MemoryStore Compose coverage for the Change 015
   scenarios and targeted regressions for existing Notes/History navigation.
8. Add one small named v4 Room integration test for StructuredLog
   create/read/close/reopen durability without touching the Change 013 suite.
9. Run targeted checks, then all required gates, schema/dependency checks,
   diff checks, and real-device visual evidence. Report blocked
   infrastructure accurately without claiming a final PASS.

## Test isolation

- Compose tests use a fake `MemoryStore` and never access Room in UI tests.
- Room integration uses a unique named v4 database and deletes only that
  dedicated test database during cleanup.
- Existing Capture History, Notes, and Listas tests remain unchanged except
  where a focused wiring/assertion update is necessary.

## Stop signals

Stop and report if implementation needs schema 5, a migration, a new memory
operation, DAO access from Compose, source Capture fabrication, editing or
deletion semantics, duplicate-key persistence changes, or a broader
Memoria/navigation redesign.
