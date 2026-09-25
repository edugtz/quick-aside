# CHG-029 QA and Evidence Plan

- Governance: **HIGH-ASSURANCE**
- Status: **ROUND-1 REVIEW: BLOCKED — REMEDIATION COMPLETE — ROUND-2 REVIEW PENDING**
- Canonical base HEAD: `797e1557e8b5d94d4c8611a9b72749a8d7ac53f7`
- Branch: `chg-029-captureplan-task-execution`
- Published implementation commit: `2dc0425434c3b5b40a3ff25f1feba85bf3130efb`
- Evidence root: `docs/changes/029-captureplan-task-execution/evidence/`

The planning preflight ran no implementation verification. This file records
the subsequent authorized implementation results and evidence.

## Provenance rule

Every gate gets a durable, inspectable artifact in the repository immediately
after it finishes. Do not rely on terminal scrollback or a later Gradle run to
recover output. For every run, record:

- exact command, start/end time, exit code, and device/emulator identity when
  applicable;
- `git rev-parse HEAD`, branch, and `git status --short` at run time;
- a SHA-256 manifest for every changed production, test, build/config, and
  schema file in the tested worktree, including untracked source files;
- the generated JUnit XML or lint report associated with that command.
- the SHA-256 of the exact tracked worktree diff (`git diff --binary HEAD`) and
  a sorted SHA-256 manifest of changed production/test files, including
  untracked source files. Together these form the reproducible worktree/source
  fingerprint for the gate.

At verification time, implementation was uncommitted. The recorded base HEAD
and branch identify that historical run context; the diff hash and file-level
source hashes identify the tested source state. The verification artifacts
were generated against precommit worktree/source fingerprint
`62d46a058954faf283e449dc9748c9213690738f3c60cb3ae997d36a208d2718` and
production/test source manifest SHA-256
`4ed7d3e16821ab9e030bb0c1f3a916c4735720ad10ae4bf62e8da82433143e32`. The
tested implementation was subsequently committed and pushed by the user as
`2dc0425434c3b5b40a3ff25f1feba85bf3130efb`; production and test source are
unchanged from that tested implementation. This documentation/provenance
closeout does not change code. Historical test artifacts remain bound to the
precommit fingerprints and were not originally commit-bound. Preserve raw logs
and reports; do not replace earlier failed attempts. Add a short summary only
as an index to the underlying artifacts.

## Required gates and artifact destinations

| Gate | Actual command / evidence | Inspectable artifacts | Result |
|---|---|---|---|
| Focused JVM contract | `./gradlew :app:testDebugUnitTest --tests com.edu.quickaside.application.capture.CapturePlanTaskExecutorContractTest --tests com.edu.quickaside.application.capture.CapturePlanValidatorTest` | `evidence/jvm/focused/` includes the first cache-permission failure, passing retry, run records, source manifests, copied JUnit XML, and `junit-summary.json` | **PASS — 32 tests** |
| Focused Room execution/Undo | Built test APKs with `./gradlew :app:assembleDebugAndroidTest`; installed and ran explicitly on `emulator-5556` using `adb -s emulator-5556 shell am instrument -w -r -e class com.edu.quickaside.data.local.CapturePlanTaskExecutorDatabaseTest com.edu.quickaside.test/androidx.test.runner.AndroidJUnitRunner` | `evidence/device/room/task-executor/` includes build output metadata, APK SHA-256 records, the stale-app failure, per-command provenance, raw instrumentation output, and test verdict. APK binaries are intentionally not retained in the repository. | **PASS — 15 tests** |
| Existing Task Room regression | `adb -s emulator-5556 shell am instrument -w -r -e class com.edu.quickaside.data.local.ReversibleTaskActionsDatabaseTest com.edu.quickaside.test/androidx.test.runner.AndroidJUnitRunner` | `evidence/device/room/reversible-task-regression/instrumentation/` includes raw output, run record, source manifests, and test verdict | **PASS — 12 tests** |
| Full JVM suite | `./gradlew :app:testDebugUnitTest` | `evidence/jvm/full/` includes raw output, run record, source manifests, 29 copied JUnit XML files, and `junit-summary.json` | **PASS — 178 tests** |
| Android test Kotlin compile | `./gradlew :app:compileDebugAndroidTestKotlin` | `evidence/build-results/compileDebugAndroidTestKotlin/` includes output, exit code, run record, and source manifests | **PASS** |
| Debug assembly | `./gradlew :app:assembleDebug` | `evidence/device/room/task-executor/current-app-build/` includes Gradle output metadata, the APK SHA-256 record, command output, run record, and source manifests. The APK binary is intentionally not retained in the repository. | **PASS** |
| Lint | `./gradlew :app:lintDebug` | `evidence/lint/` includes raw output, HTML/SARIF, run record, source manifests, and a finding summary | **PASS — 19 existing findings; none on the new executor files** |
| Schema/config/dependency | Compared v7 database/migrations/schemas, DAOs, build/dependency files, manifest, and network-security configuration to base | `evidence/scope/schema-config-dependency-comparison.txt` plus the structured run record `evidence/scope/schema-config-dependency-run-record.json`, with scoped Git checks and source provenance | **PASS — unchanged** |
| Git/scope | `git diff --check`, `git status --short`, `git diff --stat`, `git diff --name-status`, untracked-file inventory, complete diff review | `evidence/scope/git-scope-review.txt`, `evidence/scope/pre-review-readiness.json`, and final provenance | **PASS — within approved scope** |

