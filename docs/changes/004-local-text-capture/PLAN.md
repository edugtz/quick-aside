# Change 004 — Local Text Capture — PLAN

## Preflight

- Confirm the expected `chg-004-local-text-capture` branch is clean and keep
  Change 001–003 packages untouched.
- Re-read the existing Capture domain, Room entity/DAO/database, app wiring,
  navigation, and the accepted Inicio UX reference.
- Confirm Room 3.0.2, KSP 2.3.6, SQLite 2.7.0, database version 1, and the
  exported Capture-only schema remain the baseline.

## Implementation approach

1. Add a focused `CaptureWriter` boundary implemented by the existing Room
   database. It exposes only the write operation needed by this UI.
2. Add a small pure application coordinator that validates `isBlank()`,
   creates a `Capture` with injected ID/time providers, preserves valid input,
   and returns explicit saved/blank/failure results.
3. Add a `QuickAsideApplication` with lazy app-scoped database/writer/
   coordinator ownership. Wire it from `MainActivity`; do not construct a
   production database from a composable or on every recomposition.
4. Extend Inicio only: retain the current voice CTA and four destinations,
   add an accessible single-line Material text field with IME Done and
   trailing send actions, and host a Snackbar receipt.
5. On saved result, clear the field and show `Captura guardada`; on blank,
   do nothing except a small validation message; on failure, retain the field
   and show an error.

## Test approach

- Unit-test the coordinator with deterministic ID/time providers and a fake
  writer for valid text, exact text preservation, blank rejection, distinct
  IDs, and failure results.
- Add or extend an Android Compose test to submit text through the actual
  `MainActivity` production wiring, assert the receipt/field clearing, and
  read the resulting Capture from the app-scoped Room database.
- Add a focused fake-writer Compose test if needed to prove failed saves keep
  the entered text and expose the failure message.
- Keep the existing Change 003 Room tests and schema unchanged.

## Verification sequence

Run targeted unit/instrumentation checks while implementing, then run every
required final gate. Inspect the generated schema and dependency/version
files, compare the UI result to the canonical v3 reference, capture the three
requested device states, and report any unavailable or failed gate exactly.

## Stop signals

Stop and report if text capture requires a schema change, migration, new
persistence model, AI/STT/Google integration, or major navigation redesign.
Do not broaden this change into later M1/M2 behavior.
