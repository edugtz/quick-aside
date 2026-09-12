# Change 025 — Reversible Task Completion Actions Foundation — PLAN

Governance: **HIGH-ASSURANCE**
Status: **COMPLETE — REVIEW PASS**
Expected branch: `chg-025-reversible-task-completion`

This plan follows the verified CHG-024 Task-create boundary and keeps the
implementation provider-independent and Room-local.

## Preflight findings

- The worktree was clean on `main`; `git fetch origin` completed after the
  sandbox granted repository-write authorization.
- `origin/main` and `origin/chg-024-reversible-task-create` both resolve to
  `9d411908ec529117c9daf9fa23a3c8509a719346`; CHG-024 is an ancestor.
- The new branch was created from that exact `origin/main`.
- Task completion is already nullable epoch milliseconds in Room v7; no schema
  or migration change is justified.
- The existing app-scoped `reversibleTaskActions` property is sufficient.
- The direct DAO transaction pattern in CHG-024 is the applicable precedent.

## Smallest implementation

1. Extend `ReversibleTaskActions` with complete, reopen, and targeted
   completion Undo result types without renaming the CREATE contract.
2. Add a narrow Task completion payload codec for exact v1 `pending` /
   `completed:<epochMillis>` states and valid transition checks.
3. Add one nullable compare-and-set `TaskDao` update query returning `Int`.
4. Implement complete/reopen with one canonical action clock instant, a
   completion-only DAO mutation, one parent ledger row, and one child mutation
   inside `withWriteTransaction`.
5. Implement targeted Undo with strict shape/target/state checks, completion
   CAS restoration, and exact mark-undone row proof in the same transaction.
6. Add focused JVM and Room tests; keep CHG-024 production behavior and CREATE
   ledger semantics unchanged.
7. Run verification from narrowest tests outward and update live evidence only
   from actual output.

## Expected files

Production:

- `app/src/main/java/com/edu/quickaside/application/tasks/ReversibleTaskActions.kt`
- `app/src/main/java/com/edu/quickaside/application/tasks/TaskCompletionLedgerPayload.kt`
- `app/src/main/java/com/edu/quickaside/data/local/TaskDao.kt`
- `app/src/main/java/com/edu/quickaside/data/local/RoomReversibleTaskActions.kt`

Tests:

- `app/src/test/java/com/edu/quickaside/application/tasks/ReversibleTaskCompletionActionsContractTest.kt`
- `app/src/test/java/com/edu/quickaside/application/tasks/TaskCompletionLedgerPayloadTest.kt`
- `app/src/androidTest/java/com/edu/quickaside/data/local/ReversibleTaskCompletionActionsDatabaseTest.kt`

Documentation:

- `docs/ACTIVE_WORK.md`
- `docs/changes/025-reversible-task-completion/SPEC.md`
- `docs/changes/025-reversible-task-completion/PLAN.md`
- `docs/changes/025-reversible-task-completion/TASKS.md`

No generated schema, entity, migration, dependency, or application-wiring
change is expected.

## Atomicity and failure strategy

All Task completion/reopen/Undo reads and writes use one Room write
transaction. Compare-and-set returns must equal one; otherwise an exception
forces rollback and the public result is `Failed`. Ordinary exceptions are
typed as `Failed`; `CancellationException` is rethrown. Tests inject ledger
parent/child, Task-update, Task-restoration during Undo, mark-undone, and
cancellation failures with unique databases/triggers where appropriate.

## Verification order

1. focused JVM codec and result-contract tests;
2. focused new Room instrumentation test on the authorized device;
3. existing CHG-024 reversible Task-create instrumentation;
4. existing Task persistence/lifecycle/migration instrumentation;
5. existing Action Ledger and applicable `data.local` instrumentation;
6. full `:app:testDebugUnitTest`;
7. `:app:assembleDebug`;
8. `:app:lintDebug`;
9. Room version/migration and schemas 1–7 comparison to the verified base;
10. `git diff --check`, status/stat, and exact-scope diff inspection.

If a check fails, stop at the first root failure, apply only the smallest
relevant fix, and rerun the affected gate before continuing. If the authorized
device is unavailable after the permitted one-time ADB recovery, leave the
device gate unchecked and report it; never fabricate evidence.

## Required stop state

After implementation, recorded verification, and independent review:

`COMPLETE — REVIEW PASS`

Do not commit, push, merge, or release as part of this documentation-only
closeout.

## Builder evidence and closeout

- Focused JVM completion contract/codec tests: `6/6` passed; skips/failures/
  errors `0`.
- Focused Room completion test: `14/14` passed on Oppo CPH2791 / Android 16,
  including explicit Task-restoration failure during completion Undo.
- CHG-024 reversible Task-create regression: `12/12` passed.
- Task persistence/migration regression: `6/6` passed.
- Action Ledger persistence regression: `10/10` passed.
- Applicable `com.edu.quickaside.data.local` suite: `111/111` passed on
  Oppo CPH2791 / Android 16.
- Full JVM: `120/120` passed. Assemble and lint both completed successfully.
- Room version/migrations and schema comparison are unchanged/clean; diff
  check is clean. The final branch remains uncommitted.

## Independent review closeout

Independent engineering review: PASS

BLOCKER 0
MAJOR 0
MINOR 0

The targeted test-only review patch closed the sole MINOR evidence gap by
covering Task-restoration failure during completion Undo. The evidence counts
above are preserved from the completed verification; no technical gates were
rerun for this documentation-only closeout.

Closeout state: **COMPLETE — REVIEW PASS**.
