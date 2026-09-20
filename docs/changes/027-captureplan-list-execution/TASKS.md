# Change 027 — CapturePlan List Execution Foundation — TASKS

- Governance: **HIGH-ASSURANCE**
- Status: **ROUND-2 PASS_WITH_NOTES — DOCUMENTATION CLOSEOUT COMPLETE LOCALLY**
- Expected branch: `chg-027-captureplan-list-execution`

This checklist records implementation and observable evidence. It does not
declare an independent engineering verdict. Both focused Room gates have
passed on the authorized real device; see `QA.md` for the repository evidence
index and the Round-1/Round-2 independent review record.

## Ground truth and package

- [x] Fetch and inspect current `origin/main`; verify SHA
      `cb67494a7b57d0f7a939ec06396ccbc665edff7c`.
- [x] Verify starting HEAD equals that base and the starting worktree is clean.
- [x] Verify the requested branch and package did not already exist.
- [x] Read `AGENTS.md`, ACTIVE_WORK, PROJECT_SPEC, ARCHITECTURE, ROADMAP,
      ACCEPTANCE_CRITERIA, AI_WORKFLOW, and Change 018/019/024/025 packages.
- [x] Inspect CapturePlan/domain validation, list-create validation,
      Action Ledger, ListItem/Capture DAOs, database/schema, manual action,
      capture persistence, and runtime flow.
- [x] Confirm Room v7, schemas 1–7, migration chain through 6→7, and that
      required DAO operations already exist.
- [x] Confirm runtime stops at validated CapturePlan and manual list creates
      retain `sourceCaptureId = null`.
- [x] Check orchestration skill availability; `software-project-orchestrator`
      was unavailable, so the repository and user workflow instructions were
      followed.
- [x] Check device availability at preflight (none attached) and verification
      (authorized OPPO CPH2791 attached, Android 16 / API 36).
- [x] Create `chg-027-captureplan-list-execution` from verified `origin/main`.
- [x] Create SPEC/PLAN/TASKS and update ACTIVE_WORK before production changes.

## Application and Room implementation

- [x] Add the CapturePlan-specific execution boundary and deterministic result
      types, with indexed unsupported-action and list-rejection outcomes.
- [x] Accept only all-AddListItem plans with exact IDs `mandado` and
      `compras`; reject unsupported plans before durable writes.
- [x] Reuse `validateListItemCreate` and validate the whole action batch in
      order, including the persisted built-in definition contract, before
      consuming IDs/time or writing.
- [x] Verify the exact source Capture in the execution transaction and use
      only `plan.sourceCaptureId` as ledger provenance.
- [x] Preserve exact text, resolved Mandado session, Compras null session,
      action order, and one shared execution timestamp.
- [x] Persist N strict ListItems and one Action Ledger parent with N ordered
      CREATE/list_item/version-1/null-state mutations atomically.
- [x] Implement targeted Undo with provenance and full ledger/target
      preflight, exact expected-ID matching, and one-row delete/mark proofs.
- [x] Reject null/blank-provenance entries so this boundary cannot Undo
      existing manual list ledger entries.
- [x] Propagate CancellationException and roll back transaction state.
- [x] Keep manual list semantics and production callers unchanged; do not wire
      capture/UI execution.
- [x] Leave database/schema/migrations/dependencies unchanged and add no
      generic executor/replay abstraction.

## Focused tests

- [x] Add focused JVM application/result contract tests.
- [x] Add unique-database `CapturePlanListExecutorDatabaseTest` coverage for
      success, provenance, ordering, sessions, timestamp, rejection, rollback,
      cancellation, targeted Undo, malformed ledgers, and reopen durability.
- [x] Cover unsupported mixed actions/list IDs, missing source Capture, and
      missing active Mandado session without partial writes.
- [x] Cover item-ID collision and ledger parent/child persistence rollback.
- [x] Cover manual-ledger provenance rejection, target mismatch/missing,
      malformed mutation operations/types/version/payload/shape, second Undo,
      delete failure, mark-undone failure, and Undo cancellation.
- [x] Run new Room instrumentation class on the authorized real Android device:
      21 passed, 0 failed/errors, 0 skipped.
- [x] Run `ReversibleListItemActionsDatabaseTest` on the authorized real
      Android device: 9 passed, 0 failed/errors, 0 skipped.

## Verification gates

- [x] Focused JVM class: 4 tests, 4 passed, 0 failed, 0 skipped.
- [x] Focused Room/device test execution — 21/21 passed on OPPO CPH2791,
      Android 16 / API 36.
- [x] Existing manual Room regression execution — 9/9 passed on OPPO CPH2791,
      Android 16 / API 36.
- [x] Full JVM suite: 165 tests, 165 passed, 0 failed, 0 skipped.
- [x] Android test Kotlin compilation completed successfully; this is compile
      evidence only, not instrumentation execution.
- [x] Debug assembly completed successfully.
- [x] Debug lint completed successfully.
- [x] Verify Room remains v7; migration registration and schemas 1–7 are
      unchanged from the verified base.
- [x] Verify app dependencies, manifest, and network-security configuration
      are unchanged from the verified base.
