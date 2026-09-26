# Change 030 — Capture Task Auto-Execution + Receipt/Undo Integration — PLAN

- Governance: **HIGH-ASSURANCE**
- Status: **PLANNED — IMPLEMENTATION PENDING**
- Branch: `chg-030-capture-task-auto-execution` (local only)
- Base: `61c2c0b8aae6a5adab9306409cc5c6dc903a96da`
- Package: `docs/changes/030-capture-task-auto-execution/`

This plan selects only the Capture/UI wiring of the existing CHG-029 Task
executor. Do not re-plan CHG-027/CHG-028/CHG-029, do not expand to Google
sync or another action family, and do not implement during planning.

## Preflight findings

- Canonical base verified: branch `main`, `HEAD`, local `main`, and
  `origin/main` all at `61c2c0b8aae6a5adab9306409cc5c6dc903a96da`; starting
  worktree clean.
- No `chg-030-capture-task-auto-execution` branch or CHG-030 package existed
  before this planning turn; CHG-031 does not exist.
- `CHG-028` is the application/UI integration precedent: persistence-first
  submission, list-only eligibility, typed outcome, shared snackbar receipt,
  exact targeted batch Undo, and a list refresh token.
- `CHG-029` is the Task execution/Undo correctness boundary:
  `CapturePlanTaskExecutor` plus `RoomCapturePlanTaskExecutor`, tested and
  merged, deliberately unwired from production Capture.
- Current production reality matches the brief: `CaptureSubmission` executes
  only all-`AddListItem` plans; `CaptureExecutionOutcome` is list-specific;
  `PendientesScreen` has no external refresh token; `QuickAsideApplication`
  exposes only the list executor.
- `CapturePlanTaskExecutor.Executed` returns Tasks in action order plus one
  exact `ActionLedgerEntryId`; `undoExecution` requires the exact ordered Task
  IDs. The orchestration can forward those identities without recomputation.
- Room v7 and the existing DAOs are sufficient. No migration, entity, DAO,
  dependency, manifest, gateway, or provider change is expected.
- `software-project-orchestrator` is not available in the current tool
  catalog. This plan follows `AGENTS.md`, the explicit HIGH-ASSURANCE brief,
  and the accepted CHG-027/CHG-028/CHG-029 repository precedent directly.
- Planning ran only planning-safe Git checks. No build, test, lint,
  instrumentation, or device command was run.

## Result-model evolution

`CaptureExecutionOutcome` must keep compile-time typing while letting the
shell distinguish list vs task success, rejection, and failure. The chosen
narrowest reasonable evolution is a family-tagged nested sealed hierarchy:

```kotlin
sealed interface CaptureExecutionOutcome {
    data object NoValidPlan : CaptureExecutionOutcome
    data object NotEligible : CaptureExecutionOutcome

    sealed interface Executed : CaptureExecutionOutcome {
        data class ListItems(
            val receipt: CapturePlanListExecutionResult.Executed,
        ) : Executed

        data class Tasks(
            val receipt: CapturePlanTaskExecutionResult.Executed,
        ) : Executed
    }

    sealed interface Rejected : CaptureExecutionOutcome {
        data class ListItems(
            val result: CapturePlanListExecutionResult,
        ) : Rejected {
            init {
                require(
                    result is CapturePlanListExecutionResult.UnsupportedAction ||
                        result is CapturePlanListExecutionResult.Rejected ||
                        result is CapturePlanListExecutionResult.MissingSourceCapture,
                ) { "Rejected.ListItems accepts only UnsupportedAction, Rejected, or MissingSourceCapture" }
            }
        }

        data class Tasks(
            val result: CapturePlanTaskExecutionResult,
        ) : Rejected {
            init {
                require(
                    result is CapturePlanTaskExecutionResult.UnsupportedAction ||
                        result is CapturePlanTaskExecutionResult.Rejected ||
                        result is CapturePlanTaskExecutionResult.RejectedPlan ||
                        result is CapturePlanTaskExecutionResult.MissingSourceCapture,
                ) { "Rejected.Tasks accepts only UnsupportedAction, Rejected, RejectedPlan, or MissingSourceCapture" }
            }
        }
    }

    sealed interface Failed : CaptureExecutionOutcome {
        data class ListItems(
            val result: CapturePlanListExecutionResult.Failed,
        ) : Failed

        data class Tasks(
            val result: CapturePlanTaskExecutionResult.Failed,
        ) : Failed
    }
}
```

