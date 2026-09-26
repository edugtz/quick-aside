# Change 030 — Capture Task Auto-Execution + Receipt/Undo Integration — TASKS

- Governance: **HIGH-ASSURANCE**
- Status: **PLANNED — IMPLEMENTATION PENDING**
- Branch: `chg-030-capture-task-auto-execution` (local only)
- Base: `61c2c0b8aae6a5adab9306409cc5c6dc903a96da`

All items are unchecked. Planning created this package, selected CHG-030, and
ran no production/test/build/test/device command.

## Preflight

- [ ] Re-verify `git branch --show-current`, `git rev-parse HEAD`, `git status
      --short`, and `git rev-parse origin/main` against the recorded base.
- [ ] Confirm the branch is `chg-030-capture-task-auto-execution` and the
      worktree is clean before implementation.
- [ ] Confirm CHG-031 does not exist and no other Change is active.
- [ ] Re-read `docs/UX_UI_REFERENCE.md`, inspect
      `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`, and confirm the
      affected Guardado/Receipt and Pendientes invariants.
- [ ] Re-inspect `CaptureSubmission`, `CapturePlanTaskExecutor`,
      `RoomCapturePlanTaskExecutor`, `CapturePlanListExecutor`,
      `RoomCapturePlanListExecutor`, `QuickAsideApplication`, `MainActivity`,
      `QuickAsideApp`, `VoiceCaptureScreen`, and `PendientesScreen`.
- [ ] Confirm Room v7 / DAO / dependency sufficiency and record that no
      migration is expected.

## Application/domain orchestration

- [ ] Evolve `CaptureExecutionOutcome` to the family-tagged nested
      `Executed` / `Rejected` / `Failed` model from `PLAN.md`.
- [ ] Add `taskExecutor: CapturePlanTaskExecutor? = null` to
      `CaptureSubmission`.
- [ ] Compute eligibility once per validated plan: all-list → list executor;
      all-task → task executor; anything else → `NotEligible` with zero
      executor calls; never split, subset, or reorder.
- [ ] Map CHG-029 results exactly: `Executed`, `Failed`, and
      `UnsupportedAction`/`Rejected`/`RejectedPlan`/`MissingSourceCapture` into
      the typed outcome branches.
- [ ] Preserve persist-first ordering and missing-executor honesty for both
      families.
- [ ] Propagate `CancellationException` from writer, interpreter, and both
      executors; never roll back a durable Capture.
- [ ] Keep `sourceCaptureId` exactly the local Capture ID end to end.
- [ ] Do not modify the CHG-029 executor or its result contracts.

## Dependency wiring

- [ ] Add one app-scoped `RoomCapturePlanTaskExecutor` to
      `QuickAsideApplication`.
- [ ] Inject that instance into the app-scoped `CaptureSubmission`.
- [ ] Forward the same instance through `MainActivity` to `QuickAsideApp` for
      targeted Undo only.
- [ ] Do not instantiate executors per capture and do not build a registry.

## UI receipt / Undo

- [ ] Show `Pendiente guardado` for one Task and `<N> pendientes guardados`
      for N Tasks, with `Deshacer`, using the shared snackbar host.
- [ ] Call only `CapturePlanTaskExecutor.undoExecution(exact ledger ID, exact
      ordered Task IDs)` from the receipt.
- [ ] Do not query latest ledger, infer IDs, call manual
      `ReversibleTaskActions` Undo, delete Tasks, or touch DAOs from UI.
- [ ] Show `Cambio deshecho` only for
      `UndoCapturePlanTaskExecutionResult.Undone`; otherwise
      `No se pudo deshacer.` with no success claim.
- [ ] Keep unsupported/mixed plans on honest non-success copy with no Undo.
- [ ] Keep the CHG-028 list receipt/Undo behavior unchanged.

## Task freshness

- [ ] Add a shell-owned `taskRefreshToken` and pass it into
      `PendientesScreen`.
- [ ] Consume it in `LaunchedEffect(taskStore, refreshToken)`.
- [ ] Increment after successful Task execution and after every Task Undo
      attempt.
- [ ] Do not add Flow, reactive persistence, WorkManager, or a generic
      refresh framework.

## Tests

- [ ] Add focused JVM `CaptureSubmissionTaskExecutionTest` covering exactly-once
      execution, ordered receipt identity, persist-first ordering, mixed and
      unsupported zero execution, missing executor, rejection, failure,
      cancellation, and both `submit`/`submitVoice`.
- [ ] Add focused JVM result-model invariant coverage: every allowed
      `Rejected.ListItems` variant (`UnsupportedAction`, `Rejected`,
      `MissingSourceCapture`) and every allowed `Rejected.Tasks` variant
      (`UnsupportedAction`, `Rejected`, `RejectedPlan`, `MissingSourceCapture`)
      constructs; `Executed` and `Failed` payloads are rejected for both
      wrappers; `Executed`/`Failed` wrappers carry only their exact family
      payload type.
