# Change 029 — CapturePlan Task Execution Foundation — SPEC

- Governance: **HIGH-ASSURANCE**
- Status: **COMPLETE — PASS_WITH_NOTES — INTEGRATED INTO main**
- Integrated main SHA: `bcaa53d1993c44304029f6be29937ce42dbaa1e5` (fast-forward; also the pre-merge review-closeout SHA)
- Final reviewed implementation/remediation HEAD: `b466407ae8b98400fe52da40750d18529ff9a3b9`
- Repository: `edugtz/quick-aside`
- Verified planning baseline: `main` / `origin/main` / `HEAD` at
  `797e1557e8b5d94d4c8611a9b72749a8d7ac53f7`; starting worktree clean.
- Implementation branch: `chg-029-captureplan-task-execution`.
- Published implementation commit: `2dc0425434c3b5b40a3ff25f1feba85bf3130efb`.

## Objective

Add the smallest provider-independent boundary that executes one validated
`CapturePlan` only when every action is `CreateTask`. A plan is one logical
execution:

```text
validated CapturePlan
  -> N Tasks in action order
  -> one ActionLedgerEntry linked to sourceCaptureId
  -> N ordered CREATE/task mutations
  -> exact targeted batch Undo
```

This is the task counterpart to CHG-027's list execution foundation. It does
not connect the executor to Capture submission, interpretation, voice, text,
or UI.

## Verified domain and persistence baseline

- `CapturePlan` requires a nonblank `CaptureId` and at least one action.
  `CapturePlanValidator` rejects blank Task titles and titles longer than
  `CapturePlanContract.MAX_TASK_TITLE_CHARS` (500 Unicode code points), and
  preserves action order. The gateway contract limits a plan to 16 actions.
- `CapturePlanAction.CreateTask` contains `TaskSpace`, exact title, and
  nullable date-only `LocalDate dueDate`. Its constructor rejects blank titles.
- `TaskSpace` has exactly `PERSONAL` and `TRABAJO`. No unsupported enum value
  is structurally expressible through the Kotlin contract.
- `Task` contains stable `TaskId`, exact title, `TaskSpace`, optional
  `LocalDate dueDate`, and optional `completedAt`. A new Task is pending when
  `completedAt == null`.
- `TaskDao` already provides strict `ABORT` insertion, exact lookup, and exact
  deletion. `TaskStore.save()` is an UPSERT and is unsuitable for create
  execution because it could replace an existing row.
- The Action Ledger already provides strict parent/child inserts, exact
  parent lookup, ordered mutation reads, and conditional mark-undone.
  `ActionLedgerEntryEntity.sourceCaptureId` references the existing Capture.
- `QuickAsideDatabase` is Room v7 with migrations through 6→7. No DAO addition
  appears necessary for this boundary.
- Existing `RoomReversibleTaskActions.create()` writes one Task and one
  `CREATE/task` ledger per call, uses `sourceCaptureId = null`, and owns a
  transaction per Task. Looping over it cannot satisfy one-ledger batch
  provenance or atomicity. The new Room executor must write directly through
  the existing DAOs in one transaction. `ActionLedgerStore.record()` is also
  a separate transaction and must not be used for the batch.
- CHG-028 currently auto-executes only all-`AddListItem` plans. Task plans
  still stop before execution.

## Application boundary

Use a narrow contract equivalent to:

```kotlin
interface CapturePlanTaskExecutor {
    suspend fun execute(plan: CapturePlan): CapturePlanTaskExecutionResult

    suspend fun undoExecution(
        actionLedgerEntryId: ActionLedgerEntryId,
        expectedTaskIds: List<TaskId>,
    ): UndoCapturePlanTaskExecutionResult
}
```

The contract is task-only; do not add a generic executor, registry, replay, or
application-wide Undo abstraction. Success returns Tasks in action order and
one exact ledger entry ID. Deterministic outcomes distinguish plan-level
rejection (including exceeding the 16-action contract), unsupported actions,
indexed Task-action rejection, missing source Capture, and failure. Undo
outcomes distinguish missing/already-undone entry, unsupported action or
ledger shape, target mismatch/missing target, success, and failure. Preserve
the existing convention that `CancellationException` propagates.

