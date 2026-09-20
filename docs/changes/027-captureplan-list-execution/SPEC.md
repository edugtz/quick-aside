# Change 027 — CapturePlan List Execution Foundation — SPEC

- Governance: **HIGH-ASSURANCE**
- Status: **ROUND-2 PASS_WITH_NOTES — DOCUMENTATION CLOSEOUT COMPLETE LOCALLY**
- Expected branch: `chg-027-captureplan-list-execution`
- Verified base: `origin/main` at `cb67494a7b57d0f7a939ec06396ccbc665edff7c`

## Objective

Add the narrow application/data boundary that executes one validated
`CapturePlan` when every action is `AddListItem`. One plan is one logical user
action: an N-item plan writes N ListItems, one Action Ledger parent, and N
ordered `CREATE/list_item` mutations, all atomically, with
`sourceCaptureId = plan.sourceCaptureId`.

This change also adds targeted Undo for that exact execution. It does not make
the normal capture flow execute plans. The runtime continues to stop at the
validated `CapturePlan`.

## Verified baseline

- Repository: `edugtz/quick-aside`.
- `git fetch origin main:refs/remotes/origin/main` completed. Both starting
`HEAD` and `origin/main` resolve to
`cb67494a7b57d0f7a939ec06396ccbc665edff7c`; the starting worktree was clean.
- Neither `chg-027-captureplan-list-execution` nor
  `docs/changes/027-captureplan-list-execution/` existed before this change.
- The branch was created from that verified `origin/main` without a commit.
- `CapturePlan` is a typed validated representation. The current validator
  accepts the list definition IDs `mandado` and `compras` and preserves action
  order and exact accepted text. Other current actions include CreateTask,
  CreateNote, CreateStructuredLog, and UndoLast.
- `QuickAsideDatabase` is Room version 7. The existing schema set is 1–7 and
  the registered migration chain ends at `MIGRATION_6_7`.
- Existing persistence APIs already provide strict ABORT ListItem insertion,
  exact ListItem lookup/deletion, Capture lookup, Action Ledger parent/child
  insertion, ordered mutation reads, and conditional mark-undone.
- `QuickAsideDatabase.validateListItemCreate` is the shared list-create
  semantic validator used by `RoomListStore` and
  `RoomReversibleListItemActions`. It preserves text, rejects blank text,
  resolves the active Mandado session when none is supplied, and requires
  Compras to have no session.
- Manual list creates remain a separate boundary and record
  `sourceCaptureId = null`.
- At the verified base, the actual capture path persisted the Capture, then
  interpreted it and returned a locally validated plan; neither
  `CaptureSubmission` nor the app/UI invoked an executor. CHG-027 adds the
  narrow executor but does not add that wiring.
- The requested `software-project-orchestrator` workflow is not installed or
  callable in the available skill catalog or repository. This change follows
  the repository's `AGENTS.md`, `docs/AI_WORKFLOW.md`, and the user's explicit
  HIGH-ASSURANCE workflow.
- At initial preflight, `adb devices -l` reported no attached device. At
  verification, the authorized OPPO CPH2791 / Android 16 / API 36 device was
  attached and both focused Room classes passed.

## Application boundary

Use a deliberately narrow contract equivalent to:

```kotlin
interface CapturePlanListExecutor {
    suspend fun execute(plan: CapturePlan): CapturePlanListExecutionResult

    suspend fun undoExecution(
        actionLedgerEntryId: ActionLedgerEntryId,
        expectedItemIds: List<ListItemId>,
    ): UndoCapturePlanListExecutionResult
}
```

Result types must make success, unsupported action, deterministic list
rejection, missing source Capture, targeted Undo rejection, and unexpected
failure explicit. Unsupported action and validation outcomes include a
zero-based action/mutation index where applicable. Cancellation always
propagates.

Do not add a generic `ActionExecutor`, replay framework, or application-wide
undo contract.

## Supported execution contract

- Accept plans only when every action is `CapturePlanAction.AddListItem` and
  each `listDefinitionId` is exactly `mandado` or `compras`.
- Reject the first unsupported action or unsupported list ID deterministically
  before any durable write. Never skip actions or execute only the supported
  subset.
- In the write transaction, verify that the exact
  `plan.sourceCaptureId.value` still exists before any write.
- Resolve and validate every list action in original plan order with the
  existing `validateListItemCreate` behavior. Resolve all actions before
  consuming IDs or the execution clock and before inserting any row. Return a
  deterministic action-indexed rejection on the first failure. Confirm the
  persisted built-in definition still has its accepted list behavior before
  allowing execution.
- Compras items have `listSessionId = null`. Mandado items use the current
  active Mandado session resolved by the existing validator; absent, ended,
  missing, or mismatched session state rejects the whole plan.
- Preserve accepted item text exactly: no trim, normalization, deduplication,
  reordering, or case folding.
- After all preconditions pass, write the entire execution in one Room write
  transaction. Use one logical timestamp for all item `createdAt` values and
  the ledger `occurredAt`. Generate one stable ListItem ID per action and one
  ActionLedgerEntry ID for the plan. Existing ABORT insert semantics remain in
  force.
