# Change 015 — Structured Logs UI — SPEC

Governance: **STANDARD**  
Status: **IN PROGRESS**

## Objective

Expose the durable `StructuredLog` records already supported by the v4
`MemoryStore` through the existing `Memoria` destination. A user can open
`Registros`, create a generic manual record with one or more field/value
pairs, and review recent records with exact values and local creation time.

This is explicit manual management UI. It does not add natural-language
extraction or Capture → StructuredLog routing.

## In scope

- A restrained accessible `Registros` affordance beside the existing `Notas`
  affordance on Memoria Capture History.
- A local `Memoria → Registros` route with Capture History as the default.
- A focused `StructuredLogsScreen` backed only by the app-scoped `MemoryStore`.
- Loading, loaded, empty, failure, and retry states for recent Structured Logs.
- A simple manual field/value editor starting with one row, with add/remove
  row controls and at least one row always available.
- Validation for complete nonblank pairs, exact whitespace preservation, and
  duplicate exact keys without normalization or auto-renaming.
- Manual creation with `sourceCaptureId = null` and deterministic handling of
  every `StructuredLogCreationResult`.
- Immediate visible insertion of `Saved(log)` using `createdAt DESC, id DESC`.
- Read-only record cards showing local creation time and every field rendered
  by exact field key ascending.
- Injected timestamp formatting using zone, locale, and clock inputs.
- Accessible back/retry/save/add/remove semantics and focused Compose coverage.
- A small named v4 Room integration regression for StructuredLog
  create/read/close/reopen durability through the production `MemoryStore`.
- A focused Notes bottom-content-padding adjustment so the global Capture FAB
  does not obscure the final Note card.
- Representative real-device visual evidence for Memoria and Registros.

## Out of scope

- Natural-language parsing, AI, CapturePlan, STT, or Capture routing.
- Structured domain schemas, fitness-specific UI, form-builder abstractions,
  search, FTS, reminders, tags, folders, pinning, export, backup/archive,
  Action Ledger, undo, sharing, templates, charts, analytics, or Google APIs.
- StructuredLog editing, deletion, duplication, copying, or pinning.
- A fifth bottom-navigation destination, Navigation Compose solely for this,
  a generic Memoria dashboard, or placeholder Search/Archive/Backup controls.
- Room entities, DAOs, `RoomMemoryStore`, database version, migrations, or
  schemas. Database version 4 and schemas 1–4 remain unchanged.

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
- Voice as the capture-state motif, not the center of the Registros screen.

Registros and Notas are secondary affordances on Capture History with
consistent visual weight. Existing cards are read-only and expose no
edit/delete action.

## Application boundary

`QuickAsideApplication.memoryStore` remains the production owner. `MainActivity`
passes it through `QuickAsideApp`, and Compose calls only `MemoryStore`; it
does not access Room or DAOs directly.

Opening Registros calls `readRecentStructuredLogs()` and preserves the store's
supplied ordering. If a saved record is inserted locally, the UI de-duplicates
by `StructuredLogId`, orders by `createdAt DESC, id DESC`, and limits to
`RECENT_MEMORY_LIMIT`.

## Field editor and validation contract

Each row has a `Campo` and `Valor` input. Valid nonblank strings are sent to
`createStructuredLog` exactly as entered, including leading/trailing spaces.
The UI prevents saving when all rows are empty, when any key/value is blank,
when any row is only partially filled, or when two rows have the same exact
key. It does not trim, normalize case, overwrite, or auto-rename keys.

At least one row remains available. Save is disabled while invalid or saving,
and a second submission cannot start while the first is in flight.

## Result and error contract

On `Saved(log)`, the returned record is immediately visible, the editor resets
to one empty row, and lightweight success feedback is shown. Validation and
save failures retain the entered rows and show concise generic copy. Internal
exception text, SQL, IDs, and stack traces are never rendered. A
`CancellationException` propagates.

## Acceptance scenarios

1. Memoria exposes accessible `Notas` and `Registros` affordances without a
   fifth destination.
2. Opening Registros reads recent Structured Logs through `MemoryStore`.
3. Loading, empty, loaded, failure, and retry states are usable.
4. Existing records show every field and useful local date/time context.
5. Supplied record ordering is preserved; card fields render key ASC.
6. The editor starts with one row, can add/remove rows, and never removes the
   final row.
7. Empty, partial, blank-key, blank-value, and duplicate-key records cannot
   be saved.
8. Exact valid key/value whitespace reaches `createStructuredLog` unchanged
   with a null source Capture.
9. Saved records appear and reset the editor; failed saves retain all rows and
   hide internal causes; duplicate save is prevented.
10. Existing record cards have no edit/delete semantics.
11. Toolbar/system back returns to Capture History, and bottom navigation
    resets Memoria to Capture History.
12. Existing Notes, Capture History Text/Voice, transcript correction, and
    Listas/Mandado/Compras behavior remain intact.
13. A dedicated v4 Room database survives create/read/close/reopen for two
    Structured Logs with exact fields, IDs, timestamps, null source IDs,
    deterministic record order, and deterministic field reconstruction.
14. Notes content padding permits the final card to scroll fully above the
    global Capture FAB.

## Verification and authority

Run the required unit-test, assemble, lint, and connected Android-test gates,
plus `git diff --check` and `git status --short`. Inspect that database
version 4 and schemas 1–4 are unchanged, no schema 5/migration/dependency/
AI/STT/Google changes exist, Compose uses `MemoryStore`, and the four
destinations remain unchanged. Capture representative real-device screenshots
for Memoria, the Registros empty/create state, and Registros with two records.

Do not merge or push. Do not declare the final engineering verdict; report
actual evidence and leave final review to the independent reviewer.
