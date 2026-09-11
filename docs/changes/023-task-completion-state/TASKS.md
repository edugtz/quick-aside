# Change 023 — Task Completion State Foundation — TASKS

Governance: **HIGH-ASSURANCE**  
Status: **COMPLETE — REVIEW PASS**
Expected branch: `chg-023-task-completion-state`

This is a LIVE EXECUTION CHECKLIST. Mark a task `[x]` only after the work or
evidence exists. Failed, skipped, blocked, and unverified gates remain
unchecked and are described in the evidence log. This checklist records
evidence; it does not declare an independent engineering verdict.

## Change package and preflight

- [x] Verify clean `main` at `8aace0eca7c35c086d3f23db4d8ca91301ad9e1d`.
- [x] Read `AGENTS.md`, active work, project, architecture, roadmap,
      acceptance, UX, naming, and ADR-0001 sources.
- [x] Confirm Change 022 is merged/complete and the current database version is
      6 with generated schema 6.
- [x] Inspect `Task`, `TaskStore`, `TaskEntity`, `TaskDao`, `RoomTaskStore`,
      `QuickAsideDatabase`, application wiring, mapping tests, Room tests, and
      existing migration conventions.
- [x] Confirm the requested `software-project-orchestrator` skill is not
      installed/callable; use repository governance as the explicit fallback.
- [x] Create/switch to `chg-023-task-completion-state` without committing.
- [x] Create Change 023 SPEC, PLAN, and live TASKS package.
- [x] Point `docs/ACTIVE_WORK.md` at Change 023 IN PROGRESS.

## Domain and persistence implementation

- [x] Add only `Task.completedAt: Instant? = null` to the domain contract.
- [x] Preserve stable ID, exact title, Personal/Trabajo, nullable LocalDate
      dueDate, constructor compatibility, and data-class semantics.
- [x] Persist only nullable `completed_at_epoch_millis` in `TaskEntity`.
- [x] Map pending, completed, and reopened states with the established
      epoch-millis convention.
- [x] Keep `TaskStore` minimal and unchanged.
- [x] Confirm no UI, ActionExecutor, reversible Task action, Action Ledger Task
      behavior, Google, sync metadata, reminders, or AI/runtime work appears.

## Room schema and migration

- [x] Move `QuickAsideDatabase` from version 6 to 7.
- [x] Add/register explicit `MIGRATION_6_7` that only adds the nullable
      completion column to `tasks`.
- [x] Preserve migrations 1→6 and configure no destructive fallback.
- [x] Generate schema 7 through normal Room/KSP output.
- [x] Confirm schemas 1–6 remain byte-for-byte unchanged.
- [x] Confirm schema 7 has the exact intended Task-table delta and no unrelated
      schema mutation.

## Focused JVM tests

- [x] Test pending Task round-trip with `completedAt == null`.
- [x] Test completed Task round-trip with exact persisted Instant.
- [x] Test dueDate and completedAt independence.
- [x] Test Personal/Trabajo, stable ID, exact title, and null semantics remain.
- [x] Assess malformed completion-value coverage for the chosen typed INTEGER
      representation and keep any applicable failure visible.

## Fresh v7 and migration instrumentation tests

- [x] Test fresh-v7 pending Task.
- [x] Test fresh-v7 completed Task and raw epoch-millis persistence.
- [x] Test same stable ID pending → completed without duplicate rows.
- [x] Test same stable ID completed → reopened without duplicate rows.
- [x] Test close/reopen preserves each completion state.
- [x] Build a real on-disk tracked-schema v6 fixture with identity hash
      `bcce741653c243cf77bf048e39e33f9a` and `user_version = 6`.
- [x] Include Personal/null-due and Trabajo/due-date legacy Tasks.
- [x] Include representative Capture, List, Memory, and Action Ledger data.
- [x] Prove version 7, legacy Task field preservation, pending defaults, and
      unrelated data/schema preservation after migration.
- [x] Prove post-migration completion and reopen across close/reopen.

## Verification gates

- [x] Run focused Task JVM tests and record actual counts.
- [x] Run focused Task Room/migration instrumentation on Oppo CPH2791 /
      Android 16 and record actual counts/skips/failures/errors.
- [x] Run the applicable `com.edu.quickaside.data.local` connected suite.
- [x] Compile Android instrumentation sources.
- [x] Run `./gradlew :app:testDebugUnitTest`.
- [x] Run `./gradlew :app:assembleDebug`.
- [x] Run `./gradlew :app:lintDebug`.
- [x] Run schema comparison, `git diff --check`, `git status --short`, and
      `git diff --stat`.
- [x] Prepare the evidence report without declaring an independent verdict.
- [x] Record independent engineering review: `PASS`; `BLOCKER 0`, `MAJOR 0`,
      `MINOR 0`.

## Evidence log

- Preflight: clean `main` at `8aace0eca7c35c086d3f23db4d8ca91301ad9e1d`.
- Branch: `chg-023-task-completion-state` created and checked out without a
  commit.
- Requested orchestrator skill: unavailable in the current skill catalog/cache;
  repository `AGENTS.md` governance is being used as the fallback contract.
- Focused JVM `TaskEntityMappingTest`: 7/7 passed; skips/failures/errors 0.
- Focused `TaskPersistenceDatabaseTest`: 6/6 passed on Oppo CPH2791 / Android
  16; skips/failures/errors 0.
- Applicable `com.edu.quickaside.data.local` connected suite: 85/85 passed on
  Oppo CPH2791 / Android 16; skips/failures/errors 0.
- Android instrumentation sources compiled successfully as part of the focused
  connected run.
- Full `:app:testDebugUnitTest`: 111/111 passed; skips/failures/errors 0.
- `:app:assembleDebug`: BUILD SUCCESSFUL.
- `:app:lintDebug`: BUILD SUCCESSFUL.
- Generated schema 7: version 7, identity hash
  `70228faecbbf2874265f642bf5f1ec0f`; `tasks` adds only nullable INTEGER
  `completed_at_epoch_millis` after the existing four columns.
- Schemas 1–6 compare byte-for-byte unchanged with the starting tree.
- `MIGRATION_6_7` is registered; no destructive migration fallback is present.
- `git diff --check` is clean.
- Initial `connectedDebugAndroidTest --tests ...` invocation was rejected by
  Gradle before execution because that task does not support `--tests`; the
  corrected instrumentation-runner class filter executed the focused suite
  successfully. This is a command-line invocation note, not a product/test
  failure.
- Broader all-app connected coverage was not run; the change remained within
  Task/domain/Room/data.local surfaces and introduced no UI behavior.
- Independent engineering review: `PASS`; `BLOCKER 0`, `MAJOR 0`, `MINOR 0`.
- Review confirmed the existing implementation, schema, migration, device,
  JVM, build, lint, preservation, and scope evidence. No additional gates were
  added.

## Exact next gate

User-authorized commit/push of Change 023 closeout docs, then merge
`chg-023-task-completion-state` into `main`. After merge, inspect the current
repository/roadmap state before selecting the next reviewable change. Do not
select Change 024 yet.
