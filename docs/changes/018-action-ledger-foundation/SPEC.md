# Change 018 — Action Ledger Persistence Foundation — SPEC

Governance: **HIGH-ASSURANCE**
Status: **PLAN/DOCS ONLY — IN PROGRESS**
Expected branch: `chg-018-action-ledger-foundation`

## Objective

Establish the durable local Action Ledger foundation required by M1 and by
later reversible mutations/Undo. An `ActionLedgerEntry` represents one
user-visible logical action or batch, and one entry may contain multiple
ordered mutations.

For example, a future interpretation of `Compra Chobani, pollo y leche` may
record one entry containing three ordered List Item creation mutations. This
change stores the durable record needed to reverse that logical action later;
it does not execute the reversal.

## Proven baseline and authority

The current baseline is the completed Changes 001–017 implementation:

- Change 002 provides the pure-Kotlin minimal `ActionLedgerEntry` containing
  only an ID and occurrence time.
- `ActionLedgerEntryId` already exists in `domain/common/DomainIds.kt`.
- Room is currently version 4 and persists Captures, Lists, Notes, and
  Structured Logs through the existing database and store patterns.
- Room/KSP/SQLite versions remain Room 3.0.2, KSP 2.3.6, and SQLite 2.7.0.
- Existing application stores use focused interfaces, injectable clocks and ID
  providers, direct read methods, sealed write outcomes, and Room
  `withWriteTransaction`/`withReadTransaction` boundaries.
- Change 017 is complete and has no Action Ledger persistence or mutation
  integration to preserve.

The authoritative Change 018 definition, the accepted project/architecture
contracts, and the existing repository behavior govern implementation. This
package does not re-plan the feature from scratch.

## In scope

- Evolve the pure-Kotlin Action Ledger domain contract.
- Add a focused `ActionLedgerStore` application boundary for recording,
  reading, and marking entries undone.
- Add local Room entities, DAOs, explicit mappers, and a Room store.
- Move `QuickAsideDatabase` from version 4 to version 5 through an explicit,
  non-destructive 4→5 migration.
- Add only the Action Ledger tables and required indexes to schema 5.
- Keep one app-scoped `ActionLedgerStore` in `QuickAsideApplication`; no UI
  consumer is added in this change.
- Add deterministic JVM domain/mapping tests and focused Android Room
  persistence and migration evidence.
- Update existing migration-test assertions that refer to the current
  production database version from 4 to 5 when implementation makes those
  assertions stale.

## Out of scope

- Executing Undo against List, Memory, Capture, Task, Calendar, or any other
  target record.
- Wiring existing `ListStore`, `MemoryStore`, Capture correction, or future
  product mutations into the ledger.
- `ActionExecutor`, `CapturePlan`, AI, model/provider work, or interpretation.
- Google Tasks/Calendar, reminders, external sync compensation, or outbox
  behavior.
- UI, snackbar/receipt, Undo button, activity/history screen, navigation, or
  visual evidence.
- Redo, archive, backup, export, or pruning.
- JSON schemas for snapshots, parsing/normalizing payloads, or a
  JSON/kotlinx.serialization dependency.
- An exhaustive enum of target entities, a generic repository abstraction, or
  a broad history/pagination API.
- Destructive migration behavior, table rewrites, or changes to existing v4
  user tables.

## Domain contract

The existing `ActionLedgerEntry` evolves toward this pure-Kotlin model:

```text
ActionLedgerEntry
  id: ActionLedgerEntryId
  occurredAt: Instant
  sourceCaptureId: CaptureId?
  mutations: List<ActionLedgerMutation>
  undoneAt: Instant?

ActionLedgerMutation
  operation: CREATE | UPDATE | DELETE
  targetType: extensible nonblank string/value object
  targetId: nonblank string
  payloadVersion: positive integer
  beforeState: opaque nullable string
  afterState: opaque nullable string
```

The implementation should use an `ActionLedgerOperation` enum for the fixed
generic operation vocabulary and may use a small `ActionLedgerTargetType`
value object local to the actions domain. `targetType` must remain open to
future target kinds; it must not become an exhaustive enum of product
entities. `targetId` remains a string because its interpretation belongs to
the target type and later integration contract. No new ID wrapper is needed
in `DomainIds.kt` unless implementation proves one necessary.

The accepted invariants are:

- An entry cannot be recorded with zero mutations.
- Mutation order is semantic. The list order is retained exactly, including
  for multi-mutation batches.
- `targetType` and `targetId` are rejected when blank. Validation uses
  `isBlank` only; the accepted string itself is never trimmed or rewritten.
