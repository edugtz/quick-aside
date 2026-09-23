# CHG-029 Evidence Index

## Provenance

- Governance: **HIGH-ASSURANCE**.
- Branch: `chg-029-captureplan-task-execution`.
- Canonical base HEAD: `797e1557e8b5d94d4c8611a9b72749a8d7ac53f7` (not an
  implementation SHA; implementation remains uncommitted).
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
  `scope/schema-config-dependency-comparison.txt` and its JSON run record.
- Final Git/scope review and PRE-REVIEW READINESS CHECK: **PASS — READY FOR
  INDEPENDENT REVIEW**. The captured commands, inventory, and criteria are in
  `scope/git-scope-review.txt` and `scope/pre-review-readiness.json`.

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

No commit or push was made. The connected OPPO was not used. No production
device, private-gateway, Capture/UI, or CHG-030 work was performed.
