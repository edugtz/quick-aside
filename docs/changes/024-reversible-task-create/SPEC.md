# Change 024 — Reversible Task Create Action Foundation — SPEC

Governance: **HIGH-ASSURANCE**
Status: **IMPLEMENTATION COMPLETE — REVIEW PENDING**
Expected branch: `chg-024-reversible-task-create`

## Objective

Establish the first provider-independent Task application mutation boundary:

```text
create one Task
→ persist one Task, one Action Ledger entry, and one CREATE/task mutation
→ targeted Undo deletes exactly that Task and marks exactly that entry undone
```

This is a narrow application foundation for future manual Task UI and future
validated execution. It does not execute `CapturePlan`, invoke AI, or add Task
complete/reopen actions.

This change is HIGH-ASSURANCE because targeted Undo deletes durable Task data,
Task mutation and Action Ledger persistence must be atomic, and exact-target /
rollback behavior is correctness-sensitive.

## Proven baseline

- Preflight verified a clean worktree on `main` before branch creation.
- `origin/main` and `origin/chg-023-task-completion-state` both resolve to
  `a3ef7e2c258146015875cb30069e5747b682b23a`.
- This change is implemented from `origin/main` on
  `chg-024-reversible-task-create`.
- `Task` already contains stable `TaskId`, exact `title`, `TaskSpace`, nullable
  date-only `dueDate`, and nullable `completedAt`; `completedAt == null` is
  pending and non-null is completed.
- Room is version 7 with the explicit 6→7 migration already present. The
  schema and migrations are not changed by this change.
- `TaskStore.save()` is stable-ID UPSERT behavior and remains unchanged.
- Action Ledger already supports CREATE/UPDATE/DELETE, deterministic IDs and
  clock injection, and the list reversible-action implementation proves the
  required direct-DAO transaction pattern.
- Runtime AI remains paused at the provider/runtime boundary.

## Application contract

The narrow boundary is:

```kotlin
interface ReversibleTaskActions {
    suspend fun create(
        title: String,
        space: TaskSpace,
        dueDate: LocalDate? = null,
    ): CreateTaskActionResult

    suspend fun undoCreate(
        actionLedgerEntryId: ActionLedgerEntryId,
        expectedTaskId: TaskId,
    ): UndoTaskCreateResult
}
```

The boundary owns generated stable Task IDs through an injectable
`TaskIdProvider`; production uses the existing UUID convention. The Action
Ledger entry ID provider and clock use the existing application conventions.
No generic ID-generator framework is introduced.

Successful create returns the exact persisted `Task` and its
`ActionLedgerEntryId`. Deterministic create results include `BlankTitle` and
`Failed`; cancellation is rethrown. Deterministic Undo results include
`Undone`, `MissingLedgerEntry`, `AlreadyUndone`, `UnsupportedAction`,
`UnsupportedLedgerShape`, `TargetMismatch`, `TargetMissing`, and `Failed`.

## Create semantics

Create accepts exactly `title`, `TaskSpace`, and optional `LocalDate dueDate`.

- A whitespace-only title returns `BlankTitle` and writes nothing.
- A non-blank title is preserved byte-for-byte as accepted input: no trim,
  normalization, or case folding.
- The result has a newly generated stable ID, exact supplied title, exact
  supplied space, exact supplied due date, and `completedAt == null`.
- Create uses strict Task insert semantics. A generated-ID collision fails and
  cannot overwrite the existing durable Task.
- `TaskStore.save()` retains its stable-ID UPSERT semantics for existing
  lifecycle persistence and is not used as the create mutation boundary.

## Action Ledger contract

Every successful create atomically persists exactly:

```text
1 Task
1 ActionLedgerEntry
1 ActionLedgerMutation
```

The parent has `sourceCaptureId == null` and `undoneAt == null`. The single
mutation is exactly:

```text
operation       CREATE
targetType      task
targetId        exact TaskId.value
payloadVersion  1
beforeState     null
afterState      null
```

No Task state serialization, source-Capture execution, or UPDATE payload is
introduced.

## Transaction and failure contract

Task strict insert, Action Ledger parent insert, and child mutation insert run
inside one Room write transaction using the existing DAOs directly. Any
ordinary persistence/provider failure returns `Failed` and rolls back all
three rows. `CancellationException` propagates and also leaves no partial
state.

## Targeted Undo contract

`undoCreate(actionLedgerEntryId, expectedTaskId)` is not generic Undo. In one
Room write transaction it:

1. loads the exact ledger entry;
2. rejects a missing or already-undone entry;
3. loads ordered child mutations and requires exactly one;
4. requires `CREATE`, target type `task`, and payload version `1`;
5. requires the mutation target ID to equal `expectedTaskId.value`;
6. requires that exact Task to exist;
7. deletes exactly that Task and proves one row was deleted;
8. marks exactly that ledger entry undone and proves one row was updated.

Only after both writes succeed is `Undone` returned. Malformed/unsupported
ledger data, wrong targets, missing targets, second Undo, and ordinary delete /
mark failures never become success. Transaction rollback protects both sides
if either mutation fails. Cancellation propagates and rolls back.

## Room and wiring constraints

- `QuickAsideDatabase.version` remains 7.
- No migration, entity/table/index/foreign-key change, or historical schema
  edit is allowed.
- Add only strict Task insert and exact-ID delete DAO operations needed by this
  boundary.
- Wire exactly one app-scoped production `ReversibleTaskActions` through
  `QuickAsideApplication`.
- Do not wire to Compose/UI, navigation, CapturePlan execution, or external
  providers.

## In scope

- Task application action interface, typed results, constants, and injectable
  Task ID provider.
- Atomic Room-backed Task create and targeted Undo.
- Minimal Task DAO strict insert/delete additions.
- One app-scoped production implementation.
- Focused JVM contract tests and focused Room/device tests covering accepted
  scenarios, malformed ledger rejection, collision protection, rollback, and
  cancellation.
- CHG-024 SPEC/PLAN/TASKS and live ACTIVE_WORK/evidence reconciliation.

## Out of scope

Task complete/reopen application actions or UPDATE ledger payloads; generic
ActionExecutor/replay/redo/global Undo; CapturePlan execution or interpreter
wiring; AI/runtime/providers/network/auth; UI/navigation/Pendientes; Google
Tasks/Calendar/OAuth/sync; reminders/notifications; external IDs or sync
metadata; serialization; backup/archive; schema migration/version bump;
destructive fallback; new dependencies; speculative abstractions; and
unrelated refactors.

## Acceptance scenarios

1. Personal/no-date and Trabajo/with-date Tasks create pending with exact
   title, space, due date, generated stable ID, and null `completedAt`.
2. Blank/whitespace-only title is rejected with no Task or ledger rows.
3. Successful create has exactly one parent and one child with the exact
   CREATE/task/version-1/null-state contract and null source capture.
4. Generated-ID collision fails without changing the existing Task or adding
   ledger rows.
5. Create-side child failure and cancellation roll back all three rows.
6. Successful Undo deletes only the exact created Task, marks the exact entry,
   preserves unrelated Tasks, and survives close/reopen.
7. Second Undo, missing entry, wrong operation/type/version/shape, target
   mismatch, and missing Task return deterministic non-success outcomes without
   changing unrelated or protected state.
8. Delete failure and mark-undone failure roll back both sides; Undo
   cancellation also rolls back.
9. Existing TaskStore stable-ID UPSERT and pending/completed/reopened behavior,
   existing list reversible behavior, Room v7, and schemas 1–7 remain intact.
10. The final diff contains no UI, AI/runtime, Google/sync, reminder,
    complete/reopen action, generic serializer/executor, schema, or dependency
    scope leakage.

## Required evidence and stop conditions

Required evidence includes focused JVM tests, focused Task Room tests on the
available authorized device, applicable data-local instrumentation coverage,
full JVM tests, assembleDebug, lintDebug, schema/version comparison, migration
inspection, diff checks, and accurate final status/stat output. There is no UI
or screenshot gate.

Stop and report scope drift if implementation requires Room 8, schema
migration, historical schema changes, Task UPDATE serialization,
complete/reopen actions, generic replay/executor, CapturePlan execution, UI,
Google, reminders, runtime networking, or broad cross-domain refactoring.

The user retains commit, push, merge, release, and independent-review
authority. This builder turn must not commit, push, merge, release, or assign
an independent review verdict.

## Builder evidence

- Focused JVM contract tests passed 3/3 with zero skips, failures, or errors.
- Focused Room tests passed 12/12 on Oppo CPH2791 / Android 16 with zero
  skips, failures, or errors.
- The applicable `com.edu.quickaside.data.local` connected suite passed 97/97
  on Oppo CPH2791 / Android 16 with zero skips, failures, or errors.
- Full JVM tests passed 114/114 with zero skips, failures, or errors;
  `assembleDebug` and `lintDebug` succeeded.
- Room remains version 7; `MIGRATION_6_7` and schemas 1–7 are unchanged from
  `origin/main`. No migration or destructive fallback was added.
- No UI/screenshot gate applies. Independent engineering review remains
  pending.
