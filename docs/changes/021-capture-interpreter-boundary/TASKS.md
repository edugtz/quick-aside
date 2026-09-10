# Change 021 — Capture Interpreter + AIProvider Boundary — TASKS

Governance: STANDARD
Status: COMPLETE — REVIEW PASS
Expected branch: chg-021-capture-interpreter-boundary

This is a LIVE EXECUTION CHECKLIST. Mark [x] only when actual work or
evidence exists. Failed, skipped, blocked, and not-yet-run gates remain
unchecked.

## Change package and preflight

- [x] Confirm the expected branch and inspect the starting worktree.
- [x] Read AGENTS.md, project contracts, active work, and historical Change
      002/018/019/020 guidance.
- [x] Confirm Change 020 is the current merged baseline with CapturePlan,
      CapturePlanDraft, CapturePlanActionDraft, and CapturePlanValidator.
- [x] Confirm no CaptureInterpreter, AIProvider, ActionExecutor, or runtime
      provider implementation exists before this change.
- [x] Confirm Room version 5 and tracked schemas 1–5.
- [x] Confirm no dependency change is required.
- [x] Create the Change 021 SPEC, PLAN, and TASKS package.
- [x] Point docs/ACTIVE_WORK.md to Change 021.

## Contracts

- [x] Add a narrow provider-neutral AIProvider boundary.
- [x] Add an interpretation request containing only effective input text.
- [x] Add an untrusted provider candidate containing only draft actions.
- [x] Ensure the provider contract cannot supply or override sourceCaptureId.
- [x] Add the CaptureInterpreter boundary and provider-backed implementation.
- [x] Select exact Text and corrected-or-original Voice content.
- [x] Return deterministic BlankInput without invoking the provider for blank
      effective input.
- [x] Inject trusted Capture.id into CapturePlanDraft.
- [x] Route every candidate through CapturePlanValidator.
- [x] Map success, invalid plan, and provider failure results narrowly.
- [x] Rethrow CancellationException.
- [x] Preserve candidate action order and exact valid action fields.
- [x] Introduce no action execution, persistence, or external mutation.

## Focused tests

- [x] Test exact Text input to the provider.
- [x] Test exact uncorrected Voice transcript to the provider.
- [x] Test corrected Voice transcript selection.
- [x] Test valid surrounding whitespace preservation.
- [x] Test blank input result and zero provider calls.
- [x] Test provider request exposes only provider-neutral interpretation data.
- [x] Test provider candidate has no source Capture ID field.
- [x] Test trusted Capture ID becomes plan source ID.
- [x] Test valid success and exact multi-action ordering/content.
- [x] Test empty candidate actions becomes EMPTY_ACTIONS invalid plan.
- [x] Test invalid AddListItem and Note candidates expose expected issues.
- [x] Test validation action indexes survive into InvalidPlan.
- [x] Test provider failure, cancellation propagation, and exactly one call.
- [x] Test production source imports stay provider-neutral pure Kotlin.

## Verification and boundaries

- [x] Run the focused Change 021 JVM test class.
- [x] Run ./gradlew :app:testDebugUnitTest.
- [x] Run ./gradlew :app:assembleDebug.
- [x] Run ./gradlew :app:lintDebug.
- [x] Verify Room/schema/dependency/UI/provider/executor/external-service
      boundaries and unchanged historical Change 001–020 files.
- [x] Run git diff --check, inspect git status --short, and inspect
      git diff --stat.
- [x] Reconcile this checklist with actual evidence.
- [x] Update docs/ACTIVE_WORK.md to implementation complete/review pending.

## Authority

- [ ] Do not commit, push, merge, or release.

## Evidence log

Evidence:

- Focused CaptureInterpreterTest: 18/18 passed.
- Full :app:testDebugUnitTest: 104/104 passed; failures 0, errors 0, skips 0.
- :app:assembleDebug: BUILD SUCCESSFUL.
- :app:lintDebug: BUILD SUCCESSFUL.
- git diff --check: no output/errors.
- New production imports are limited to existing application/domain types
  and kotlin.coroutines.cancellation.CancellationException.
- Room remains version 5; schemas 1–5 are present and unchanged; no schema 6
  or migration was introduced.
- No dependency, UI, network, serialization, provider-specific, executor,
  Google, reminder, Calendar, or historical Change 001–020 file changed.

The final builder report must not declare the independent engineering verdict.

Independent engineering review:
PASS — BLOCKER 0 / MAJOR 0 / MINOR 0

Independent engineering review gate: complete.

## Exact next gate

Change 021 is complete and ready for user-authorized merge.
