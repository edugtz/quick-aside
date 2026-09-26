# CHG-030 QA and Evidence Contract

- Governance: **HIGH-ASSURANCE**
- Status: **PLANNED — IMPLEMENTATION PENDING**
- Gate results: **NONE YET — ALL PENDING**
- Branch: `chg-030-capture-task-auto-execution` (local only)
- Base: `61c2c0b8aae6a5adab9306409cc5c6dc903a96da`
- Evidence root: `docs/changes/030-capture-task-auto-execution/evidence/`
  (to be created during implementation)

This file is the verification and evidence contract. It contains no fabricated
results and marks unexecuted gates PENDING. Planning ran only `git diff
--check`, `git status --short`, `git diff --stat`, and `git diff --name-status`
on the documentation-only planning change set; those planning-scope checks are
reported in the planning report and are not a substitute for the
implementation gates below.

## Provenance rule

Every gate gets a durable, inspectable artifact under the evidence root
immediately after it finishes. Do not rely on terminal scrollback or a later
Gradle run. For every run, record:

- exact command, start/end UTC, exit code, and device/emulator identity when
  applicable (serial and AVD/API);
- `git rev-parse HEAD`, branch, and `git status --short` at run time;
- a SHA-256 manifest for every changed production, test, build/config, and
  schema file in the tested worktree, including untracked source files;
- the generated JUnit XML or instrumentation raw stream plus a
  machine-readable verdict where JUnit XML is not produced;
- a SHA-256 of the tracked worktree diff (`git diff --binary HEAD`) combined
  with the changed-source manifest as the worktree/source fingerprint.

Gradle overwrites `app/build/reports` and `app/build/test-results`; copy the
reports before the next Gradle run. Never let a later run overwrite the only
copy of connected-test output. Preserve failed and environmental attempts;
never replace them with a passing rerun.

## Deterministic gates