- `payloadVersion` must be greater than zero.
- `beforeState` and `afterState` are opaque persistence payloads. Their exact
  nullable strings, including whitespace and punctuation, round-trip without
  trimming, parsing, normalization, or rewriting.
- No JSON structure or target-specific snapshot schema is defined here.
- `sourceCaptureId` is optional. When supplied to the store, it must refer to
  an existing Capture.
- A newly recorded entry has `undoneAt == null`.
- Marking undone is one-way for this foundation. An already-undone entry is
  not marked again and keeps its original `undoneAt`.

Domain construction and persistence mapping must fail visibly for corrupt
stored operation/target/payload values rather than inventing a replacement
mutation. Target-specific inverse semantics are deferred to later changes.

Because the persistence boundary must return deterministic validation results,
the application API may use a focused raw `ActionLedgerMutationInput` (or
equivalent request type) for recording. It is not a persisted record and must
be converted into a valid `ActionLedgerMutation` only after validation. This
keeps invalid blank target/payload cases testable without weakening the
domain's valid-value invariants.

## Application boundary

Add a focused `ActionLedgerStore` consistent with `ListStore` and
`MemoryStore`:

```text
record(mutations, sourceCaptureId?): ActionLedgerRecordResult
getEntry(ActionLedgerEntryId): ActionLedgerEntry?
readRecentEntries(limit = RECENT_ACTION_LEDGER_LIMIT): List<ActionLedgerEntry>
readLatestUndoable(): ActionLedgerEntry?
markUndone(ActionLedgerEntryId): ActionLedgerMarkUndoneResult
```

The normal recent limit is 50, matching the existing local-store convention.
There is no pagination or general history query in this change. Recent reads
include persisted entries regardless of undone state. The latest undoable read
returns at most one entry and excludes every entry whose `undoneAt` is
non-null.

All entry reads use the deterministic order:

```text
occurredAt DESC, id DESC
```

`ActionLedgerRecordResult` must distinguish at least:

- `Saved(entry)`;
- empty mutation batch;
- invalid mutation with a deterministic position/reason for blank
  `targetType`, blank `targetId`, or non-positive `payloadVersion`;
- missing `sourceCaptureId`;
- `Failed(cause: Exception)` for unexpected persistence failures.

`ActionLedgerMarkUndoneResult` must distinguish:

- `MarkedUndone(entry)`;
- missing entry;
- `AlreadyUndone`;
- `Failed(cause: Exception)`.

The store validates a record request before consuming the ID/clock providers
or writing rows. Source Capture existence is checked inside the same write
transaction as the eventual insert. Record insertion creates the parent and
all ordered child rows atomically. Mark-undone uses a conditional one-way
update and must not report a second call as success. An injected clock supplies
the timestamp for the successful mark, and an injected ID provider supplies
the new entry ID. Production providers use UUID and system time patterns
matching the existing stores; tests supply deterministic values.

Unexpected ordinary exceptions are converted to `Failed` for write
operations. `CancellationException` is always rethrown and is never converted
to `Failed`. Read methods retain the existing focused-store direct return
shape; persistence/mapping failures must remain visible rather than being
converted into fabricated domain data.

## Room schema 5

`QuickAsideDatabase` moves from version 4 to 5 and adds the two new entities
and DAO accessors. The conceptual new tables are:

### `action_ledger_entries`

```text
id                         TEXT    PRIMARY KEY NOT NULL
occurred_at_epoch_millis   INTEGER NOT NULL
source_capture_id         TEXT    NULL
undone_at_epoch_millis    INTEGER NULL
```

`source_capture_id` has a foreign key to `captures(id)` with `NO ACTION` on
delete and update, and an index appropriate for the optional Capture lookup
and Room foreign-key validation (`index_action_ledger_entries_source_capture_id`).

### `action_ledger_mutations`

```text
action_ledger_entry_id     TEXT    NOT NULL
position                   INTEGER NOT NULL
operation                  TEXT    NOT NULL
target_type                TEXT    NOT NULL
target_id                  TEXT    NOT NULL
payload_version            INTEGER NOT NULL
before_state               TEXT    NULL
after_state                TEXT    NULL
```

The primary key is `(action_ledger_entry_id, position)`. The foreign key
references `action_ledger_entries(id)` with `NO ACTION` on delete and update.
The composite primary key begins with `action_ledger_entry_id`, so no
additional mutation-parent index is planned unless Room schema validation
shows that one is required.

Mutation `position` is persistence metadata derived from the domain list
index. Reads use `position ASC` and restore exactly the same mutation order.
It is not a user-visible or target-specific identifier.

All new timestamp columns use epoch milliseconds, matching existing local
entities. All strings are stored as supplied. The new tables must not add
triggers, JSON columns, snapshot parsing, or changes to Captures, Lists,
Notes, or Structured Logs.

