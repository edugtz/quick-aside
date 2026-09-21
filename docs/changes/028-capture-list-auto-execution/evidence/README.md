# CHG-028 inspectable evidence

## Review identity and provenance

- Branch: `chg-028-capture-list-auto-execution`
- Reviewed implementation SHA: `b8a14bf4a435b33870ee9bbf2127a2fd8f7b1d67`
- Reviewed base SHA: `6ede3d08f298a376cfdfd7749fc2d92a2eca3f5c`
- Device for the original connected-test and acceptance records: OPPO CPH2791, Android 16 / API 36.
- Device for the fresh canonical Room verification: `CHG028_Room_API35(AVD)`, Android API 35.
- This evidence package and the linked QA/TASKS/ACTIVE_WORK edits were created after the implementation commit. No implementation, test, schema, dependency, manifest, gateway, provider, QA1, Tailscale, or network-security file was changed for this remediation.

The recovered JVM reports contain UTC timestamps around `2026-09-20T01:02:27Z` (2026-09-19 19:02 local, America/Mexico_City). The Compose report and recovered regression summary are timestamped `2026-09-20T01:45:08` and `2026-09-20T01:26:27` respectively; their original JUnit timestamps have no timezone field, while the prior task record places these connected runs on the prior evening before the implementation commit. The prior task history also contains the successful compile, assemble, lint, and device-run command results. The source worktree became implementation SHA `b8a14bf`; no pre-run Git tree hash was captured, so that association is chronological/task-history provenance, not a cryptographic binding to the commit.

The acceptance screenshots, original app-process Logcat sample, and Room snapshot were captured before the implementation commit on 2026-09-20. The sanitized Room excerpt was generated after the commit from the read-only precommit snapshot. It is acceptance-state evidence, not an instrumentation-test report. Room evidence now includes a fresh focused API 35 verification at unchanged HEAD `b8a14bf4a435b33870ee9bbf2127a2fd8f7b1d67`; its generated XML is the canonical Room verification artifact. This run verifies the same instrumentation gate and does not reproduce the original OPPO historical run. The earlier OPPO signing-conflict attempt, Pixel 9 Pro boot-failure record, and first API 35 infrastructure failure remain separate historical artifacts.

## Verification artifacts

| Gate | Artifact(s) | Original command and result | Provenance |
|---|---|---|---|
| Focused JVM regressions | [three class JUnit XML files](jvm/focused/), [recovered command/exit record](jvm/focused/run-record.txt) | The exact focused command is recorded with exit 0. The three classes total **17 tests, 0 failures, 0 errors, 0 skipped** in the preserved full-suite reports (9 + 6 + 2). | The focused invocation's standalone XML was not retained. The files in `jvm/focused/` are copies of those same suite reports from the full 174-test run, not output from the focused invocation. This distinction preserves the machine counts without attributing them to the wrong run. |
| Full JVM suite | [28 full-suite JUnit XML files](jvm/full/) | `./gradlew :app:testDebugUnitTest` — **174 tests, 0 failures, 0 errors, 0 skipped**. | Recovered machine-generated XML; totals were summed from the XML suite attributes. |
| CHG-028 Compose UI | [connected JUnit XML](device/compose/TEST-CPH2791-Android16.xml), [exit code](device/compose/test-result-exit-code.txt) | `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.edu.quickaside.CaptureListAutoExecutionUiTest` — **8 tests, 0 failures, 0 errors, 0 skipped**, exit 0; JUnit timestamp `2026-09-20T01:45:08`. | Recovered machine-generated JUnit XML from CPH2791 / Android 16. Original report output directory was later overwritten; this copy was saved before the Room rerun. |
| Existing connected regressions | [recovered JUnit suite-tag excerpt](device/regressions/recovered-junit-suite-summary.txt) | `./gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.edu.quickaside.CaptureTextSubmissionTest,com.edu.quickaside.VoiceCaptureTest,com.edu.quickaside.QuickAsideAppTest,com.edu.quickaside.MandadoUiTest,com.edu.quickaside.ComprasUiTest'` — **35 tests, 0 failures, 0 errors, 0 skipped**, prior command exit 0. Counts: QuickAsideApp 1, CaptureTextSubmission 2, VoiceCapture 10, Mandado 12, Compras 10. | Original combined JUnit XML was overwritten by the later connected run. The exact five `<testsuite>` lines were recovered from prior machine-command output; individual testcase rows are unavailable. JUnit timestamp `2026-09-20T01:26:27` (timezone absent); device identity is from the contemporaneous QA record. This is not reconstructed from QA prose. |
| Real Room pipeline — canonical verification | [canonical API 35 JUnit XML](device/room/TEST-CHG028_Room_API35-AVD.xml), [successful run/console record](device/room/api35-successful-run.txt); prior attempts remain in [device/room/](device/room/) | `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.edu.quickaside.data.local.CaptureSubmissionListExecutionDatabaseTest --stacktrace` — **4 tests started, 4 finished; 4 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESSFUL**. | Fresh focused API 35 emulator verification at unchanged reviewed implementation SHA `b8a14bf4a435b33870ee9bbf2127a2fd8f7b1d67`, with JUnit timestamp `2026-09-21T03:04:37` (timezone omitted). This independently substantiates the Room gate; it does not reproduce the original OPPO historical 4/4 run, whose original machine report remains unrecoverable. The earlier OPPO signing-conflict attempt and API 35 infrastructure failure remain preserved and are not pass evidence. |
| Android-test Kotlin compile | [recovered command result](build-results/compileDebugAndroidTestKotlin.txt) | `./gradlew :app:compileDebugAndroidTestKotlin` — recorded exit 0, `BUILD SUCCESSFUL`. | Excerpt recovered from the prior implementation task's stored command result; no standalone compile log or absolute run timestamp was persisted. |
| Debug assemble | [recovered command result](build-results/assembleDebug.txt) | `./gradlew :app:assembleDebug` — recorded exit 0, `BUILD SUCCESSFUL`. | Excerpt recovered from the prior implementation task's stored command result; no absolute run timestamp was persisted. |
| Lint | [lint text report](lint/lint-results-debug.txt), [SARIF report](lint/lint-results-debug.sarif), [command result](build-results/lintDebug-command-result.txt) | `./gradlew :app:lintDebug` — recorded exit 0; **0 errors, 16 warnings, 2 hints**. | Generated lint reports recovered from `app/build/reports`; command result recovered from prior task output. No absolute run timestamp was retained. Source URIs were normalized to `repo/`. |

