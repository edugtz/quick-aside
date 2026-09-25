# CHG-029 Round-1 Remediation Evidence Index

- Governance: **HIGH-ASSURANCE**.
- Branch: `chg-029-captureplan-task-execution`.
- Starting HEAD for this remediation: `ad845759c85346c8fe4a976ba211a6f5f53a12c6`.
- Published implementation commit under review:
  `2dc0425434c3b5b40a3ff25f1feba85bf3130efb`.
- Canonical base: `797e1557e8b5d94d4c8611a9b72749a8d7ac53f7`.
- Round-1 independent-review verdict: **BLOCKED** — 0 BLOCKER / 1 MAJOR /
  2 MINOR / 3 NOTE.
- Scope of this remediation: tests, evidence, and documentation only. No
  production source changed.

Starting worktree state is recorded honestly: the worktree was **not clean**
at the start of this remediation turn. It contained the uncommitted
`app/src/androidTest/java/com/edu/quickaside/data/local/CapturePlanTaskExecutorDatabaseTest.kt`
modification that adds the two MAJOR-1 tests. This turn verified, compiled,
and executed that test change and completed the evidence/documentation
remediation. `ad845759c85346c8fe4a976ba211a6f5f53a12c6` is the reviewed
Round-1 HEAD, not a remediation implementation SHA.

## Remediation result

`com.edu.quickaside.data.local.CapturePlanTaskExecutorDatabaseTest`:

- **PASS — 17 tests, 0 failures, 0 errors, 0 skipped** (discovered count from
  the raw stream, not an assumed value).
- Both MAJOR-1 tests executed and finished with
  `INSTRUMENTATION_STATUS_CODE: 0`:
  - `firstTaskIdCollisionFailsWithoutChangingExistingOrUnrelatedState`
  - `duplicateGeneratedTaskIdsFailBeforeInsertionAndPreserveAllExistingState`
- Device: AVD `CHG028_Room_API35`, API 35, Android 15, serial
  `emulator-5556`, arm64-v8a. Every install and instrumentation command used
  explicit `adb -s emulator-5556`. The production OPPO was not attached or
  used.
- Raw output: `device/room/task-executor/instrumentation/instrumentation-output.txt`.
- Machine-readable verdict:
  `device/room/task-executor/instrumentation/test-verdict.json`.
- Run record and provenance:
  `device/room/task-executor/instrumentation/run-record.json`,
  `provenance-start/`, `provenance-end/`.

## Gates in this artifact set

| Gate | Command | Result |
|---|---|---|
| Modified androidTest compile | `./gradlew :app:compileDebugAndroidTestKotlin` | PASS |
| androidTest APK rebuild | `./gradlew :app:assembleDebugAndroidTest` | PASS |
| App APK build | `./gradlew :app:assembleDebug` | PASS (UP-TO-DATE; hash matches the reviewed build) |
| Install app APK | `adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk` | PASS |
| Install test APK | `adb -s emulator-5556 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | PASS |
| Focused Room remediation | `adb -s emulator-5556 shell am instrument -w -r -e class com.edu.quickaside.data.local.CapturePlanTaskExecutorDatabaseTest com.edu.quickaside.test/androidx.test.runner.AndroidJUnitRunner` | PASS — 17 tests |

The one app APK build was up-to-date; its SHA-256
`24352f4c3a53c6814ea9b90252cc9e0658ee0d30a8f37282df8a4ee981084c35` matches
`device/room/task-executor/current-app-build/app-apk-sha256.json` from the
original CHG-029 verification, so the installed app is the same reviewed
artifact. The rebuilt androidTest APK SHA-256 is
`fb2f5dd710a02d62efd592c66d559970dd5b29c1a8869f12183b249976df0351`
and contains both new test method names (see
`device/room/task-executor/apk-content-verification.txt`).

## Provenance

All remediation gates recorded branch `chg-029-captureplan-task-execution`,
starting HEAD `ad845759c85346c8fe4a976ba211a6f5f53a12c6`, clean-run
instrumentation preconditions, and the remediation worktree/source
fingerprint:

`6fd26bc13d6c3c25f9e096720a0a6b1642de584510ec1adddce782960f490b45`

Each gate directory contains `run-record.json`, `exit-code.txt`,
`provenance-start/`, and `provenance-end/`. The source manifest for this
fingerprint covers the modified androidTest test source. Documentation edits
made after the test run are outside the tested source manifest; they are
reviewable in Git and in the remediation readiness record.

`parse_room_verdict.py` derives `test-verdict.json` and augments the
instrumentation `run-record.json` from the raw stream. It never assumes a
test count.

## Preservation of original Round-1 evidence

The original committed CHG-029 artifacts remain untouched at
`docs/changes/029-captureplan-task-execution/evidence/` (for example the
15-test canonical run under `device/room/task-executor/` and the failed
attempts). This remediation added a sibling `review-round-1-remediation/`
tree and did not overwrite, rename, or regenerate any historical machine
evidence.

## Binary retention

No APK, AAB, DB, WAL, or SHM binary is retained in the repository. Build
identity is preserved through SHA-256 records, Gradle output metadata, and
the APK content verification file. The APK inputs themselves remain in the
ignored Gradle build directory and are not tracked.

## Round-2 status

Independent HIGH-ASSURANCE Round-2 review reviewed
`b466407ae8b98400fe52da40750d18529ff9a3b9` and returned
**PASS_WITH_NOTES** (0 BLOCKER / 0 MAJOR / 0 MINOR / 3 NOTE); MAJOR-1,
MINOR-1, and MINOR-2 are closed. Both new tests, their execution, the raw Room
output, and this provenance were independently inspected without remediation.
CHG-029 is **COMPLETE — PASS_WITH_NOTES — INTEGRATED INTO main** at
`bcaa53d1993c44304029f6be29937ce42dbaa1e5` (fast-forward; also the pre-merge
review-closeout SHA). CHG-030 remains unreserved.
