# Change 008 — Transcript Correction UI — PLAN

## Preflight

- Confirm the `chg-008-transcript-correction-ui` branch and clean working tree.
- Read the accepted project, architecture, roadmap, acceptance, naming, and
  UX contracts plus Changes 005–007.
- Inspect the canonical v3 visual reference before implementation.
- Inspect the existing Capture domain, reader/history UI, corrector boundary,
  Room implementation, application owner, activity wiring, and Voice/Text
  regression tests.
- Confirm Room 3.0.2, KSP 2.3.6, SQLite 2.7.0, database version 2, and the
  exported schemas remain the baseline.

## Implementation approach

1. Create the Change 008 package and mark it active before source changes.
2. Pass the already app-scoped `CaptureTranscriptCorrector` from
   `MainActivity` into `QuickAsideApp`.
3. Add an Edit action only to Voice cards in the existing history surface.
4. Add a focused transcript-editor composable for the native modal bottom
   sheet, with a labeled multiline field, read-only original context, Cancel,
   Save, back dismissal, and accessible error state.
5. Keep editor state local and deterministic: initialize from effective text,
   preserve exact input, disable blank/unchanged saves, call only the existing
   corrector, and replace the matching loaded capture with `Saved.capture`.
6. Add isolated Compose tests using a fake corrector for exact-input,
   no-op/blank, cancel, failure, Missing, and NotVoice behavior.
7. Add/extend Room-backed UI coverage for successful correction, immediate
   Memoria refresh, original preservation, and a second correction.
8. Keep existing Voice happy-path and Text submission tests intact and run the
   full required verification sequence.
9. Capture representative visual evidence and inspect it against the written
   UX contract and v3 visual direction.

## Test approach

- Use dedicated named Room databases for every Android test that persists
  fixtures; never use production `quick_aside.db`.
- Use a fake `CaptureTranscriptCorrector` only for deterministic UI error and
  call-count assertions.
- Use `RoomCaptureTranscriptCorrector` with a dedicated real Room database for
  the successful persistence path and original-value verification.
- Assert that Text cards do not expose the edit semantics and that Voice cards
  do.
- Assert Save is unavailable for blank and exact-equal values, and that Cancel
  and back do not call the boundary.
- Re-run existing Voice and Text integration coverage as regression evidence.

## Verification sequence

Run targeted tests while implementing, then run all required unit, assemble,
lint, connected instrumentation, diff, status, schema, dependency, and visual
evidence checks. Report unavailable or failed gates exactly; do not claim a
final PASS.

## Stop signals

Stop and report if editing needs a Room migration/schema change, direct DAO
writes from Compose, original-text replacement, mandatory capture review,
interpretation correction, or a major navigation/history redesign.
