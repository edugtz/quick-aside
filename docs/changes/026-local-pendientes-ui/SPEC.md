# Change 026 — Local Pendientes UI Foundation — SPEC

Governance: **STANDARD**
Status: **IMPLEMENTATION COMPLETE — REVIEW PENDING**
Expected branch: `chg-026-local-pendientes-ui`

## Objective

Replace the Pendientes placeholder with the smallest useful local-first Task
management surface. The screen reads through `TaskStore`, applies presentation
ordering in memory, and performs create/complete/reopen/targeted Undo through
the existing `ReversibleTaskActions` boundary. Runtime AI and Google Tasks
sync remain unavailable.

No new persistence, Room schema, domain semantics, or repository abstraction is
introduced.

## Proven baseline

- `origin/main` and `HEAD` were verified at
  `b4333a11e05403c1afe96890e0ecf73ec1f634ea`.
- The starting worktree was clean and this branch was created from the
  verified `origin/main`.
- Room remains version 7; the existing Task and reversible-action boundaries
  already support the required local mutations.
- `Task.completedAt == null` means pending; non-null means completed.
- `TaskStore.readAll()` is the read boundary. `TaskStore.save()` is not used
  by the UI for task creation or lifecycle changes.
- Runtime AI remains paused and Google Tasks synchronization is not
  implemented.

## Affected UX reference and invariants

This change materially affects **Screen 5 — Pendientes** in
`docs/UX_UI_REFERENCE.md` and the canonical visual-direction image
`docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`.

The implementation preserves these accepted invariants:

- Personal/Trabajo distinction;
- normal local Task pending/completed/reopened lifecycle;
- due dates are displayed when present;
- Google Tasks state is visible, honest, and not dominant;
- behavior and controls remain Android-native and accessible;
- the global capture FAB remains a global action and is not duplicated by a
  Task-specific FAB.

The obsolete `VoiceApp` pixels in the reference image are not copied into
product UI or code.

## Screen contract

Pendientes is a root navigation destination. It opens with Personal selected
and provides:

- a native two-option Personal/Trabajo selector;
- concise status text, `Google Tasks aún no conectado`;
- an inline `Agregar pendiente` field and add/send affordance;
- pending tasks followed by a completed section only when applicable;
- useful space-specific empty states;
- the existing global `Capturar` FAB supplied by `QuickAsideApp`.

The selected space only changes filtering and the destination of a new task;
switching spaces never mutates a Task.

## Loading and presentation

The screen distinguishes Loading, Loaded, and Failed states. It reads all
tasks through `TaskStore.readAll()` and filters by the selected `TaskSpace` in
memory. Ordinary read failures show a concise error and retry action;
`CancellationException` propagates.

Manual creation is available only from a successfully loaded task snapshot.
While Loading or Failed, the add action and keyboard Done action are disabled;
entered text is retained and Failed continues to expose Retry. A mutation may
update the visible snapshot only when the current state is already Loaded;
Loading and Failed cannot be promoted to a fabricated singleton Loaded state.

Pending tasks sort by due date first, due date ascending, then normalized title
and Task ID. Tasks without a due date follow dated tasks. Completed tasks sort
by `completedAt` descending, then normalized title and Task ID. Presentation
ordering is deterministic and is not persisted or moved into the DAO.

Existing due dates use a deterministic concise `es-MX`-friendly display such as
`17 sep 2026`. Manually created tasks always use `dueDate = null`; no date
picker or due-date editing is added.

## Local task actions

Manual creation calls exactly:

```kotlin
reversibleTaskActions.create(
    title = enteredText,
    space = selectedSpace,
    dueDate = null,
)
```

Nonblank title text is passed byte-for-byte. Whitespace-only input cannot be
submitted. A create is single-flight; failures retain the input. A successful
create updates the visible state, clears the input, and shows
`Pendiente agregado` with `Deshacer`. Undo calls
`undoCreate(actionLedgerEntryId, expectedTaskId)` with the exact saved receipt
IDs. Undo failure reloads persisted state and shows `No se pudo deshacer.`.

Pending rows call `complete(task.id)` and completed rows call `reopen(task.id)`.
Each Task ID is protected from duplicate concurrent lifecycle taps. Successful
changes render the returned persisted Task and show the corresponding receipt
with `Deshacer`. Completion Undo calls
`undoCompletionChange(actionLedgerEntryId, expectedTaskId)` with exact IDs and
reloads persisted state after success. Missing, no-op, and failed outcomes do
not invent success; they reload when appropriate and show concise feedback.

## Explicit exclusions

This change does not implement:

