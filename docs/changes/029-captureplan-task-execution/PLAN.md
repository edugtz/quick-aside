# Change 029 — CapturePlan Task Execution Foundation — PLAN

- Governance: **HIGH-ASSURANCE**
- Status: **ROUND-1 REVIEW: BLOCKED — REMEDIATION COMPLETE — ROUND-2 REVIEW PENDING**
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
check are complete. The builder stopped before commit/push; the user
subsequently committed and pushed the implementation as
`2dc0425434c3b5b40a3ff25f1feba85bf3130efb` on
`chg-029-captureplan-task-execution`. The verification artifacts were generated
before that commit against worktree/source fingerprint
`62d46a058954faf283e449dc9748c9213690738f3c60cb3ae997d36a208d2718` and
production/test source manifest SHA-256
`4ed7d3e16821ab9e030bb0c1f3a916c4735720ad10ae4bf62e8da82433143e32`. They
identify the tested source state; they were not originally commit-bound.
Results and provenance records remain under `evidence/`.

## Round-1 independent review and remediation

Independent Round-1 review reviewed branch HEAD
`ad845759c85346c8fe4a976ba211a6f5f53a12c6` (implementation commit
`2dc0425434c3b5b40a3ff25f1feba85bf3130efb`) and returned **BLOCKED**:
0 BLOCKER / 1 MAJOR / 2 MINOR / 3 NOTE.

- MAJOR-1 — the new batch executor lacked direct executed evidence for two
  ID-integrity paths. Remediation added two focused Room tests directly
  against `RoomCapturePlanTaskExecutor`:
  `firstTaskIdCollisionFailsWithoutChangingExistingOrUnrelatedState` and
  `duplicateGeneratedTaskIdsFailBeforeInsertionAndPreserveAllExistingState`.
  Both executed and passed; the focused class now reports **17/17** on
  `CHG028_Room_API35` (API 35, `emulator-5556`). New artifacts are under
  `evidence/review-round-1-remediation/`; the original 15-test artifacts
  remain unchanged.
- MINOR-1 — stale current-state wording was reconciled here and in
  `TASKS.md`, `QA.md`, `SPEC.md`, `docs/ACTIVE_WORK.md`, and
  `docs/ROADMAP.md`. The Task execution foundation exists but remains
  unwired to normal CaptureSubmission/text/voice; Google Tasks sync and Event
  execution remain pending; end-to-end Task natural-language mutation is not
  complete.
- MINOR-2 — the `QA.md` schema/config/dependency pointer now names the actual
  structured artifact `scope/schema-config-dependency-run-record.json`. No
  historical machine evidence was renamed or regenerated.
- NOTE-1/2/3 — the Round-1 historical notes (focused-JVM Gradle/cache
  environment failure, harness argument-order failure, stale APK / zero-test
  initial Room attempt, the successful 15/15 canonical Room run,
  `INSTRUMENTATION_CODE -1` / `Activity.RESULT_OK` correction, evidence
  hygiene conclusions, and intentionally absent APKs with hashes/metadata
  retained) remain preserved unchanged in `QA.md` and `evidence/README.md`.

Production source changed by this remediation: none. Round 1 found no
production correctness defect, so remediation is tests/evidence/docs only.
Round 2 has not run, and CHG-030 remains unreserved.

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
