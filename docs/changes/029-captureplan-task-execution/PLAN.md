# Change 029 — CapturePlan Task Execution Foundation — PLAN

- Governance: **HIGH-ASSURANCE**
- Status: **IMPLEMENTED — READY FOR INDEPENDENT REVIEW**
- Planning baseline: `797e1557e8b5d94d4c8611a9b72749a8d7ac53f7`

This package selects only the provider-independent Task batch execution
foundation. Do not re-plan CHG-028, wire Capture/UI, or expand to another
action family.

## Preflight findings

- Repository: `edugtz/quick-aside`.
- Starting branch: `main`; starting `HEAD`, local `main`, and `origin/main`
  all matched the request-supplied canonical main SHA
  `797e1557e8b5d94d4c8611a9b72749a8d7ac53f7`.
- Starting worktree was clean. No `chg-029-captureplan-task-execution`
  branch or CHG-029 package existed at preflight.
- CHG-028 is complete and integrated. It auto-executes only all-list-item
  plans. No task CapturePlan execution boundary exists.
- Existing Task and Action Ledger DAOs provide the needed strict insert,
  exact lookup/delete, ordered mutation read, and conditional mark-undone
  operations. Room v7 and the current migration chain need no change.
- `RoomReversibleTaskActions.create()` and `RoomActionLedgerStore.record()`
  have separate transaction/provenance semantics and are not safe batch
  primitives. Direct DAOs inside one `withWriteTransaction` are the minimal
  supported route.
- `software-project-orchestrator` is not present in the available skill
  catalog. This plan follows the explicit HIGH-ASSURANCE request, repository
  `AGENTS.md`, `docs/AI_WORKFLOW.md`, and accepted CHG-027/028 practices.
- Planning made no production/test changes and ran no tests/builds.

## Implementation and verification closeout

1. Reconfirm the canonical base and create the authorized branch while
   preserving preflight documentation.
2. Add the narrow `CapturePlanTaskExecutor` application contract and typed
   execution/Undo outcomes.
3. Add `RoomCapturePlanTaskExecutor` using the existing ID/clock providers and
   direct Room DAOs in a single write transaction.
4. Add exact targeted batch Undo with provenance, ledger-shape, ordered-ID,
   all-target existence, delete-count, and mark-undone checks.
5. Add focused JVM contract coverage and unique-database Room instrumentation.
6. Run the required gates and preserve results and source provenance under
   `evidence/`.
7. Complete the full diff, schema/config/dependency, scope, and evidence review.
   Do not commit or push.

Implementation steps 1–6, final Git/scope review, and the pre-review readiness
check are complete. Results and exact worktree provenance are recorded under
`evidence/`.

## Expected files

Production additions:

- `app/src/main/java/com/edu/quickaside/application/capture/CapturePlanTaskExecutor.kt`
- `app/src/main/java/com/edu/quickaside/data/local/RoomCapturePlanTaskExecutor.kt`

Test additions:

- `app/src/test/java/com/edu/quickaside/application/capture/CapturePlanTaskExecutorContractTest.kt`
- `app/src/androidTest/java/com/edu/quickaside/data/local/CapturePlanTaskExecutorDatabaseTest.kt`

Existing regression to run unchanged:

- `app/src/androidTest/java/com/edu/quickaside/data/local/ReversibleTaskActionsDatabaseTest.kt`

Planning/evidence records:

- `docs/changes/029-captureplan-task-execution/{SPEC,PLAN,TASKS,QA}.md`
- `docs/changes/029-captureplan-task-execution/evidence/README.md`
- `docs/ACTIVE_WORK.md`
- A minimal `docs/ROADMAP.md` status correction only because it currently
  says CHG-029 is not reserved.

No TaskDao, TaskStore, database, entity, schema, migration, manifest, network,
Gradle/dependency, app wiring, CaptureSubmission, UI, or gateway file is
expected to change.

## Atomic execution design

1. Snapshot the ordered action list. Before entering the write sequence,
   reject an unsupported action family or invalid Task title/bounds; never
   filter a mixed plan.
2. Within one Room write transaction, require the exact source Capture.
3. Prepare all Task values/IDs in action order, with exact title,
   `TaskSpace`, optional date, and `completedAt = null`. Ensure generated Task
   IDs are nonblank and unique within the batch; generate one nonblank ledger
   ID. Do not pre-query persisted IDs, so strict ABORT insertion detects
   collisions inside the transaction after earlier inserts where applicable.
4. Strictly insert all Tasks. Then obtain one clock instant and create one
   parent entry with the plan's source Capture ID plus N ordered
   CREATE/task/version-1/null-state mutations. Strictly insert the one parent
   and its children in the same transaction.
5. Any Task/ledger insert, clock/provider failure, or cancellation aborts the
   entire transaction; cancellation propagates to the caller. A later Task ID
   or parent ledger ID collision must exercise and prove rollback of earlier
   writes. Keep strict inserts as the final collision guard.
6. Return exactly the persisted Task IDs in CapturePlan action order and one
   ledger entry ID.

Do not call `ReversibleTaskActions.create()` in a loop: that produces N
transactions, N ledger entries, and null source-Capture provenance. Do not use
`TaskStore.save()` because it is an UPSERT. Do not call the separately
transactional `ActionLedgerStore.record()`.

## Atomic Undo design

Within one Room write transaction, load the exact ledger parent and children;
reject missing/already-undone entries, blank Capture provenance, malformed or
noncontiguous mutation positions, non-CREATE/non-task mutations, wrong version
or payload, blank/duplicate target IDs, and any expected ordered-ID mismatch.
Following CHG-027, do not require the source Capture row to still exist during
Undo. Confirm every Task exists before the first delete. Then delete only the
recorded IDs and require one row per delete; mark only that ledger entry undone
and require one affected row. Any delete/mark failure or cancellation rolls
back every deletion and the ledger update. Never modify/delete the source
Capture.

## Verification results

The actual commands, per-run records, source fingerprints, test outputs, and
report paths are in `QA.md` and `evidence/README.md`. Focused JVM, focused Room,
the direct reversible-Task Room regression, full JVM, Android test Kotlin
compilation, debug assembly, lint, and schema/config/dependency comparison all
passed.

Instrumentation was issued as explicit `adb -s emulator-5556` commands on
`CHG028_Room_API35` (API 35), because the connected-device Gradle task could
also select the attached production OPPO. The first Room invocation exposed a
stale app APK and ran no test methods. Its failed output was preserved; the
app was rebuilt and the focused Room suite then passed. The `assembleDebug`
gate was therefore run before the later build-gate order originally planned.
No production-device acceptance was run.

## Stop conditions

Stop without broadening scope if atomicity requires schema migration, a Task
DAO/domain change, a new dependency, reuse/refactoring of the per-task action
transaction, Capture/UI wiring, Google sync, another action family, generic
Undo/replay, or an unrelated ledger redesign. Follow the project rule to
identify the first root error and stop after two failed attempts at that same
root error. The user retains implementation, commit, push, merge, and release
authority.
