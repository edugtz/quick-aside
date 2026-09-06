# Change 014 — Notes UI — TASKS

## Change package and preflight

- [x] Confirm expected branch and repository state.
- [x] Read project, architecture, roadmap, acceptance, naming, UX, and
      Changes 005, 008, and 013 guidance.
- [x] Inspect the canonical v3 UI reference before implementation planning.
- [x] Create the Change 014 SPEC, PLAN, and TASKS package.
- [x] Mark `docs/ACTIVE_WORK.md` as Change 014 IN PROGRESS.

## Navigation and application wiring

- [x] Add the local Memory History/Notes route while preserving default
      Capture History behavior.
- [x] Reset the Memory route on bottom-navigation selection.
- [x] Return from Notes to Capture History through toolbar and system back.
- [x] Pass the app-scoped `MemoryStore` through MainActivity and QuickAsideApp.
- [x] Add a restrained accessible Notes affordance in Memoria.

## Notes UI

- [x] Add focused NotesScreen with loading, loaded, empty, failure, and retry.
- [x] Add multiline freeform creation with exact text preservation.
- [x] Pass `sourceCaptureId = null` and prevent blank/duplicate saves.
- [x] Handle all MemoryStore outcomes without leaking internal errors.
- [x] Show Saved Notes immediately and clear the input.
- [x] Render local date/time context with deterministic injectable formatting.
- [x] Keep existing rows read-only with no edit/delete affordance.

## Automated tests and evidence

- [x] Add deterministic fake-MemoryStore Compose coverage for the Change 014
      acceptance scenarios and existing Capture History behavior.
- [x] Add a named v4 Room create/read/close/reopen integration regression.
- [x] Run `./gradlew :app:testDebugUnitTest`.
- [x] Run `./gradlew :app:assembleDebug`.
- [x] Run `./gradlew :app:lintDebug`.
- [x] Run `./gradlew :app:connectedDebugAndroidTest`.
- [x] Capture representative real-device screenshots and review them against
      the written UX contract and v3 visual direction.
- [x] Confirm database version 4, unchanged schemas 1–4, no schema 5 or
      migration, no dependency/AI/STT/Google changes, and isolated databases.
- [x] Run `git diff --check`, `git status --short`, and diff statistics.

## Scope and authority

- [x] Do not modify historical Change 001–013 documentation packages.
- [x] Do not add editing, deletion, reminders, search, logs, archive/backup,
      AI, Capture routing, or a fifth navigation destination.
- [x] Do not merge or push.
- [x] Prepare the implementation report without declaring the final verdict.

## Verification note

The full connected suite completed on CPH2791 with 122 tests and 0 failures
before the final `mutableIntStateOf` cleanup. Unit tests, assemble, and lint
also completed on the exact final tree. The exact-final-tree connected rerun
and a bounded Notes rerun then encountered the device harness error
`No compose hierarchies found in the app` during startup and hung; both were
stopped after the second occurrence per the project stop rule. No Notes
assertion failure was observed in those runs.
