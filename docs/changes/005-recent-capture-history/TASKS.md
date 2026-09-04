# Change 005 — Recent Capture History — TASKS

## Change package and preflight

- [x] Confirm expected branch and repository state.
- [x] Read project, architecture, roadmap, acceptance, naming, UX, and
      Change 003–004 guidance.
- [x] Inspect the canonical v3 UI reference before implementation planning.
- [x] Create the Change 005 SPEC, PLAN, and TASKS package.
- [x] Mark `docs/ACTIVE_WORK.md` as Change 005 IN PROGRESS.

## Capture read boundary

- [x] Add the bounded deterministic recent query to the existing Capture DAO.
- [x] Add the smallest application-layer Capture reader.
- [x] Map Room records to the existing Text and Voice Capture domain inputs.
- [x] Wire the reader through the existing app-scoped database owner.

## Memoria UI

- [x] Replace the Memoria placeholder with a restrained recent-capture view.
- [x] Show original text/transcript, input kind, and local human-readable time.
- [x] Add an explicit empty state.
- [x] Refresh on Memoria entry and after a successful new capture.
- [x] Preserve Inicio, Pendientes, Listas, and the global Capture action.

## Tests and evidence

- [x] Add deterministic timestamp formatter tests.
- [x] Add isolated Room read/order/voice/limit/close-reopen coverage.
- [x] Add isolated Compose coverage for empty, one, multiple, and Inicio-to-
      Memoria visibility.
- [x] Capture empty, one-Capture, and multiple-Capture Memoria screenshots.

## Verification

- [x] Run `./gradlew :app:testDebugUnitTest`.
- [x] Run `./gradlew :app:assembleDebug`.
- [x] Run `./gradlew :app:lintDebug`.
- [x] Attempt `./gradlew :app:connectedDebugAndroidTest`; the required physical
      device gate was blocked twice by UTP split-APK installation timeout before
      tests ran. Direct emulator instrumentation supplied separate evidence.
- [x] Confirm database version 1 and semantically unchanged schema.
- [x] Confirm Room 3.0.2 / KSP 2.3.6 and no AI/STT/Google dependency changes.
- [x] Run `git diff --check` and report `git status --short` and diff stat.

## Scope and authority

- [x] Do not modify historical Change 001–004 documentation packages.
- [x] Do not merge or push.
- [ ] Independent reviewer/orchestrator supplies the final verdict.
