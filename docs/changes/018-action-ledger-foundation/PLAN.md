# Change 018 — Action Ledger Persistence Foundation — PLAN

Governance: **HIGH-ASSURANCE**
Status: **PLAN/DOCS ONLY — IN PROGRESS**
Expected branch: `chg-018-action-ledger-foundation`

This planning turn is documentation-only. It must not create production code,
test code, generated schema output, dependencies, commits, or pushes.

## 1. Preflight findings

- The current branch is `chg-018-action-ledger-foundation`.
- The starting worktree is clean.
- `docs/ACTIVE_WORK.md` points to the completed Change 017 baseline and must
  be moved to this Change 018 planning package.
- `domain/actions/ActionLedger.kt` currently contains only `id` and
  `occurredAt`; `ActionLedgerEntryId` already exists and is sufficient for
  this foundation.
- `QuickAsideDatabase` is version 4 with Captures, Lists, Notes, and
  Structured Logs registered. The current migration chain ends at
  `MIGRATION_3_4`.
- Current Room entities use explicit column names, epoch-millisecond
  timestamps, non-cascading foreign keys, and indexes on optional parent
  references.
- Current DAOs expose focused insert/get/recent/update methods. Recent queries
  use `timestamp DESC, id DESC` ordering and a limit where appropriate.
- `RoomListStore` and `RoomMemoryStore` use injectable clock/ID providers,
  `withWriteTransaction` for atomic writes, direct read APIs, sealed write
  results, and explicit `CancellationException` propagation.
- Current persistence dependencies are Room 3.0.2, KSP 2.3.6, and SQLite
  2.7.0. No dependency change is planned.
- Existing migration tests contain production-version assertions of `4L` in
  `ListPersistenceDatabaseTest`, `MemoryPersistenceDatabaseTest`, and
  `CaptureTranscriptCorrectionDatabaseTest`; implementation must update those
  assertions to 5 where they verify the final production database version.
- No current UI or product mutation consumes an Action Ledger store, so no
  activity, Compose, navigation, or existing mutation wiring is required.

The UX reference is not an affected source for this non-UI change; no visual
inspection or screenshot evidence is required.

## 2. Implementation sequence

### A. Evolve the pure domain contract

Update `domain/actions/ActionLedger.kt` with:

1. `ActionLedgerOperation` containing only `CREATE`, `UPDATE`, and `DELETE`.
2. An extensible, nonblank target-type string/value object local to the action
   domain if useful; do not create an exhaustive entity enum.
3. `ActionLedgerMutation` with the fixed operation, target type, target ID,
   positive payload version, and nullable opaque before/after strings.
4. The expanded `ActionLedgerEntry` with optional source Capture, ordered
   mutations, and nullable `undoneAt`.
5. Pure-Kotlin invariant checks that reject zero mutations, blank target
   values, and non-positive payload versions without changing accepted
   nonblank strings.

Keep `DomainIds.kt` unchanged unless a narrowly justified new identity helper
is proven necessary. Do not add serialization, Room annotations, Android
types, inverse logic, or target-specific payload rules.

### B. Define the focused application store

Add `application/actions/ActionLedgerStore.kt` with the store interface,
injectable clock and entry-ID provider, the normal recent limit of 50, and
sealed results for recording and marking undone.

Use a small application input/spec when raw invalid target/payload values must
be reported as deterministic validation results. Validation must occur before
ID/clock consumption and before any write. The valid input path constructs one
entry with the supplied mutation list in the same order and with
`undoneAt == null`.

Define the mark result so that missing and already-undone entries are expected
outcomes, while unexpected exceptions are `Failed`. A successful mark uses the
injected clock exactly once for the persisted `undoneAt`; a repeated mark does
not replace the original time.

### C. Add local entities, DAOs, and mappers

Add focused data-local files, using the repository's existing naming and
mapping style. The expected later touch points are:

- `ActionLedgerEntities.kt` for `ActionLedgerEntryEntity` and
  `ActionLedgerMutationEntity` plus explicit domain/entity mappers;
