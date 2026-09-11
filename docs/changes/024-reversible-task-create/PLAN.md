# Change 024 — Reversible Task Create Action Foundation — PLAN

Governance: **HIGH-ASSURANCE**
Status: **IMPLEMENTATION COMPLETE — REVIEW PENDING**
Expected branch: `chg-024-reversible-task-create`

This plan follows the verified Change 023 baseline. It does not re-plan the
project or reopen the paused runtime-AI track.

## Preflight findings

- Starting worktree was clean on `main`.
- `git fetch origin` completed after the sandbox required repository-write
  authorization. `origin/main` and `origin/chg-023-task-completion-state` both
  resolve to `a3ef7e2c258146015875cb30069e5747b682b23a`.
- `chg-024-reversible-task-create` was created from the verified
  `origin/main` without a commit.
- `Task` already has nullable `completedAt`; Room database version 7 and
  `MIGRATION_6_7` are present. No schema change is justified.
- `TaskStore.save()` is a stable-ID `@Upsert` path and must remain unchanged.
- `ActionLedgerEntryDao`/`ActionLedgerMutationDao` already use ABORT inserts,
  exact-ID reads, ordered mutations, and an atomic mark-undone update.
- `RoomReversibleListItemActions` proves the direct-DAO transaction pattern,
  injectable item/ledger IDs and clock, strict shape validation, exact delete,
  cancellation propagation, and typed failure outcomes.
- `QuickAsideApplication` already owns app-scoped stores; one analogous
  `reversibleTaskActions` property is sufficient.
- The requested `software-project-orchestrator` skill is not installed or
  callable in the current catalog; repository `AGENTS.md` governance and the
  CHG-024 brief are the explicit proportional-governance fallback.
- An authorized `CPH2791` Android 16 device is currently visible through ADB.
- No UI behavior is changed, so UX image/screenshot evidence is not applicable.

## Expected files

Production:

- `app/src/main/java/com/edu/quickaside/application/tasks/ReversibleTaskActions.kt`
- `app/src/main/java/com/edu/quickaside/data/local/RoomReversibleTaskActions.kt`
- `app/src/main/java/com/edu/quickaside/data/local/TaskDao.kt`
- `app/src/main/java/com/edu/quickaside/QuickAsideApplication.kt`

Tests:

- focused JVM Task action contract test;
- `app/src/androidTest/java/com/edu/quickaside/data/local/ReversibleTaskActionsDatabaseTest.kt`;
- no schema/generated file changes expected.

## Minimal implementation sequence

1. Keep the CHG-024 docs live and point ACTIVE_WORK to this package.
2. Add the narrow application contract, typed results, constants, and
   injectable UUID-backed Task ID provider.
3. Add `TaskDao.insertStrict` with Room ABORT conflict behavior and
   `deleteById`, preserving `upsert`.
4. Implement `RoomReversibleTaskActions.create` with early blank-title
   validation, domain construction with `completedAt = null`, direct strict
   insert, parent ledger insert, and child mutation insert in one
   `withWriteTransaction`.
5. Implement targeted Undo with ordered ledger-shape checks, exact target
   checks, exact one-row delete/update checks, typed outcomes, rollback, and
   cancellation propagation.
6. Wire exactly one app-scoped implementation in `QuickAsideApplication`.
7. Add deterministic JVM and unique-database instrumentation tests. Use
   SQLite triggers for create-child, delete, and mark-undone failure injection
   where feasible; use injected clocks for cancellation.
8. Run focused JVM → focused Room/device → applicable data-local → full JVM →
   assemble → lint → schema/diff checks, updating TASKS only from evidence.
9. Close ACTIVE_WORK at implementation-complete/review-pending only after all
   obtainable applicable evidence is recorded; leave independent review
   unchecked.

## Atomic transaction design

Create uses direct `TaskDao`, `ActionLedgerEntryDao`, and
`ActionLedgerMutationDao` operations inside one Room write transaction. It
does not call `TaskStore.save()` and `ActionLedgerStore.record()` because
those are separate boundaries. `TaskDao.insertStrict` is `@Insert` with
`OnConflictStrategy.ABORT`, so an ID collision aborts the transaction rather
than replacing the row.

Undo checks all preconditions before mutation, then calls exact-ID Task delete
and exact-ID ledger mark-undone. A non-one return or trigger/driver exception
throws inside the transaction so Room rolls back both changes and the outer
boundary returns `Failed`. Cancellation is caught only to rethrow.

## Focused verification and failure injection

The new Room test will cover:

- Personal/no date and Trabajo/date create;
- exact surrounding-whitespace title, pending completion state, returned ID;
- exact one-entry/one-mutation ledger contract;
- blank title, collision, child-insert rollback, and create cancellation;
- successful exact-target Undo, unrelated-task preservation, durability,
  second Undo, missing entry/target, mismatch;
- unsupported operation/type/version/multiple-child ledger shapes;
- delete failure, mark failure, and Undo cancellation rollback.

Existing `TaskPersistenceDatabaseTest` proves stable-ID TaskStore UPSERT and
pending/completed/reopened persistence and remains untouched except for its
existing baseline evidence. Existing list reversible tests should remain
green; no shared infrastructure refactor is planned.

## Device, schema, and closeout plan

- Check `adb devices -l`; if it fails, perform at most the one targeted server
  restart/re-enumeration recovery allowed by the builder brief, then record the
  actual blocker without fabrication.
- Run the focused new Room class with the repository's instrumentation class
  filter, then the applicable `com.edu.quickaside.data.local` suite without
  unsupported `--tests` flags.
- Confirm Room version 7, no migration/fallback changes, schemas 1–7 exactly
  unchanged from the verified base, and no out-of-scope diff.
- Run `git diff --check`, inspect `git status --short`, `git diff --stat`, and
  review production/test/docs content before stopping.

## Closeout state

Builder closeout is `IMPLEMENTATION COMPLETE — REVIEW PENDING` when the
implementation and all reasonably obtainable applicable gates have evidence.
The independent engineering review task remains unchecked. The exact next
gate is user-authorized commit/push of the combined CHG-024 implementation,
docs, tests, and evidence, followed by independent review of
`main...chg-024-reversible-task-create`.

## Implementation evidence

- `./gradlew :app:testDebugUnitTest --tests
  com.edu.quickaside.application.tasks.ReversibleTaskActionsContractTest`:
  3/3 passed; skips/failures/errors 0.
- `./gradlew :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=com.edu.quickaside.data.local.ReversibleTaskActionsDatabaseTest`:
  12/12 passed on Oppo CPH2791 / Android 16; skips/failures/errors 0.
- `./gradlew :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.package=com.edu.quickaside.data.local`:
  97/97 passed on Oppo CPH2791 / Android 16; skips/failures/errors 0.
- `./gradlew :app:testDebugUnitTest`: 114/114 passed; skips/failures/errors 0.
- `./gradlew :app:assembleDebug`: BUILD SUCCESSFUL.
- `./gradlew :app:lintDebug`: BUILD SUCCESSFUL.
- The first 11-test focused device invocation found two test-fixture defects;
  the fixtures were corrected and that focused rerun passed. After adding the
  parent-collision case, a 12-test invocation found one Int/Long assertion
  mismatch; it was corrected and the final focused rerun passed. No production
  code was changed for these test-only issues.
- The byte-for-byte schema comparison against `origin/main` was clean. Room
  version 7, migrations through 6→7, and no destructive fallback were
  confirmed. `git diff --check` was clean.
- No applicable gate is blocked. No UI screenshots were required. Independent
  engineering review remains pending.