### Exact result-model invariants

- `Executed.ListItems` contains only `CapturePlanListExecutionResult.Executed`.
- `Executed.Tasks` contains only `CapturePlanTaskExecutionResult.Executed`.
- `Rejected.ListItems` may contain only
  `CapturePlanListExecutionResult.UnsupportedAction`,
  `CapturePlanListExecutionResult.Rejected`, or
  `CapturePlanListExecutionResult.MissingSourceCapture`.
- `Rejected.Tasks` may contain only
  `CapturePlanTaskExecutionResult.UnsupportedAction`,
  `CapturePlanTaskExecutionResult.Rejected`,
  `CapturePlanTaskExecutionResult.RejectedPlan`, or
  `CapturePlanTaskExecutionResult.MissingSourceCapture`.
- `Rejected.ListItems` and `Rejected.Tasks` must not contain `Executed` or
  `Failed`; the `require` guards fail fast on a disallowed payload.
- `Failed.ListItems` contains only `CapturePlanListExecutionResult.Failed`;
  `Failed.Tasks` contains only `CapturePlanTaskExecutionResult.Failed`.
- Focused JVM tests must construct every allowed rejection variant and assert
  that `Executed` and `Failed` payloads are rejected for both `Rejected`
  wrappers.
- No `Any`, cast, common generic result wrapper, or executor registry is
  introduced.

Why this is the narrowest reasonable evolution:

- It reuses the existing top-level names (`Executed`, `Rejected`, `Failed`) and
  makes the action family explicit at the type level, so UI orchestration uses
  `is` smart casts only: no `Any`, no `as`, no generic executor parameter type.
- It changes only the list-specific payload shape; the CHG-027/CHG-029
  executor contracts, result types, and semantics are untouched.
- Future action families that become executable add one variant inside each
  family interface instead of a new parallel top-level name, without
  introducing a registry or universal execution framework.
- Existing CHG-028 assertions are updated mechanically
  (`Executed(...)` → `Executed.ListItems(...)`, etc.) without weakening any
  CHG-028 behavior or coverage.

Rejected alternatives:

- Additive top-level variants (`TaskExecuted`/`TaskRejected`/`TaskFailed`):
  smallest textual diff, but duplicates the family dimension in names, gives
  no family grouping in the shell `when`, and does not scale without name
  explosion.
- A single `Executed(receipt: <common type>)` or family enum: would require
  either a common supertype, `Any`, or casts, weakening the typed contract.
- Introducing a generic `ActionExecutor` registry: explicitly out of scope and
  banned by the brief.

## Orchestration design

1. Keep `CaptureSubmission.submit` / `submitVoice` → `submitInput` unchanged:
   blank check, local `Capture` construction, `writer.save(capture)` first.
2. Replace `executeEligibleListPlan` with `executeEligiblePlan`:
   - take the plan from `CaptureInterpretationResult.Success` or return
     `NoValidPlan`;
   - if `plan.actions` are all `AddListItem`, run the existing list mapping; if
     all `CreateTask`, run the new task mapping; otherwise return `NotEligible`
     **before calling any executor**;
   - compute eligibility once per submission; never split, subset, or reorder.
3. List mapping keeps current behavior and wraps results as
   `Executed.ListItems`, `Rejected.ListItems`, `Failed.ListItems`. The
   missing-list-executor branch (`Failed` with an `IllegalStateException`) is
   preserved.
