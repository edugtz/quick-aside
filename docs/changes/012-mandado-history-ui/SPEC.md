# Change 012 — Mandado History UI — SPEC

Governance: **STANDARD**  
Status: **IN PROGRESS**

## Objective

Make completed Mandado sessions discoverable and readable from the existing
Listas → Mandado management surface without changing persistence semantics.
History is read-only: viewing it never reopens, edits, completes, copies, or
deletes a session or item.

## References and affected UX contract

This change is reviewed against:

- `docs/UX_UI_REFERENCE.md`;
- `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`;
- the Listas/history sections of `docs/PROJECT_SPEC.md`;
- the Change 009 list persistence contract;
- the Change 010 Mandado surface and the Change 011 nested Listas navigation.

Affected accepted invariants:

- top-level destinations remain exactly `Inicio`, `Pendientes`, `Listas`, and
  `Memoria`;
- `Capture` remains an action, not a destination;
- management is explicit, structured, calm, native Android/Material, and
  accessible;
- completed Mandado sessions remain independently retrievable;
- durable list data is retained and never silently deleted;
- the current Mandado remains the primary management surface;
- read-only historical rows do not appear editable or actionable.

## In scope

- A restrained `Historial` affordance on `Mandado`, available with or without
  an active session.
- A focused nested `MandadoHistoryScreen` with Loading, Loaded, Empty, and
  Failed states.
- Loading through `ListStore.readRecentSessions(MANDADO.id)` and filtering
  `session.endedAt != null` at the presentation boundary.
- Deterministic newest-first presentation using the order supplied by the
  existing ListStore/persistence contract (`startedAt DESC, id DESC`).
- Spanish local date/time context and item counts for each history row.
- A focused read-only `MandadoHistoryDetailScreen` showing all retained items
  in the supplied deterministic item order and their completion state.
- Local nested navigation Mandado → Historial → Detalle de mandado, with
  Android Back and top-bar Back returning one level at a time.
- Deterministic Compose UI tests and a dedicated named-database Room history
  integration flow.
- Representative visual evidence for the Mandado affordance, history list,
  and mixed-completion detail.

## Out of scope

- Reopen, continue, repeat, copy, restore, edit, complete/uncomplete, delete,
  export, share, search, filtering, pagination, or grouping history.
- Compras history or any Compras lifecycle change.
- AI, CapturePlan, Capture routing, STT, Google APIs, reminders, Action
  Ledger/Undo, or a navigation framework.
- New top-level destinations, Listas root redesign, generic caching/repository
  abstractions, or background refresh architecture.
- Any Room schema/entity/DAO/persistence-semantic/dependency-version change.

## Product contract

### Discoverability and navigation

`MandadoScreen` exposes a labeled `Historial` action whether an active session
exists or not. History is not a fifth bottom-navigation destination and is not
rendered as cards inside the current-session surface.

The nested route is local state only:

`Listas → Mandado → Historial → Detalle de mandado`

Back from detail returns to history; Back from history returns to Mandado;
Back from Mandado returns to the Listas root. Switching bottom destinations
resets the nested Listas route to the root.

Opening history does not call `startSession`, `finishActiveSession`,
`finishSession`, or any item mutation operation.

### History list

The screen calls:

```kotlin
listStore.readRecentSessions(BuiltInListDefinitions.MANDADO.id)
```

Only sessions with `endedAt != null` are displayed. If the store returns an
active session, it is excluded without changing persistence. The remaining
objects retain the ListStore order; the UI does not reorder from formatted
date strings.

Rows show understandable local date/time context and item count, do not expose
IDs/epoch/database terminology, and open the selected immutable session object
in detail. Empty completed history is valid and shows:
`No hay mandados anteriores.`. Load failures show
`No se pudo cargar el historial.` with an accessible focused retry.

### History detail

Detail shows the historical session date/context and every retained item in
the existing deterministic item order. Checked items visibly communicate
`completado`; unchecked items visibly communicate `pendiente`. The item visual
is non-clickable and uses no interactive checkbox semantics. No detail action
calls `setItemCompleted`, `addItem`, `finishSession`, or `startSession`.

If the selected session is passed from the history list, no detail read is
needed. If a future equivalent reload is used, it must call `readSession`,
reject a null or active result, and show `No se pudo cargar este mandado.` on
failure; this change uses the already loaded immutable object.

### Time and accessibility

Date/time presentation uses `java.time` with injectable `ZoneId` and `Locale`
where tested, and ordering remains based on domain/persistence values. All
history actions, rows, Back actions, and retry controls expose useful
semantics. Read-only completion state is not color-only and does not falsely
announce an actionable checkbox.

Cancellation exceptions propagate; exception details, SQL, IDs, and stack
traces never reach user-facing copy.

## Persistence contract

- `QuickAsideDatabase.version` remains `3`.
- Tracked schemas `1.json`, `2.json`, and `3.json` remain unchanged.
- No `4.json`, migration, entity, DAO, or persistence-semantics change is
  introduced.
- `ListStore` remains the only UI/application boundary used by Compose.
- Real integration tests use unique named databases and never
  `quick_aside.db`.

## Acceptance scenarios

1. Mandado exposes a visible/accessibly labeled History affordance.
2. History is reachable with no active Mandado.
3. History is reachable while an active Mandado exists.
4. Opening History does not start or finish a session.
5. Only ended sessions appear; an active result is filtered out.
6. Completed sessions preserve the supplied deterministic newest-first order.
7. Loading, Loaded, Empty, and Failed/retry states are understandable.
8. Rows show useful local date/time context and item count.
9. Selecting a row opens read-only detail with all retained items.
10. Completion state is retained and visibly distinguishes checked/unchecked.
11. Detail exposes no completion mutation or edit action.
12. Viewing history/detail performs zero list writes.
13. Back returns detail → history → Mandado, and bottom navigation resets the
    nested Lists route.
14. Existing Mandado, Compras, text capture, voice capture, and transcript
    correction behavior remains covered.
15. A real named-database Room flow proves completed-session durability,
    active-session distinction, item attachment/order, completion retention,
    and close/reopen preservation.

## Authority and verification

Do not merge or push. The user retains commit, merge, release, and push
authority. The implementation report must record actual evidence and must not
declare the final engineering verdict; that verdict belongs to the
independent reviewer/orchestrator.
