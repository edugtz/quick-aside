# Change 030 — Capture Task Auto-Execution + Receipt/Undo Integration — SPEC

- Governance: **HIGH-ASSURANCE**
- Status: **PLANNED — IMPLEMENTATION PENDING**
- Repository: `edugtz/quick-aside`
- Branch: `chg-030-capture-task-auto-execution` (created locally only; not
  pushed)
- Base: `main` / `origin/main` / starting `HEAD` at
  `61c2c0b8aae6a5adab9306409cc5c6dc903a96da`; starting worktree clean
- Package: `docs/changes/030-capture-task-auto-execution/`

This planning turn produced this package plus the required `ACTIVE_WORK` /
`ROADMAP` selection records. It ran no production or test change, no build,
test, lint, instrumentation, device acceptance, commit, push, merge, or
release, and no CHG-031 work.

## Objective

Connect the already-reviewed CHG-029 Task execution foundation to normal
persistence-first Capture, in the same narrow way CHG-028 connected the
CHG-027 list executor. A validated `CapturePlan` whose actions are all
`CreateTask` auto-executes exactly once; the app shows a compact receipt with
one exact targeted batch Undo; Pendientes reflects durable Room state.

Target happy path:

```text
text or voice Capture
    -> persist Capture first
    -> interpret
    -> validated CapturePlan
    -> deterministic action-family eligibility
    -> if ALL actions are CreateTask:
         CapturePlanTaskExecutor.execute(plan)
    -> durable Tasks + one source-linked ledger batch
    -> compact receipt
    -> optional exact targeted batch Undo
    -> Pendientes reflects durable state
```

The existing list path remains:

```text
if ALL actions are AddListItem:
    existing CHG-028 CapturePlanListExecutor path (unchanged)
```

This Change orchestrates CHG-029; it does not reimplement Task persistence.

## Verified baseline

- Repository `edugtz/quick-aside`; branch `main`; `HEAD`, local `main`, and
  `origin/main` all resolved to
  `61c2c0b8aae6a5adab9306409cc5c6dc903a96da`; starting worktree clean.
- `CHG-027` (list execution foundation), `CHG-028` (list Capture/UI
  integration, receipt, exact batch Undo), and `CHG-029` (unwired Task
  execution foundation) are complete and integrated into `main`.
- Production `CaptureSubmission` saves through `CaptureWriter`, then
  interprets, then executes only validated all-`AddListItem` plans through an
  optional `CapturePlanListExecutor`. It has no Task executor parameter and no
  Task caller.
- `CaptureExecutionOutcome` is list-specific: `Executed` and `Rejected` wrap
  `CapturePlanListExecutionResult`; `Failed` wraps
  `CapturePlanListExecutionResult.Failed`.
- `RoomCapturePlanTaskExecutor` already provides the reviewed CHG-029
  boundary: one Task per action, exact title/TaskSpace/nullable dueDate,
  `completedAt = null`, strict inserts, one source-linked ledger batch, ordered
  mutations, atomic rollback, and exact targeted batch Undo. It has no
  production construction site or caller.
- `QuickAsideApp` shows the list receipt and performs list Undo with the exact
  CHG-027 ledger ID and ordered item IDs; unsupported/rejected/failed
  executions receive honest non-success copy and no Undo.
- `PendientesScreen` loads through `LaunchedEffect(taskStore)` only; there is
  no external refresh token for a global-Capture Task mutation.
- `QuickAsideApplication` exposes one app-scoped `RoomCapturePlanListExecutor`
  and injects it into `CaptureSubmission`; `MainActivity` forwards it to the
  app shell only for targeted receipt Undo.
- Room is version 7 with migrations through 6→7. No schema, DAO, dependency,
  or Task-domain change is expected.
- The canonical UX reference `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`
  and `docs/UX_UI_REFERENCE.md` were inspected during planning. The affected
  reference surface is **3. Guardado / Receipt** (saved state already
  committed, Undo immediately available, adapt size to complexity) and the
  **5. Pendientes** freshness expectation.

## Execution-family eligibility policy

Eligibility is computed once from the validated plan, before any executor is
invoked. Every policy below is a strict "all actions in one family" test:

| Plan shape | Behavior |
|---|---|
| A. all `AddListItem` | Existing CHG-028 list executor; unchanged |
| B. all `CreateTask` | CHG-029 Task executor, exactly once |
| C. mixed `AddListItem` + `CreateTask` | Execute nothing; `NotEligible` |
| D. `CreateTask` + any other action | Execute nothing; `NotEligible` |
| E. `AddListItem` + any other action | Execute nothing; `NotEligible` |
| F. `CreateNote` / `CreateStructuredLog` / `UndoLast` / any future or unsupported family | Execute nothing; `NotEligible` |

Never split a mixed plan, execute a supported subset, reorder actions, or
silently discard unsupported actions. This Change does not solve general
multi-family execution. `CapturePlanAction` currently has no Event or Reminder
variant; any future variant is ineligible by default because eligibility
requires every action to be exactly one known family.

## Persistence-first invariant

Capture persistence remains mandatory and strictly before interpretation,
list execution, and task execution:

- `CaptureWriter.save(capture)` completes before `CaptureInterpreter` runs.
- A writer failure returns `CaptureSubmissionResult.Failed` without
  interpretation or execution.
- Interpretation or execution failure/rejection never deletes or rolls back
  the already-durable Capture.
- `sourceCaptureId` is always the locally generated `CaptureId`:
  `ProviderCaptureInterpreter` builds the validator draft from
  `capture.id.value`, and `RoomCapturePlanTaskExecutor` requires the exact
  source Capture row before writes. No remote/provider component can create or
  substitute `sourceCaptureId`.
- `CancellationException` from the writer, interpreter, list executor, or task
  executor propagates to the caller. A Capture already written stays durable;
  an execution batch is rolled back by the executor's own Room transaction.

## Execution outcome contract

`CaptureSubmissionResult.Saved` continues to prove that the original Capture is
durable and carries the interpretation plus one typed execution outcome that
distinguishes:

- `NoValidPlan` — interpretation absent or not a validated plan;
- `NotEligible` — valid plan, no family eligible, zero executor calls;
- successful LIST execution with the exact CHG-027 result;
- successful TASK execution with the exact CHG-029 result;
- list rejection/failure;
- task rejection/failure.

The outcome model must remain compile-time typed with no `Any`, no unsafe
casts, no generic executor registry, and without weakening CHG-028 semantics.
The chosen family-tagged model and its rationale are in `PLAN.md`, and its
rejection variants are exact:

- `Rejected.ListItems` may contain only
  `CapturePlanListExecutionResult.UnsupportedAction`,
  `CapturePlanListExecutionResult.Rejected`, or
  `CapturePlanListExecutionResult.MissingSourceCapture`;
- `Rejected.Tasks` may contain only
  `CapturePlanTaskExecutionResult.UnsupportedAction`,
  `CapturePlanTaskExecutionResult.Rejected`,
  `CapturePlanTaskExecutionResult.RejectedPlan`, or
  `CapturePlanTaskExecutionResult.MissingSourceCapture`;
- neither `Rejected` wrapper may contain `Executed` or `Failed`;
- `Executed.ListItems`/`Executed.Tasks` contain only the matching family
  `Executed` result, and `Failed.ListItems`/`Failed.Tasks` contain only the
  matching family `Failed` result.

Focused JVM tests verify these invariants directly (allowed rejection variants
construct; `Executed`/`Failed` payloads are rejected).

Missing executor configuration for an eligible family is an honest
`Failed` outcome: the Capture remains durable, no execution occurs, and the UI
shows non-success copy.

## Receipt and targeted Undo

Successful Task execution uses the same lightweight interaction pattern as
CHG-028:

```text
Capture -> receipt -> optional Deshacer -> continue
```

- One Task: `Pendiente guardado` with `Deshacer`.
- Multiple Tasks: `<N> pendientes guardados` with `Deshacer`.
- Successful Undo: `Cambio deshecho`.
- Any Undo non-success: `No se pudo deshacer.` with no success claim.
- The receipt uses the existing shared Material snackbar host; no new route,
  modal, or editor is introduced.

Undo calls only:

```kotlin
CapturePlanTaskExecutor.undoExecution(
    actionLedgerEntryId = exact successful receipt ledger ID,
    expectedTaskIds = exact ordered Task IDs returned by execution,
)
```

The UI must not query a latest ledger entry, infer Task IDs, call manual
`ReversibleTaskActions` Undo, delete Tasks directly, or mutate DAOs. A
successful Undo removes the exact Task batch, marks the exact parent ledger
undone, keeps the original Capture, and refreshes visible Pendientes state. A
non-successful Undo shows generic failure feedback and reconciles visible Task
state from Room.

