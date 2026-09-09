# Change 020 — Typed CapturePlan + Validator Foundation — PLAN

Governance: **STANDARD**
Status: **IMPLEMENTATION COMPLETE — REVIEW PENDING**
Expected branch: `chg-020-capture-plan-foundation`

The user-defined Change 020 request is authoritative. This plan records the
minimal implementation sequence and verification without widening the feature.

## Preflight findings

- The expected branch is checked out and the starting worktree is clean.
- Changes 001–019 are present in the repository; `docs/ACTIVE_WORK.md` marks
  Change 019 complete.
- Existing pure-Kotlin domain types provide `CaptureId`, `ListDefinitionId`,
  `TaskSpace.PERSONAL`, `TaskSpace.TRABAJO`, `LocalDate`, `Note`, and
  `StructuredLog` semantics.
- No `CapturePlan`, `CaptureInterpreter`, `AIProvider`, or `ActionExecutor`
  production contract exists.
- Room is version 5 and tracked schemas 1–5 exist.
- The app has no dependency needed for this pure-Kotlin contract; no new
  dependency is planned.

## Implementation sequence

1. Create this SPEC/PLAN/TASKS package and point `ACTIVE_WORK.md` at it.
2. Add a focused pure-Kotlin CapturePlan domain file with typed validated
   actions and a draft/candidate representation.
3. Add a focused pure-Kotlin application validator with deterministic
   plan/action issue categories and no personal-content echoing.
4. Add deterministic JVM tests for valid/invalid candidates, order, exact
   content, task spaces/dates, UndoLast, issue indexing, and source imports.
5. Run focused and full applicable verification, then reconcile TASKS and
   ACTIVE_WORK with only evidence-backed completion.

## Expected files

Production:

- `app/src/main/java/com/edu/quickaside/domain/capture/CapturePlan.kt`
- `app/src/main/java/com/edu/quickaside/application/capture/CapturePlanValidator.kt`

Tests:

- `app/src/test/java/com/edu/quickaside/application/capture/CapturePlanValidatorTest.kt`

Documentation:

- this Change 020 package;
- `docs/ACTIVE_WORK.md`.

No Room, UI, provider, historical Change 001–019, or dependency files should
change.

## Verification matrix

- Focused Change 020 JVM test class.
- Full `:app:testDebugUnitTest`.
- `:app:assembleDebug`.
- `:app:lintDebug`.
- `git diff --check`, status, and diff-stat inspection.
- Source/import review of every new production file.
- Explicit checks for Room version 5, unchanged schemas 1–5, no schema 6,
  no dependency changes, no runtime provider/executor code, and no UI or
  historical-package changes.

Connected Android tests and visual evidence are not applicable.

## Stop conditions

Stop and report rather than expanding scope if the contract appears to require
Room/schema changes, persistence, serialization, network/provider work,
credentials, action execution, capture-pipeline wiring, UI, Google behavior,
reminders, Calendar semantics, or a generic validation framework.

## Implementation and verification evidence

- Added the pure-Kotlin CapturePlan/draft contract and focused validator.
- Added 21 deterministic JVM tests covering the required valid, invalid,
  ordering, exact-content, issue-content, and source-boundary cases.
- `./gradlew :app:testDebugUnitTest --tests
  'com.edu.quickaside.application.capture.CapturePlanValidatorTest'` passed
  21/21.
- `./gradlew :app:testDebugUnitTest` passed 86/86 with no failures, errors, or
  skips.
- `./gradlew :app:assembleDebug` and `./gradlew :app:lintDebug` succeeded.
- `git diff --check` succeeded; status/stat and source-boundary checks were
  inspected.
- Room remains version 5; schemas 1–5 are present and unchanged. No schema 6,
  migration, dependency, UI, provider, executor, or historical-package change
  was introduced.

## Closeout

Implementation is complete and the package is review pending. The exact next
gate is independent engineering review after the user commits/pushes the
combined Change 020 docs and implementation. The final engineering verdict is
not declared here.