## Migration contract

Add and register an explicit `MIGRATION_4_5` after the existing migration
chain. It may only create `action_ledger_entries`,
`action_ledger_mutations`, and the required new indexes. It must not alter,
rewrite, recreate, normalize, delete, or reseed any existing v4 table or row.

There is no destructive fallback. Fresh database creation must export schema
5 normally. Tracked schemas `1.json` through `4.json` remain unchanged;
`5.json` may add only the planned ledger structures and their metadata.

If implementation appears to require modifying any existing v4 user table,
STOP and request re-scope.

## Persistence acceptance scenarios

Focused fresh-v5 evidence must cover:

1. Recording a single valid mutation creates one entry and one ordered child
   row with `undoneAt == null`.
2. Recording a multi-mutation batch creates one entry with all mutation rows.
3. Mutation ordering round-trips exactly, independent of insertion/read
   order at the DAO boundary.
4. `beforeState` and `afterState` preserve exact opaque strings, including
   nulls and surrounding whitespace.
5. A valid optional source Capture association round-trips.
6. A missing source Capture returns the deterministic validation outcome and
   creates no parent or child row.
7. Recent entries are newest-first with the deterministic ID tie-break.
8. The latest undoable entry excludes entries already marked undone.
9. A successful mark-undone persists across database close/reopen.
10. A second mark-undone returns `AlreadyUndone`, preserves the original
    timestamp, and does not silently succeed.
11. An empty mutation batch creates nothing.
12. Blank `targetType` and blank `targetId` requests create nothing and return
    their expected validation reason.
13. A non-positive `payloadVersion` request creates nothing and returns its
    expected validation reason.
14. A forced unexpected database failure is surfaced as `Failed`, with no
    partially written batch.
15. `CancellationException` is rethrown.

Record parent/child insertion must be verified as one transaction. A forced
child-row failure must leave neither the parent entry nor any mutation row.

## Migration evidence contract

Because this is HIGH-ASSURANCE, later implementation must add a real on-disk
v4 fixture matching the tracked schema 4 and its Room identity hash. The
fixture must include representative existing durable data, preserving exact
values where practical:

- a Text Capture;
- a corrected Voice Capture, retaining both original and corrected transcript
  values;
- the built-in Mandado and Compras definitions;
- a completed Mandado session with item history;
- a Compras item;
- a Note, preferably associated with an existing Capture;
- a StructuredLog with multiple fields and its normalized field rows.

Open the fixture through the production `QuickAsideDatabase.create` migration
chain. Verify that:

- every pre-existing value and relationship is unchanged, including spaces,
  corrected transcript, list/session/item state, Note text/source, and every
  StructuredLog field;
- `action_ledger_entries` and `action_ledger_mutations` are initially empty;
- SQLite `user_version` is 5;
- the migrated database closes and reopens through schema 5 successfully;
- a new ledger entry/batch can be written after migration and reads back
  correctly;
- the old v4 table definitions remain intact and no v4 data was rewritten.

The test must use a unique named database and clean up only that fixture.
Production `quick_aside.db` must never be opened, deleted, or used as test
state.

## Verification contract

Later implementation evidence must include:

- focused Action Ledger JVM domain/mapping tests;
- focused Action Ledger Room persistence tests;
- the real 4→5 migration/data-preservation test;
- `./gradlew :app:testDebugUnitTest`;
- `./gradlew :app:assembleDebug`;
- `./gradlew :app:lintDebug`;
- focused connected Android tests on a supported real device when available;
- inspection of generated `schemas/4.json` versus `schemas/5.json` proving
  schema 5 adds only the planned ledger structures;
- `git diff --check`;
- `git status --short`;
- `git diff --stat`.

A broad connected-suite harness failure must be reported truthfully and is
not a pass. Do not repeat an identical known Compose harness failure when
focused deterministic Room evidence succeeds. No visual evidence is required
because Change 018 has no UI.

This docs-only turn must not run Gradle, modify production/test source, or
commit, push, merge, or release.

## Stop conditions

Stop instead of expanding scope if planning or implementation discovers that
Change 018 requires:

- modifying an existing v4 user table;
- actual target-record Undo execution;
- a serialization dependency or snapshot schema;
- AI/CapturePlan, Google integration, reminders, UI, or sync compensation;
- destructive migration behavior;
- a broad history/pagination API or a second persistence boundary.

## Exact next gate

The Change 018 planning package must be independently reviewed and committed
before production implementation begins.

## Authority

This SPEC is the reviewed Change 018 contract. It defines scope and required
evidence but does not declare an implementation verdict. The user retains
commit, merge, release, and push authority.
