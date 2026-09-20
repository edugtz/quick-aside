# CHG-027 QA, Evidence, and Review Record

- Governance: **HIGH-ASSURANCE**
- Implementation commit: `b9ba067ff442259225127645c8a4c04eeb65dfc6`
- Evidence/remediation commit and Round-2 reviewed HEAD:
  `eddbfcd505a89ff7f7f0d4a37d511ad92abdcd24`
- Branch: `chg-027-captureplan-list-execution`
- Verified base / `origin/main`: `cb67494a7b57d0f7a939ec06396ccbc665edff7c`
- Reviewed implementation tree: `3e0e6150cad449c4add573dfe714d64487966a87`
- Round-1 verdict at `b9ba067ff442259225127645c8a4c04eeb65dfc6`:
  **BLOCKED** — 1 BLOCKER / 0 MAJOR / 1 MINOR / 2 NOTE.
- Round-2 verdict at `eddbfcd505a89ff7f7f0d4a37d511ad92abdcd24`:
  **PASS_WITH_NOTES**.

The Round-1 BLOCKER was missing independently inspectable GitHub test/lint/
device evidence, not a discovered production-code correctness defect. The
MINOR was stale current-state documentation and ROADMAP selection wording.

Round-2 findings and closeout:

- The Round-1 BLOCKER is **RESOLVED**; independently inspectable repository
  evidence is present.
- The Round-1 MINOR was **PARTIALLY RESOLVED** at Round 2: important
  project-state corrections had been made, while post-push wording remained
  stale. This documentation closeout corrects that remaining wording.
- Round 2 identified a new MINOR: the QA index cited standalone compile and
  assembly logs and exit-code files that are not present in the repository.
  This closeout removes those claims and records the evidence that does
  exist; it does not add replacement or fabricated artifacts.
- Round-2 NOTE: remediation remained documentation/evidence-only and did not
  reopen production behavior.
- No production correctness finding remains.

Round-1 NOTE findings:

1. No static correctness defect was found in the transaction/rollback/
   cancellation/provenance/targeted-Undo implementation. No implementation
   remediation was requested.
2. The diff exceeds the workflow's generic split signal, but remains one
   coherent behavior; most volume is focused tests/docs. No split was
   requested.

## Evidence provenance

The local branch and `origin/chg-027-captureplan-list-execution` both resolved
to the reviewed implementation SHA at remediation start; `origin/main` matched
the verified base and the worktree was clean. The existing full-JVM, lint, and
manual-list reports were recovered from Gradle's generated report directories.
Their timestamps precede the implementation commit. The implementation run
record says no production or test-source change was made after those gates;
those reports therefore describe the production/test content committed at the
reviewed SHA. The recovered XML/SARIF files do not embed an SCM SHA, so this
provenance is based on the recorded clean baseline, report timestamps, and
unchanged source/test content rather than an embedded revision field.

The missing `CapturePlanListExecutorDatabaseTest` report was regenerated after
the implementation SHA was established. Android test Kotlin compilation and
debug assembly were rerun and recorded by the builder; separate standalone
command logs and exit-code files for those two commands are not present in the
repository. The connected CapturePlan test log does record the Android test
Kotlin compile task as `UP-TO-DATE` within that successful connected build. No
production or test-source changes were made during this remediation.

## Verification results

| Gate | Command / result | Evidence |
|---|---|---|
| Focused JVM | `./gradlew :app:testDebugUnitTest --tests com.edu.quickaside.application.capture.CapturePlanListExecutorContractTest` — **BUILD SUCCESSFUL**, 4 started/passed, 0 failed/errors/skipped. Recovered class XML is part of the full-suite report. | `evidence/recovered-gradle-daemon-excerpts.txt`; `evidence/jvm/full/TEST-com.edu.quickaside.application.capture.CapturePlanListExecutorContractTest.xml` |
| Full JVM | `./gradlew :app:testDebugUnitTest` — **BUILD SUCCESSFUL**, 165 started/passed, 0 failed/errors/skipped across 27 XML reports. | All files under `evidence/jvm/full/`; recovered daemon result in `evidence/recovered-gradle-daemon-excerpts.txt` |
| Android test Kotlin compilation | Historical builder-recorded standalone command `./gradlew :app:compileDebugAndroidTestKotlin` — **BUILD SUCCESSFUL**. The checked-in connected-test Gradle output separately records `:app:compileDebugAndroidTestKotlin UP-TO-DATE` during the successful Room-test build. | `TASKS.md` (historical standalone command record); `evidence/device/capture-plan/gradle-output.txt` (connected-run machine output, not a standalone compile log) |
| Debug assembly | Historical builder-recorded standalone command `./gradlew :app:assembleDebug` — **BUILD SUCCESSFUL**. No standalone assembly log or exit-code artifact is present in the repository. | `TASKS.md` (historical command record only) |
| Lint | `./gradlew :app:lintDebug` — historical **BUILD SUCCESSFUL**; 0 errors, 16 warnings, 1 hint. Gate not rerun. | `evidence/lint/lint-results-debug.sarif`, `evidence/lint/lint-results-debug.txt`, and `evidence/recovered-gradle-daemon-excerpts.txt` |
| New Room executor class | `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.edu.quickaside.data.local.CapturePlanListExecutorDatabaseTest` — **BUILD SUCCESSFUL**, exit 0; 21 started/passed, 0 failed/errors/skipped. | `evidence/device/capture-plan/gradle-output.txt`, `gradle-exit-code.txt`, JUnit XML, runner log, and `test-result-exit-code.txt` |
| Manual-list regression | `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.edu.quickaside.data.local.ReversibleListItemActionsDatabaseTest` — recovered historical **BUILD SUCCESSFUL**; 9 started/passed, 0 failed/errors/skipped; runner exit 0. Gate not rerun. | `evidence/device/manual-list/` and recovered result excerpt |
| Room/schema/config comparisons | Both `git diff --exit-code origin/main -- ...` comparisons exited 0; no Room/database/schema, dependency, manifest, or network-security configuration differences. | `evidence/scope/schema-config-comparison.txt` |

