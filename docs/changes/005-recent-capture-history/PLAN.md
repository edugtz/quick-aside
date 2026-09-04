# Change 005 — Recent Capture History — PLAN

## Preflight

- Confirm the expected `chg-005-recent-capture-history` branch and preserve
  the historical Change 001–004 packages.
- Re-read the existing Capture domain, Room entity/DAO/database, app-scoped
  database wiring, navigation, and Memoria reference surface.
- Confirm Room 3.0.2, KSP 2.3.6, SQLite 2.7.0, database version 1, and the
  Capture-only schema remain the baseline.

## Implementation approach

1. Extend `CaptureDao` with one bounded, deterministic recent-read query using
   `captured_at_epoch_millis DESC, id DESC`.
2. Add a focused `CaptureReader` application boundary and a Room-backed
   implementation that maps `CaptureEntity` values to `Capture`.
3. Own the reader from the existing `QuickAsideApplication` and pass it from
   `MainActivity` into `QuickAsideApp`.
4. Replace only the Memoria placeholder with a lifecycle-scoped recent-history
   screen. Reload on Memoria entry and on the existing save callback so the
   UI updates without restart; do not add Flow/ViewModel/DI machinery.
5. Add an isolated timestamp formatter using injected clock/zone/locale for
   deterministic JVM coverage while production uses device defaults.
6. Add Compose and Room instrumentation coverage with named dedicated test
   databases. Do not write fixtures to production `quick_aside.db`.
7. Capture representative Memoria screenshots and run every required build,
   test, lint, diff, and schema verification gate.

## Test approach

- Unit-test the reader-facing mapping and timestamp formatting with fixed
  inputs.
- Use the real Room implementation in Android tests for empty, text, voice,
  newest-first, equal-timestamp tie ordering, bounded results, and close/reopen
  behavior.
- Use a dedicated named Room database in Compose UI tests to prove one and
  multiple captures render and that the Inicio save path can be read in
  Memoria.
- Keep all existing Change 003/004 tests and production write semantics
  intact.

## Verification sequence

Run targeted unit and Android checks during implementation, then run all
required final gates. Inspect the generated schema and dependency versions,
verify that only four navigation destinations remain, review the UI against
the canonical reference, and report unavailable or failed gates exactly.

## Stop signals

Stop and report if the implementation needs a schema change, migration, new
table/column, delete API, search system, AI/STT/Google integration, or major
navigation redesign. Do not broaden this change into the later memory/search
milestones.