- `ActionLedgerDao.kt` for parent and child DAO methods;
- `RoomActionLedgerStore.kt` for the application implementation.

The entry entity maps timestamps to epoch milliseconds and has a nullable
`source_capture_id` FK to `captures(id)` with `NO ACTION` on update/delete and
an index. The mutation entity uses `(action_ledger_entry_id, position)` as its
primary key and stores all payload strings exactly. Read mapping must query
children with `position ASC`, verify the parent ID and valid/contiguous
positions, and reconstruct the domain list without sorting by any other
field.

The parent insert and all child inserts must be inside one
`withWriteTransaction`. Validate the optional source Capture in that same
transaction before inserting the parent. The mark operation should use a
conditional SQL update on `undone_at_epoch_millis IS NULL`, then distinguish
missing versus already-undone without allowing a second successful transition.

### D. Move Room to version 5

Update `QuickAsideDatabase.kt` only for the new ledger persistence:

1. Register the two new entities and DAO accessors.
2. Change the version from 4 to 5.
3. Add `MIGRATION_4_5` after `MIGRATION_3_4` in the builder.
4. Make `MIGRATION_4_5` execute only `CREATE TABLE`/`CREATE INDEX`
   statements for the new structures.

Do not alter the existing 1→2, 2→3, or 3→4 SQL. Do not recreate old tables,
copy old data, seed existing definitions, add fallback destructive migration,
or change unrelated entities/indexes. Generate schema 5 through the existing
Room schema-directory configuration and preserve schema files 1–4.

### E. Add one app-scoped store owner

Add one lazy `actionLedgerStore` property to
`QuickAsideApplication.kt`, backed by the shared `QuickAsideDatabase` and
`RoomActionLedgerStore`. Do not pass it through `MainActivity` or Compose,
because no UI or mutation integration is in scope yet. Do not create another
database or a second production store instance.

## 3. Test approach

### JVM domain and mapper tests

Add focused tests under `app/src/test` for:

- valid single and multi-mutation domain construction;
- exact target type/ID and before/after payload preservation;
- operation vocabulary and positive payload version;
- rejection of zero mutations, blank target type/ID, and non-positive
  payload version;
- entity round-trip preserving entry fields and exact mutation order;
- mapping failure for invalid operation/positions or mismatched parent rows;
- no dependency boundary expansion beyond pure Kotlin and `java.time` in the
  domain package.

### Fresh-v5 Room persistence tests

Add a focused Android test, likely
`app/src/androidTest/java/com/edu/quickaside/data/local/ActionLedgerPersistenceDatabaseTest.kt`,
using a unique named database for each test. Cover the scenarios in the SPEC,
including:

- single and multi-mutation writes;
- ordered child rows and exact opaque payload values;
- optional and missing Capture association;
- deterministic recent and latest-undoable reads;
- mark-undone and close/reopen durability;
- second mark returning `AlreadyUndone`;
- invalid batches creating no rows;
- forced child-insert failure proving parent/children atomicity;
- closed/unavailable database returning `Failed`;
- cancellation propagation.

Use deterministic queue providers or equivalent fixed providers for IDs and
timestamps. A SQLite trigger on the new mutation table is the existing
repository pattern for forcing an unexpected child insert failure. Assertions
must inspect both parent and child tables after failure.

### Real 4→5 migration fixture

The focused Android test should also create an on-disk schema-4 fixture
directly with `BundledSQLiteDriver`, using the exact tracked schema-4 table
definitions and identity hash. Set `PRAGMA user_version = 4` and insert:

- Text and corrected Voice Captures, including exact original/corrected
  transcript strings;
- Mandado and Compras definitions;
- a completed Mandado session and item history;
- a Compras item;
- a Note with exact text and source Capture where practical;
- a StructuredLog with multiple exact field rows.