For each test command, preserve console output, test results, and per-run source
provenance before the next Gradle run. Gradle/JUnit XML is copied for JVM
gates. AndroidJUnitRunner was invoked directly because a production OPPO was
also attached and the user explicitly excluded it; every device command used
`adb -s emulator-5556`. Its raw status stream and a machine-readable verdict are
preserved. The first direct Room attempt loaded an app APK from September 20
that lacked current CHG-029 classes; it ran zero test methods and failed. The
current app was then assembled and installed, and the Room class passed all 15
tests. The earlier failed output remains in evidence.

## Round-1 review and remediation

Independent HIGH-ASSURANCE Round-1 review reviewed branch HEAD
`ad845759c85346c8fe4a976ba211a6f5f53a12c6` and returned **BLOCKED**:
0 BLOCKER / 1 MAJOR / 2 MINOR / 3 NOTE.

| Finding | Disposition | Evidence |
|---|---|---|
| MAJOR-1 — no direct executed evidence for a first persisted Task-ID collision and duplicate generated IDs within one batch | **Addressed.** Two focused Room tests were added directly against `RoomCapturePlanTaskExecutor`; both executed and passed. | `evidence/review-round-1-remediation/device/room/task-executor/instrumentation/`; `test-verdict.json` records the discovered count and per-test final status codes. |
| MINOR-1 — stale current-state wording | **Addressed.** Reconciled in `PLAN.md`, `TASKS.md`, `QA.md`, `SPEC.md`, `docs/ACTIVE_WORK.md`, and `docs/ROADMAP.md`. The foundation exists but is unwired; Google Tasks sync and Event execution remain pending; end-to-end Task natural-language mutation is not complete. | Documentation diff for this remediation. |
| MINOR-2 — QA pointer named `schema-config-dependency-comparison.txt` and `.json` | **Addressed in documentation.** The structured artifact is `scope/schema-config-dependency-run-record.json`; historical machine evidence was not renamed or regenerated. | `scope/schema-config-dependency-run-record.json` remains tracked and unmodified. |
| NOTE-1/2/3 — historical verification notes | **Preserved unchanged.** Focused-JVM Gradle/cache failure, harness argument-order failure, stale-APK zero-test attempt, successful 15/15 canonical Room run, `INSTRUMENTATION_CODE -1` / `Activity.RESULT_OK`, evidence hygiene, and intentionally absent APKs with hashes/metadata retained. | Original `evidence/device/room/task-executor/` and `evidence/jvm/` artifacts unmodified. |

Round-1 remediation gates (new artifacts only):

