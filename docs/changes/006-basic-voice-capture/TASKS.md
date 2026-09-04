# Change 006 — Basic Voice/STT Capture — TASKS

## Change package and preflight

- [x] Confirm expected branch and repository state.
- [x] Read project, architecture, roadmap, acceptance, naming, UX, and
      Change 004–005 guidance.
- [x] Inspect the canonical v3 UI reference before implementation planning.
- [x] Verify official Android SpeechRecognizer and runtime-permission guidance.
- [x] Create the Change 006 SPEC, PLAN, and TASKS package.
- [x] Mark `docs/ACTIVE_WORK.md` as Change 006 IN PROGRESS.

## Platform and persistence boundary

- [x] Add only `RECORD_AUDIO` and the required `RecognitionService` query.
- [x] Add the injectable speech boundary and Android implementation.
- [x] Prefer on-device recognition on API 31+ when available; preserve the
      normal system fallback without claiming it is offline.
- [x] Extend `CaptureSubmission` minimally for exact Voice persistence.
- [x] Preserve database version 1, schema, writer, reader, and text behavior.

## Voice capture UI and lifecycle

- [x] Replace the placeholder with the temporary listening surface.
- [x] Handle granted, requested, denied, permanently denied, and unavailable
      microphone permission states.
- [x] Render ready/listening, partial, finalizing, saving, saved, and failure
      states with accessible cancel/retry/exit actions.
- [x] Save only one non-blank final transcript and close on success.
- [x] Destroy/cancel the active recognizer on completion, dismissal, and
      composition disposal.
- [x] Reuse Memoria and show the saved Voice Capture immediately.

## Automated tests and evidence

- [x] Add deterministic unit coverage for Voice submission and blank/error
      behavior.
- [x] Add fake-transcriber Compose coverage for the Change 006 contract.
- [x] Use dedicated test databases and verify no production DB fixture use.
- [x] Capture listening/transcript and Memoria screenshots.
- [x] Run `./gradlew :app:testDebugUnitTest`.
- [x] Run `./gradlew :app:assembleDebug`.
- [x] Run `./gradlew :app:lintDebug`.
- [x] Run `./gradlew :app:connectedDebugAndroidTest`.
- [x] Perform required physical-device voice/permission/lifecycle QA and
      record device/API and actual observations.
- [x] Confirm schema/dependency/permission/STT checks.
- [x] Run `git diff --check` and report `git status --short` and diff stat.

## Scope and authority

- [x] Do not modify historical Change 001–005 documentation packages.
- [x] Do not merge or push.
- [x] Leave final engineering verdict to the independent reviewer/orchestrator.

The package is ready for that independent review; this implementation report
does not assign the final verdict.
