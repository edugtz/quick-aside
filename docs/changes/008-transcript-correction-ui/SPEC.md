# Change 008 — Transcript Correction UI — SPEC

Governance: **STANDARD**  
Status: **IN PROGRESS**

## Objective

Allow a user to correct a saved Voice Capture from the explicit `Memoria`
management surface without inserting a review or confirmation gate into the
Voice Capture happy path:

`Memoria → Voice Capture → Editar transcript → edit effective transcript → Guardar`

The original recognized transcript remains preserved in the existing Change
007 domain and Room model. A successful correction immediately replaces the
displayed effective transcript and leaves the user in `Memoria`.

## In scope

- A restrained, accessible Edit affordance on Voice Capture history cards.
- A lightweight Android-native transcript editor, implemented as a
  `ModalBottomSheet`.
- Opening the editor with `Capture.effectiveTranscript`.
- Optional read-only display of the original Voice transcript.
- Exact, non-blank correction submission through the existing
  `CaptureTranscriptCorrector`.
- Disabled Save behavior for blank/whitespace-only and unchanged values.
- Immediate in-memory Memoria replacement using the `Saved.capture` returned
  by the corrector.
- Concise failure, Missing, and NotVoice feedback while avoiding false
  success.
- Cancel and normal bottom-sheet dismissal with no mutation.
- App-scoped corrector wiring through the existing `MainActivity` path.
- Deterministic Compose and Room-backed Android tests for the Change 008
  contract, while preserving the existing Voice and Text capture tests.
- Representative screenshots for the Voice card, open editor, and corrected
  Memoria card.

## Out of scope

- Mandatory post-STT confirmation or transcript review in Voice Capture.
- Editing during live listening.
- Interpretation, destination, or routing correction.
- Text Capture editing.
- AI, STT, CapturePlan, Lists, Tasks, Notes, Structured Logs, Google APIs,
  reminders, Undo, search, delete/archive, or new navigation destinations.
- Any Room schema/table/column/index change, migration, or database version
  change.
- Hilt, a generic repository/ViewModel/CRUD abstraction, or reactive/paging
  architecture introduced only for this surface.
- Unrelated refactoring of `VoiceCaptureScreen`.

## Reference surfaces and UX invariants

The affected reference surface is the `Memoria` history screen in
`docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`, interpreted with
`docs/UX_UI_REFERENCE.md`. The implementation preserves:

- `Inicio`, `Pendientes`, `Listas`, and `Memoria` as the only management
  destinations;
- Capture as a global action rather than a fifth destination;
- a calm, structured, scan-friendly history surface rather than chat bubbles;
- correction as an optional explicit-management branch;
- the Voice happy path `invoke → speak → auto-save → receipt → continue`;
- native Android touch targets, labels, back behavior, and text scaling.

The card continues to show the effective transcript and `Voz · timestamp`.
Only Voice cards receive the restrained Edit affordance; Text cards remain
unchanged.

## Editor contract

The editor presents:

- title: `Editar transcript`;
- a labeled multiline editable field initialized with the current effective
  transcript;
- optional read-only contextual text in the form `Original: <original>`;
- `Cancelar` and `Guardar` actions.

The original transcript is never edited or replaced. Exact String equality is
used for the unchanged check; whitespace is not normalized for comparison or
persistence.

## Save and error contract

- Save is disabled when the edited value is blank/whitespace-only, identical
  to the current effective transcript, or a save is already in progress.
- A valid value is passed unchanged to `CaptureTranscriptCorrector`.
- `Saved.capture` replaces the matching visible history item immediately,
  the editor closes, and `Corrección guardada` is shown in a lightweight
  snackbar.
- `Failed`, `Missing`, and `NotVoice` keep the editor open with the entered
  value intact and show concise understandable error feedback. None is shown
  as success.
- Cancel or normal Android back dismissal closes the editor without calling
  the corrector or changing the visible/history data.

## Application and persistence boundaries

`QuickAsideApplication.captureTranscriptCorrector` remains the production
owner. `MainActivity` passes it into `QuickAsideApp`; the UI never writes
through `CaptureDao`. Test-only dependency parameters may provide a fake
corrector for deterministic error-state coverage.

`QuickAsideDatabase` remains version **2**. `schemas/1.json` and `schemas/2.json`
remain unchanged, no `3.json` is created, and the Change 007 semantics of
`original_text` and nullable `corrected_transcript` are not altered.

## Acceptance scenarios

1. A Voice Capture card exposes an accessible Edit affordance.
2. A Text Capture card exposes no transcript Edit affordance.
3. An uncorrected Voice editor opens with the original transcript.
4. An already corrected Voice editor opens with the current correction.
5. If shown, the original transcript is visibly read-only/contextual.
6. Exact non-blank input, including leading/trailing spaces, reaches the
   corrector unchanged.
7. A successful real Room correction updates Memoria immediately and shows
   lightweight success feedback.
8. The persisted original transcript remains unchanged after UI correction.
9. Blank/whitespace-only input cannot be saved.
10. Unchanged effective text causes no corrector/database write.
11. Cancel/back causes no mutation or failure feedback.
12. Persistence failure keeps the editor and input open with an error.
13. Missing and NotVoice outcomes are not reported as success.
14. A second correction updates the effective transcript again while the
    original remains unchanged.
15. Existing Voice capture still auto-saves without a mandatory editor.
16. Existing Text capture behavior remains green.

## Required verification evidence

- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:assembleDebug`
- `./gradlew :app:lintDebug`
- `./gradlew :app:connectedDebugAndroidTest`
- `git diff --check` and `git status --short`
- Inspection confirming database version 2, unchanged schema 1/2, no schema 3,
  no migration/dependency/version changes, and isolated test databases.
- Representative Android screenshots for the Voice card with Edit, the open
  transcript editor, and corrected Memoria state.

## Stop conditions

Stop and report if this UI requires a schema version 3, changing or clearing
`original_text`, a generic CRUD architecture, mandatory capture confirmation,
interpretation/routing UI, or a major Memoria redesign.

## Authority

Do not merge or push. The final engineering verdict belongs to the independent
reviewer/orchestrator; this implementation report must not declare PASS.