## Pendientes freshness

A shell-owned Task refresh token, analogous to the CHG-028 `listRefreshToken`,
is passed into `PendientesScreen` and consumed by its state-load boundary. It
is incremented after a successful Task execution and after any Task Undo
attempt so visible Pendientes state reflects Room. No reactive persistence
architecture, Flow, or WorkManager work is introduced.

## Text and voice parity

Text and voice both call the same `CaptureSubmission` instance and share the
same eligibility, execution, receipt, and Undo semantics. Voice requirements:

- one accepted final transcript;
- one persisted Capture;
- at most one interpreted execution;
- at most one Task batch;
- one receipt;
- no duplicate execution from Compose recomposition or callback replay;
- current capture-surface close behavior preserved.

No Task business logic is forked into `VoiceCaptureScreen`.

## UX invariants affected

This Change affects the canonical Invocar → Hablar/Escribir → Guardar →
Receipt → Continuar flow:

- preserve optional review; no mandatory transcript/interpretation gate;
- use the existing shared Material snackbar host and concise Spanish copy;
- keep four management destinations and the global Capture action;
- keep Undo immediately available after automatic mutation;
- keep the voice orb concentrated in capture state;
- do not add a permanent Capture destination.

## Scope and explicit exclusions

In scope: wiring the existing CHG-029 executor into `CaptureSubmission`, one
typed result-model evolution, app dependency wiring, Task receipt/Undo in the
app shell, a Pendientes refresh token, and their tests/evidence.

Explicitly excluded:

- Google OAuth, Google Tasks API, bidirectional Google Tasks sync,
  local/external ID mapping, sync outbox, retry/backoff, conflict resolution,
  and external-sync idempotency protocol;
- Calendar/Event execution, CreateNote execution, StructuredLog execution,
  reminders/scheduling, and UndoLast;
- mixed-family transactional execution, generic CapturePlan executor registry,
  and generic multi-action-family Undo framework;
- gateway, provider/model, QA1, Tailscale, and VPS changes;
- Room schema/migration changes unless current code proves one unavoidable;
- new dependencies unless current code proves one unavoidable;
- rewriting the CHG-027/CHG-028 list executor or the CHG-029 Task executor
  merely for symmetry;
- CHG-031 and future roadmap work.

Task creation does not create Google Tasks or sync metadata. The Pendientes
`Google Tasks aún no conectado` status remains truthful and unchanged.

## Acceptance evidence

Required evidence is focused JVM orchestration and result-model-invariant
coverage, a focused real-Room pipeline instrumentation class, focused
text/voice/Pendientes Compose coverage, directly affected
CHG-028/CHG-029/Capture/Pendientes regressions, full JVM, Android-test Kotlin
compile, debug assembly, lint, schema/config/dependency comparison, Git/scope
review, and the **required** focused real-environment acceptance:

- a controlled real-device TEXT Task capture;
- a coordinated real-human VOICE Task capture;
- exact durable Task / Action Ledger / Capture provenance;
- receipt;
- Pendientes visibility;
- exact targeted Undo;
- duplicate check;
- sanitized visual and privacy evidence.

If the private gateway or supported device is unavailable, the required
gate and the change status remain **PENDING**; pre-review readiness is
`NOT REVIEW-READY — REQUIRED REAL-ENVIRONMENT ACCEPTANCE PENDING`, and
independent implementation review must not begin while it is pending. Mocks,
emulator runs, and fake-executor tests do not substitute for this acceptance.
A required gate without observed evidence is never reported as PASS.

The validated `CapturePlan` is **not persisted** and cannot be reconstructed
from Room. Real acceptance therefore proves what the app and Room actually
retain: the exact locally persisted Capture, successful Task-family automatic
execution, exact Task rows and values, the exact `ActionLedgerEntry` linked to
`sourceCaptureId`, exact ordered `CREATE/task` mutations, zero
marker-correlated list mutation, Pendientes visibility, exact targeted Undo,
Task absence after Undo, Capture retention, the ledger marked undone, and
duplicate count = 1. Because CHG-030 executes Task mutation only when every
validated action is `CreateTask`, a successful Task-family execution
establishes that the Task eligibility gate was reached. Acceptance never
claims "the CapturePlan was verified via Room", and no raw provider response
logging is added to prove the plan.

The user retains product, commit, push, merge, release, and production
authority.
