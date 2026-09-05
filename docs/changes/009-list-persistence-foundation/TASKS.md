# Change 009 — List Persistence Foundation — TASKS

## Change package and preflight

- [x] Confirm the expected branch and repository state.
- [x] Read the project, architecture, roadmap, acceptance, naming, UX, and
      historical Change 002/003/007/008 guidance.
- [x] Inspect the current list domain, IDs, Room entities/DAO/database,
      application wiring, migration tests, and schemas 1/2.
- [x] Confirm Room 3.0.2, KSP 2.3.6, SQLite 2.7.0, and dependency versions.
- [x] Create the Change 009 SPEC, PLAN, and TASKS package.
- [x] Mark `docs/ACTIVE_WORK.md` as Change 009 IN PROGRESS.

## Domain and built-ins

- [x] Extend `ListItem` with completion and deterministic creation time.
- [x] Add stable Mandado/Compras definitions without closing the domain enum.
- [x] Enforce invalid blank item text at the domain/application boundary.
- [x] Add deterministic injectable ID and time providers outside pure domain.

## Room schema and migration

- [x] Add list entities, required foreign keys/indexes, DAOs, and mappers.
- [x] Move `QuickAsideDatabase` to version 3.
- [x] Preserve and register the existing 1→2 migration.
- [x] Add explicit non-destructive 2→3 migration and built-in seeding.
- [x] Add explicit fresh-v3 seeding through the same seed owner.
- [x] Generate and inspect `schemas/3.json`; confirm schemas 1/2 unchanged.

## Application boundary

- [x] Add focused list operations for definitions, sessions, history, items,
      and completion state.
- [x] Implement transaction-safe single-active-session start behavior.
- [x] Implement durable session finish/no-op behavior.
- [x] Enforce session/definition behavior and ownership rules on item insert.
- [x] Wire the production list boundary without changing Lists UI.

## Tests and verification

- [x] Add deterministic JVM domain/mapping tests.
- [x] Add real v2→v3 migration fixture coverage with Text and corrected Voice
      Capture exact-value checks.
- [x] Add fresh-database seed and schema/table checks.
- [x] Add list session/item/completion/ordering/concurrency tests.
- [x] Add v3 Capture and transcript-correction regressions.
- [x] Run `./gradlew :app:testDebugUnitTest`.
- [x] Run `./gradlew :app:assembleDebug`.
- [x] Run `./gradlew :app:lintDebug`.
- [x] Run `./gradlew :app:connectedDebugAndroidTest` on `Pixel_9_Pro` API 35.
- [x] Run `git diff --check`, `git status --short`, and diff stats.
- [x] Prepare the implementation report without declaring the final verdict.

## Scope and authority

- [x] Keep historical Change 001–008 documentation packages and schemas
      unchanged; update only the stale current-version regression expectation.
- [x] Do not add UI, AI/STT, sync, reminders, delete, reorder, or speculative
      list fields.
- [x] Do not change Room/KSP/SQLite versions or add production dependencies.
- [x] Do not merge or push.
- [x] Final independent review; verdict: PASS_WITH_NOTES.