| # | Gate | Intended command (where knowable) | Expected evidence artifact | Pass condition |
|---|---|---|---|---|
| D1 | Focused JVM orchestration/contract | `./gradlew :app:testDebugUnitTest --tests com.edu.quickaside.application.capture.CaptureSubmissionTaskExecutionTest --tests com.edu.quickaside.application.capture.CaptureSubmissionExecutionTest --tests com.edu.quickaside.application.capture.CaptureSubmissionTest --tests com.edu.quickaside.application.capture.CapturePlanTaskExecutorContractTest` | `evidence/jvm/focused/` with raw output, exit code, copied JUnit XML + summary, run record, source manifests, start/end provenance | Command exits 0; all focused tests pass; focused class proves the contract coverage below |
| D2 | Focused real-Room pipeline | `./gradlew :app:assembleDebug` and `:app:assembleDebugAndroidTest`, install both on the emulator, then run `com.edu.quickaside.data.local.CaptureSubmissionTaskExecutionDatabaseTest` with explicit device targeting (`adb -s <emulator-serial> shell am instrument -w -r -e class com.edu.quickaside.data.local.CaptureSubmissionTaskExecutionDatabaseTest com.edu.quickaside.test/androidx.test.runner.AndroidJUnitRunner`, or a connected Gradle run constrained and verified to that serial) | `evidence/device/room/` with APK SHA-256/metadata (no APK binaries), install records, raw instrumentation stream or JUnit XML, `test-verdict.json`, run record, provenance | Zero failures/errors; every listed Room scenario executed and passed |
| D3 | Focused Compose UI | Same install route as D2, running `com.edu.quickaside.CaptureTaskAutoExecutionUiTest`, preferably on the emulator so the production device stays reserved for acceptance | `evidence/device/compose/` with raw or JUnit result, verdict, run record, provenance | Zero failures/errors; every listed UI scenario executed and passed |
| D4 | Existing CHG-028/CHG-029/Capture/Voice/Pendientes regressions | Run at minimum `CaptureSubmissionExecutionTest`, `CaptureSubmissionTest`, `CaptureSubmissionRemoteIntegrationTest`, `CaptureSubmissionListExecutionDatabaseTest`, `CaptureListAutoExecutionUiTest`, `CaptureTextSubmissionTest`, `VoiceCaptureTest`, `QuickAsideAppTest`, `MandadoUiTest`, `ComprasUiTest`, `PendientesUiTest`, `ReversibleTaskActionsDatabaseTest`, `ReversibleTaskCompletionActionsDatabaseTest`, `CapturePlanTaskExecutorContractTest`, `CapturePlanTaskExecutorDatabaseTest` | `evidence/regressions/` (JVM) and `evidence/device/regressions/` (instrumentation) with counts, output, run records, provenance | Zero failures/errors; the CHG-028 list path and CHG-029 Task foundation pass unchanged |
| D5 | Full JVM suite | `./gradlew :app:testDebugUnitTest` | `evidence/jvm/full/` with raw output, all JUnit XML, summary, run record, provenance | Command exits 0; zero failures/errors/skips |
| D6 | Android-test Kotlin compile | `./gradlew :app:compileDebugAndroidTestKotlin` | `evidence/build-results/compileDebugAndroidTestKotlin/` with output, exit code, run record, provenance | Command exits 0 |
| D7 | Debug assembly | `./gradlew :app:assembleDebug` | `evidence/build-results/assembleDebug/` with output, APK SHA-256/metadata, run record, provenance; no APK binary retained | Command exits 0 |
| D8 | Lint | `./gradlew :app:lintDebug` | `evidence/lint/` with raw output, HTML/SARIF, run record, provenance, finding summary | Command exits 0; no new finding in CHG-030 files (pre-existing findings documented) |
| D9 | Schema/config/dependency comparison | Compare Room `version`, migrations, `app/schemas/*/7.json`, DAOs, `gradle/libs.versions.toml`, `build.gradle.kts`, manifest, and network-security config against base | `evidence/scope/schema-config-dependency-comparison.txt` plus `...-run-record.json` | Room remains v7; schemas/migrations/dependencies/manifest/network config unchanged; no unexpected file changes |
| D10 | Git/scope review | `git diff --check`, `git status --short`, `git diff --stat`, `git diff --name-status`, complete diff inspection, untracked inventory | `evidence/scope/git-scope-review.txt` | Clean whitespace; changed set matches the planned files only; no secrets/binaries/DB dumps/future scope |
| D11 | Pre-review readiness | Repository readiness checklist covering branch/base, source sets, executed gates, repository-local evidence, source/device provenance, **completed required real-environment acceptance (A1/A2) with observed evidence**, no prose-only gate, no secrets, no binaries, schema/dependency scope, ACTIVE_WORK consistency, CHG-031 absent | `evidence/scope/pre-review-readiness.json` and `...txt` | `READY FOR INDEPENDENT REVIEW` only when every required gate including A1/A2 has observed evidence. If the required real-environment acceptance is unavailable, `NOT REVIEW-READY — REQUIRED REAL-ENVIRONMENT ACCEPTANCE PENDING` and no independent implementation review. Other gaps: `NOT REVIEW-READY — EVIDENCE CLOSEOUT REQUIRED` |

## Required contract coverage

Focused JVM orchestration (D1):

- all-list path is unchanged and still maps to the exact CHG-027 receipt;
- all-task path executes exactly once with the complete plan, in order
  persist → interpret → execute;
- ordered multi-Task receipt preserves the exact ledger ID and Task-ID order;
- mixed `AddListItem` + `CreateTask` executes nothing (neither executor
  called);
- unsupported family (`CreateNote`, `CreateStructuredLog`, `UndoLast`, other)
  executes nothing;
- missing Task executor yields an honest `Failed.Tasks` outcome while the
  Capture remains persisted;