## Execution contract

- Require a nonempty plan and require **every** action to be
  `CapturePlanAction.CreateTask`. Reject the first unsupported action before
  any durable write; never skip, split, or partially execute a mixed plan.
- Check the complete plan/action-count contract and every Task action before
  its first insert. Defensively reject blank or over-limit titles even though
  the validated action constructor/validator already reject them. Respect the
  existing 16-action contract. `TaskSpace` is a non-null closed enum with only
  Personal/Trabajo; `dueDate` is already a valid date-only `LocalDate?`.
- Inside one Room write transaction, require the exact source Capture to
  exist before writes. Preserve the source Capture unchanged.
- Build one pending Task per action with exact title, space, and optional due
  date; do not trim, normalize, deduplicate, or reorder. Duplicate titles and
  shared due dates are valid distinct Tasks. No due date remains null and
  `completedAt` starts null.
- Allocate one nonblank Task ID per action, require generated Task IDs to be
  unique within the batch, and allocate one nonblank ledger entry ID before
  writes. Do not preflight existing database IDs: strict `ABORT` inserts must
  detect a persisted collision so a later Task collision proves rollback of
  earlier inserts. A parent ledger-ID collision must likewise fail its strict
  insert and roll back the Tasks. Use the existing Task ID provider, Action
  Ledger ID provider, and Action Ledger clock conventions.
- In the same Room transaction, persist all N Tasks, exactly one Action Ledger
  parent with `sourceCaptureId = plan.sourceCaptureId` and `undoneAt = null`,
  then N ordered mutations. Each mutation is exactly `CREATE`, target type
  `task`, the corresponding Task ID, payload version 1, and null before/after
  state. Use one logical `occurredAt` for the parent.
- Return Task IDs in original action order. Any later Task insert, parent
  insert, child insert, provider failure, or cancellation rolls back the whole
  batch. Never call `TaskStore.save()` for these creates.

## Targeted batch Undo contract

`undoExecution` targets only one CHG-029 execution; it is not `UndoLast` or
generic ledger replay. Within one Room write transaction, before the first
delete:

1. Load the exact ledger entry; return a deterministic result when missing or
   already undone.
2. Require nonblank source-Capture provenance. Following the accepted CHG-027
   contract, do not require the Capture row to still exist during Undo. Manual
   Task entries with `sourceCaptureId = null` are out of scope.
3. Require a nonempty mutation list with contiguous positions `0..N-1`.
   Every mutation must be `CREATE/task`, payload version 1, with null
   before/after state. Reject blank or duplicate target IDs.
4. Require the recorded target IDs to equal `expectedTaskIds` exactly in
   count, value, and order.
5. Verify every exact Task target exists before deleting any target.

Only after every check passes, delete each exact Task and require exactly one
row deleted for each ID. Then mark only the exact ledger entry undone and
require exactly one row updated. A failure or cancellation rolls back every
delete and the ledger update. Undo leaves the original Capture untouched,
preserves unrelated Tasks/ledger entries, and a second Undo cannot succeed.

## Task-domain cases

- Blank title: rejected by the existing `CreateTask` constructor and draft
  validator; keep a defensive executor check. No Task or ledger writes.
- Unsupported/invalid space: not representable through `TaskSpace`'s closed
  two-value Kotlin enum and non-null action field; no new parsing/schema
  behavior is needed.
- Missing source Capture: deterministic no-write result.
- Multiple actions: one Task per action, IDs and ledger mutations in action
  order.
- Duplicate titles/shared due dates: allowed; IDs distinguish the Tasks.
- No due date: persist `dueDate = null`.
- Initial completion: `completedAt = null` for every created Task.
- Existing Task ID: strict collision failure; the existing Task is unchanged
  and the full new batch rolls back.
- Parent/child ledger failure, later Task insertion failure, or cancellation:
  complete transaction rollback.
