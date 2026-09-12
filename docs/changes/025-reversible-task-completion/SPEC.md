# Change 025 — Reversible Task Completion Actions Foundation — SPEC

Governance: **HIGH-ASSURANCE**
Status: **IMPLEMENTATION COMPLETE — REVIEW PENDING**
Expected branch: `chg-025-reversible-task-completion`

## Objective

Extend the existing provider-independent `ReversibleTaskActions` boundary with
the smallest application surface needed to complete and reopen a Task and to
targeted-Undo that completion-state change. The boundary owns the durable
completion mutation and its Action Ledger record; UI, CapturePlan execution,
and runtime providers remain outside this change.

## Proven baseline

- `origin/main` and `origin/chg-024-reversible-task-create` are both
  `9d411908ec529117c9daf9fa23a3c8509a719346`.
- CHG-024 is merged into that base and provides Task CREATE plus targeted Undo.
- `Task.completedAt == null` means pending; non-null means completed.
- Room is version 7 with the existing explicit 6→7 migration.
- `TaskStore.save()` is stable-ID `@Upsert` behavior and remains unchanged.
- Runtime AI is paused at the provider/runtime boundary.

## Application contract

The existing `ReversibleTaskActions` interface gains:

```kotlin
suspend fun complete(taskId: TaskId): TaskCompletionActionResult

suspend fun reopen(taskId: TaskId): TaskCompletionActionResult

suspend fun undoCompletionChange(
    actionLedgerEntryId: ActionLedgerEntryId,
    expectedTaskId: TaskId,
): UndoTaskCompletionChangeResult
```

Completion and reopen return typed deterministic outcomes:

- `Changed(task, actionLedgerEntryId)`;
- `MissingTask`;
- `AlreadyInRequestedState`;
- `Failed(cause)`.

Cancellation propagates. `Changed.task` is read from the exact state persisted
by the transaction.

## Completion/reopen semantics

Each successful mutation runs in one Room write transaction:

1. load the exact Task;
2. return `MissingTask` when absent;
3. return `AlreadyInRequestedState` without any writes when already in the
   requested state;
4. canonicalize the injected Action Ledger clock to epoch-millisecond
   precision;
5. update only `completed_at_epoch_millis` through nullable compare-and-set;
6. persist one Action Ledger parent and exactly one `UPDATE/task/version-1`
   child; and
7. return the exact resulting Task and entry ID.

`complete` uses the canonical clock instant as `completedAt`. `reopen` uses the
exact existing persisted completion millisecond value as its `beforeState` and
sets the Task completion column to null.

The existing Task CREATE ledger shape is not changed.

## Completion UPDATE payload v1

The only supported v1 states are:

```text
pending
completed:<epochMillis>
```

Tokens are lowercase and exact; there is no surrounding whitespace. The
completed value is the exact base-10 `Long.toString()` representation of an
epoch-millisecond value. Decoding is strict, locale-independent, and narrow to
Task completion state; malformed values are rejected without repair.

Both `beforeState` and `afterState` are non-null. The only valid transitions
are `pending -> completed:<millis>` and `completed:<millis> -> pending`.
Same-state transitions, completed-to-completed transitions, null states,
unknown tokens, and malformed values are unsupported ledger shapes.

## Targeted completion Undo

`undoCompletionChange` is not generic Undo. In one Room write transaction it:

1. loads the exact entry and rejects missing/already-undone entries;
2. requires exactly one child mutation;
3. requires `UPDATE`, target type `task`, payload version `1`, and the exact
   requested Task ID;
4. strictly decodes both non-null payload states and requires a valid v1
   transition;
5. loads the exact Task and rejects a missing target;
6. compares the Task's current completion state with the recorded `afterState`;
7. returns `TargetStateMismatch` with no mutation if those states differ;
8. restores only `completedAt` through compare-and-set;
9. marks exactly that ledger entry undone and proves one row changed; and
10. returns `Undone(actionLedgerEntryId, taskId)`.