- Google OAuth, Google Tasks API, remote Task lists, sync engine, outbox/retry,
  idempotency/conflict policy, sync timestamps, or remote list IDs;
- task edit/delete/move, due-date creation/editing, date picker, reminders,
  notification actions, Calendar, or recurring tasks;
- CapturePlan execution, ActionExecutor, AI/runtime/provider/gateway work, or
  generic Undo;
- Room/entity/schema/migration changes, destructive fallback, or new
  dependencies;
- redesign of Inicio, Listas, or Memoria; task search; or CHG-027.

## Acceptance scenarios

1. Pendientes opens the real local management UI with Personal selected by
   default and the global capture action still available.
2. Switching to Trabajo filters only the visible tasks and routes new tasks to
   Trabajo without mutating existing data.
3. Pending/completed sections, due dates, empty states, and deterministic
   ordering render correctly.
4. Manual create preserves exact nonblank input, selected space, and
   `dueDate = null`; blank input is not submitted.
5. Create success updates the screen and exposes targeted Undo; create failure
   preserves input; Undo uses exact ledger/task IDs and reloads state.
6. Complete and reopen use exact Task IDs, update only through the reversible
   boundary, expose targeted Undo, and reload after completion Undo.
7. Missing, already-in-state, ordinary failure, and Undo failure outcomes do
   not claim a successful mutation.
8. Google Tasks status is explicitly not connected and does not imply a
   working integration.
9. An unavailable task snapshot cannot be promoted to
   `Loaded(listOf(newTask))`: creation is blocked during Loading/Failed, input
   is retained, Retry restores creation only after a successful read, and
   existing tasks remain visible after recovery.

## Required evidence and stop state

Required evidence is the focused Compose instrumentation test on the
authorized Oppo CPH2791 / Android 16 where available, directly affected shell
tests, CHG-024/025 Task regressions, full JVM/build/lint checks, Room/schema
comparison, diff/status inspection, and representative device screenshots.

## Verification reconciliation

The retained pre-repair connected result identified the exact failure as
`com.edu.quickaside.MandadoUiTest#undoFailureReloadsVisibleStateAndShowsConciseError`.
It threw `ComposeTimeoutException` from `MandadoUiTest.kt:198` while waiting
for the newly added product with a displayed-node assertion. The same exact
method passed on both the current CHG-026 checkout and the detached
`origin/main` baseline at `b4333a11e05403c1afe96890e0ecf73ec1f634ea`, so this
was a pre-existing test synchronization defect/flake, not a CHG-026 regression.

The smallest fix changed that pre-undo wait to the existing semantic-presence
helper, avoiding a brittle viewport/IME display assumption. No production
Mandado behavior was changed. The repaired method passed 3/3 focused
repetitions; `MandadoUiTest` passed 12/12, `MandadoHistoryUiTest` 7/7, and
`QuickAsideAppTest` 1/1. Before the targeted review patch below, the connected
suite passed 249/249 with 0 skipped and 0 failures on
`Pixel_9_Pro(AVD) - 15` (API 35).

## Review patch reconciliation

Independent review of reviewed remote head
`eb20cc18f1b832259e4a510b713327e524111f07` found exactly one remaining issue:
**MINOR** — manual create could call `withTask` while Pendientes was Loading
or Failed, fabricating `Loaded(listOf(newTask))` and discarding the unavailable
snapshot.

The targeted fix gates manual creation on `PendientesState.Loaded`, disables
the add and keyboard Done actions while Loading or Failed, retains entered
text, and makes `PendientesState.withTask` preserve Loading/Failed instead of
fabricating a partial Loaded state. The focused test now covers read failure,
blocked create with no `ReversibleTaskActions.create` call, input preservation,
Retry recovery, and retention of existing tasks.

Post-patch evidence: `PendientesUiTest` passed 14/14 on both
`Pixel_9_Pro(AVD) - 15` (API 35) and `CPH2791 - 16` (API 36);
`QuickAsideAppTest` passed 1/1 on both devices; `:app:testDebugUnitTest`
passed 120/120 with 0 skipped and 0 failures/errors; `assembleDebug` and
`lintDebug` passed; and no Room schema changes were present. The full
connected suite was not rerun because this was a targeted Pendientes-only
patch; the 249/249 result above is pre-patch evidence.

Independent review remains unchecked.

The builder must not commit, push, merge, release, select CHG-027, or assign an
independent engineering verdict. When implementation and obtainable evidence
are complete, the package and `docs/ACTIVE_WORK.md` must say:

`IMPLEMENTATION COMPLETE — REVIEW PENDING`

Independent review remains unchecked.
