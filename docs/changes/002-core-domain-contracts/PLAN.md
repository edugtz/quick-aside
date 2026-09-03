# Change 002 — Core Domain Contracts — PLAN

Governance: **STANDARD**
Status: **IN PROGRESS**

## Current-state inspection

- Change 001 is complete on the existing Compose/Material 3 single-module
  baseline.
- The current branch is `chg-002-core-domain-contracts`.
- `app/src/main` currently contains only the Android entry point and UI shell;
  no domain package exists.
- `app/src/test` contains the navigation-shell JVM test and the module already
  has JUnit support.
- The repository has no Room, KSP, Google, runtime AI, or serialization
  dependency needed by this change.

## Minimal package/model approach

Add a small package tree under `com.edu.quickaside.domain`:

- `common` — typed local ID wrappers.
- `capture` — capture input boundary and timestamped Capture.
- `lists` — list definition behavior, sessions, and items.
- `tasks` — task spaces, date-only due dates, and Task.
- `memory` — Note and deliberately minimal StructuredLog.
- `reminders` — Task/Note target and exact scheduled time.
- `actions` — minimal ActionLedgerEntry.

Use immutable data classes, enums/sealed types, `java.time.Instant`, and
`java.time.LocalDate`. Do not add constructors that generate IDs, persistence
annotations, repositories, mappers, use cases, or integration abstractions.

## Test approach

Add deterministic JVM unit tests that cover the seven scenarios in the SPEC:
list behavior mapping, task-space distinction, separate date/time semantics,
Note reminder targeting, and original Text/Voice capture retention. Verify
the dependency boundary through source inspection and the normal JVM/build
compilation rather than adding artificial compile-time tests.

## Documentation update

- Create this SPEC, PLAN, and TASKS package before implementation.
- Point `docs/ACTIVE_WORK.md` at this change and state the next verification
  gate.
- Do not modify the completed Change 001 package or rewrite project-level
  product/architecture/roadmap documents.

## Verification contract

Run, and report the exact result of:

```text
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
git diff --check
git status --short
git diff --stat main...HEAD
```

If the implementation remains uncommitted, also inspect the working-tree
diff/stat as needed so the report describes the actual repository state.
No connected Android test or visual evidence is required unless the
implementation unexpectedly crosses into Android-dependent behavior.

## Split signals

Stop and split into a later change if implementation requires Room/schema
decisions, Android scheduling/notifications, STT, AI/CapturePlan/routing,
Google integration, repositories/use cases, undo execution, or any UI change.

