# Change 019 — Reversible List Item Create + Undo — SPEC

Governance: HIGH-ASSURANCE
Status: IMPLEMENTATION COMPLETE — REVIEW PENDING
Expected branch: chg-019-list-item-create-undo

## Objective

Make manual single-item creation in Mandado and Compras the first real
end-to-end use of the Action Ledger:

    user adds one product
    → one ListItem, one ledger entry, and one CREATE/list_item mutation commit
    → the list updates and shows a native Deshacer receipt
    → Undo deletes that exact item and marks that exact ledger entry undone

This is a thin slice. It does not add a general executor, CapturePlan, AI, or
global Undo.

## Scope

In scope:

- Narrow ReversibleListItemActions application boundary.
- Atomic manual create for Mandado and Compras.
- One ActionLedgerEntry with one CREATE/list_item mutation per create.
- Atomic target-checked Undo for that create.
- App wiring through the existing application/activity/Compose path.
- Native Material snackbar receipt with Deshacer.
- Focused JVM, Room, Compose, real-device, and visual evidence.

Out of scope:

- Completion/uncompletion or Mandado start/finish ledgering.
- Notes, Structured Logs, Capture, transcript correction, AI, Google,
  reminders, redo, history UI, arbitrary ledger replay, serialization,
  migrations, schema changes, and a general ActionExecutor.

## References and UX invariants

The affected reference is the existing Listas direction in
docs/UX_UI_REFERENCE.md and the canonical v3 PNG. Preserve exactly four bottom
destinations (Inicio, Pendientes, Listas, Memoria), the global Capture FAB, the
Mandado session model, the Compras continuous model, current rows/checkboxes,
history, keyboard behavior, one-handed use, and Android-native accessibility.

After create, the save is already committed. Update the visible list
immediately, clear the field as today, and show the shared SnackbarHostState
with a long native duration, message Producto agregado, and action Deshacer.
Dismiss/replace a stale receipt when a newer create arrives. On success, Undo
removes only the receipt item. On failure, reconcile visible state and show
only No se pudo deshacer. Do not add a modal, route, or confirmation gate.

## Application contract

The boundary is intentionally narrow:

    interface ReversibleListItemActions {
        suspend fun create(
            listDefinitionId: ListDefinitionId,
            text: String,
            listSessionId: ListSessionId? = null,
        ): CreateListItemActionResult

        suspend fun undoCreate(
            actionLedgerEntryId: ActionLedgerEntryId,
            expectedItemId: ListItemId,
        ): UndoListItemCreateResult
    }

Saved returns the exact ListItem and ActionLedgerEntryId needed by the receipt.
Create outcomes preserve AddListItemResult semantics: BlankText,
MissingDefinition, NoActiveSession, MissingSession, SessionNotActive,
SessionDefinitionMismatch, SessionNotAllowed, and Failed. Cancellation is
re-thrown.

Undo distinguishes Undone, MissingLedgerEntry, AlreadyUndone,
UnsupportedAction, UnsupportedLedgerShape, TargetMismatch, TargetMissing, and
Failed. Invalid/malformed ledger data is never a successful Undo.

## Create semantics and atomicity

Reuse the existing ListStore validation semantics:

- blank text is rejected before persistence;
- definitions must exist;
- Mandado needs a valid active or supplied matching session;
- missing, ended, or mismatched sessions are rejected;
- Compras rejects a supplied session and stores a null session;
- accepted text remains exact;
- unexpected persistence errors return Failed.

Use injectable item-ID, ledger-entry-ID, and clock providers. One logical clock
value is used for ListItem.createdAt and ActionLedgerEntry.occurredAt.

The ledger shape is fixed for this change:

    operation       CREATE
    targetType      list_item
    targetId        exact ListItemId.value
    payloadVersion  1
    beforeState     null
    afterState      null
    sourceCaptureId null
    undoneAt        null

Item, parent ledger entry, and child mutation insert in one Room
withWriteTransaction. A forced child failure leaves all three absent.

## Undo semantics and atomicity

Inside one write transaction, load the entry and ordered mutations; reject a
missing/already-undone entry; accept only exactly one CREATE/list_item mutation
with payloadVersion 1; require targetId to equal expectedItemId.value; require
the item to exist; delete exactly that item; mark exactly that entry undone; and
commit both changes. Preconditions are checked before mutations. Delete/update
races and unexpected failures roll back both sides and return Failed.

## Room and wiring constraints

Room remains version 5. Do not change entities, tables, indexes, foreign keys,
migrations, Action Ledger persisted shape, dependencies, or schemas 1–5. Only a
ListItemDao delete-by-ID method may be added.

QuickAsideApplication owns exactly one app-scoped implementation backed by the
existing database and passes it through MainActivity, QuickAsideApp, and
ManagementScreen to both list screens. Production create paths must not call
ListStore.addItem. ListStore still owns reads, completion, session operations,
and lower-level behavior outside this action path. Compose tests inject a fake
action boundary.

## Required evidence and stop conditions

Room tests cover both list behaviors, all existing validation outcomes, exact
ledger rows, create rollback, successful/malformed/mismatched/missing/second
Undo, unrelated-item preservation, Undo rollback, reopen durability, and
cancellation. Compose tests cover both screens, snackbar/Undo IDs, field/error
behavior, completion, navigation, four destinations, and Capture FAB. Capture
mandado-create-undo.png and compras-create-undo.png on CPH2791 / Android 16
when available.

Run the required focused tests, full debug JVM, assemble, lint, and git checks.
Blocked infrastructure evidence remains blocked, never PASS. Stop instead of
expanding scope if implementation needs Room 6, schema/migration changes,
general replay/executor, serialization, AI, Google, reminders, unrelated Undo,
or navigation redesign. The user retains commit/push/merge/release authority.