- Each item preserves the exact ID, definition ID, text, resolved session or
  null, initial `isCompleted = false`, and common timestamp.
- Persist exactly one Action Ledger entry with the exact source Capture ID and
  `undoneAt = null`. Its N mutations follow CapturePlan order and have exactly:
  `CREATE`, target type `list_item`, exact item ID, payload version 1, and null
  before/after state.
- Any unexpected write failure rolls back every item and all ledger rows.
  A later item-ID collision must not overwrite an existing ListItem or leave
  earlier plan items behind.

## Targeted batch Undo contract

`undoExecution` is specific to a successful CHG-027 execution. It is not
UndoLast, generic replay, or arbitrary Action Ledger Undo.

In one write transaction, before the first delete:

1. Load the exact entry; reject missing or already-undone entries.
2. Require nonblank `sourceCaptureId` provenance. Manual list entries with
   `sourceCaptureId = null` are outside this targeted executor.
3. Require a non-empty mutation list with contiguous positions `0..N-1`,
   where every mutation is `CREATE/list_item`, payload version 1, with null
   before/after state.
4. Reject blank or duplicate target IDs as an unsupported ledger shape.
5. Require target IDs to match the supplied `expectedItemIds` exactly in
   count, value, and order.
6. Verify every exact target ListItem currently exists.

Only after every precondition passes, delete each exact ListItem and require
each delete to affect one row. Mark exactly the ledger entry undone and require
one row to change. Any delete or mark failure rolls back all deletes. Invalid
shape, target mismatch, missing target, or a second Undo leaves all items and
the active ledger state unchanged. Preserve unrelated items and ledger rows.

## Persistence and scope constraints

- Room remains version 7. No entities, tables, indexes, foreign keys,
  migrations, tracked schemas, destructive fallback, or dependencies change.
- Existing DAOs appear to provide all required operations; add a DAO operation
  only if implementation evidence proves the exact operation is missing.
- Do not change the meaning or production routing of
  `ReversibleListItemActions` / `RoomReversibleListItemActions`.
- Do not wire execution into `CaptureSubmission`,
  `ProviderCaptureInterpreter`, `QuickAsideApplication`, capture UI, receipts,
  navigation, or snackbar handling. Do not add an unused app-scoped property.
- No UI is changed; screenshot/visual QA is not applicable.

## Explicit non-goals

No CreateTask/CreateNote/CreateStructuredLog/UndoLast execution; generic
executor, replay, Undo, or redo; automatic CapturePlan execution; UI receipts,
capture-flow wiring, retry/deferred execution; Google Tasks/Calendar, OAuth,
sync/outbox/conflict handling; reminders/notifications; fallback models,
gateway/server/VPS/Tailscale or QA1 changes; schema migration/version bump,
new tables, new dependencies, serialization, broad Action Ledger redesign,
manual-list redesign, CHG-028, or future roadmap implementation.

## Required deterministic evidence

Add focused tests proving:

- one Compras action writes one exact item, one parent, one mutation, and the
  exact source Capture ID;
- N Compras actions write N exact items, one parent, N ordered mutations, and
  one common timestamp;
- mixed Mandado/Compras actions retain order, use the active Mandado session,
  and use null for Compras;
- unsupported mixed action families and unsupported list IDs write nothing;
- absent active Mandado session and missing source Capture write nothing and
  return deterministic indexed/non-success outcomes;
- a later ListItem ID collision, parent insert failure, child insert failure,
  and cancellation roll back the complete execution;
- successful batch Undo deletes only exact targets, marks only its parent,
  preserves unrelated rows, and survives close/reopen;
- ID mismatch/order mismatch, missing targets, malformed operation/type/
  version/payload/ID/shape, and second Undo do not mutate state;
- delete failure after an earlier delete and mark-undone failure roll back;
- existing manual ReversibleListItemActions behavior remains valid.

Required verification also includes focused JVM tests, focused Room tests on an
authorized real Android device when available, the existing manual Room
regression, full JVM tests, Android test Kotlin compilation, assemble, lint,
Room/schema/migration comparison, and final Git scope checks. A missing device
leaves the Room/device gate PENDING and prevents a PASS claim.

## Stop conditions

Stop and report instead of expanding scope if complete-plan atomicity requires
a generic executor, capture/UI auto-wiring, another action family, generic
UndoLast/replay, Room migration or schema work, a dependency, Google/reminder/
gateway changes, broad ledger redesign, serialization, redesign of manual
list actions, schema history changes, or behavior that cannot be guaranteed
atomic.

The user retains product, commit, push, merge, release, and production
authority. The implementation commit is
`b9ba067ff442259225127645c8a4c04eeb65dfc6`; the evidence/remediation commit
and Round-2 reviewed HEAD is
`eddbfcd505a89ff7f7f0d4a37d511ad92abdcd24`. Round 1 returned **BLOCKED** and
Round 2 returned **PASS_WITH_NOTES**. The Round-1 evidence BLOCKER is resolved;
no production correctness finding remains. This local closeout corrects
documentation/provenance records only and leaves the implementation scope and
product contract unchanged. The next gate is user authorization to
commit/push this documentation-only closeout, followed by user-authorized
merge into `main`.