4. Task mapping mirrors it exactly:
   - `taskExecutor ?: return Failed.Tasks(Failed(IllegalStateException(...)))`;
   - `executor.execute(plan)` in `try`/`catch`; `CancellationException`
     rethrows; other `Exception` becomes `CapturePlanTaskExecutionResult.Failed`;
   - `Executed` → `Executed.Tasks(result)`; `Failed` → `Failed.Tasks(result)`;
     `UnsupportedAction` / `Rejected` / `RejectedPlan` / `MissingSourceCapture`
     → `Rejected.Tasks(result)`.
5. Add the optional `taskExecutor: CapturePlanTaskExecutor? = null` constructor
   parameter to `CaptureSubmission`. Default null keeps all existing
   constructor call sites compiling and preserves the missing-executor honesty
   test.

## Dependency wiring

- Add one app-scoped `val capturePlanTaskExecutor: CapturePlanTaskExecutor by
  lazy { RoomCapturePlanTaskExecutor(database) }` to `QuickAsideApplication`.
- Inject it into the app-scoped `CaptureSubmission` as `taskExecutor`.
- Expose the same instance through `MainActivity` to `QuickAsideApp` for the
  targeted receipt Undo only. Do not instantiate executors per capture.
- `QuickAsideApp` gains an optional `capturePlanTaskExecutor:
  CapturePlanTaskExecutor? = null` parameter. Existing named-argument call
  sites keep compiling; the production shell receives the app-scoped instance.

## Receipt and Undo design

Extend the existing `onCaptureSaved` shell handler; do not add a new route or
a second snackbar owner:

- Refresh bookkeeping: `historyRefreshToken += 1` always; increment
  `listRefreshToken` only for `Executed.ListItems`; increment the new
  `taskRefreshToken` for `Executed.Tasks`.
- For `Executed.ListItems`, keep the existing CHG-028 list receipt and Undo
  flow byte-for-byte in behavior.
- For `Executed.Tasks`, dismiss the current snackbar, then show
  `Pendiente guardado` for one Task or `<N> pendientes guardados` for N Tasks,
  action label `Deshacer`, `SnackbarDuration.Long`.
- On `SnackbarResult.ActionPerformed`, call the injected
  `CapturePlanTaskExecutor.undoExecution` with
  `receipt.actionLedgerEntryId` and `receipt.tasks.map { it.id }`. Catch
  `CancellationException` and rethrow; catch other exceptions as a null result.
  Increment `taskRefreshToken` after the attempt regardless of outcome. Show
  `Cambio deshecho` only for `UndoCapturePlanTaskExecutionResult.Undone`;
  otherwise `No se pudo deshacer.`
- For every non-executed outcome, keep `savedCaptureMessage` honest: valid but
  ineligible → `Captura guardada · interpretación lista, sin aplicar`;
  rejected/failed → `Captura guardada · no se pudo aplicar la interpretación`.
  No Undo is offered.
- `savedCaptureMessage(result)` must handle both new `Executed` families with
  `error(...)` because real executions take the receipt branch.

## Pendientes freshness design

- Add `var taskRefreshToken by remember { mutableStateOf(0) }` to
  `QuickAsideApp`, passed through `ManagementScreen` into
  `PendientesScreen(refreshToken = taskRefreshToken)`.
- `PendientesScreen` gains `refreshToken: Int = 0` and changes its load
  boundary to `LaunchedEffect(taskStore, refreshToken) { loadState() }`,
  matching `MandadoScreen`/`ComprasScreen`.
- Token increments after successful Task execution and after every Task Undo
  attempt. This reconciles Undo while Pendientes is visible and defensively
  covers the post-capture return path. No Flow/reactive rewrite.

## Failure and cancellation handling

- Persistence failure → `Failed`; no interpretation, no execution.
- Interpretation non-success → `NoValidPlan`; Capture durable.
- Mixed/unsupported plan → `NotEligible`; zero executor calls.
- Missing executor → `Failed.<Family>` with `IllegalStateException`; honest UI.
- Executor rejection → `Rejected.<Family>`; honest UI, no Undo.
- Executor failure → `Failed.<Family>`; honest UI, no Undo.
- `CancellationException` from any suspend boundary propagates; the durable
  Capture remains and the executor's Room transaction rolls back.
