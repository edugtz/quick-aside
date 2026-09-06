# Change 011 — Compras UI — PLAN

## Preflight

- Confirm `chg-011-compras-ui` and preserve the clean starting worktree.
- Keep the accepted project, architecture, roadmap, acceptance, naming, UX,
  Change 009, and Change 010 contracts in force.
- Preserve Room 3 / database version 3, schemas 1/2/3, dependencies, and the
  existing app-scoped `ListStore` wiring.

## Minimal implementation approach

1. Extend `ListsScreen` only enough to make Compras an accessible interactive
   entry and pass an `onOpenCompras` callback.
2. Extend the existing local `QuickAsideApp` route with `Compras`, keeping the
   four `AppDestination` entries and resetting nested routes on bottom-nav
   changes.
3. Add a focused `ui/lists/ComprasScreen.kt`. Read only through `ListStore`,
   use a small `Loading` / `Loaded(items)` / `Failed` state, and keep add and
   completion mutation state local to the screen.
4. Use exact input text for `addItem`, pass `null` for the session, append the
   returned item on success, and preserve input on all non-success outcomes.
5. Call `setItemCompleted` with the visible item ID and requested state. Replace
   only the matching row on success; retain the old row on failure.
6. Use native Material controls, accessible labels, a calm card/list hierarchy,
   and visible checked treatment consistent with Mandado. Do not extract a
   generic list framework or refactor Mandado unrelated behavior.
7. Add deterministic Compras Compose tests with a fake `ListStore`, update only
   stale current test expectations needed by the new product contract, and add
   a real named-database integration test using `RoomListStore`.
8. Capture representative visual evidence for Listas, empty/usable Compras,
   and multiple checked/unchecked items.

## State and error policy

Loading errors become the focused Spanish load message. Add and completion
failures never fabricate success, never expose exception text/internal IDs, and
retain the user-visible input or checked state. `CancellationException` is
always rethrown. At most one add operation runs at a time, and each item has
its own completion-in-flight guard.

## Verification sequence

- Run focused unit/Android tests after implementation.
- Run the named-database Compras integration flow and the existing UI suite.
- Run `./gradlew :app:testDebugUnitTest`, `:app:assembleDebug`,
  `:app:lintDebug`, and `:app:connectedDebugAndroidTest`.
- Run `git diff --check` and inspect `git status --short` / diff statistics.
- Verify no schema/dependency/DAO-boundary/session-lifecycle regressions and
  record actual visual evidence without declaring the final verdict.

## Stop signals

Stop and report if Compras cannot be represented by the current continuous
`ListStore`, if schema version 4 or migration becomes necessary, if history /
delete / filtering is needed for basic usability, or if a navigation framework,
AI, STT, Google integration, or Mandado lifecycle change appears necessary.
