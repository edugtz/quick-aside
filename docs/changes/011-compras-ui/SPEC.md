# Change 011 — Compras UI — SPEC

Governance: **STANDARD**  
Status: **IN PROGRESS**

## Objective

Expose the existing continuous `Compras` list through `Listas` so the user can
open it, see durable items, add products, check or uncheck products, leave, and
return later to the same list.

`Compras` is always available and never owns a `ListSession`. It has no start,
finish, or session-history flow.

## References and affected UX contract

This change was reviewed against:

- `docs/PROJECT_SPEC.md` list and navigation sections;
- `docs/ARCHITECTURE.md` list boundary and persistence rules;
- `docs/UX_UI_REFERENCE.md` and the canonical v3 visual reference;
- the Change 009 list persistence contract;
- the Change 010 Listas/Mandado surface and navigation shell.

Affected accepted invariants:

- top-level destinations remain exactly `Inicio`, `Pendientes`, `Listas`, and
  `Memoria`;
- `Capture` remains an action, not a destination;
- management is explicit, structured, calm, native Android/Material, and
  accessible;
- durable list data is retained and survives app/database reopen;
- checkboxes are fast and obvious, and checked rows remain visible;
- Compose uses the application `ListStore` boundary, never Room DAOs.

## In scope

- An interactive `Compras` entry under the existing `Listas` root.
- A focused nested `Compras` screen with title/context, add-item input, current
  items, checkbox state, empty state, and native back navigation.
- Loading with `ListStore.readCurrentItems(COMPRAS.id)`.
- Exact non-blank item insertion through `ListStore.addItem` with
  `listSessionId = null`.
- Immediate visible insertion and input clearing on `Saved`.
- Safe Spanish feedback while retaining input on add failures.
- Completion changes through `ListStore.setItemCompleted`, replacing only the
  matching visible item on success and retaining state on failure.
- Duplicate mutation prevention and cancellation propagation.
- Deterministic UI tests, a real named-database Room integration test, and
  representative visual evidence.

## Out of scope

- Any Room schema, migration, entity, DAO, dependency, or built-in-ID change.
- Starting, finishing, or displaying sessions for Compras.
- Compras history, delete, clear-completed, archive, filtering, editing,
  quantities, prices, stores, categories, reorder, or custom lists.
- Mandado lifecycle changes, Mandado history UI, AI, Capture routing, Google
  APIs, reminders, sync, Action Ledger/Undo, or a navigation/state framework.
- A fifth bottom-navigation destination.

## Product contract

### Listas root

`Listas` keeps the Change 010 calm visual hierarchy. `Mandado` remains
interactive. `Compras` becomes an interactive entry and no longer says it is
available later.

### Compras surface

The nested surface is titled `Compras` and offers a labeled add field/action,
the current continuous items, a clear valid-empty state, and a back action
labeled `Volver a Listas`. It never displays session terminology or start/
finish controls.

### Load

Opening the surface reads current items for `BuiltInListDefinitions.COMPRAS.id`.
An empty list is a valid loaded state, not a missing list. Load failures show
`No se pudieron cargar tus compras.` and do not expose internal details.

### Add

Blank or whitespace-only text cannot be submitted. Any valid text reaches the
store unchanged, including surrounding whitespace. The call is:

```kotlin
listStore.addItem(
    listDefinitionId = BuiltInListDefinitions.COMPRAS.id,
    text = enteredText,
    listSessionId = null,
)
```

On `Saved`, the item is visible immediately and the field clears. On every
current non-success result (`BlankText`, `MissingDefinition`,
`NoActiveSession`, `MissingSession`, `SessionNotActive`,
`SessionDefinitionMismatch`, `SessionNotAllowed`, and `Failed`), the input is
retained and the user sees `No se pudo agregar el producto.`.

### Completion

Each checkbox calls `setItemCompleted(item.id, checked)`. `Updated` replaces
only the matching visible item. `Missing` and `Failed` retain the prior visible
state and show `No se pudo actualizar el producto.`. Completed rows remain
visible with a checkbox and supporting visual treatment such as line-through.

### Navigation

The nested route is the minimal `Root` / `Mandado` / `Compras` extension of the
existing local route state. Android Back and the top-bar back action return to
the Listas root. Switching any bottom-navigation destination resets the nested
route to `Root`.

## Persistence contract

- `QuickAsideDatabase` remains version 3.
- Schemas `1.json`, `2.json`, and `3.json` remain unchanged; no `4.json` and no
  migration are added.
- Compras items persist with `listSessionId == null`.
- Opening Compras does not call `startSession` and never creates a session.
- Close/reopen returns the same IDs, text, order, and completed state.
- A dedicated test database is used; `quick_aside.db` is never deleted or used
  by integration tests.

## Acceptance scenarios

1. Listas exposes interactive Compras and interactive Mandado with four bottom
   destinations.
2. Opening Compras loads the continuous list and does not call or create a
   session.
3. Empty Compras renders a valid empty state.
4. Exact non-blank text, including surrounding whitespace, reaches the store;
   blank text cannot submit.
5. Saved items appear immediately and clear the field; add failures retain it
   and show concise Spanish feedback.
6. Add uses `listSessionId = null`.
7. Completion calls the expected item ID/state, updates only the matching row
   on success, and leaves completed rows visible.
8. Completion failures retain visible state and show concise feedback.
9. Android Back and explicit back return to Listas; bottom-nav switching resets
   the nested route.
10. A real RoomListStore flow proves close/reopen durability, NULL session IDs,
    ordering, completion persistence, and zero Compras sessions.
11. Existing Mandado, Inicio text/voice capture, and Memoria transcript
    correction tests remain green.
12. Touched controls expose useful accessibility semantics and errors are not
    communicated by color alone.

## Authority

Do not merge or push. The user retains commit, merge, release, and push
authority. The final engineering verdict belongs to the independent
reviewer/orchestrator; this package must not declare `PASS`.