The authorized device was OPPO / model `CPH2791`, Android 16, API 36. At the
required `adb devices -l` check its state was `device` (authorized); the
connected-test output identifies it as `CPH2791 - 16`. The sanitized device
record is `evidence/device/device-identity.txt`.

The initial focused connected-test attempt could not open the Gradle wrapper
cache lock under the default sandbox and exited before Gradle or the test
runner started. That attempt is retained in
`evidence/device/capture-plan/gradle-output-wrapper-permission-blocked.txt`;
the lock path is sanitized. Retrying the same focused class with the existing
Gradle cache access ran all 21 tests and passed. This was an environment
permission block, not a test failure. No fix attempt or source change was
needed.

## Recovered and rerun gates

- Recovered: focused and full JVM XML; lint SARIF/text; manual-list Room JUnit
  XML, instrumentation log, and runner exit code. Their previous command and
  build results remain recorded in `TASKS.md`; focused/full/lint/manual-list
  gates were not rerun.
- Rerun for missing evidence: only the new `CapturePlanListExecutorDatabaseTest`
  Room class among device test classes. It was run as one focused class; the
  existing manual-list regression artifact was recoverable.
- Standalone `compileDebugAndroidTestKotlin` and `assembleDebug` commands were
  rerun during evidence remediation and recorded as successful by the
  builder. Their individual logs and exit-code files are not present in the
  repository. The connected CapturePlan Gradle output is the checked-in
  machine evidence that includes the Kotlin compile task state and successful
  connected build result.
- No full connected Compose/UI suite or screenshot was run or needed.

## Artifact index and sanitization

- `evidence/jvm/full/` — 27 JUnit XML files; together they report 165 tests,
  with no failures, errors, or skips. The focused CapturePlan executor
  contract class report is included here.
- `evidence/recovered-gradle-daemon-excerpts.txt` — minimal recovered Gradle
  output for the focused/full JVM, lint, and manual-list gates.
- `evidence/lint/` — Android Lint SARIF and text report.
- `evidence/device/capture-plan/` — new Room class Gradle output, JUnit XML,
  instrumentation log, runner/Gradle exit codes, and the pre-Gradle permission
  attempt record.
- `evidence/device/manual-list/` — recovered manual-list JUnit XML,
  instrumentation log, and runner exit code.
- `evidence/device/device-identity.txt` — `adb devices -l` and device property
  output with the unique ADB identifier redacted.
- `evidence/scope/schema-config-comparison.txt` — exact database/schema and
  dependency/configuration comparison commands and exit statuses.
- `evidence/device/capture-plan/gradle-output.txt` — connected Room-test
  machine output, including the Android test Kotlin compile task state and
  the successful connected build. It is not a standalone compile-command
  log. `TASKS.md` records the builder-reported standalone compile and assembly
  command results as historical results; their individual logs and exit-code
  files are absent from the repository.

Sanitization was limited to machine-local data: the host name in copied JUnit
XML was replaced with `local-runner`; the local repository/Gradle-cache path
was replaced with a relative or placeholder path in copied lint/Gradle text;
and the unique ADB identifier was redacted. No test content, counts, results,
or source paths within the repository were changed. No ANSI codes or
credential-like values were found in the evidence package.

## GitHub evidence and next gate

The machine-generated evidence listed above is present in the repository at
the evidence/remediation commit. Per the independent Round-2 review, no GitHub
CI run or status check is present. Historical standalone compile and assembly
successes are builder-recorded in `TASKS.md`, not represented by standalone
machine logs in the repository. Round 2 accepted the implementation with
PASS_WITH_NOTES; no further production or device re-review is required for
this documentation-only closeout.

The exact next gate is user authorization to commit/push this
documentation-only closeout, followed by user-authorized merge into `main`.
Do not release or begin CHG-028.