- Undo non-success → no success claim, generic copy, Pendientes reload.
- No silent retry, no optimistic UI state, no direct DAO mutation from UI.

## Expected production files

| File | Why |
|---|---|
| `app/src/main/java/com/edu/quickaside/application/capture/CaptureSubmission.kt` | Family-tagged outcome model; `taskExecutor` injection; all-task eligibility; exact result mapping; cancellation/failure semantics |
| `app/src/main/java/com/edu/quickaside/QuickAsideApplication.kt` | One app-scoped `RoomCapturePlanTaskExecutor`; inject into `CaptureSubmission` |
| `app/src/main/java/com/edu/quickaside/MainActivity.kt` | Forward the app-scoped Task executor to the shell for targeted Undo |
| `app/src/main/java/com/edu/quickaside/ui/QuickAsideApp.kt` | Task receipt/Undo, honest copy for both families, `taskRefreshToken`, parameter plumbing |
| `app/src/main/java/com/edu/quickaside/ui/tasks/PendientesScreen.kt` | `refreshToken` parameter consumed by its `LaunchedEffect` |

Explicitly expected **not** to change: `CapturePlanListExecutor.kt`,
`RoomCapturePlanListExecutor.kt`, `CapturePlanTaskExecutor.kt`,
`RoomCapturePlanTaskExecutor.kt`, `VoiceCaptureScreen.kt`, Task/Action Ledger
DAOs/entities, `QuickAsideDatabase`, schemas/migrations, Gradle/dependencies,
manifest/network security, gateway/provider code. If focused evidence shows a
real integration defect that forces one of these files to change, stop and
report rather than broadening scope silently.

## Expected test files

New:

| File | Why |
|---|---|
| `app/src/test/java/com/edu/quickaside/application/capture/CaptureSubmissionTaskExecutionTest.kt` | Focused JVM Task orchestration: exactly-once all-task path, ordered receipt identity, mixed/unsupported zero execution, missing executor, rejection, failure, cancellation, persistence-first ordering, and result-model invariants (every allowed `Rejected` payload constructs; `Executed`/`Failed` payloads are rejected) |
| `app/src/androidTest/java/com/edu/quickaside/data/local/CaptureSubmissionTaskExecutionDatabaseTest.kt` | Real-Room pipeline: exact Capture provenance, batch order/fields, mixed zero mutation, exact targeted batch Undo with Capture retained, unrelated durable state preserved |
| `app/src/androidTest/java/com/edu/quickaside/CaptureTaskAutoExecutionUiTest.kt` | Compose: text single/batch receipt, exact ordered Undo forwarding, mixed/unsupported honesty, executor rejection/failure, Undo failure honesty, voice exactly-once, visible Pendientes refresh after execution and after Undo |

Mechanically updated for the result model (test-only refactor, no semantic
weakening):

| File | Why |
|---|---|
| `app/src/test/java/com/edu/quickaside/application/capture/CaptureSubmissionExecutionTest.kt` | `Executed`/`Rejected`/`Failed` references become the nested list-family variants; all CHG-028 scenarios preserved |
| `app/src/androidTest/java/com/edu/quickaside/data/local/CaptureSubmissionListExecutionDatabaseTest.kt` | Casts updated to the nested list-family types; assertions unchanged |

Regression suites to run unchanged where possible:

- CHG-028: `CaptureSubmissionExecutionTest`, `CaptureSubmissionTest`,
  `CaptureSubmissionRemoteIntegrationTest`, `CaptureSubmissionListExecutionDatabaseTest`,
  `CaptureListAutoExecutionUiTest`, `CaptureTextSubmissionTest`, `VoiceCaptureTest`.
- Shell/list: `QuickAsideAppTest`, `MandadoUiTest`, `ComprasUiTest`.
- Pendientes: `PendientesUiTest`.
- CHG-029: `CapturePlanTaskExecutorContractTest`,
  `CapturePlanTaskExecutorDatabaseTest`, `ReversibleTaskActionsDatabaseTest`,
  `ReversibleTaskCompletionActionsDatabaseTest`.

