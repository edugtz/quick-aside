# Change 006 — Basic Voice/STT Capture — SPEC

Governance: **STANDARD**  
Status: **IMPLEMENTED — AWAITING INDEPENDENT REVIEW**

## Objective

Make the existing Home microphone and global Capture action start the first
usable voice capture path:

`tap microphone → grant permission if needed → speak → save Voice Capture → receipt → continue`

For this change, speech such as `Comprar leche mañana` is persisted as one
`CaptureInput.Voice(originalTranscript = "Comprar leche mañana")`. It is not
interpreted, classified, routed, or turned into an AI `CapturePlan`.

## In scope

- `RECORD_AUDIO` manifest declaration and normal runtime permission handling.
- Android speech-recognition service visibility for Android 11+.
- A small injectable speech platform boundary with an Android
  `SpeechRecognizer` implementation and deterministic fake-compatible UI
  seam.
- On-device recognition preference on API 31+ when the device reports it is
  available, with the normal system recognizer as the remaining fallback.
- Free-form recognition using the device default locale and partial-result
  requests.
- Explicit lightweight capture states: permission, ready/listening, partial
  transcript, finalizing, saving, saved, and understandable failure.
- Automatic persistence of one non-blank final transcript through the existing
  `CaptureSubmission` and `CaptureWriter` pipeline.
- Cleanup of the active recognizer when the temporary capture surface is
  dismissed, completed, or disposed.
- Reuse of Change 005 Memoria so saved voice captures immediately render as
  `Voz · <timestamp>` with the original transcript.
- Isolated deterministic tests for the speech boundary seam, permission and
  error paths, exact final-text persistence, cleanup, UI receipt, Memoria
  visibility, and preserved text capture behavior.
- Representative visual evidence for listening/transcript and Memoria
  states, plus required build/lint/test/schema checks.

## Out of scope

- Editable transcript or mandatory confirmation/review.
- Interpretation, AI, `CapturePlan`, routing, lists, tasks, notes, structured
  logs, calendar, Google APIs, reminders, undo, search, or new navigation
  destinations.
- Raw microphone/audio-file persistence, recognition bundles, confidence
  arrays, or transcript-bearing diagnostics/logs.
- A third-party STT SDK, custom speech backend, foreground service,
  WorkManager, background/always-on listening, wake word, widget, tile, or
  major navigation redesign.
- Any Room schema change, migration, table, column, index, or delete API.

## Platform contract

The Android boundary follows the current official `SpeechRecognizer`
contract:

- `Manifest.permission.RECORD_AUDIO` is declared and requested at the moment
  the user invokes voice capture.
- Recognizer methods are called on the main application thread.
- `destroy()` is called when the active capture session no longer needs the
  recognizer.
- On API 31+, `isOnDeviceRecognitionAvailable(context)` is checked before
  `createOnDeviceSpeechRecognizer(context)` is used.
- Otherwise `createSpeechRecognizer(context)` is used. This fallback is the
  normal system recognizer and is not claimed to be offline; its service may
  use network/remote recognition depending on the device implementation.
- The recognizer intent uses `ACTION_RECOGNIZE_SPEECH`,
  `LANGUAGE_MODEL_FREE_FORM`, the device default language tag, and
  `EXTRA_PARTIAL_RESULTS = true`. It does not force
  `EXTRA_PREFER_OFFLINE`.

## Domain and persistence contract

`CaptureSubmission` adds the smallest parallel operation needed for voice:

```kotlin
suspend fun submitVoice(originalTranscript: String): CaptureSubmissionResult
```

The operation shares ID, timestamp, blank validation, writer, and result
behavior with text submission while constructing `CaptureInput.Voice`.
Valid transcript text is preserved exactly, including surrounding whitespace.
Blank/final-empty results return `Blank` and create no row. Persistence uses
the existing `CaptureWriter`, `CaptureEntity`, and `QuickAsideDatabase`.

The database remains version 1 with the existing four columns:
`id`, `kind`, `original_text`, and `captured_at_epoch_millis`.

## UI and UX contract

The affected reference surfaces are `Inicio` and temporary `Captura` in
`docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`, interpreted with
`docs/UX_UI_REFERENCE.md`.

The implementation preserves:

- the four management destinations `Inicio`, `Pendientes`, `Listas`, and
  `Memoria`;
- Capture as a temporary action rather than a fifth destination;
- voice as the dominant capture action and text as a first-class alternative;
- a dark, focused listening surface with the orb/microphone visually
  dominant, transcript secondary, minimal controls, and an obvious Cancel;
- the happy path `invoke → speak/type → save → lightweight receipt → continue`;
- no mandatory transcript edit or confirmation gate;
- Android-native touch targets, accessible labels, and understandable state
  text rather than color-only signaling.

After a successful save, the temporary surface closes, the prior management
destination is restored, `Captura guardada` is shown, and the new Voice
Capture is readable in Memoria without an app restart.

## Acceptance scenarios

1. The Home microphone and global Capture action enter the temporary voice
   surface without adding a navigation destination.
2. A granted microphone permission starts the injected/Android transcriber.
3. A first permission request uses the Android runtime permission surface;
   denial creates no Capture and does not crash or automatically loop.
4. A permanent/unavailable permission state offers a concise exit back to the
   app rather than repeated requests.
5. A supplied partial transcript renders as secondary live text.
6. No partial transcript is still a valid listening flow.
7. One non-blank final transcript creates exactly one `CaptureInput.Voice`
   with a new ID, capture timestamp, and exact original transcript.
8. Blank final results create no Capture and show an understandable retry/
   exit state.
9. No-match, timeout, insufficient permission, busy, network/service, and
   unavailable recognizer outcomes do not crash, expose raw numeric codes, or
   create a Capture.
10. User cancellation creates no Capture, is not shown as a save failure, and
    releases the active recognizer.
11. A successful voice save closes the capture surface and shows
    `Captura guardada`.
12. The saved voice capture appears in Memoria as `Voz · <timestamp>` with
    the original transcript.
13. Existing text submission, Room persistence, schema version 1, and the
    four management destinations remain working and unchanged in contract.
14. Automated speech tests use fake/injected transcribers and dedicated test
    databases; no fixture is written to production `quick_aside.db`.

## Required verification evidence

- Official Android speech/permission preflight recorded in the implementation
  report.
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:assembleDebug`
- `./gradlew :app:lintDebug`
- `./gradlew :app:connectedDebugAndroidTest`
- `git diff --check` and `git status --short`
- Inspection showing database version 1 and semantically unchanged schema,
  Room 3.0.2, KSP 2.3.6, no third-party STT/AI/Google dependency, only
  `RECORD_AUDIO` as the new runtime permission, and the required
  `RecognitionService` query.
- Real-device QA, preferably on the physical CPH2791 Android 16 device,
  covering permission, Spanish voice capture, cancel, no-speech/failure, and
  lifecycle cleanup. Record the actual device/API and observed outcomes.
- Representative screenshots for listening, transcript/final state, and
  Memoria voice history.

## Stop conditions

Stop and report if this slice requires a schema migration, raw-audio storage,
a foreground/background service, always-on listening, a third-party/cloud STT
SDK, AI, Google integration, or a major navigation redesign.

## Authority

Do not merge or push. The final engineering verdict belongs to the independent
reviewer/orchestrator; this report must not declare PASS.
