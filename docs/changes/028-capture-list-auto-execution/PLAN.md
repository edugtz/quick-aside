# Change 028 — Capture List Auto-Execution + Receipt/Undo Integration — PLAN

- Governance: **HIGH-ASSURANCE**
- Status: **COMPLETED — MERGED INTO MAIN; ROUND 2 PASS_WITH_NOTES**
- Branch: `chg-028-capture-list-auto-execution`
- Verified base: `6ede3d08f298a376cfdfd7749fc2d92a2eca3f5c`

## Post-merge provenance

- Implementation commit: `b8a14bf4a435b33870ee9bbf2127a2fd8f7b1d67`.
- Evidence remediation commit: `d8d5acdd9ae6982cb790054bdccaefdc0b1701be`.
- Evidence command correction commit / merged branch head:
  `44c4d3befd97ad37dadc8fcf93fb2dc7a5ab8232`.
- Round 1: **BLOCKED** due evidence provenance only.
- Round 2: **PASS_WITH_NOTES** — 0 BLOCKER / 0 MAJOR / 1 MINOR / 2 NOTE.
- Integrated main SHA: `44c4d3befd97ad37dadc8fcf93fb2dc7a5ab8232`.

Verification evidence and the device blocker are recorded in `QA.md`.

## Preflight findings

- Fetched live GitHub `main`; fetched SHA, `origin/main`, and clean starting
  `HEAD` match the required base exactly.
- No CHG-028 branch or package conflicted with this scope.
- Governing product/architecture/roadmap/acceptance/naming/UX documents,
  CHG-027, relevant CHG-026 conventions, and the canonical v3 image were
  inspected before implementation.
- Current production reality matches the prompt: persistence precedes remote
  interpretation, UI stops at a validated plan, and CHG-027 has no production
  caller.
- Room v7 and current DAO/schema contracts are sufficient; no migration or
  dependency is expected.
- The OPPO CPH2791 / Android 16 / API 36 device is attached.
- The requested orchestration skill is unavailable; repository and user
  HIGH-ASSURANCE instructions govern directly.

## Minimal implementation sequence

1. Extend `CaptureSubmissionResult.Saved` with a typed execution outcome and
   inject the narrow `CapturePlanListExecutor` into `CaptureSubmission`.
2. Preserve save-first ordering, then interpret. For a successful all-list
   plan invoke the executor once with the complete plan; map exact executor
   results without swallowing cancellation. Mark all other interpretation and
   plan families as not attempted/ineligible.
3. Create one app-scoped `RoomCapturePlanListExecutor` and inject it into the
   production `CaptureSubmission`; expose only the focused executor boundary
   to the app shell for targeted receipt Undo.
4. Replace split voice callbacks with one complete saved-result callback.
   Centralize text/voice receipt handling in the shell, including auth pairing,
   exact batch Undo, and durable list refresh signaling.
5. Add a refresh token to Mandado/Compras loaders so a list already visible
   before global voice Capture reloads after execution and Undo.
6. Add focused JVM tests, real-Room pipeline tests, and Compose tests for text,
   voice, receipt/Undo identity forwarding, non-success honesty, exactly-once
   execution, and visible-list refresh.
7. Run narrow checks first and stop on the first real validation error. Apply
   no more than two focused fixes for the same root blocker.

## Expected production files

- `app/src/main/java/com/edu/quickaside/application/capture/CaptureSubmission.kt`
- `app/src/main/java/com/edu/quickaside/QuickAsideApplication.kt`
- `app/src/main/java/com/edu/quickaside/MainActivity.kt`
- `app/src/main/java/com/edu/quickaside/ui/QuickAsideApp.kt`
- `app/src/main/java/com/edu/quickaside/ui/voice/VoiceCaptureScreen.kt`
- `app/src/main/java/com/edu/quickaside/ui/lists/MandadoScreen.kt`
- `app/src/main/java/com/edu/quickaside/ui/lists/ComprasScreen.kt`

No CHG-027 executor internals are expected to change unless focused evidence
finds a real integration defect.

## Verification order

1. Focused new `CaptureSubmission` JVM tests and existing submission
   regressions.
2. Focused real-Room pipeline instrumentation test.
3. Focused text, voice, and list-refresh Compose/device tests plus directly
   affected existing regressions.
4. `./gradlew :app:testDebugUnitTest`.
5. `./gradlew :app:compileDebugAndroidTestKotlin`.
6. `./gradlew :app:assembleDebug`.
7. `./gradlew :app:lintDebug`.
8. Confirm Room v7 and compare schemas, migrations, dependencies, manifest,
   network security, and gateway/provider code against the base.
9. Perform focused real-device text/voice/private-gateway acceptance only if
   the configured environment is available and authorized; capture and inspect
   representative receipt/Undo screenshots.
10. Run `git diff --check`, status/stat/name-status, and inspect the complete
    diff for unrelated files, secrets, generated junk, and future scope.

At planning time, no commit, push, merge, release, or CHG-029 work was
authorized by the plan. CHG-028 was later reviewed and merged into `main` as
recorded above; no CHG-029 work is selected or reserved.
