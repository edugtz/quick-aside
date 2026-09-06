# Change 010 — Mandado UI — SPEC

Governance: **STANDARD**  
Status: **IN PROGRESS**

## Objective

Expose the Change 009 list persistence foundation through a small, explicit
management surface under the existing `Listas` destination. The user can
open `Mandado`, explicitly start the current session, add items, check or
uncheck items, and explicitly finish the session. Session and item state must
remain durable Room data.

## References and affected UX contract

This change was reviewed against:

- `docs/UX_UI_REFERENCE.md`;
- `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`;
- the list-management section of `docs/PROJECT_SPEC.md`;
- the Change 009 application and persistence contracts.

Affected accepted invariants:

- the top-level destinations remain exactly `Inicio`, `Pendientes`, `Listas`,
  and `Memoria`;
- management remains explicit and structured rather than a chat surface;
- list checkboxes are fast and obvious to use;
- the UI is calm, native Android/Material, one-handed, and accessible;
- durable user data is retained and never silently deleted;
- the session lifecycle is explicit and successful mutations provide concise
  feedback.

## In scope

- A `Listas` root showing interactive `Mandado` and visibly deferred,
  non-interactive `Compras`.
- A nested Mandado management surface using the app-scoped `ListStore`.
- Deterministic loading of the current active session and its items.
- Explicit session start with `Created` and `Existing` treated as success.
- Exact-text item insertion through `ListStore.addItem`.
- Completion changes through `ListStore.setItemCompleted`.
- Checked items remaining visible in the current session.
- Explicit finish with a native confirmation dialog and
  `ListStore.finishActiveSession`.
- Safe Spanish feedback for all relevant non-success outcomes.
- Android back/explicit navigation from Mandado to the Listas root.
- Focused Compose UI tests with fakes for success and failure behavior.
- At least one named-database Android integration flow using real
  `RoomListStore`, including close/reopen durability and post-finish history
  reads.
- Representative visual evidence for the Listas root, active Mandado, and
  finish/no-active state.

## Out of scope

- Mandado history UI or recreation/reopen behavior.
- Functional Compras UI or continuous-list interaction.
- AI, routing, Capture → ListItem interpretation, CapturePlan, STT, Google
  APIs, reminders, Action Ledger/Undo, deletion, reorder, editing, quantities,
  prices, stores, categories, custom lists, archive/export, or sync.
- New top-level navigation destinations or a navigation/state framework.
- Room schema, domain contract, migration, dependency-version, or built-in-ID
  changes.
- Changes to historical Change 001–009 packages or schema files.

## Product contract

### Listas root

`Listas` is the entry point for list management. It presents:

- `Mandado` — interactive and opens the current Mandado surface;
- `Compras` — visibly deferred and not interactive in this change.

There is no fifth bottom-navigation item.

### No active Mandado

The nested surface shows `Mandado`, `No hay un mandado activo.` and a primary
`Iniciar mandado` action. It does not create a session merely by opening
Listas, opening Mandado, or launching the app.

`SessionStartResult.Created` and `SessionStartResult.Existing` both lead to
the active session surface. `MissingDefinition`, `NotSessionBased`, and
`Failed` do not show success and produce concise understandable feedback.

### Active Mandado

The surface shows the Mandado context, an add-item field/action, current
items, a checkbox for every item, a clear zero-item state, and an explicit
`Terminar mandado` action. It never exposes internal IDs or database terms.

### Item insertion

Whitespace-only text cannot be submitted. A valid string is passed to
`ListStore.addItem` unchanged, including surrounding whitespace. On `Saved`
the item appears, the field clears, and the user remains on Mandado. On every
non-success result the field remains intact and feedback is shown.

### Completion

Each checkbox calls `setItemCompleted(item.id, checked)`. On `Updated`, only
the matching visible item is replaced. On `Missing` or `Failed`, the visible
state is retained and feedback is shown. Completed rows are not deleted or
hidden.

### Finish

`Terminar mandado` opens a native confirmation dialog titled
`¿Terminar este mandado?`, explains that items remain in history, and offers
`Cancelar` and `Terminar`. Cancellation or dialog dismissal does not mutate
the session. Confirmation calls `finishActiveSession(MANDADO.id)`. On
`Finished`, the UI returns to the no-active-session state and shows
`Mandado terminado`; it does not automatically start a new session. All
other finish outcomes avoid false success and reconcile the current state
when appropriate.

### Navigation and state

The smallest focused state model is used: `Loading`, `NoActiveSession`,
`Active(session, items)`, and `Failed`, with local mutation/dialog state as
needed. Back and an explicit nested back action return to the Listas root.
Back dismisses the finish dialog when it is open.

## Application and persistence boundary

- Production UI receives `QuickAsideApplication.listStore` through
  `MainActivity` and `QuickAsideApp`.
- Compose calls only `ListStore`; it does not access Room DAOs.
- No second production `RoomListStore` is constructed in Compose.
- `QuickAsideDatabase.version` remains `3`; schemas `1.json`, `2.json`, and
  `3.json` remain unchanged; there is no schema 4 or migration.
- Change 009 remains the owner of list semantics, durable rows, and lifecycle
  outcomes.

## Acceptance scenarios

1. Listas exposes Mandado and deferred Compras without adding a destination.
2. Entering Listas/Mandado without an active session shows the empty/start
   state and does not create a session.
3. Starting Mandado handles Created and Existing as active success.
4. Start failures are not presented as success.
5. An active empty session shows an empty-items state.
6. Exact non-blank item text reaches the ListStore; blank text does not.
7. Saved items appear and clear the field; add failures retain the field.
8. Completion calls the expected item ID/state and only replaces that item on
   success; checked items remain visible.
9. Finish requires confirmation; cancellation is non-mutating; confirmation
   calls the finish boundary and successful finish returns to no-active state.
10. Session and items remain durable after finish and are readable through
    ListStore history reads.
11. Existing Inicio text capture, voice capture, and Memoria transcript
    correction tests remain green.
12. Accessibility labels, normal font scaling, and non-color-only state are
    preserved for the touched controls.

## Required verification evidence

- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:assembleDebug`
- `./gradlew :app:lintDebug`
- `./gradlew :app:connectedDebugAndroidTest`
- `git diff --check`, `git status --short`, and diff statistics.
- Inspection proving DB version 3, schemas 1/2/3 unchanged, no schema 4,
  no migration/dependency changes, and no direct DAO use from Compose.
- Representative screenshots or real-device evidence for the three requested
  Mandado states.

## Stop conditions

Stop and report if the UI requires a schema change, delete/reopen behavior,
history UI, functional Compras behavior, AI/routing, or a generic navigation
or state framework.

## Authority

Do not merge or push. The user retains commit, merge, release, and push
authority. The final engineering verdict belongs to the independent reviewer
orchestrator.
