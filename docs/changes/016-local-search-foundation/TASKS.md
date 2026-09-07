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

- [x] Add the focused `LocalSearch` application boundary.
- [x] Add the closed typed result contract for Capture, Note, Structured Log,
      and List Item.
- [x] Define and test trim/blank behavior, literal SQL escaping, ASCII case,
      Unicode/Spanish behavior, result limit, and deterministic ordering.
- [x] Preserve source IDs and the minimum source context needed later.

## Durable DAO and Room implementation

- [x] Add direct durable-table Capture search using effective Voice text.
- [x] Add direct durable-table Note search.
- [x] Add Structured Log field key/value search with one result per log.
- [x] Add List Item text search covering current, completed, historical
      Mandado, and continuous Compras items with context.
- [x] Implement `RoomLocalSearch` with one read transaction, per-source
      bounded queries, global merge/order, and final limit.
- [x] Wire the production boundary without adding UI or changing `MemoryStore`.

## Automated tests and evidence

- [x] Add JVM tests for query construction/escaping and result ordering.
- [x] Add a named v4 Room integration test for all four sources and durable
      history beyond recent-screen limits.
- [x] Verify Voice correction fallback, Structured Log de-duplication, List
      context, literal `%`/`_`/backslash`, blank input, Unicode behavior, and
      caller limits.
- [x] Run `./gradlew :app:testDebugUnitTest`.
- [x] Run `./gradlew :app:assembleDebug`.
- [x] Run `./gradlew :app:lintDebug`.
- [x] Run `./gradlew :app:connectedDebugAndroidTest`.
- [x] Run `git diff --check`, `git status --short`, and diff statistics.
- [x] Confirm Room version 4 and schemas 1–4 remain unchanged and no FTS,
      migration, dependency, or UI artifacts were introduced.

## Scope and authority

- [x] Do not add Search UI, FTS, fuzzy/semantic search, AI, ranking
      heuristics, highlighting, filters, tags, archive/backup, reminders,
      Action Ledger, Undo, Google behavior, or new schema objects.
- [x] Do not modify historical Change 001–015 documentation packages.
- [x] Do not merge or push during implementation.
- [x] Prepare the implementation report without declaring the final verdict.
- [x] Final independent review.
      - Verdict: PASS_WITH_NOTES.
      - Full connected suite: 147/147 passed.
      - Final focused LocalSearch integration suite: 8/8 passed on CPH2791 / Android 16.
      - StructuredLog multi-field de-duplication was explicitly verified by the final focused test.
      - GitHub has no CI/status checks; deterministic local/device evidence is authoritative for this change.

## Current-turn boundary

This turn intentionally stops after documentation. Production code, tests,
schema files, dependencies, and UI remain unchanged.