- Undo missing ledger, mismatched order/count/ID, missing target, malformed
  provenance/shape/payload, or already-undone entry: no target is deleted and
  the ledger is not newly marked undone.

No Google identifier or sync metadata is introduced.

## Scope exclusions

No CaptureSubmission or `QuickAsideApplication` call-path wiring; no text,
voice, provider, gateway, confidence-policy, receipt, snackbar, UI, navigation,
or Pendientes refresh changes; no Event, Note, StructuredLog, or UndoLast
execution; no generic `ActionExecutor`, registry, replay, or retry; no Google
Tasks/OAuth/API/sync/outbox; no Calendar, reminder, schema/migration,
dependency, build configuration, Task-domain format, or broad Action Ledger
change; no CHG-030 reservation or implementation.

## Verification evidence

The required evidence is indexed in `QA.md` and `evidence/README.md`. Passed
results include focused JVM contract/validator tests (32), focused Room
execution/Undo instrumentation (15), the unchanged reversible Task Room
regression (12), and the full JVM suite (178), all with zero failures.
`compileDebugAndroidTestKotlin`, `assembleDebug`, `lintDebug`, and the
Room/schema/config/dependency comparison also passed. Lint emitted 19 existing
version-availability and unused-resource findings; none point to the new
executor files.

Room instrumentation used the `CHG028_Room_API35` emulator (API 35), with
`adb -s emulator-5556` for install and execution. This explicitly kept the
connected OPPO out of the test run. An initial instrumentation attempt used a
stale app APK and failed before running test methods; the stale artifact and
output are retained beside the rebuilt APK and successful rerun. The first
Gradle wrapper attempt was blocked by sandbox cache permissions, then passed
with the authorized cache access. See per-run records and source fingerprints
under `evidence/`.

No Capture/UI integration or production-device/private-gateway acceptance is
part of this foundation. The test artifacts were generated against the
precommit worktree/source fingerprint
`62d46a058954faf283e449dc9748c9213690738f3c60cb3ae997d36a208d2718` and
production/test source manifest SHA-256
`4ed7d3e16821ab9e030bb0c1f3a916c4735720ad10ae4bf62e8da82433143e32`. That
tested implementation was subsequently committed and pushed by the user as
`2dc0425434c3b5b40a3ff25f1feba85bf3130efb`; this closeout changes documentation
and provenance wording only. The historical test artifacts were not generated
against or labeled with the commit SHA.

Round-1 independent review reviewed branch HEAD
`ad845759c85346c8fe4a976ba211a6f5f53a12c6` and returned **BLOCKED**
(0 BLOCKER / 1 MAJOR / 2 MINOR / 3 NOTE). MAJOR-1 required direct executed
evidence for a first persisted Task-ID collision and for duplicate generated
IDs within one batch. Two focused Room tests were added and executed:
`firstTaskIdCollisionFailsWithoutChangingExistingOrUnrelatedState` and
`duplicateGeneratedTaskIdsFailBeforeInsertionAndPreserveAllExistingState`. The
focused class now passes **17/17** on `CHG028_Room_API35` (API 35,
`emulator-5556`), with artifacts under
`evidence/review-round-1-remediation/`. MINOR-1 corrected stale current-state
wording and MINOR-2 corrected the structured evidence pointer. This
remediation changes no production source because Round 1 found no production
correctness defect. Round-2 independent review reviewed
`b466407ae8b98400fe52da40750d18529ff9a3b9` and returned
**PASS_WITH_NOTES** (0 BLOCKER / 0 MAJOR / 0 MINOR / 3 NOTE); MAJOR-1,
MINOR-1, and MINOR-2 are closed, and no required gate remains. CHG-029 is
**integrated into `main`** at `bcaa53d1993c44304029f6be29937ce42dbaa1e5`. The
foundation remains unwired to normal CaptureSubmission/text/voice; Google
Tasks sync and Event execution remain pending; end-to-end Task natural-language
mutation is not complete. CHG-030 remains unreserved.