- [ ] Mechanically update CHG-028 JVM/Room test references to the nested list
      family without weakening coverage.
- [ ] Add real-Room `CaptureSubmissionTaskExecutionDatabaseTest` covering exact
      Capture provenance, batch order/fields, mixed zero mutation, exact
      targeted Undo with Capture retained, and unrelated-state preservation.
- [ ] Add Compose `CaptureTaskAutoExecutionUiTest` covering text single/batch
      receipts, exact ordered Undo forwarding, mixed/unsupported honesty,
      executor rejection/failure, Undo failure honesty, voice exactly-once,
      visible Pendientes refresh after execution, and Pendientes reconciliation
      after Undo.
- [ ] Run directly affected CHG-028/CHG-029/Capture/Voice/Pendientes
      regressions.

## Deterministic verification

- [ ] Focused JVM orchestration/contract gate.
- [ ] Focused real-Room pipeline instrumentation gate on the emulator.
- [ ] Focused Compose UI gate plus affected connected regressions.
- [ ] `./gradlew :app:testDebugUnitTest` full JVM.
- [ ] `./gradlew :app:compileDebugAndroidTestKotlin`.
- [ ] `./gradlew :app:assembleDebug`.
- [ ] `./gradlew :app:lintDebug`.
- [ ] Room v7 / schema / migration / dependency / manifest /
      network-security comparison against base.
- [ ] Copy every artifact and its provenance into `evidence/` immediately
      after each run.
- [ ] Preserve every failed or environmental attempt.

## Real-environment acceptance

- [ ] This is a **REQUIRED** acceptance gate. Confirm the private Tailscale
      route, gateway, and supported paired device are available. If
      unavailable, the gate and change status stay **PENDING**; pre-review
      readiness becomes
      `NOT REVIEW-READY — REQUIRED REAL-ENVIRONMENT ACCEPTANCE PENDING`, and
      independent implementation review must not begin. Mocks, emulator runs,
      and fake-executor tests do not substitute.
- [ ] Controlled TEXT Task capture with a unique disposable marker: one
      locally persisted Capture, successful Task-family automatic execution,
      exact Task row(s) and values, one exact source-linked ledger with exact
      ordered `CREATE/task` mutations, zero marker-correlated list mutation,
      receipt, Pendientes visibility.
- [ ] Coordinated real-human VOICE Task capture: one VOICE Capture, exactly
      one execution, correct Task row values, one exact source-linked ledger
      with exact ordered `CREATE/task` mutations, zero marker-correlated list
      mutation, receipt, Pendientes visibility.
- [ ] Exact targeted Undo for both paths: Task absence, Capture retained,
      ledger undone, duplicate count = 1.
- [ ] Capture the required sanitized visual evidence for receipt/Undo/
      Pendientes and the required privacy/log sample scan.
- [ ] Do not claim the validated `CapturePlan` was verified via Room; it is
      not persisted. Record failed or non-eligible attempts as observations
      and retry with a clarified disposable utterance before claiming
      acceptance.
- [ ] Record device model/OS, app identity, markers, and timestamps without
      secrets or personal data.

## Scope / provenance

- [ ] Run `git diff --check`, `git status --short`, `git diff --stat`, and
      `git diff --name-status`.
- [ ] Inspect the complete diff for unrelated files, secrets, binaries, DB
      dumps, and future scope.
- [ ] Confirm the changed-file set matches the planned production/test/docs
      files only.
- [ ] Confirm zero schema/migration/dependency changes.
- [ ] Confirm evidence is repository-local and machine-inspectable; no APK/AAB
      or DB/WAL/SHM artifact is retained.

## Independent review

- [ ] Prepare the implementation/evidence record without assigning a verdict.
- [ ] Run the pre-review readiness check. Require `READY FOR INDEPENDENT
      REVIEW` only when every required gate, including the required
      real-environment acceptance, has observed evidence. If that acceptance
      is pending, record
      `NOT REVIEW-READY — REQUIRED REAL-ENVIRONMENT ACCEPTANCE PENDING` and do
      not begin independent implementation review. Other gaps record
      `NOT REVIEW-READY — EVIDENCE CLOSEOUT REQUIRED`.
- [ ] Stop before commit/push/merge; the user authorizes those.
- [ ] Support independent HIGH-ASSURANCE review and disposition findings.

## Closeout

- [ ] Reconcile `SPEC.md`, `PLAN.md`, `TASKS.md`, `QA.md`, and
      `docs/ACTIVE_WORK.md` with actual evidence and status.
- [ ] Confirm the list path did not regress and Task execution remained
      exactly-once.
- [ ] Confirm Google Tasks sync, Event execution, reminders, mixed-family
      execution, and CHG-031 remain out of scope and unimplemented.
- [ ] Record the final user-authorized integration state only after the user
      merges.
