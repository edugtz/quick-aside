# Change 014 — Notes UI — PLAN

Governance: **STANDARD**  
Status: **IN PROGRESS**

## Preflight

- Confirm the `chg-014-notes-ui` branch and preserve the historical Change
  001–013 packages.
- Read the accepted project, architecture, roadmap, acceptance, naming, UX,
  and Changes 005, 008, and 013 guidance.
- Inspect the canonical v3 visual reference before implementation.
- Inspect the current Memoria/Capture History UI, Listas nested-route pattern,
  app-scoped `MemoryStore`, Room memory implementation, Note domain model, and
  existing UI/integration tests.
- Confirm Room 3.0.2, KSP 2.3.6, SQLite 2.7.0, database version 4, and
  schemas 1–4 remain the baseline.

## Implementation approach

1. Add `MemoryRoute` beside the existing `ListsRoute`. Keep History as the
   default route, reset it whenever bottom navigation is selected, and handle
   toolbar/system back from Notes to History.
2. Pass the already app-scoped `MemoryStore` from `MainActivity` into
   `QuickAsideApp`; keep it optional only for existing test compatibility.
3. Add a restrained `Notas` action to the existing Capture History screen.
4. Add focused `ui/memory/NotesScreen.kt` state, creation, error, retry, and
   read-only row rendering. Call only `MemoryStore` from Compose.
5. Add a focused injectable Note timestamp formatter using device defaults in
   production and fixed zone/locale inputs in tests.
6. Preserve exact input, explicitly pass `sourceCaptureId = null`, disable
   blank/saving/duplicate submissions, propagate cancellation, and update the
   visible list from `Saved(note)` with domain ordering.
7. Add deterministic Compose instrumentation coverage with a fake
   `MemoryStore` for discoverability, load/empty/failure/retry, ordering,
   exact-input, null source, success, failure, duplicate-save, read-only,
   back, nested reset, and existing history regressions.
8. Add one small named-database Room integration test for the production
   `RoomMemoryStore` create/read/close/reopen path without changing the
   Change 013 persistence suite or using `quick_aside.db`.
9. Run targeted checks, then all required gates, schema/dependency inspections,
   diff checks, and real-device visual evidence. Report blocked infrastructure
   accurately without claiming a final PASS.

## Test isolation

- Compose tests use a fake `MemoryStore` and never access Room from the UI
  assertions.
- Room integration uses a unique named v4 database and deletes only that
  dedicated test database during cleanup.
- Existing Capture History and Listas tests remain unchanged unless a focused
  wiring update is necessary for the new optional dependency.

## Stop signals

Stop and report if implementation needs schema 5, a migration, a new memory
operation, DAO access from Compose, source Capture fabrication, editing or
deletion semantics, or a major Memoria/navigation redesign.
