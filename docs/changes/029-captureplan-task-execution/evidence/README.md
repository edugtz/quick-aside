# CHG-029 Evidence Index

## Provenance

- Governance: **HIGH-ASSURANCE**.
- Branch: `chg-029-captureplan-task-execution`.
- Canonical base HEAD: `797e1557e8b5d94d4c8611a9b72749a8d7ac53f7` (not an
  implementation SHA).
- Published implementation commit: `2dc0425434c3b5b40a3ff25f1feba85bf3130efb`
  (committed and pushed by the user; independent review remains pending).
- Verification was generated before commit against precommit worktree/source
  fingerprint `62d46a058954faf283e449dc9748c9213690738f3c60cb3ae997d36a208d2718`
  and production/test source manifest SHA-256
  `4ed7d3e16821ab9e030bb0c1f3a916c4735720ad10ae4bf62e8da82433143e32`.
  The tested implementation was subsequently committed and pushed as the
  commit above. The historical artifacts were not generated against or labeled
  with that commit SHA.
- Initial pre-verification worktree/source fingerprint:
  `368c63c203e4201e4e10f40ac7a96ee8ea1d878fe11549def092a041c7434ee4`.
- The repeatable fingerprint algorithm is recorded in `provenance.py`; every
  gate's `provenance-start/` and `provenance-end/` directory contains the
  branch, base HEAD, worktree status, tracked-diff hash, changed-source hash
  manifest, and combined worktree/source fingerprint.
- `run_gate.py` records the exact command, start/end UTC, process and gate exit
  status, device identity when applicable, raw output, and start/end
  provenance. AndroidJUnitRunner gates retain the raw instrumentation stream
  and `test-verdict.json` because direct `adb` execution does not create
  Gradle JUnit XML.

## Gate results

- Focused JVM contract plus validator: **PASS, 32 tests**. Raw output, JUnit
  XML, summary, and provenance are in `jvm/focused/retry-1/`. The initial
  wrapper run was blocked before Gradle execution because sandbox permissions
  denied a lock under `~/.gradle`; the failed attempt is retained in
  `jvm/focused/`.
- Focused Room Task execution/Undo: **PASS, 15 tests** on
  `CHG028_Room_API35`, API 35, serial `emulator-5556`. APK SHA-256 records and
  Gradle output metadata are in `device/room/task-executor/apk-build/` and
  `device/room/task-executor/current-app-build/`; APK binaries are
  intentionally not retained in the repository. Emulator install records
  and the passing raw test stream/verdict are under
  `device/room/task-executor/`. Every install/test command explicitly used
  `adb -s emulator-5556`.
- Round-1 remediation focused Room rerun: **PASS, 17 tests** on
  `CHG028_Room_API35`, API 35, serial `emulator-5556`. This is a new, separate
  artifact set under `review-round-1-remediation/`; both new ID-integrity
  tests executed and finished with status code 0. The original 15-test
  canonical run above is preserved unchanged.
- Existing `ReversibleTaskActionsDatabaseTest` regression: **PASS, 12 tests**.
  Raw instrumentation output, verdict, run record, and provenance are in
  `device/room/reversible-task-regression/instrumentation/`.
- Full JVM suite: **PASS, 178 tests**. Raw output, 29 copied JUnit XML files,
  summary, run record, and provenance are in `jvm/full/`.
- `compileDebugAndroidTestKotlin`: **PASS**; output and provenance are in
  `build-results/compileDebugAndroidTestKotlin/`.
- `assembleDebug`: **PASS**; output, Gradle APK metadata, APK SHA-256, and
  provenance are in `device/room/task-executor/current-app-build/`. The APK
  binary is intentionally not retained in the repository.
- `lintDebug`: **PASS**; output, copied HTML/SARIF, run record, and provenance
  are in `lint/`. SARIF reports 19 existing dependency/version and unused
  resource findings, with no findings in the new executor files.
- Room v7/schema/migration/build/dependency/manifest/network comparison:
  **PASS, unchanged**. Commands and results are in
  `scope/schema-config-dependency-comparison.txt` plus its structured run
  record `scope/schema-config-dependency-run-record.json`.
