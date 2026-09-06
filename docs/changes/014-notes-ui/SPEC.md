# Change 014 — Notes UI — SPEC

Governance: **STANDARD**  
Status: **IN PROGRESS**

## Objective

Expose the durable `Note` records created by Change 013 through the existing
`Memoria` destination. A user can open Notes, create a manual freeform Note,
and review recent Notes with exact text and local creation time.

This is explicit manual management UI. It does not add AI routing or convert
Captures into Notes.

## In scope

- A restrained accessible `Notas` affordance on the existing Memoria history
  surface.
- A lightweight local `Memoria → Notas` route while keeping Memoria as one
  bottom-navigation destination.
- A focused `NotesScreen` backed by the app-scoped `MemoryStore`.
- Loading, loaded, empty, and failure/retry states for recent Notes.
- Native multiline freeform Note creation with exact valid text preservation.
- Manual creation with `sourceCaptureId = null`.
- Immediate visible insertion of a returned `Saved(note)` using the domain
  ordering contract (`createdAt DESC, id DESC`).
- Read-only calm Material-style Note rows showing exact text and local time.
- Deterministic timestamp formatting with injected `ZoneId` and `Locale`.
- Accessibility labels, back behavior, nested-route reset, and focused Compose
  coverage using a fake `MemoryStore`.
- A small named-database Room integration regression for create/read/close/
  reopen durability through the production `MemoryStore` path.
- Representative real-device visual evidence for Memoria and Notes.

## Out of scope

- AI, Capture → Note routing, CapturePlan, STT, or Google APIs.
- Note editing, deletion, reminders, snooze, tags, folders, pinning, sharing,
  export, backup, undo, search, FTS, Structured Logs UI, or a generic memory
  dashboard.
- A fifth bottom-navigation destination or Navigation Compose solely for Notes.
- Room entities, DAOs, `RoomMemoryStore`, database version, migrations, or
  schema changes. No schema 5 is permitted.

## Reference surfaces and UX invariants

The affected reference surface is `Memoria` in
`docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`, interpreted with
`docs/UX_UI_REFERENCE.md` and the accepted navigation baseline.

The implementation preserves:

- `Inicio`, `Pendientes`, `Listas`, and `Memoria` as the only management
  destinations, plus the global Capture action.
- Capture History as the default Memoria route and its current Text/Voice,
  refresh, and transcript-correction behavior.
- Calm Material 3 hierarchy, readable density, local-time presentation, and
  structured memory rather than chat bubbles.
- Capture as an action, not a permanent navigation destination.
- Voice as the capture-state motif, not the center of the Notes management
  screen.

Notes are discoverable from Memoria without adding placeholder controls for
Search, Logs, Archive, or Backup. Existing Note rows are read-only and expose
no edit or delete affordance.

## Application boundary

`QuickAsideApplication.memoryStore` remains the production owner. `MainActivity`
passes it through `QuickAsideApp`, and Compose calls only `MemoryStore`; it
does not access Room or DAOs directly. Existing tests may omit the optional
store while retaining Capture History coverage.

The existing `MemoryStore` result contract is used:

- `Saved(note)` updates the visible list and clears the draft.
- `BlankText` reports a concise validation message and retains the draft.
- `MissingSourceCapture` is treated as a generic save failure because manual
  Notes always pass `sourceCaptureId = null`.
- `Failed` reports a concise generic failure without exposing the cause.
- `CancellationException` propagates.

## Note read/create contract

Opening Notes calls `MemoryStore.readRecentNotes()` and uses the supplied
ordering. The UI never sorts formatted timestamps. If a saved Note is inserted
locally, it is de-duplicated by `NoteId` and ordered by `createdAt DESC,
id DESC`, without assuming timestamp uniqueness.

The multiline field accepts exact text. Whitespace-only input cannot be saved;
valid leading/trailing whitespace is passed unchanged to
`createNote(text, sourceCaptureId = null)`. Save is disabled while blank or
saving, and a second submission cannot start while the first is in flight.

## Acceptance scenarios

1. Memoria exposes an accessible Notes affordance without a fifth destination.
2. Opening Notes reads recent Notes through `MemoryStore`.
3. Loading, empty, loaded, failure, and retry states are usable and
   understandable.
4. Existing Notes show exact text and useful local date/time context in the
   supplied store order.
5. Whitespace-only input cannot be submitted.
6. Valid leading/trailing whitespace reaches `createNote` unchanged with a
   null source Capture.
7. A returned Saved Note is immediately visible and clears the input.
8. Save failure retains the input and shows generic understandable feedback.
9. Duplicate save is prevented while saving.
10. Note rows have no edit/delete action.
11. Back from Notes returns to Capture History, and switching bottom
    destinations resets Memoria to Capture History.
12. Existing Capture History Text/Voice rendering and transcript correction,
    plus Listas/Mandado/Compras behavior, remain intact.
13. A dedicated v4 Room database survives create Note A/create Note B,
    deterministic read order, close, reopen, and read again with exact values,
    IDs, timestamps, and null source Capture IDs.

## Verification evidence

Run the required unit-test, assemble, lint, and connected Android-test gates,
plus `git diff --check` and `git status --short`. Inspect that database version
4 and schemas 1–4 are unchanged, no schema 5/migration/dependency/AI/STT/
Google changes exist, Compose uses `MemoryStore`, and the four destinations
remain unchanged. Capture representative real-device screenshots for the
Memoria affordance, Notes empty/create surface, and Notes with two saved
Notes. Report any unavailable or failed gate accurately; this package does
not declare the final engineering verdict.

## Authority and stop conditions

Do not merge or push. Stop and report if Notes require a schema/migration,
missing `MemoryStore` operations, source Capture linking, editing/deletion, or
a broader Memoria architecture, or if implementation starts pulling in Search,
Structured Logs, reminders, backup/archive, AI, or Google behavior.