- [x] Run final Git status/stat/name-status/diff-whitespace checks and inspect
      all changed and new files for scope leakage, secrets, and artifacts.
- [x] Prepare the required implementation report without assigning an
      independent engineering verdict.

## Evidence log

- Baseline and starting HEAD: `cb67494a7b57d0f7a939ec06396ccbc665edff7c`;
  the starting worktree was clean.
- Branch: `chg-027-captureplan-list-execution`; implementation commit
  `b9ba067ff442259225127645c8a4c04eeb65dfc6` was pushed for Round-1 review.
  Evidence/remediation commit `eddbfcd505a89ff7f7f0d4a37d511ad92abdcd24`
  was pushed and independently reviewed in Round 2. This documentation-only
  closeout is local and awaits user authorization to commit/push.
- Focused JVM command:
  `./gradlew :app:testDebugUnitTest --tests com.edu.quickaside.application.capture.CapturePlanListExecutorContractTest`
  — historical implementation run was **BUILD SUCCESSFUL**; the recovered
  class XML in `evidence/jvm/full/` reports 4 tests, 0 failures, 0 errors, 0
  skipped. The same class report is part of the recovered full-suite report.
- Full JVM command: `./gradlew :app:testDebugUnitTest` — **BUILD
  SUCCESSFUL**; recovered XML reports 165 tests across 27 result files, 0
  failures, 0 errors, 0 skipped.
- Android test compilation:
  `./gradlew :app:compileDebugAndroidTestKotlin` — **BUILD SUCCESSFUL**; the
  standalone command result is a historical builder record. The connected
  CapturePlan Gradle output records the compile task as `UP-TO-DATE` during
  that successful connected build. No standalone command log or exit-code
  file is present in the repository.
- Assembly: `./gradlew :app:assembleDebug` — **BUILD SUCCESSFUL**; the
  result is a historical builder record. No standalone assembly log or
  exit-code file is present in the repository.
- Lint: `./gradlew :app:lintDebug` — historical run **BUILD SUCCESSFUL**;
  recovered SARIF/text reports are in `evidence/lint/` (0 errors, 16 warnings,
  1 hint).
- Device check: at verification `adb devices -l` reported one authorized
  device, state `device`, product/model `CPH2791`; properties report
  manufacturer OPPO, Android 16, API 36. The unique ADB identifier is redacted
  in the evidence copy.
- New Room command:
  `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.edu.quickaside.data.local.CapturePlanListExecutorDatabaseTest`
  — Round-1 evidence-remediation run **BUILD SUCCESSFUL**; 21 started/passed,
  0 failed, 0 errors, 0 skipped; device `CPH2791 - 16`. Captured Gradle
  output, JUnit XML, runner log, and both exit codes are in
  `evidence/device/capture-plan/`.
- Manual regression command:
  `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.edu.quickaside.data.local.ReversibleListItemActionsDatabaseTest`
  — recovered prior run **BUILD SUCCESSFUL**; 9 started/passed, 0 failed,
  0 errors, 0 skipped; device `CPH2791 - 16`. Its JUnit XML, runner log, and
  runner exit code are in `evidence/device/manual-list/`. The class was not
  rerun because its result artifact was recoverable.
- The first current connected-test attempt was blocked before Gradle launch by
  the sandbox's Gradle wrapper-cache lock permission. The same focused class
  then ran successfully with the established Gradle cache access. The blocked
  attempt is preserved with its exit code and is not a test failure.
- No production or test-source fix was required. The full JVM and lint gates
  were not rerun; their existing reports were recovered. Android test Kotlin
  compilation and debug assembly were rerun during evidence remediation and
  recorded as successful by the builder. The checked-in connected-test log
  includes the compile task state and successful connected build, but no
  standalone compile/assembly logs or exit-code files are in the repository.
- Schema/database comparison:
  `git diff --exit-code origin/main -- app/schemas app/src/main/java/com/edu/quickaside/data/local/QuickAsideDatabase.kt`
  — exit 0, no differences; captured in `evidence/scope/schema-config-comparison.txt`.
- Dependency/configuration comparison:
  `git diff --exit-code origin/main -- app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/res/xml`
  — exit 0, no differences; captured in `evidence/scope/schema-config-comparison.txt`.
- No production capture/UI wiring, gateway/VPS/Tailscale change, provider/auth
  change, or logging change was made. The only generated artifacts added are
  the indexed text/XML/SARIF evidence files; no APK, build cache, screenshot,
  or secret was added.
- The initial default-sandbox Gradle wrapper attempt was blocked by external
  cache/lock permissions; the required commands succeeded with the approved
  Gradle cache access.

## Exact next gate

User authorization to commit/push this documentation-only closeout, followed
by user-authorized merge into `main`. Round 2 returned **PASS_WITH_NOTES** at
`eddbfcd505a89ff7f7f0d4a37d511ad92abdcd24`; the implementation was accepted
with no production correctness finding. No further production or device
re-review is required for this documentation-only closeout. The user retains
merge, release, and production authority.

Stop here. Do not commit, push, merge, release, or start CHG-028.
