# Change 028 — Capture List Auto-Execution + Receipt/Undo Integration — SPEC

- Governance: **HIGH-ASSURANCE**
- Status: **COMPLETED — MERGED INTO MAIN; ROUND 2 PASS_WITH_NOTES**
- Branch: `chg-028-capture-list-auto-execution`
- Verified base: `origin/main` at `6ede3d08f298a376cfdfd7749fc2d92a2eca3f5c`

## Post-merge provenance

- Implementation commit: `b8a14bf4a435b33870ee9bbf2127a2fd8f7b1d67`.
- Evidence remediation commit: `d8d5acdd9ae6982cb790054bdccaefdc0b1701be`.
- Evidence command correction commit / merged branch head:
  `44c4d3befd97ad37dadc8fcf93fb2dc7a5ab8232`.
- Round 1: **BLOCKED** due evidence provenance only.
- Round 2: **PASS_WITH_NOTES** — 0 BLOCKER / 0 MAJOR / 1 MINOR / 2 NOTE.
- Integrated main SHA: `44c4d3befd97ad37dadc8fcf93fb2dc7a5ab8232`.

## Objective

Connect the existing persistence-first `CaptureSubmission` pipeline to the
reviewed CHG-027 list executor. A successfully interpreted, validated
`CapturePlan` auto-executes only when every action is
`CapturePlanAction.AddListItem`. Text and voice share the same application
outcome and the same lightweight Material snackbar receipt with one exact
batch Undo action.

The complete happy path is:

```text
capture persists locally
    -> remote interpretation
    -> validated CapturePlan
    -> deterministic all-AddListItem eligibility
    -> one CapturePlanListExecutor.execute(plan)
    -> durable list mutation
    -> lightweight receipt + optional exact batch Undo
```

## Verified baseline

- Repository: `edugtz/quick-aside`.
- Starting `HEAD`, fetched live GitHub `main`, and `origin/main` all resolve to
  `6ede3d08f298a376cfdfd7749fc2d92a2eca3f5c`; the starting worktree was clean.
- Neither the branch nor this change package existed before CHG-028.
- Production `CaptureSubmission` saves through `CaptureWriter`, then invokes
  `CaptureInterpreter`, and returns `Saved(capture, interpretation)`.
- Production UI currently reports a successful plan as
  `Captura guardada · interpretación lista, sin aplicar`; no production code
  calls `CapturePlanListExecutor`.
- CHG-027 provides a narrow atomic executor for all-`AddListItem` plans. One
  successful execution returns one exact `ActionLedgerEntryId` and ordered
  created `ListItem` identities; `undoExecution` targets exactly those IDs.
- Manual Mandado/Compras creation uses the separate
  `ReversibleListItemActions` boundary and established Material snackbar /
  `Deshacer` conventions.
- Mandado and Compras load durable state on composition but do not currently
  have an external refresh token for a global Capture mutation.
- Room is version 7, schemas 1–7 are tracked, and migrations end at 6→7.
- The authorized OPPO CPH2791 / Android 16 / API 36 device was attached at
  preflight.
- `software-project-orchestrator` is not installed or callable. This package
  follows `AGENTS.md`, the explicit user brief, and the repository's existing
  HIGH-ASSURANCE precedent directly.

## Application outcome contract

`CaptureSubmissionResult.Saved` continues to prove that the original Capture
is durable and carries the interpretation result plus a typed execution
outcome. The execution outcome must distinguish:

- no execution attempt because interpretation did not yield a valid plan;
- a valid but CHG-028-ineligible plan;
- successful list execution with the exact CHG-027 result;
- executor rejection;
- executor failure.

Strings are UI copy only, never the application contract. Cancellation from
interpretation or execution propagates. Once persistence succeeds, later
non-success never deletes or rolls back the original Capture.

## Eligibility and execution

- Only `CaptureInterpretationResult.Success` is considered.
- Execute exactly once only when every action in the validated, non-empty plan
  is `AddListItem`.
- Pass the complete plan to `CapturePlanListExecutor.execute`; never invoke per
  action, split mixed plans, discard unsupported actions, or execute a subset.
- `CreateTask`, `CreateNote`, `CreateStructuredLog`, `UndoLast`, mixed plans,
  and all future action families are ineligible and cause zero execution
  mutations.
- Do not invent confidence scores or broader PLAN/CLARIFY policy.

## Receipt and Undo

- A one-item successful execution reports `Producto agregado`; multiple items
  report the accurate count, for example `3 elementos guardados`.
- One `Deshacer` action represents the entire logical execution.
- Undo calls `CapturePlanListExecutor.undoExecution` with the exact successful
  `ActionLedgerEntryId` and ordered `ListItem.id` values. The UI must not query
  latest ledger state, recompute IDs, call manual list Undo, or mutate DAOs.
- Successful Undo reports concise success, preserves the Capture, and refreshes
  visible list state. Any Undo non-success reports a generic error and reloads
  visible list state so no false success or stale optimistic state remains.
- Valid but ineligible plans retain honest “interpretación lista, sin aplicar”
  feedback. Rejected/failed execution never uses success copy or offers Undo.

## Text, voice, and list freshness

- Text and voice both call the same `CaptureSubmission` instance and deliver
  the same complete typed result to the app shell.
- Voice result delivery must occur once per accepted final transcript; Compose
  recomposition must not duplicate execution.
- Capture closes after successful voice persistence according to current
  navigation behavior.
- A small shell-owned list refresh token invalidates the currently visible
  Mandado/Compras loader after successful execution and successful or failed
  Undo reconciliation. Later navigation naturally reads Room.

## UX invariants affected

This change affects the canonical Captura and Guardado/Receipt flow:

- preserve `Invocar → Hablar/Escribir → Guardar → Receipt → Continuar`;
- no mandatory transcript/interpretation confirmation, modal, editor, new
  route, or chat-style result;
- use the existing shared Material snackbar host and concise Spanish copy;
- keep four management destinations and the global Capture action;
- keep the voice orb concentrated in capture state;
- keep Undo immediately available after automatic mutation.

## Persistence, security, and explicit exclusions

Capture persistence remains strictly before interpretation and execution.
Room remains v7 with no entity, table, index, foreign-key, schema, migration,
or destructive-fallback change. No dependency is added.

No generic executor/registry, Task/Note/StructuredLog/UndoLast execution,
retry/deferred execution, Google Tasks/Calendar/OAuth/sync, reminders,
WorkManager, gateway, QA1, Keystore, Tailscale, VPS, provider prompt/schema,
provider auth, transport, network-security, logging, navigation redesign, or
future Change work is in scope.

## Acceptance evidence

Required evidence is focused application/JVM orchestration coverage, a focused
real-Room pipeline instrumentation class, focused text/voice/visible-list
Compose coverage, directly affected regressions, full JVM, Android-test Kotlin
compile, debug assembly, lint, schema/config comparisons, diff inspection,
and focused real-device/visual acceptance where available. A required device
or private-gateway gate without observed evidence remains PENDING; it is never
reported as PASS.

The user retains commit, push, merge, release, and production authority.