The original Room test source is not copied or changed by this evidence-only task. The new API 35 JUnit report is the canonical Room verification artifact for this review; its passing result is a fresh focused verification of the same instrumentation gate at the unchanged reviewed implementation SHA, not a reproduction of the original OPPO historical run.

## Production acceptance artifacts

| Acceptance | Artifact | What it supports / limit |
|---|---|---|
| Text success receipt | [text-success-receipt.png](acceptance/text-success-receipt.png) | Manual CPH2791 screenshot, source mtime `2026-09-20T17:37:15-06:00`; success receipt with `Deshacer` for the accepted text marker. |
| Text Undo | [text-undo.png](acceptance/text-undo.png) | Manual CPH2791 screenshot, source mtime `2026-09-20T17:37:16-06:00`; `Cambio deshecho` receipt for the accepted text marker. |
| Capture retained after Undo | [capture-retained-in-memoria.png](acceptance/capture-retained-in-memoria.png) | Manual CPH2791 screenshot, source mtime `2026-09-20T17:48:29-06:00`; accepted text Capture remains visible in Memoria after Undo. The visible records are QA markers. |
| Voice Undo | [voice-undo.png](acceptance/voice-undo.png) | User-provided CPH2791 screenshot, source mtime `2026-09-20T18:37:28-06:00`, showing `Cambio deshecho`; it does not show the preceding successful voice execution receipt. |
| Representative Compras view | [compras-representative-list.png](acceptance/compras-representative-list.png) | Manual CPH2791 screenshot, source mtime `2026-09-19T21:09:14-06:00`; shows a representative list state from an earlier QA marker, not the accepted `uvas moradas` voice marker. |
| Sanitized acceptance Room facts | [room-acceptance.txt](acceptance/room-acceptance.txt) | Generated `2026-09-20T19:52:03-06:00` from the precommit read-only snapshot. Only the two accepted TEXT/VOICE marker Capture rows and their linked ledgers, mutations, target-absence counts, and duplicate-marker counts are included. It contains no raw database pages or unrelated user records. The voice mutation stores null before/after state and the deleted target row is absent, so this excerpt alone cannot independently prove `listDefinitionId=compras`. |

The screenshots are manual device/user-provided artifacts; there is no shell command for capture. Their repository copies were verified byte-identical to their recovered source images. The device and timestamps above are the available source metadata; no separate screenshot-time source-tree hash exists.

No standalone successful human-voice execution screenshot for the accepted `uvas moradas` marker was found in the recovered local artifacts. The user-provided voice Undo screenshot is preserved. The previous QA's exact voice destination screenshot is not included or relied upon here.

## Privacy sample

- [Sanitized 32-line app-process Logcat sample](privacy/logcat-acceptance-sample.txt)
- [Scope, exact regex categories, and zero-count summary](privacy/scan-summary.txt)

The sample was captured after the earlier real-human voice execution on 2026-09-20 at approximately 17:45:46 America/Mexico_City. The recorded scan found zero matches in each listed category: acceptance transcript/item, capture/transcript/raw-text terms, authorization/bearer, QA1/signature/HMAC/nonce/pairing-code, six-digit pairing-code shape, provider/API credential/token, and request-body/prompt/message terms. Only the 32-line app-process sample was included; no raw database, pairing code, signature/header, provider credential, request body, or prompt content is included.

The prior Logcat collection command was not retained. The sample itself and its process/time bounds were recovered from the local app-process Logcat capture; the evidence file records the exact scan regexes and zero counts. No current or production Logcat collection was run for this remediation.

## Scope and limitations

- [Base-to-implementation inventory and prior evidence-remediation snapshot](scope/scope-inventory.txt). Its working-tree counts describe the earlier evidence package before this API 35 closeout; the current requested Git checks are reported in this closeout.
- Evidence-only files are under this `evidence/` directory; the only other remediation edits are QA/TASKS/ACTIVE_WORK documentation updates.
- No production or test source, dependency, Room schema/migration, manifest, network-security, gateway/provider/QA1/Tailscale, or runtime configuration file was changed.
- The recovered reports and command excerpts are attributed to the precommit worktree through prior task output and timestamps, not a saved source-tree hash. The sanitized acceptance Room excerpt is a postcommit derivative of a precommit snapshot. The postcommit Room artifacts include the earlier OPPO installation-failure report and the current successful API 35 JUnit report/run record; the latter is canonical for current Room verification.
- The Room 4/4 instrumentation result is now independently inspectable in the canonical API 35 JUnit report and run record. The historical original OPPO report remains unrecoverable. Independent review has not yet assigned a verdict.
- The included voice acceptance facts establish a recorded human VOICE Capture with one undone CREATE mutation and absent target, but the exact destination list and standalone successful execution visual are not independently established by the included post-Undo artifacts.
- No raw Room database, WAL/SHM, audio recording, auth material, secret, or unrelated personal record is included.
