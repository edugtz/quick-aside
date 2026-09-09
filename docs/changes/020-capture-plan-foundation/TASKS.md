# Change 020 — Typed CapturePlan + Validator Foundation — TASKS

Governance: **STANDARD**
Status: **COMPLETE — REVIEW PASS**
Expected branch: `chg-020-capture-plan-foundation`

This is a LIVE EXECUTION CHECKLIST. Mark `[x]` only when actual work or
evidence exists. Failed, skipped, blocked, and not-yet-run gates remain
unchecked.

## Change package and preflight

- [x] Confirm the expected branch and inspect the starting worktree.
- [x] Read `AGENTS.md`, project contracts, active work, and historical Change
      002/018/019 guidance.
- [x] Confirm Change 019 is complete and no CapturePlan/provider/executor
      contract exists.
- [x] Confirm Room version 5 and tracked schemas 1–5.
- [x] Confirm no dependency change is required.
- [x] Create the Change 020 SPEC, PLAN, and TASKS package.
- [x] Point `docs/ACTIVE_WORK.md` to Change 020.

## Typed contracts

- [x] Add a pure-Kotlin validated CapturePlan with mandatory source Capture ID
      and ordered non-empty typed actions.
- [x] Add a draft/candidate contract capable of representing invalid values.
- [x] Add only AddListItem, CreateTask, CreateNote, CreateStructuredLog, and
      UndoLast action families.
- [x] Preserve existing ListDefinitionId, TaskSpace, LocalDate, Note, and
      StructuredLog semantics without persistence or execution.

## Validator

- [x] Add deterministic CapturePlan validation results for valid and invalid
      candidates.
- [x] Reject all required blank/empty plan and action cases.
- [x] Report plan/action scope and deterministic action index/category.
- [x] Do not embed rejected personal text in validation issues.
- [x] Preserve exact valid whitespace and draft action order.

## Focused tests

- [x] Add deterministic JVM tests for valid plans, multi-actions, order,
      whitespace, task spaces/dates, Note, Structured Log, and UndoLast.
- [x] Test every required invalid candidate and deterministic action index.
- [x] Test issue content does not contain rejected personal text.
- [x] Test new production files are pure Kotlin and provider-independent.

## Verification and boundaries

- [x] Run the focused Change 020 JVM test class.
- [x] Run `./gradlew :app:testDebugUnitTest`.
- [x] Run `./gradlew :app:assembleDebug`.
- [x] Run `./gradlew :app:lintDebug`.
- [x] Verify no Room/schema/dependency/UI/provider/executor/historical-package
      changes.
- [x] Run `git diff --check`, inspect `git status --short`, and inspect
      `git diff --stat`.
- [x] Independent engineering review complete — PASS (BLOCKER 0 / MAJOR 0 /
      MINOR 0).

## Authority

- [ ] Do not commit, push, merge, or release.

## Evidence log

- Focused `CapturePlanValidatorTest`: 21/21 passed.
- Full `:app:testDebugUnitTest`: 86/86 passed; failures 0, errors 0, skips 0.
- `:app:assembleDebug`: BUILD SUCCESSFUL.
- `:app:lintDebug`: BUILD SUCCESSFUL.
- `git diff --check`: no output/errors.
- Source/import review confirms the two new production files use only Kotlin,
  existing Quick Aside domain types, and `java.time`; no Android, Compose,
  Room, SQLite, Google, network, serialization, provider, or executor imports
  exist.
- Room remains version 5; schemas 1–5 are present and unchanged; no schema 6
  or migration exists.
- No dependency, UI, runtime AI/provider, action execution, reminder/Calendar,
  Google, or historical Change 001–019 files changed.
- Independent engineering review: PASS — BLOCKER 0 / MAJOR 0 / MINOR 0.

## Exact next gate

Change 020 is complete and ready for user-authorized merge.

After merge, M2 continues with the next reviewable change.
