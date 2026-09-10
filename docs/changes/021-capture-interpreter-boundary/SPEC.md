# Change 021 — Capture Interpreter + AIProvider Boundary — SPEC

Governance: STANDARD
Status: IMPLEMENTATION COMPLETE — REVIEW PENDING
Expected branch: chg-021-capture-interpreter-boundary

## Objective

Establish the provider-independent runtime interpretation boundary between a
trusted persisted Capture and the validated CapturePlan foundation from
Change 020:

    trusted Capture
    → CaptureInterpreter
    → AIProvider
    → untrusted provider candidate actions
    → trusted Capture.id injected by the interpreter
    → CapturePlanDraft
    → CapturePlanValidator
    → validated CapturePlan

This change stops at a validated in-memory plan/result. It does not execute
actions, persist interpretation results, or integrate a runtime provider.

## In scope

- A narrow CaptureInterpreter suspend boundary for one trusted Capture.
- A provider-neutral AIProvider suspend boundary.
- An interpretation request containing only the effective input text.
- An untrusted provider candidate containing only ordered draft actions.
- Exact effective-text selection from Text and Voice captures.
- Trusted Capture.id injection into CapturePlanDraft.
- Mandatory validation through the existing CapturePlanValidator.
- Structured interpretation results for success, blank input, invalid plans,
  provider failure, and cancellation propagation.
- Deterministic JVM tests using local fakes/test doubles.
- This change package and the active-work pointer.

## Accepted contract

The application boundary may use the following concrete names:

    fun interface CaptureInterpreter {
        suspend fun interpret(capture: Capture): CaptureInterpretationResult
    }

    fun interface AIProvider {
        suspend fun interpret(request: AIInterpretationRequest): AIInterpretationCandidate
    }

    data class AIInterpretationRequest(val inputText: String)

    data class AIInterpretationCandidate(
        val actions: List<CapturePlanActionDraft>,
    )

The provider candidate contains no sourceCaptureId and no validated action
type. The interpreter constructs the draft with sourceCaptureId =
capture.id.value and actions = candidate.actions.

CaptureInterpretationResult distinguishes:

- Success with a validated CapturePlan;
- BlankInput when effective input is blank and the provider was not called;
- InvalidPlan with the validator's structured issues;
- ProviderFailure for a provider exception.

Provider CancellationException values are rethrown. No user-facing strings,
confidence semantics, telemetry, persistence, or action execution belong in
this result contract.

## Effective input

- Text uses CaptureInput.Text.originalText.
- Voice uses transcriptCorrection when present; otherwise it uses
  CaptureInput.Voice.originalTranscript.
- The selected content is passed exactly as stored. No trimming,
  normalization, title-casing, whitespace collapsing, or rewriting is allowed.
- isBlank() is used only to decide whether to return BlankInput.

## Validation and ordering

Every nonblank provider candidate follows this sequence:

1. Create AIInterpretationRequest from the exact selected text.
2. Invoke the provider once.
3. Inject the trusted Capture ID into a CapturePlanDraft.
4. Call CapturePlanValidator.
5. Map Valid to Success and Invalid to InvalidPlan.

Provider action order and valid action field content must remain unchanged from
candidate through draft and validated plan. No sorting, deduplication,
regrouping, or direct provider-to-validated-plan path is allowed.

## Out of scope

- MiMo, DeepSeek, LongCat, Qwen, model IDs, prompts, HTTP, JSON,
  serialization, SDKs, API keys, Keystore, retries, rate limits, token
  accounting, or provider-specific implementations.
- Local rule engines, regex/keyword routing, fallback policy, confidence
  thresholds, clarification questions, review/edit policy, or correction
  learning.
- ActionExecutor, list/memory/task mutation, Action Ledger writes, Room,
  migrations, schemas, Google APIs, Calendar, Tasks, reminders, or sync.
- Android, Compose, UI, navigation, receipts, or device tests.
- New production dependencies.

## Dependency boundary

New production files remain pure Kotlin/application code and may depend only
on Kotlin, existing Quick Aside domain/application contracts, and
CancellationException if needed. They must not import Android, Compose,
Room/SQLite, Google, HTTP, JSON/serialization, or provider-specific types.

## Verification and authority

Required applicable evidence:

    ./gradlew :app:testDebugUnitTest --tests '<actual Change 021 focused test class>'
    ./gradlew :app:testDebugUnitTest
    ./gradlew :app:assembleDebug
    ./gradlew :app:lintDebug
    git diff --check
    git status --short
    git diff --stat

No connected Android tests or visual evidence are required because this change
has no Android/UI behavior. Room must remain version 5, schemas 1–5 must be
unchanged, and no schema 6 or migration may be introduced.

Do not commit, push, merge, release, or declare the final engineering verdict;
independent review is the next gate after implementation.
