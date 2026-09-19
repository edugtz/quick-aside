# Change 027 — CapturePlan List Execution Foundation — PLAN

- Governance: **HIGH-ASSURANCE**
- Status: **ROUND-1 REVIEW BLOCKED — REMEDIATION COMPLETE — ROUND-2 RE-REVIEW PENDING**
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
- At the verified base, the runtime flow persisted Capture before
  interpretation and stopped at locally validated `CapturePlan`; no executor
  existed or was wired. CHG-027 adds the narrow executor without adding caller
  or capture/UI wiring.
- At initial preflight, `adb devices -l` returned no attached devices. During
  verification an authorized OPPO CPH2791 running Android 16 / API 36 was
  available and both focused Room classes passed.
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

## Round-1 review and evidence remediation

- Independent Round-1 review of `b9ba067ff442259225127645c8a4c04eeb65dfc6`
  returned **BLOCKED**, with 1 BLOCKER / 0 MAJOR / 1 MINOR / 2 NOTE.
- The BLOCKER concerns inspectable test/lint/device evidence on GitHub; the
  reviewer reported no production-code correctness defect. The MINOR concerns
  stale current-state wording and ROADMAP selection state.
- Remediation is limited to machine-generated evidence and the current-state
  documents authorized by the review brief. `QA.md` indexes recovered and
  newly captured evidence, identifies the reviewed SHA, and distinguishes
  local repository evidence from GitHub CI/status evidence.
- The overwritten new Room test report was regenerated on the reviewed HEAD.
  Android test Kotlin compilation and debug assembly were rerun to retain
  standalone command output. The full JVM, lint, and existing manual-list
  reports were recovered. No production or test-source fix was required.

## Stop state and authority

The implementation is committed/pushed at reviewed SHA
`b9ba067ff442259225127645c8a4c04eeb65dfc6`; its Round-1 review returned
BLOCKED. This remediation adds only evidence and current-state documentation.
Stop at `REMEDIATION COMPLETE — RE-REVIEW PENDING`. The user reviews the local
diff and separately authorizes commit/push of this remediation. Independent
HIGH-ASSURANCE Round-2 review follows against the updated GitHub HEAD.
Do not commit, push, merge, release, or start CHG-028 in this remediation.

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
- Round-1 evidence remediation reran the missing `CapturePlanListExecutorDatabaseTest`
  on the reviewed HEAD and captured standalone Android test Kotlin compilation
  and debug assembly output. Existing full JVM, lint, and manual-list reports
  were recovered; the full JVM, lint, and manual-list gates were not rerun.
- See `QA.md` and `evidence/` for report files, sanitized device details,
  exact commands, results, and provenance notes. No independently available
  GitHub CI/status evidence was found.
- Room v7, migrations, tracked schemas, dependencies, and manifest/network
  security configuration are unchanged from the verified base.
- The reviewed implementation commit is `b9ba067ff442259225127645c8a4c04eeb65dfc6`.
  No remediation commit or push has been created. See `TASKS.md` and `QA.md`
  for the evidence index and current next gate.
