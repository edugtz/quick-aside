# Change 022 — Local Task Persistence Foundation — TASKS

Governance: **HIGH-ASSURANCE**  
Status: **COMPLETE — REVIEW PASS_WITH_NOTES**
Expected branch: `chg-022-local-task-persistence`

This is a LIVE EXECUTION CHECKLIST. Mark a task `[x]` only after the work or
evidence exists. Failed, skipped, blocked, and unverified gates remain
unchecked and are described in the evidence log.

## Change package and preflight

- [x] Verify clean `main` and expected baseline commit
      `88787552fcbb327efd6a5bbf958337c89aefc298`.
- [x] Read `AGENTS.md`, `docs/ACTIVE_WORK.md`, project, architecture,
      roadmap, acceptance, AI workflow, naming, and ADR-0001 sources.
- [x] Inspect current Task/TaskSpace/TaskId semantics.
- [x] Inspect QuickAsideDatabase, current Room entities/DAOs/stores/mappers,
      migrations 1→5, schema 5, application wiring, tests, and comparable
      Changes 003, 009, 013, and 018.
- [x] Confirm Room 3.0.2 / KSP 2.3.6 / SQLite 2.7.0 and current DB version 5.
- [x] Confirm no Task persistence boundary or Task UI exists.
- [x] Create the Change 022 SPEC, PLAN, and live TASKS package.
- [x] Point `docs/ACTIVE_WORK.md` at Change 022 IN PROGRESS.
- [x] Create/switch to `chg-022-local-task-persistence` without committing.

## Domain and application boundary

- [x] Preserve the current Task domain unchanged: stable ID, exact title,
      Personal/Trabajo space, nullable date-only due date, equality, and
      nullability.
- [x] Add the minimal `TaskStore` contract for save/upsert, stable-ID lookup,
      and deterministic enumeration.
- [x] Add `RoomTaskStore` with existing transaction/error conventions.
- [x] Ensure cancellation propagates and is not converted to ordinary failure.
- [x] Wire exactly one app-scoped TaskStore if required by app conventions.
- [x] Confirm no CapturePlan execution or interpreter-to-store wiring exists.

## Room schema and migration

- [x] Add only the Task entity, DAO, and explicit domain/entity mappers.
- [x] Persist only ID, title, TaskSpace, and nullable LocalDate due date.
- [x] Move `QuickAsideDatabase` from version 5 to 6.
- [x] Add/register explicit non-destructive `MIGRATION_5_6` only.
- [x] Preserve migrations 1→5 and all existing v5 tables/relationships.
- [x] Confirm destructive migration fallback remains absent.
- [x] Generate and inspect schema 6; confirm schemas 1–5 are unchanged.

## Focused JVM tests

- [x] Test Personal and Trabajo mapping/round trips.
- [x] Test due date present and null mapping.
- [x] Test stable ID/title preservation and unknown space failure.
- [x] Test deterministic listing contract and save/upsert behavior at the
      store boundary through the focused Room instrumentation test.

## Fresh v6 and migration instrumentation tests

- [x] Add fresh-v6 Room persistence coverage for both spaces, date/null,
      stable ID, deterministic listing, upsert, and close/reopen.
- [x] Add cancellation propagation and unexpected ordinary failure coverage
      according to existing store conventions.
- [x] Build a real on-disk schema-5 fixture with tracked v5 identity hash and
      `user_version = 5`.
- [x] Populate representative v5 Capture, List, Memory, and Action Ledger
      rows with valid foreign-key relationships.
- [x] Open the fixture through production `QuickAsideDatabase.create` and
      prove migration 5→6 succeeds on the Oppo CPH2791 / Android 16 device.
- [x] Verify all representative pre-existing rows/relationships remain
      semantically intact after migration on device.
- [x] Verify Task table structure, empty pre-insert state, version 6, and
      post-migration Task save/read plus close/reopen on device.
- [x] Verify old v5 table/index definitions are unchanged in tracked schema
      comparison and the migrated database retains those definitions.

## Verification gates

- [x] Run focused Task JVM tests: 5/5 passed.
- [x] Run focused Task Room/migration instrumentation tests on Oppo CPH2791 /
      Android 16: 5/5 passed; skips/failures/errors 0.
