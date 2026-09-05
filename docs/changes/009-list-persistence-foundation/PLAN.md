# Change 009 — List Persistence Foundation — PLAN

## Preflight

- Confirm the expected `chg-009-list-persistence-foundation` branch and
  working-tree state.
- Read the accepted project, architecture, roadmap, acceptance, naming, UX,
  and historical Changes 002, 003, 007, and 008 guidance.
- Inspect the current list domain, typed IDs, Capture entity/DAO/database,
  Room builder, application wiring, migration tests, and exported schemas 1
  and 2.
- Confirm Room 3.0.2, KSP 2.3.6, SQLite 2.7.0, and existing Android/toolchain
  dependencies remain unchanged.
- Confirm no UI work is needed and the existing Listas placeholder remains
  untouched.

## Implementation approach

1. Extend the pure-Kotlin list domain minimally with built-in definitions,
   completion state, creation time, and blank-text validation while retaining
   extensible list behavior.
2. Add focused list entities and explicit mappers under
   `com.edu.quickaside.data.local`. Store `Instant` values as epoch
   milliseconds and enum behavior as deterministic names.
3. Add list DAOs for definition reads, active/history session reads, session
   finish, item reads/inserts, and exact completion updates. Keep Room types
   inside the local data boundary.
4. Move `QuickAsideDatabase` to version 3, register the existing 1→2
   migration and new 2→3 migration, and add only the required list entities.
5. Centralize the two built-in seed rows in one explicit local bootstrapper.
   Call it from migration 2→3 and from a Room create callback for fresh v3
   databases, using idempotent insert semantics without a general seed
   framework.
6. Add a small `ListStore`/`RoomListStore` application boundary. Use Room's
   `withWriteTransaction` for active-session check/create and completion
   read-after-write. Validate definition behavior, session ownership, active
   status, and exact item text before insertion.
7. Wire the production list store from `QuickAsideApplication` without
   passing it into UI or modifying the Lists placeholder.
8. Add deterministic JVM mapping/domain tests and dedicated Android tests for
   fresh v3 databases, real v2 migration, list operations, concurrency,
   close/reopen durability, and capture/correction regressions.
9. Generate and inspect schema 3, proving schemas 1 and 2 remain unchanged and
   no unrelated structures appear.

## Migration fixture approach

Android instrumentation will create a dedicated on-disk SQLite file matching
tracked schema 2: the unchanged five-column `captures` table,
`room_master_table` with the schema-2 identity hash, and `user_version = 2`.
The fixture will contain Text and corrected Voice rows. Opening it through
`QuickAsideDatabase.create` exercises production 2→3 migration and Room schema
validation. The test verifies every Capture value, list-table existence,
built-in definitions, version 3, and close/reopen durability.

## Test isolation

- Every new Android persistence test uses a unique named test database.
- The production `quick_aside.db` is never used or deleted by tests.
- Fixture cleanup closes Room/raw connections and deletes only its dedicated
  test file.
- Historical Change 001–008 packages and schema JSON files are not rewritten.

## Verification sequence

- Run focused JVM tests after domain/mapping changes.
- Run targeted Android list/migration tests after database wiring.
- Generate and inspect schema 3.
- Run all required unit, assemble, lint, connected instrumentation, diff, and
  status gates.
- Report the actual emulator/device and any blocked or failed gate without
  claiming a final PASS.

## Stop signals

- Stop on the first real migration validation failure; do not weaken tests or
  rewrite historical schema evidence.
- Stop if active-session transaction safety is unclear after inspecting the
  current Room 3 APIs; do not replace it with UI flags or process-local locks.
- Split or ask for direction if implementation requires UI, generic CRUD,
  delete/archive, sync, AI, STT, or unrelated schema/dependency work.
