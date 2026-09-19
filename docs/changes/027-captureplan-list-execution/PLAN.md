# Change 027 — CapturePlan List Execution Foundation — PLAN

- Governance: **HIGH-ASSURANCE**
- Status: **IMPLEMENTATION COMPLETE — REVIEW PENDING**
- Expected branch: `chg-027-captureplan-list-execution`

This plan follows the verified `origin/main` baseline. It records the narrow
execution and Undo work selected by the user; it does not reopen the runtime,
manual list behavior, or Room schema design.

## Preflight findings

- Fetched and inspected current `origin/main`; verified base SHA is
  `cb67494a7b57d0f7a939ec06396ccbc665edff7c`.
- Starting `HEAD` matched `origin/main`; the starting worktree was clean.
- The expected branch and Change 027 package were absent before work began.
- `chg-027-captureplan-list-execution` was created from the verified
  `origin/main` without a commit.
- The requested governing docs and the Change 018, 019, 024, and 025 packages
  were read. Existing precedents establish strict ListItem inserts, direct DAO
  transactions, ordered Action Ledger mutations, one-row delete/mark checks,
  failure injection, and cancellation propagation.
- `CapturePlanValidator` accepts exactly `mandado` and `compras` for list
  actions. `validateListItemCreate` is the existing shared validator for
  blank text, definition lookup, Mandado active-session resolution, and
  Compras null-session behavior; the manual boundary already uses it.
- `ListItemDao.insert` is ABORT; `getById`/`deleteById` exist. Capture lookup,
  Action Ledger parent/child writes, ordered child reads, and conditional
  mark-undone also exist. No new DAO operation is currently justified.
- Room is v7 with tracked schemas 1–7 and migrations through 6→7. No schema
  change is planned.
- The runtime flow still persists Capture before interpretation and stops at
  locally validated `CapturePlan`. No executor exists or is wired.
- No device was attached at preflight (`adb devices -l` returned no devices).
  Recheck once during verification; do not call the Room/device gate PASS if
  no authorized real device is available.
- `software-project-orchestrator` is unavailable in the installed skill
  catalog and repository. Use repository `AGENTS.md`, `docs/AI_WORKFLOW.md`,
  and this explicit user brief as the governance workflow.

## Smallest implementation

1. Add a CapturePlan-specific application boundary and typed execution / Undo
   outcomes. Keep it narrow to AddListItem plans and reuse the existing
   ListItem ID, Action Ledger ID, and list clock provider conventions.
2. Add a Room implementation that first rejects unsupported actions and list
   IDs without writes. Inside one write transaction, verify the source Capture,
   resolve and validate every action in original order with
   `validateListItemCreate`, confirm the persisted built-in list behavior,
   then consume IDs/clock and persist all items and the single ordered Action
   Ledger entry.
3. Implement targeted batch Undo in that boundary. Validate the exact entry,
   contiguous mutation positions/shape/targets, and existence of every target
   before the first delete. Prove each delete and mark affects exactly one row; throw
   within the transaction to roll back any unexpected write failure.
4. Leave the manual list-action contract and its callers untouched. Do not add
   app-scoped wiring or connect the new executor to the interpretation flow.
5. Add focused JVM contract coverage and a unique-database Room test covering
   success, precondition rejection, rollback, cancellation, Undo, malformed
   ledgers, and reopen durability.
6. Run the narrowest checks first, then the requested host gates and exact
   scope/schema/Git review. Record unavailable device evidence as PENDING.

## Expected files

Production:

- `app/src/main/java/com/edu/quickaside/application/capture/CapturePlanListExecutor.kt`
- `app/src/main/java/com/edu/quickaside/data/local/RoomCapturePlanListExecutor.kt`

Tests:

- `app/src/test/java/com/edu/quickaside/application/capture/CapturePlanListExecutorContractTest.kt`
- `app/src/androidTest/java/com/edu/quickaside/data/local/CapturePlanListExecutorDatabaseTest.kt`
- Existing regression: `app/src/androidTest/java/com/edu/quickaside/data/local/ReversibleListItemActionsDatabaseTest.kt`

Documentation:

- `docs/ACTIVE_WORK.md`
- `docs/changes/027-captureplan-list-execution/SPEC.md`
- `docs/changes/027-captureplan-list-execution/PLAN.md`
- `docs/changes/027-captureplan-list-execution/TASKS.md`

No DAO, database, entity, schema, migration, manifest, UI, capture-flow, or
dependency file is expected to change.

## Atomic execution design

Preflight action-family and exact list-ID support without writes. In one Room
write transaction, check the exact source Capture and call the current list
create validator for every action in plan order. Retain each resolved
definition/session result. If any precondition fails, return its deterministic
outcome before consuming IDs or the clock.

After the complete batch validates, generate one ListItem ID per action, take
one clock value, and create the exact items. Insert the items with the existing
strict ABORT DAO operation. Insert one parent Action Ledger entry using the
plan's sourceCaptureId, then insert its N ordered CREATE/list_item mutations.
All rows are in that same transaction. Any exception, collision, or
cancellation aborts and rolls back the whole transaction.

## Atomic Undo design

Within one Room write transaction, load the entry and ordered mutations and
check that the entry has nonblank Capture provenance, every
mutation/expected-ID condition holds, and every target exists before deleting
anything. Manual list entries with null Capture provenance are out of scope.
Then delete targets in recorded order and require each delete count to be one;
conditionally mark the exact entry undone and require one affected row. A
failed assertion or DAO call aborts the transaction. Cancellation is
rethrown.

## Verification order

1. Focused JVM tests for application result/index contracts.
2. Focused new Room/device class on the authorized real device, if attached.
3. Existing `ReversibleListItemActionsDatabaseTest` Room regression.
4. `./gradlew :app:testDebugUnitTest`.
5. `./gradlew :app:compileDebugAndroidTestKotlin`.
6. `./gradlew :app:assembleDebug`.
7. `./gradlew :app:lintDebug`.
8. Verify Room remains v7; migrations and schemas 1–7 are unchanged.
9. Run `git diff --check`, `git status --short`, `git diff --stat`, and
   `git diff --name-status`; inspect the complete diff for scope leakage.

Do not run the full connected Compose/UI suite. Stop at the first real
validation error, diagnose the root cause, and attempt at most two focused
fixes for that same blocker. Do not claim the Room/device gate passed without
an authorized real device run.

## Stop state and authority

After implementation and all obtainable verification, stop at
`IMPLEMENTATION COMPLETE — REVIEW PENDING`. The user reviews this report and
local diff, then separately authorizes commit/push. Independent
HIGH-ASSURANCE review is later performed against the actual GitHub branch.
Do not commit, push, merge, release, or start CHG-028.

## Verification outcome

- Focused JVM contract tests: 4 passed, 0 failed, 0 skipped.
- Full JVM suite: 165 passed, 0 failed, 0 skipped.
- Android test Kotlin compilation, debug assembly, and lint completed
  successfully.
- On the authorized OPPO CPH2791 running Android 16 / API 36, the focused
  `CapturePlanListExecutorDatabaseTest` passed 21/21 tests and
  `ReversibleListItemActionsDatabaseTest` passed 9/9 tests, sequentially.
- Both Room/device gates passed. No code or test fix was needed after these
  runs.
- Room v7, migrations, tracked schemas, dependencies, and manifest/network
  security configuration are unchanged from the verified base.
- No commit or push was created. See `TASKS.md` for exact commands and final
  Git scope evidence.