| Gate | Command | Result | Artifact |
|---|---|---|---|
| Modified androidTest compile | `./gradlew :app:compileDebugAndroidTestKotlin` | **PASS** | `evidence/review-round-1-remediation/device/room/task-executor/compile-android-test-kotlin/` |
| androidTest APK rebuild | `./gradlew :app:assembleDebugAndroidTest` | **PASS** | `evidence/review-round-1-remediation/device/room/task-executor/apk-build/` |
| App APK build | `./gradlew :app:assembleDebug` | **PASS (UP-TO-DATE)**; SHA-256 `24352f4c3a53c6814ea9b90252cc9e0658ee0d30a8f37282df8a4ee981084c35`, matching the reviewed `current-app-build` artifact | `evidence/review-round-1-remediation/device/room/task-executor/app-build/` |
| Install app + test APK | `adb -s emulator-5556 install -r ...` | **PASS** | `install-app/`, `install-test-apk/` |
| Focused Room remediation | `adb -s emulator-5556 shell am instrument -w -r -e class com.edu.quickaside.data.local.CapturePlanTaskExecutorDatabaseTest com.edu.quickaside.test/androidx.test.runner.AndroidJUnitRunner` | **PASS — 17 tests, 0 failures**; both new tests executed and finished with status code 0 | `device/room/task-executor/instrumentation/` with raw output, `test-verdict.json`, `run-record.json`, and provenance |

The remediation run records branch `chg-029-captureplan-task-execution`,
starting HEAD `ad845759c85346c8fe4a976ba211a6f5f53a12c6`, and worktree/source
fingerprint `6fd26bc13d6c3c25f9e096720a0a6b1642de584510ec1adddce782960f490b45`
for every remediation gate. App and test APK SHA-256 values are recorded in the
remediation `app-build/` and `apk-build/` metadata; APK binaries are
intentionally not retained in the repository. Production source is unchanged:
only the focused Room test source, this package's documentation, and the new
remediation evidence changed. The original Round-1 artifacts were not
overwritten. Round 2 has not run.

## Required contract coverage

Focused JVM contract tests should cover result invariants, plan-level and
action-index rejections, exact ordered identity fields, and the existing
validator/constructor rejection of blank titles. Focused Room tests should
prove:

- one and multiple Task actions create exact pending Tasks in order, including
  Personal/Trabajo, optional/null dates, duplicate titles, and shared dates;
- one source-linked ledger parent and exactly N contiguous ordered
  CREATE/task/version-1/null-state mutations;
- unsupported mixed plans, over-limit titles/action counts, and missing source
  Capture write nothing. Blank titles are rejected before an invalid
  `CreateTask` can be constructed and are covered by validator tests;
- duplicate/existing ID collision, second Task insert failure, ledger-parent
  failure, ledger-child failure, and cancellation injected after Task inserts
  leave no partial batch or ledger;
- exact ordered batch Undo deletes only its targets, marks only its parent,
  preserves unrelated Tasks and the source Capture, and survives close/reopen;
- missing/already-undone entry, wrong count/order/IDs, missing target,
  missing source provenance, malformed position/operation/type/version/payload/IDs, delete failure,
  mark failure, and Undo cancellation never produce partial deletion or false
  ledger state;
- the existing per-Task `ReversibleTaskActions` create/Undo semantics remain
  unchanged.

## Scope and stop rules

The final comparison must show Room remains v7; migration definitions and
tracked schemas remain unchanged; no dependency/build/config/manifest/network
changes are present; and only the planned application/data/test/docs files
changed. CaptureSubmission, app/UI/voice/text wiring, Google Tasks/OAuth/sync,
outbox/retry, Event/Note/StructuredLog/UndoLast, reminders, provider/gateway,
confidence, and CHG-030 remain excluded.

Independent HIGH-ASSURANCE Round-1 review reviewed
`ad845759c85346c8fe4a976ba211a6f5f53a12c6` and returned **BLOCKED**
(0 BLOCKER / 1 MAJOR / 2 MINOR / 3 NOTE). Round-1 remediation is complete in
the working tree; Round 2 has not run. CHG-030 remains unreserved.

Follow `AGENTS.md`: identify the first root error before a build fix, stop
after two failed attempts on the same root error, do not run destructive
commands, and do not commit or push.
