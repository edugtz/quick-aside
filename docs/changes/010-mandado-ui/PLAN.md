# Change 010 — Mandado UI — PLAN

## Preflight

- Confirm the expected `chg-010-mandado-ui` branch and clean working tree.
- Read the accepted product, architecture, roadmap, acceptance, and UX
  contracts plus the Change 002 and Change 009 packages.
- Inspect the canonical v3 visual reference before implementation.
- Inspect the existing ListStore/RoomListStore, app-scoped wiring, navigation
  shell, and current UI/instrumentation tests.
- Preserve Room 3.0.2, SQLite 2.7.0, KSP 2.3.6, schemas, and all Change 009
  production contracts.

## Minimal implementation approach

1. Add `ui/lists/ListsScreen.kt` for the Listas hub. Keep Mandado interactive,
   render Compras as deferred, and avoid introducing another destination.
2. Add `ui/lists/MandadoScreen.kt` with a focused state model and coroutine
   operations against `ListStore` only.
3. Keep nested route state in `QuickAsideApp`: Listas root versus Mandado.
   Use `BackHandler` and a top-app-bar back action for the nested route.
4. Pass the app-scoped `listStore` from `MainActivity`; retain optional UI
   injection compatibility for existing capture tests.
5. Use native Material controls and Spanish content descriptions/feedback.
   Keep completed rows visible with checked semantics and supporting text
   rather than color alone.
6. Add focused Compose tests with deterministic fake ListStore outcomes for
   root exposure, lifecycle, exact input, completion, confirmation, errors,
   and back behavior.
7. Add a dedicated named-database Android integration test using real
   `RoomListStore`: start, add two, complete one, close/reopen, verify state,
   finish, and verify retained session/items through history reads.
8. Run the full required verification gates and collect representative visual
   evidence without changing production database/schema artifacts.

## State and error policy

Initial load reads the active Mandado session and, when present, its current
items. Successful start/add/finish operations reload or deterministically
replace visible state. Failed operations retain user-entered text or the
persisted visible checkbox state and show concise Spanish feedback. Coroutine
cancellation is rethrown.

## Verification sequence

- Compile/test the new UI and fake-store tests first.
- Run the real Room integration flow and the existing connected suite.
- Run unit tests, assemble, lint, connected tests, and diff checks.
- Inspect DB version/schema/dependency diffs and direct DAO references.
- Capture the Listas root, an active checked-item Mandado, and the finish
  confirmation or returned no-active state.

## Non-goals and split signals

Do not broaden into history, Compras, AI/routing, capture interpretation,
schema work, deletion, reorder, generic repositories, or framework
introduction. Split/report if any becomes necessary for the active Mandado
flow.
