# Change 004 — Local Text Capture — TASKS

## Change package and preflight

- [x] Confirm expected branch and clean starting state.
- [x] Read the project, architecture, roadmap, acceptance, naming, UX, and
      Change 003 persistence guidance.
- [x] Inspect the canonical v3 UI reference before planning.
- [x] Create the Change 004 SPEC, PLAN, and TASKS package.
- [x] Mark `docs/ACTIVE_WORK.md` as Change 004 IN PROGRESS.

## Application-layer capture flow

- [x] Add the focused existing-database Capture writer boundary.
- [x] Add deterministic ID/time injection and the text submission coordinator.
- [x] Preserve exact valid input and reject blank/whitespace-only input.
- [x] Add lifecycle-safe app-scoped production database ownership.

## Inicio UI

- [x] Add accessible first-class text input below the voice CTA.
- [x] Support IME submission and trailing send action.
- [x] Clear the field and show `Captura guardada` only after success.
- [x] Retain input and show an understandable error after save failure.
- [x] Keep the four management destinations and voice placeholder behavior.

## Tests and evidence

- [x] Add deterministic application/coordinator tests.
- [x] Add real production-wiring Android UI/persistence coverage.
- [x] Add failure-state UI coverage where the boundary permits it.
- [x] Capture empty-field, entered-text, and success-receipt screenshots on a
      `Pixel_9_Pro (AVD) - 15` emulator.

## Verification

- [x] Run `./gradlew :app:testDebugUnitTest`.
- [x] Run `./gradlew :app:assembleDebug`.
- [x] Run `./gradlew :app:lintDebug`.
- [x] Run `./gradlew :app:connectedDebugAndroidTest` on
      `Pixel_9_Pro (AVD) - 15`.
- [x] Confirm database version 1 and semantically unchanged schema.
- [x] Confirm Room 3.0.2 / KSP 2.3.6 and no AI/STT/Google dependency changes.
- [x] Run `git diff --check`, `git status --short`, and report diff stat.

## Scope and authority

- [x] Do not modify historical Change 001–003 packages.
- [x] Do not merge or push.
- [x] Final independent review; verdict: PASS_WITH_NOTES.