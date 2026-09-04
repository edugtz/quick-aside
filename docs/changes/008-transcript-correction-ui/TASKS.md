# Change 008 — Transcript Correction UI — TASKS

## Change package and preflight

- [x] Confirm the expected branch and clean repository state.
- [x] Read the required project, architecture, roadmap, acceptance, naming,
      UX, and Change 005–007 guidance.
- [x] Inspect the canonical v3 UI reference before implementation planning.
- [x] Inspect the current Capture, reader, corrector, Room, application,
      activity, Memoria UI, and regression tests.
- [x] Confirm Room/KSP/SQLite versions and database version 2.
- [x] Create the Change 008 SPEC, PLAN, and TASKS package.
- [x] Mark `docs/ACTIVE_WORK.md` as Change 008 IN PROGRESS.

## Memoria and editor UI

- [x] Pass the app-scoped transcript corrector into the UI.
- [x] Add a restrained accessible Edit affordance to Voice cards only.
- [x] Add the native transcript editor surface.
- [x] Initialize from effective transcript and show original as read-only
      context.
- [x] Preserve exact non-blank text and disable blank/unchanged Save.
- [x] Implement success, failure, Missing, NotVoice, Cancel, and back behavior.
- [x] Replace the visible item immediately after a successful correction.
- [x] Keep the Voice happy path and existing Text path unchanged.

## Automated tests and evidence

- [x] Add deterministic UI coverage for Voice/Text affordance visibility,
      initial values, exact input, blank/no-op, cancel, and errors.
- [x] Add real Room-backed successful correction, refresh, original-preserving,
      and second-correction coverage.
- [x] Run `./gradlew :app:testDebugUnitTest`.
- [x] Run `./gradlew :app:assembleDebug`.
- [x] Run `./gradlew :app:lintDebug`.
- [x] Run `./gradlew :app:connectedDebugAndroidTest`.
- [x] Capture screenshots for card, editor, and corrected Memoria states.
- [x] Confirm database version 2, schemas 1/2 unchanged, no schema 3, no
      migration/dependency changes, and isolated test databases.
- [x] Run `git diff --check` and report `git status --short` and diff stat.

## Scope and authority

- [x] Do not modify historical Change 001–007 documentation packages.
- [x] Do not merge or push.
- [x] Final independent review; verdict: PASS_WITH_NOTES.