# Change 012 — Mandado History UI — PLAN

## Preflight

- Confirm the expected `chg-012-mandado-history-ui` branch and clean starting
  worktree.
- Preserve the accepted project, architecture, roadmap, acceptance, naming,
  UX, and Changes 009–011 contracts.
- Inspect the canonical v3 visual reference and keep current Mandado primary,
  history discoverable but restrained, and management calm/information-dense.
- Confirm the existing `ListStore` already supplies session-plus-items data,
  deterministic ordering, `endedAt`, and no-write reads.
- Confirm Room remains version 3 with schemas 1/2/3 and no migration is needed.

## Minimal implementation approach

1. Extend the local Listas route state with History and History Detail while
   preserving the four `AppDestination` entries and bottom-nav reset behavior.
2. Add a small `Historial` action to `MandadoScreen`; keep the history list and
   detail in separate focused files.
3. Implement `MandadoHistoryScreen` with a small state model. Read only through
   `ListStore.readRecentSessions`, filter active sessions at presentation, and
   preserve the supplied order.
4. Add a focused local date/time formatter using `java.time`, with injected
   timezone/locale seams for deterministic tests.
5. Implement `MandadoHistoryDetailScreen` from the selected immutable
   `ListSessionWithItems`. Render completion state with non-interactive visual
   semantics and no mutation callbacks.
6. Add deterministic Compose coverage for discoverability, both active/no
   active paths, filtering/order/count/date, empty/failure/retry, detail
   content, read-only writes, back behavior, and nested-route reset. Preserve
   existing Mandado/Compras/capture tests.
7. Add a dedicated named-database Room integration test for sessions A/B and
   active C, including completion, attachment/order, filtering, and
   close/reopen durability.
8. Capture/inspect representative screenshots against the written UX and v3
   visual direction.

## State and error policy

- History load failure becomes `No se pudo cargar el historial.`; no internal
  exception text is exposed.
- CancellationException is rethrown.
- Detail uses the loaded object, so opening a valid selected row makes no
  additional read or write. Any defensive invalid/active selection must not be
  shown as historical detail.
- No history action can call start, finish, add, or completion APIs.

## Verification sequence

- Run focused unit tests for time formatting and focused Android UI tests.
- Run the dedicated real Room history integration test and existing list/capture
  regressions.
- Run `./gradlew :app:testDebugUnitTest`, `:app:assembleDebug`,
  `:app:lintDebug`, and `:app:connectedDebugAndroidTest`.
- Run `git diff --check`, inspect `git status --short`, diff statistics, schema
  files, dependencies, and the Compose/DAO boundary.
- Record visual evidence and every unavailable/failed gate without declaring
  the final verdict.

## Stop signals

Stop and report if history requires schema 4, if active/completed sessions
cannot be distinguished, if read-only detail requires persistence mutation, or
if generic navigation/state architecture, reopen/delete/copy behavior, AI,
sync, or unrelated product scope becomes necessary.