- Final Git/scope review and PRE-REVIEW READINESS CHECK: **PASS — READY FOR
  INDEPENDENT REVIEW**. The captured commands, inventory, and criteria are in
  `scope/git-scope-review.txt` and `scope/pre-review-readiness.json`.

## Round-1 review remediation

Independent Round-1 review reviewed `ad845759c85346c8fe4a976ba211a6f5f53a12c6`
and returned **BLOCKED**: 0 BLOCKER / 1 MAJOR / 2 MINOR / 3 NOTE.

- MAJOR-1 added two focused Room tests directly against
  `RoomCapturePlanTaskExecutor`
  (`firstTaskIdCollisionFailsWithoutChangingExistingOrUnrelatedState`,
  `duplicateGeneratedTaskIdsFailBeforeInsertionAndPreserveAllExistingState`).
  Both executed and passed; the class reported 17 tests with zero failures.
  Raw output, `test-verdict.json`, `run-record.json`, APK SHA-256 metadata,
  install records, device identity, and per-run provenance are under
  `review-round-1-remediation/`.
- MINOR-1 reconciled stale current-state wording in the CHG-029 package,
  `docs/ACTIVE_WORK.md`, and `docs/ROADMAP.md`; the Task execution foundation
  exists but remains unwired to Capture, and Google Tasks sync and Event
  execution remain pending.
- MINOR-2 corrected the schema/config/dependency pointer in `QA.md` and this
  index to `scope/schema-config-dependency-run-record.json`. No historical
  machine evidence was renamed or regenerated.
- Round-1 NOTE findings remain preserved exactly as recorded below.

Remediation changed no production source. The original 15-test artifacts were
not overwritten, and no APK/AAB/DB/WAL/SHM binary was added to the repository.

## Preserved failed attempts and interpretation

- The first focused JVM wrapper attempt failed before Gradle started because
  the sandbox denied the required Gradle user-cache lock. Its output and exit
  code are retained; the authorized retry passed.
- One APK-build harness invocation put `--device-info` after the positional
  output path. Gradle did not start. The exact harness error and correction
  are recorded in `device/room/task-executor/apk-build/harness-attempt-1.json`.
- The first instrumentation attempt loaded an app APK last built on September
  20, which did not contain the CHG-029 result classes referenced by the
  September 23 test APK. It failed during test-runner initialization before
  any test method ran. The failed output, diagnosis, APK SHA-256 records, and
  build metadata are preserved under `device/room/task-executor/`; APK
  binaries are intentionally not retained in the repository. The app was
  rebuilt and all 15 Room tests passed afterward.
- AndroidJUnitRunner ends successful runs with `INSTRUMENTATION_CODE: -1`
  (`Activity.RESULT_OK`). Gate verdicts use the emitted JUnit summary and
  failure markers; raw outputs remain available for independent inspection.
  The first wrapper interpretation of the successful 15-test run treated that
  result code as a failure; the corrected PASS and reason are recorded in
  `device/room/task-executor/instrumentation/retry-1/gate-verdict-correction.json`.

The builder stopped before commit/push; the user subsequently committed and
pushed the tested implementation. This post-push closeout changes documentation
and provenance wording only and did not rerun tests or alter historical test
artifacts. The connected OPPO was not used. No production-device,
private-gateway, or Capture/UI work was performed.

Independent HIGH-ASSURANCE Round-1 review subsequently reviewed
`ad845759c85346c8fe4a976ba211a6f5f53a12c6` and returned **BLOCKED**
(0 BLOCKER / 1 MAJOR / 2 MINOR / 3 NOTE). The MAJOR-1 remediation added and
executed two focused Room tests (17/17 on `CHG028_Room_API35`, API 35,
`emulator-5556`); MINOR-1 and MINOR-2 reconciled current-state wording and the
structured evidence pointer. All Round-1 NOTE findings and every earlier
failed attempt remain preserved unchanged. Remediation changed no production
source. Round 2 has not run, and CHG-030 remains unreserved.