## Verification sequence

1. Focused JVM orchestration and contract tests.
2. Focused real-Room pipeline instrumentation test on the emulator.
3. Focused Compose UI tests plus directly affected existing connected
   regressions, preserving the production device for acceptance only.
4. `./gradlew :app:testDebugUnitTest` (full JVM).
5. `./gradlew :app:compileDebugAndroidTestKotlin`.
6. `./gradlew :app:assembleDebug`.
7. `./gradlew :app:lintDebug`.
8. Room v7 / schema / migration / dependency / manifest / network-security
   comparison against base.
9. Focused real-environment acceptance (controlled TEXT Task capture and
   coordinated real-human VOICE Task capture) is a **REQUIRED** gate. If the
   private Tailscale route, gateway, or supported paired device is
   unavailable, the gate and change status remain **PENDING**, pre-review
   readiness is
   `NOT REVIEW-READY — REQUIRED REAL-ENVIRONMENT ACCEPTANCE PENDING`, and
   independent implementation review must not begin. Mocks, emulator runs,
   and fake-executor tests do not substitute.
10. Privacy/log scan of the app-process sample.
11. `git diff --check`, `git status --short`, `git diff --stat`,
    `git diff --name-status`, complete diff review, and the pre-review
    readiness gate.

Stop at the first real validation error, identify its root cause, and apply no
more than two focused fixes for the same root blocker.

## Rollback and failure strategy

- All work stays on the local `chg-030-capture-task-auto-execution` branch;
  nothing is committed, pushed, merged, or released during implementation
  without explicit user authorization.
- There is no schema or data migration, so rollback is a branch revert with no
  data conversion. Tasks already created remain durable user data; the feature
  provides its own exact Undo, and no destructive cleanup is performed on
  rollback.
- If correctness requires schema/DAO/Task-domain changes, a new dependency, a
  mixed-family executor, or a gateway/provider change, stop and report instead
  of broadening scope.
- Failure copy is always honest: no success claim without an exact `Undone`
  result.

## Evidence strategy

- Repository-local evidence root:
  `docs/changes/030-capture-task-auto-execution/evidence/` (created during
  implementation; not created by planning).
- Every gate records: exact command, start/end, exit code, raw output,
  JUnit XML or instrumentation summary/verdict, device serial + AVD/API for
  emulator gates, `git rev-parse HEAD`/branch/status at run time, a SHA-256
  changed-source manifest including untracked sources, and a worktree/source
  fingerprint.
- Reports are copied immediately after each run; Gradle must never overwrite
  the only copy of connected-test output.
- No APK/AAB binaries and no DB/WAL/SHM dumps are retained. APK hashes and
  metadata are recorded instead.
- Failed/environmental attempts are preserved, never replaced.
- Acceptance evidence is sanitized: unique disposable markers only, no
  secrets or personal data, no raw provider request/response bodies.
- `QA.md` lists the intended gates; results are filled only when executed.

## Independent review gate

After implementation, deterministic gates, and the **required** real-device
TEXT/VOICE acceptance with observed evidence (a PENDING acceptance gate blocks
review), the change enters independent HIGH-ASSURANCE review of the
implementation commit. The reviewer verifies production correctness, exact
Task/ledger/Undo identity, list-path non-regression, evidence provenance,
scope, and absence of secrets/binaries. Findings are dispositioned in a later
round; the user retains the final commit/push/merge decision. This plan does
not assign any verdict.

## Stop conditions

Stop without broadening scope if the work requires: schema/migration or DAO
change; a new dependency; rewriting the CHG-027/CHG-028 list path or the
CHG-029 Task executor; mixed-family transactional execution; a generic
executor registry or generic Undo framework; Google/OAuth/Calendar/reminder
work; gateway/provider/QA1/Tailscale/VPS changes; or any CHG-031 work.
