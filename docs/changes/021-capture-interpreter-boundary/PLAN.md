# Change 021 — Capture Interpreter + AIProvider Boundary — PLAN

Governance: STANDARD
Status: IMPLEMENTATION COMPLETE — REVIEW PENDING
Expected branch: chg-021-capture-interpreter-boundary

The user-defined Change 021 request is authoritative. This plan records the
minimal implementation and verification sequence without widening the scope.

## Preflight findings

- The expected branch is checked out and the starting worktree is clean.
- main and the Change 021 branch start at the Change 020 closeout commit;
  Changes 001–020 are present in the repository baseline.
- Change 020 provides CapturePlan, CapturePlanDraft,
  CapturePlanActionDraft, and CapturePlanValidator.
- Capture distinguishes Text and Voice input and exposes corrected Voice
  transcript semantics; no interpreter or provider contract currently exists.
- No ActionExecutor or runtime provider implementation exists.
- Room is version 5, tracked schemas 1–5 are the current baseline, and no
  database change is needed.
- Existing application boundaries use suspend functions, sealed results, and
  explicit CancellationException propagation. No new dependency is planned.
- There is no UI or Android behavior in this change, so visual/device evidence
  is not applicable.

## Implementation sequence

1. Create this SPEC/PLAN/TASKS package and point ACTIVE_WORK.md at it.
2. Add pure-Kotlin/application AIProvider, request, and untrusted candidate
   contracts with no provenance field or provider-specific metadata.
3. Add CaptureInterpreter, CaptureInterpretationResult, and a provider-backed
   interpreter that selects exact effective text, short-circuits blank input,
   injects the trusted Capture ID, validates all candidates, and maps narrow
   failures.
4. Add deterministic JVM tests with local fakes for text/voice selection,
   whitespace, blank input, provenance, validation, ordering, exact fields,
   failure, cancellation, and one-call behavior.
5. Review imports and repository boundaries, run focused/full verification,
   reconcile TASKS, and update ACTIVE_WORK with evidence-backed status.

## Expected files

Production:

- app/src/main/java/com/edu/quickaside/application/capture/AIProvider.kt
- app/src/main/java/com/edu/quickaside/application/capture/CaptureInterpreter.kt

Tests:

- app/src/test/java/com/edu/quickaside/application/capture/CaptureInterpreterTest.kt

Documentation:

- this Change 021 package;
- docs/ACTIVE_WORK.md.

No Room, UI, provider implementation, dependency, schema, or historical
Change 001–020 files should change.

## Verification matrix

- Focused Change 021 JVM test class.
- Full :app:testDebugUnitTest.
- :app:assembleDebug.
- :app:lintDebug.
- git diff --check, status, and diff-stat inspection.
- Source/import review of every new production file.
- Explicit checks for Room version 5, unchanged schemas 1–5, no schema 6,
  no dependency changes, no UI, no provider-specific implementation, no
  execution, and no historical-package modifications.

Connected Android tests and visual evidence are not applicable.

## Stop conditions

Stop and report rather than expand scope if implementation requires provider
SDK/network/serialization work, credentials, persistence/schema changes,
action execution, UI, local interpretation rules, confidence/fallback policy,
Google behavior, reminders, or a generic AI framework.

## Closeout target

When implementation and all applicable verification pass, mark this package:

- TASKS.md: IMPLEMENTATION COMPLETE — REVIEW PENDING;
- docs/ACTIVE_WORK.md: IMPLEMENTATION COMPLETE — REVIEW PENDING.

The exact next gate is independent engineering review after the user commits
and pushes the combined Change 021 documentation and implementation. Do not
mark that review complete in this builder turn.

## Implementation and verification evidence

- Added the provider-neutral AIProvider request/candidate contracts and the
  provider-backed CaptureInterpreter in pure Kotlin/application code.
- Added 18 deterministic JVM tests covering exact Text/Voice selection,
  whitespace, blank-input short-circuiting, trusted provenance, validation,
  ordering, exact fields, failure, cancellation, one-call behavior, and
  source-boundary structure.
- Focused CaptureInterpreterTest passed 18/18.
- Full :app:testDebugUnitTest passed 104/104 with failures 0, errors 0,
  skips 0; :app:assembleDebug and :app:lintDebug succeeded.
- Room remains version 5; schemas 1–5 are present and unchanged. No schema 6,
  migration, dependency, UI, provider-specific, network, serialization,
  executor, Google, reminder, Calendar, or historical Change 001–020 change
  was introduced.
- git diff --check produced no output/errors.

The implementation is complete and review is pending; no independent
engineering verdict is declared here.
