# Change 018 — Action Ledger Persistence Foundation — TASKS

Governance: **HIGH-ASSURANCE**
Status: **PLAN/DOCS ONLY — IN PROGRESS**
Expected branch: `chg-018-action-ledger-foundation`

This is a LIVE EXECUTION CHECKLIST. During later implementation, mark a task
`[x]` only after the task and its evidence actually exist. Do not reset
completed tasks. Failed, skipped, or infrastructure-blocked verification must
remain accurately documented.

## Change package and planning preflight

- [x] Confirm the expected `chg-018-action-ledger-foundation` branch and clean
      starting worktree.
- [x] Read `AGENTS.md`, `docs/ACTIVE_WORK.md`, `docs/PROJECT_SPEC.md`,
      `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`,
      `docs/ACCEPTANCE_CRITERIA.md`, and `docs/NAMING.md`.
- [x] Read the required Change 002 and Change 013 SPEC, PLAN, and TASKS
      guidance.
- [x] Inspect the current `ActionLedger.kt`, `DomainIds.kt`,
      `QuickAsideDatabase.kt`, Room entities/DAOs, stores, migration tests,
      and tracked schemas.
- [x] Confirm the Room 3.0.2 / KSP 2.3.6 / SQLite 2.7.0 baseline and current
      database version 4.
- [x] Confirm this change has no UI surface and therefore needs no UX-image
      inspection or visual evidence.
- [x] Create the Change 018 SPEC, PLAN, and TASKS package.
- [x] Point `docs/ACTIVE_WORK.md` to Change 018 PLAN/DOCS ONLY — IN PROGRESS.
- [x] Record the exact next gate: independent review and commit before
      production implementation.
- [x] Preserve the docs-only boundary: no production/test source changes, no
      Gradle run, no commit, push, merge, or release.

## Domain and application boundary — later implementation

- [ ] Evolve `ActionLedgerEntry` with source Capture, ordered mutations, and
      nullable `undoneAt`.
- [ ] Add fixed `CREATE`/`UPDATE`/`DELETE` operation vocabulary without an
      exhaustive target-entity enum.
- [ ] Add nonblank target type/ID and positive payload-version validation while
      preserving exact accepted strings.
- [ ] Preserve opaque nullable `beforeState`/`afterState` values without
      defining JSON schemas or adding serialization.
- [ ] Add the focused `ActionLedgerStore` contract and any raw mutation input
      needed for deterministic validation outcomes.
- [ ] Add injectable Action Ledger clock and entry-ID provider patterns.
- [ ] Add explicit record outcomes for saved, validation, missing Capture, and
      unexpected persistence failure.
- [ ] Add explicit mark-undone outcomes for marked, missing, already-undone,
      and unexpected persistence failure.
- [ ] Ensure cancellation propagates rather than becoming `Failed`.

## Room schema and migration — later implementation

- [ ] Add `action_ledger_entries` with the planned columns, Capture FK, and
      required source-Capture index.
- [ ] Add `action_ledger_mutations` with the planned columns, composite
      `(action_ledger_entry_id, position)` primary key, and NO ACTION FK.
- [ ] Add explicit entity/domain mappers that restore mutation position order
      and fail visibly on corrupt rows.
- [ ] Add parent/child DAOs with deterministic recent/latest-undoable queries
      and conditional one-way mark-undone update.
- [ ] Add `RoomActionLedgerStore` with transactional batch insertion and
      source-Capture validation.
- [ ] Move `QuickAsideDatabase` from version 4 to 5 and register only
      `MIGRATION_4_5` for the new structures.
- [ ] Confirm existing migrations and v4 user tables are not modified,
      recreated, normalized, or deleted.
- [ ] Wire exactly one app-scoped `ActionLedgerStore` in
      `QuickAsideApplication` without UI/mutation integration.
- [ ] Generate `schemas/5.json` and confirm schemas 1–4 are unchanged.

## Fresh-v5 tests — later implementation

- [ ] Add focused JVM domain tests for valid entries/mutations and all domain
      invariants.
- [ ] Add focused JVM mapping tests for exact fields, opaque payloads, and
      mutation order.
- [ ] Add a unique-database Android persistence test for a single mutation.
- [ ] Cover a multi-mutation logical batch and exact ordering round-trip.
- [ ] Cover exact before/after strings, null payloads, and optional Capture
      association.
- [ ] Cover missing source Capture rejection with no created rows.
- [ ] Cover deterministic recent newest-first ordering with ID tie-break.
- [ ] Cover latest undoable exclusion of already-undone entries.
- [ ] Cover successful mark-undone and close/reopen durability.
- [ ] Cover second mark returning `AlreadyUndone` without changing its time.
- [ ] Cover empty batch, blank target values, and non-positive payload version
      rejection with no rows.
- [ ] Force a child-row database failure and prove the entire batch rolls back.
- [ ] Surface an unexpected closed/failed database as `Failed`.
- [ ] Verify `CancellationException` is rethrown.

## Real 4→5 migration evidence — later implementation

- [ ] Build a real on-disk schema-4 fixture with the tracked schema-4 identity
      hash and `user_version = 4`.
- [ ] Populate the fixture with Text and corrected Voice Captures, built-in
      list definitions, Mandado session/item history, a Compras item, a Note,
      and a StructuredLog with fields.
- [ ] Open the fixture through the production migration chain.
- [ ] Verify every existing value/relationship remains unchanged, including
      whitespace, corrected transcript, list state, Note source, and all log
      fields.
- [ ] Verify both new Action Ledger tables are empty immediately after
      migration.
- [ ] Verify SQLite `user_version == 5` and schema 5 reopens successfully.
- [ ] Write/read a new ledger batch after migration.
- [ ] Verify the old v4 table definitions and tracked schemas 1–4 remain
      unchanged.
- [ ] Use unique fixture cleanup and never touch production `quick_aside.db`.

## Existing regressions and required gates — later implementation

- [ ] Update only stale final production-version assertions from 4 to 5 in
      existing migration tests; preserve their data assertions.
- [ ] Run focused Action Ledger JVM tests.
- [ ] Run focused Action Ledger Room persistence/migration tests on a
      supported device when available.
- [ ] Run `./gradlew :app:testDebugUnitTest`.
- [ ] Run `./gradlew :app:assembleDebug`.
- [ ] Run `./gradlew :app:lintDebug`.
- [ ] Run the applicable connected Android tests and report any broad harness
      failure truthfully rather than calling it a pass.
- [ ] Inspect generated schema 5 against schema 4 and confirm only planned
      ledger structures were added.
- [ ] Run `git diff --check`.
- [ ] Inspect `git status --short` and `git diff --stat`.
- [ ] Leave the final engineering verdict to independent review.

## Scope and authority

- [ ] Do not implement target-record Undo execution in Change 018.
- [ ] Do not wire List/Memory/Capture mutations into the ledger.
- [ ] Do not add AI, CapturePlan, Google behavior, reminders, UI, receipts,
      redo, archive/backup, external sync compensation, pagination, or a
      serialization dependency.
- [ ] Do not modify old v4 user tables or use destructive migration fallback.
- [ ] Do not commit, push, merge, or release during implementation.

## Exact next gate

The Change 018 planning package must be independently reviewed and committed
before production implementation begins.