If a Task restoration succeeds but mark-undone fails, the Room transaction
rolls back both. Cancellation also propagates and rolls back. A stale entry
such as `complete -> A -> completed`, followed by `reopen -> B -> pending`,
cannot restore or claim Undo for A.

## Persistence invariants

Add only a nullable compare-and-set DAO update for the completion column:

```text
update Task ID's completion column
only when its current nullable value equals the expected nullable value
return affected row count
```

Completion/reopen/Undo must preserve `id`, `title`, `space`, and `dueDate`.
Undo must preserve newer unrelated-field changes when the current completion
state still matches the ledger's recorded `afterState`.

Task mutation and both ledger rows are atomic. No call to `TaskStore.save()` or
`ActionLedgerStore.record()` is used as a separate transaction boundary.

## In scope

- typed Task completion/reopen/targeted-Undo application results;
- strict Task completion payload-v1 codec;
- atomic Room-backed implementation and nullable CAS DAO operation;
- focused JVM contract/codec tests;
- focused Room/device tests for success, no-op, malformed ledger, rollback,
  cancellation, stale state, durability, and narrow-field preservation;
- live CHG-025 documentation and evidence.

## Explicit out of scope

No UI/navigation/screenshots; Google Tasks/Calendar/OAuth/sync; reminders or
notifications; CapturePlan execution; ActionExecutor; generic Undo/redo/replay;
arbitrary Task editing/deletion; title/space/due-date actions; source capture
execution; AI/runtime/provider/gateway work; dependencies; Room/schema/migration
changes; generic Action Ledger serialization; outbox/retry/conflict behavior;
or unrelated refactors.

## Required evidence

Record actual results for focused JVM codec/contract tests, the focused Room
instrumentation class on the authorized device when available, existing
CHG-024/Task/Action Ledger and applicable `data.local` regressions, full JVM,
`assembleDebug`, `lintDebug`, Room/version/migration and schema comparison,
`git diff --check`, and final status/stat/scope inspection. Keep the
independent-review item unchecked.

## Builder evidence

- Focused JVM `ReversibleTaskCompletionActionsContractTest` and
  `TaskCompletionLedgerPayloadTest`: `6/6` passed; skips/failures/errors `0`.
- Focused `ReversibleTaskCompletionActionsDatabaseTest`: `14/14` passed on
  Oppo CPH2791 / Android 16; skips/failures/errors `0`.
- The focused Room class explicitly covers Task restoration/update failure
  during `undoCompletionChange`, proving the completed Task, active ledger
  entry, and unrelated Task/ledger state remain unchanged after rollback.
- Existing CHG-024 `ReversibleTaskActionsDatabaseTest`: `12/12` passed on
  Oppo CPH2791 / Android 16.
- Existing `TaskPersistenceDatabaseTest`: `6/6` passed on Oppo CPH2791 /
  Android 16; existing `ActionLedgerPersistenceDatabaseTest`: `10/10` passed.
- Applicable `com.edu.quickaside.data.local` connected suite: `111/111`
  passed on Oppo CPH2791 / Android 16; skips/failures/errors `0`.
- Full `./gradlew :app:testDebugUnitTest`: `120/120` passed; skips/failures/
  errors `0`.
- `./gradlew :app:assembleDebug`: `BUILD SUCCESSFUL`.
- `./gradlew :app:lintDebug`: `BUILD SUCCESSFUL`.
- Room remains version `7`; migrations 1→2 through 6→7 remain registered and
  no destructive fallback is configured. `app/schemas` is byte-for-byte
  unchanged from `origin/main` (schema diff exit `0`); no generated schema
  file entered the diff.
- `adb devices -l` showed authorized Oppo CPH2791 / Android 16. `git diff
  --check` was clean.
- Two regression commands were initially launched concurrently and produced
  device APK-lifecycle/output failures; serial reruns passed. No production
  code was changed for that invocation issue.

## Builder authority and stop state

The builder must not commit, push, merge, release, or assign the independent
engineering verdict. When obtainable work is complete, set this SPEC, PLAN,
TASKS, and `docs/ACTIVE_WORK.md` to:

`IMPLEMENTATION COMPLETE — REVIEW PENDING`
