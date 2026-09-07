# Change 016 — Local Search Foundation — TASKS

## Change package and preflight

- [x] Confirm expected branch and clean worktree.
- [x] Read project, architecture, roadmap, acceptance, naming, UX, and prior
      Change 005/012/013/014/015 guidance.
- [x] Inspect the canonical v3 UI reference before planning.
- [x] Inspect current Capture/List/Memory domain, application, persistence,
      Room schema 4, schemas 1–4, and integration-test patterns.
- [x] Create the Change 016 SPEC, PLAN, and TASKS package.
- [x] Point `docs/ACTIVE_WORK.md` at Change 016 PLAN/DOCS ONLY.

## Search contract

- [ ] Add the focused `LocalSearch` application boundary.
- [ ] Add the closed typed result contract for Capture, Note, Structured Log,
      and List Item.
- [ ] Define and test trim/blank behavior, literal SQL escaping, ASCII case,
      Unicode/Spanish behavior, result limit, and deterministic ordering.
- [ ] Preserve source IDs and the minimum source context needed later.

## Durable DAO and Room implementation

- [ ] Add direct durable-table Capture search using effective Voice text.
- [ ] Add direct durable-table Note search.
- [ ] Add Structured Log field key/value search with one result per log.
- [ ] Add List Item text search covering current, completed, historical
      Mandado, and continuous Compras items with context.
- [ ] Implement `RoomLocalSearch` with one read transaction, per-source
      bounded queries, global merge/order, and final limit.
- [ ] Wire the production boundary without adding UI or changing `MemoryStore`.

## Automated tests and evidence

- [ ] Add JVM tests for query construction/escaping and result ordering.
- [ ] Add a named v4 Room integration test for all four sources and durable
      history beyond recent-screen limits.
- [ ] Verify Voice correction fallback, Structured Log de-duplication, List
      context, literal `%`/`_`/backslash`, blank input, Unicode behavior, and
      caller limits.
- [ ] Run `./gradlew :app:testDebugUnitTest`.
- [ ] Run `./gradlew :app:assembleDebug`.
- [ ] Run `./gradlew :app:lintDebug`.
- [ ] Run `./gradlew :app:connectedDebugAndroidTest`.
- [ ] Run `git diff --check`, `git status --short`, and diff statistics.
- [ ] Confirm Room version 4 and schemas 1–4 remain unchanged and no FTS,
      migration, dependency, or UI artifacts were introduced.

## Scope and authority

- [ ] Do not add Search UI, FTS, fuzzy/semantic search, AI, ranking
      heuristics, highlighting, filters, tags, archive/backup, reminders,
      Action Ledger, Undo, Google behavior, or new schema objects.
- [ ] Do not modify historical Change 001–015 documentation packages.
- [ ] Do not merge or push during implementation.
- [ ] Prepare the implementation report without declaring the final verdict.
- [ ] Final independent review.

## Current-turn boundary

This turn intentionally stops after documentation. Production code, tests,
schema files, dependencies, and UI remain unchanged.