- [x] Run the applicable existing Room/database instrumentation suite on
      Oppo CPH2791 / Android 16: 84/84 passed; skips/failures/errors 0.
- [x] Compile Android instrumentation sources.
- [x] Run `./gradlew :app:testDebugUnitTest`: 109/109 passed; skips/failures/
      errors 0.
- [x] Run `./gradlew :app:assembleDebug`: BUILD SUCCESSFUL.
- [x] Run `./gradlew :app:lintDebug`: BUILD SUCCESSFUL.
- [x] Inspect generated schema 6 and schemas 1–5.
- [x] Run `git diff --check`.
- [x] Inspect `git status --short` and `git diff --stat`.
- [x] Prepare the implementation report without declaring an independent
      engineering verdict.
- [x] Record the independent review verdict: `PASS_WITH_NOTES`; `BLOCKER 0`,
      `MAJOR 0`, `MINOR 0`.

## Scope and authority

- [x] Confirm no AI/provider/runtime, PLAN/CLARIFY/UNSUPPORTED, temporal
      interpretation, ActionExecutor, Google, Calendar, sync/outbox/conflict,
      reminders, Task UI, VPS/Hermes/gateway, provider credentials, fallback
      provider, speculative field, or unrelated refactor was added.
- [x] Keep commit, push, merge, and release under user authority; record the
      independently supplied review outcome without changing scope.

## Evidence log

- Preflight: clean `main` at `88787552fcbb327efd6a5bbf958337c89aefc298`.
- Branch: `chg-022-local-task-persistence` created and checked out.
- Requested `software-project-orchestrator` skill was not installed or
  callable in this session; repository `AGENTS.md` governance is being used
  as the fallback orchestration contract.
- Focused JVM `TaskEntityMappingTest`: 5/5 passed.
- Full JVM `:app:testDebugUnitTest`: 109/109 passed; zero skips, failures, or
  errors.
- `:app:compileDebugAndroidTestKotlin`: BUILD SUCCESSFUL.
- `:app:assembleDebug --rerun-tasks`: BUILD SUCCESSFUL.
- `:app:lintDebug --rerun-tasks`: BUILD SUCCESSFUL.
- Generated schema 6: version 6, identity hash
  `bcce741653c243cf77bf048e39e33f9a`; schemas 1–5 remain byte-for-byte
  unchanged and schema 6 contains only the additional `tasks` table.
- `adb devices -l` enumerated the authorized Oppo CPH2791; Gradle identified it
  as `CPH2791 - 16`.
- Focused `TaskPersistenceDatabaseTest`: 5/5 passed on the Oppo, with zero
  skips, failures, or errors; this includes fresh v6, real v5→v6 migration,
  legacy-data preservation, and post-migration Task persistence.
- Applicable `com.edu.quickaside.data.local` connected suite: 84/84 passed on
  the Oppo, with zero skips, failures, or errors.
- Independent review verdict: `PASS_WITH_NOTES`; `BLOCKER 0`, `MAJOR 0`,
  `MINOR 0`.
- A broader all-app `:app:connectedDebugAndroidTest` run completed 208/209
  tests and observed one
  `MandadoUiTest.undoFailureReloadsVisibleStateAndShowsConciseError` Compose
  timeout. `MandadoUiTest` and its fake list/action dependencies are unchanged
  by Change 022; the failure is outside the required Task/Room verification
  gates, and current evidence does not attribute it to Change 022. No
  baseline-main reproduction was performed, so it is not classified here as
  pre-existing.
- All required Change 022 gates remain checked; the unrelated UI timeout is a
  non-blocking review note, not an additional Change 022 gate.

## Exact next gate

Change 022 is `COMPLETE — REVIEW PASS_WITH_NOTES`; the independent engineering
verdict is recorded above. The exact next gate is user-authorized commit/push
of Change 022 closeout docs, then merge `chg-022-local-task-persistence` into
`main`. After merge, the orchestrator will inspect the current roadmap and
repository state and select the next reviewable change. Change 023 has not
been selected. No commit, push, merge, or release is part of this builder
turn.