Open the fixture through production `QuickAsideDatabase.create` and verify
raw old-table values plus domain reads. Verify the two ledger tables are
empty, `user_version == 5`, the database closes/reopens, and a new ledger
batch writes and reads after migration. Assert the old v4 table columns,
foreign keys, indexes, and row values are not rewritten.

This fixture must use the existing test cleanup discipline: unique file name,
close every handle, and delete only that named database.

### Existing regression assertions

Update only stale current-version expectations in existing tests, notably:

- `ListPersistenceDatabaseTest.kt` final version checks;
- `MemoryPersistenceDatabaseTest.kt` migration version check and any comment
  that explicitly describes the opened physical database version;
- `CaptureTranscriptCorrectionDatabaseTest.kt` final version check.

Keep their existing Capture/List/Memory value assertions and regression
coverage intact. Do not weaken them to accommodate the version bump. Historical
change packages and schemas remain unchanged.

## 4. Schema and migration inspection

After implementation:

1. Inspect generated `schemas/5.json` for exactly the two new tables, their
   columns, composite key, FKs, and intended indexes.
2. Confirm schemas 1–4 have no working-tree changes and remain byte-for-byte
   equivalent to the pre-change baseline.
3. Inspect `MIGRATION_4_5` directly to verify it only adds the ledger
   structures.
4. Check that no existing v4 entity/table is annotated or rewritten for this
   feature.

If any old table needs modification, stop and request re-scope before adding
implementation or tests.

## 5. Verification sequence

Run focused JVM and Android Action Ledger tests while implementing. At the
later implementation closeout, run:

```text
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

Run focused connected Android tests on a supported device when available. A
broad connected-suite infrastructure failure must be reported as blocked or
failed infrastructure, never as a pass when tests did not execute.

Then inspect:

```text
git diff --check
git status --short
git diff --stat
```

The docs-only planning closeout runs only those requested Git checks; it does
not run Gradle.

## 6. Expected later file map

### Planning files created now

- `docs/ACTIVE_WORK.md`;
- `docs/changes/018-action-ledger-foundation/SPEC.md`;
- `docs/changes/018-action-ledger-foundation/PLAN.md`;
- `docs/changes/018-action-ledger-foundation/TASKS.md`.

### Expected implementation touch points later

- `app/src/main/java/com/edu/quickaside/domain/actions/ActionLedger.kt`;
- `app/src/main/java/com/edu/quickaside/application/actions/ActionLedgerStore.kt`;
- `app/src/main/java/com/edu/quickaside/data/local/ActionLedgerEntities.kt`;
- `app/src/main/java/com/edu/quickaside/data/local/ActionLedgerDao.kt`;
- `app/src/main/java/com/edu/quickaside/data/local/RoomActionLedgerStore.kt`;
- `app/src/main/java/com/edu/quickaside/data/local/QuickAsideDatabase.kt`;
- `app/src/main/java/com/edu/quickaside/QuickAsideApplication.kt`;
- `app/schemas/com.edu.quickaside.data.local.QuickAsideDatabase/5.json`;
- focused JVM domain/mapping tests;
- focused Android Action Ledger persistence/migration tests;
- the existing migration tests whose final production-version assertions
  become 5.

No UI, activity, navigation, existing mutation store, dependency, or external
service file is expected to change.

## 7. Risks and stop conditions

Implementation risks include:

- accidentally treating target type as a closed product-entity enum;
- trimming or normalizing opaque payloads or target strings;
- storing one ledger row per mutation instead of one logical batch entry;
- losing mutation order by relying on unspecified SQLite row order;
- allowing a parent row to survive a failed child insertion;
- treating an already-undone entry as a successful second transition;
- using an invalid ID/clock value after validation failure;
- weakening existing migration tests or old data assertions for the version
  bump.

Stop and report if any risk requires old-table modification, destructive
migration, serialization, Undo execution, AI/CapturePlan, Google behavior,
reminders, UI, sync compensation, or a broader history API.

## 8. Exact next gate

The Change 018 planning package must be independently reviewed and committed
before production implementation begins.

The user retains commit, merge, release, and push authority.