- Task executor rejection yields `Rejected.Tasks`;
- Task executor failure yields `Failed.Tasks`;
- result-model invariants: every allowed `Rejected.ListItems` payload
  (`UnsupportedAction`, `Rejected`, `MissingSourceCapture`) and every allowed
  `Rejected.Tasks` payload (`UnsupportedAction`, `Rejected`, `RejectedPlan`,
  `MissingSourceCapture`) constructs successfully; `Executed` and `Failed`
  payloads are rejected for both wrappers;
- `Executed.ListItems`/`Executed.Tasks` and `Failed.ListItems`/`Failed.Tasks`
  carry only their exact family payload type;
- `CancellationException` from the Task executor propagates after persistence;
- Capture persistence is proven to precede Task execution;
- `submit` and `submitVoice` both route through the same Task path.

Real-Room pipeline (D2):

- one Task execution creates the exact Task with exact title/TaskSpace,
  nullable dueDate honored, `completedAt == null`, and one ledger whose
  `sourceCaptureId` equals the exact Capture row;
- batch execution preserves action order and per-action fields, including
  duplicate titles and shared/null dates;
- mixed plan creates zero Tasks and zero ledger rows while the Capture
  remains;
- exact targeted batch Undo deletes only the returned Task IDs, marks only the
  exact ledger undone, retains the Capture, and leaves unrelated durable state
  (pre-existing Tasks, ledger entries, list items) untouched;
- a second Undo cannot falsely succeed.

Compose UI (D3):

- text single Task shows `Pendiente guardado` with `Deshacer`;
- text batch shows `<N> pendientes guardados` and forwards exactly one Undo
  call with the exact ledger ID and exact ordered Task IDs;
- mixed/unsupported plan shows `Captura guardada · interpretación lista, sin
  aplicar` with no `Deshacer` and zero executor calls;
- Task executor rejection/failure shows
  `Captura guardada · no se pudo aplicar la interpretación` with no `Deshacer`;
- Undo failure shows `No se pudo deshacer.` and never `Cambio deshecho`;
- voice uses the same path, executes once despite repeated final transcripts,
  and closes the capture surface as today;
- visible Pendientes refreshes after global-Capture Task execution;
- visible Pendientes reconciles after Undo (the undone Task disappears).

## Deterministic acceptance behavior

Text (high-confidence Task capture):

- persists exactly one Capture;
- is driven by a controlled all-`CreateTask` validated plan (the deterministic
  suite injects the plan; the plan itself is never persisted or claimed as
  Room-verified);
- creates the exact Task(s) with correct TaskSpace, dueDate/null dueDate, and
  `completedAt == null`;
- creates exactly one ledger linked to the exact Capture;
- shows an accurate receipt;
- appears in Pendientes;
- `Deshacer` removes the exact Task(s);
- the Capture remains after Undo and the ledger becomes undone;
- no duplicate mutation.

Voice (real accepted final transcript):

- exactly one VOICE Capture;
- exactly one Task execution;
- correct Task values;
- receipt visible;
- Pendientes reflects the Task;
- exact Undo works; Capture remains; no duplicate execution.

Mixed plan (valid `AddListItem` + `CreateTask`):

- Capture kept;
- zero executed list items;
- zero executed Tasks;
- zero execution ledger/mutations;
- honest "interpretation ready, not applied" feedback with no Undo.

## Real-environment acceptance gates

A1, A2, and A3 are **REQUIRED HIGH-ASSURANCE gates** because CHG-030 enables
production user-visible automatic Task mutation. They require the actual
private interpretation path. If the private Tailscale route, gateway, or
supported paired device is unavailable, the gate and the change status remain
**PENDING**, the pre-review readiness result is
`NOT REVIEW-READY — REQUIRED REAL-ENVIRONMENT ACCEPTANCE PENDING`, and
independent implementation review must not begin. Mocks, emulator runs, and
fake-executor tests do not substitute for this acceptance.

The validated `CapturePlan` is **not persisted** and cannot be reconstructed
from Room. Acceptance therefore proves what the app and Room actually retain,
not the plan:

- the exact locally persisted Capture;
- successful Task-family automatic execution;
- exact Task rows and values;
- the exact `ActionLedgerEntry` linked to `sourceCaptureId`;
- exact ordered `CREATE/task` mutations;
- zero marker-correlated list mutation;
- Pendientes visibility;
- exact targeted Undo;
- Task absence after Undo;
- Capture retained;
- ledger marked undone;
- duplicate count = 1.

Because CHG-030's deterministic eligibility contract executes Task mutation
**only** when every validated action is `CreateTask`, a successful Task-family
execution establishes that the Task-family eligibility gate was reached. Do
not claim "the CapturePlan was verified via Room". Do not add raw provider
response logging to prove the plan; privacy and logging requirements stay
unchanged.

| # | Gate | Expected evidence | Pass condition |
|---|---|---|---|
| A1 | TEXT Task acceptance | Sanitized screenshots of receipt and `Deshacer`; read-only marker-scoped Room excerpt for the exact Capture, Task row(s), source-linked ledger, and ordered mutations; exact command/device/timestamp record | One locally persisted Capture; successful Task-family automatic execution; exact Task row(s) and values; one exact source-linked ledger with exact ordered `CREATE/task` mutations; zero marker-correlated list mutation; receipt; Pendientes visibility; exact Undo; Task absence; Capture retained; ledger undone; duplicate count = 1 |
| A2 | Coordinated real-human VOICE Task acceptance | Same as A1 for one real accepted final transcript, with device/human-run timestamp record | Exactly one VOICE Capture; successful Task-family automatic execution; exactly one execution; exact Task row(s) and values; one exact source-linked ledger with exact ordered `CREATE/task` mutations; zero marker-correlated list mutation; receipt; Pendientes visibility; exact Undo; Capture retained; no duplicate execution |
| A3 | Privacy/log sample | App-process logcat sample plus scan-summary with exact regex categories and counts | Zero raw capture/transcript marker matches; zero auth/bearer/signature/HMAC/nonce/pairing-code/provider-key/request-body/prompt matches |

Acceptance markers must be unique and disposable (for example
`QA030-TEXT-<date>` / `QA030-VOICE-<date>` in the task title) and must not
contain secrets or personal data. If a real provider utterance does not reach
Task automatic execution (non-eligible, rejected, failed, or provider
failure), preserve and record that attempt as an observation, do not treat it
as acceptance, retry with a clarified disposable utterance, and pass only when
the Task-family automatic execution path is actually exercised with observed
durable effects.

## Evidence provenance requirements

- Repository-local evidence root only; `/tmp`-only evidence does not count.
- Per-gate exact command, exit code, raw output, and JUnit XML/summary or
  instrumentation verdict.
- Device serial plus AVD/API for every emulator gate; device model/OS
  for every production-device gate.
- Source manifest and worktree/source fingerprint per gate.
- APK hashes/metadata only; no APK/AAB binaries in the repository.
- No Room DB/WAL/SHM dumps; acceptance Room excerpts are sanitized,
  marker-scoped, and read-only.
- Failed/environmental attempts preserved.
- Real acceptance evidence must prove only what the app and Room actually
  retain; the validated `CapturePlan` is not persisted and is never claimed as
  Room-verified.
- Prefer an emulator for routine instrumentation; reserve the supported
  production device for the acceptance gate.
- Evidence must be independently inspectable from GitHub; prose-only claims
  are not evidence.

## Scope and stop rules

The final comparison must show: Room remains v7; no schema/migration change;
no new dependency; no gateway/provider/QA1/Tailscale/VPS change; no Event,
Note, StructuredLog, UndoLast, reminder, Google Tasks/OAuth/Calendar, sync,
mixed-family execution, generic registry, or CHG-031 work. Production changes
are limited to the planned files in `PLAN.md`.

Follow `AGENTS.md`: identify the first root error before a build fix, stop
after two failed attempts on the same root error, do not run destructive
commands, and do not commit or push without user authorization.
