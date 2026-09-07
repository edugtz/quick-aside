# Change 017 — Search UI — PLAN

Governance: **STANDARD**  
Status: **PLAN/DOCS ONLY — IN PROGRESS**  
Expected branch: `chg-017-search-ui`

This plan is intentionally limited to documentation and planning. Production
code, tests, schemas, dependencies, and visual evidence remain unchanged in
this turn.

## 1. Repository state

- Current branch is `chg-017-search-ui`.
- The worktree was clean before this planning package was created.
- Change 016 is the completed local-search foundation and is the active
  implementation baseline.
- `docs/ACTIVE_WORK.md` currently points at Change 016 and must be updated to
  Change 017 PLAN/DOCS ONLY — IN PROGRESS.
- No Gradle gates are needed for this docs-only turn.

## 2. Current Memoria structure

Current production route state in `ui/QuickAsideApp.kt`:

```text
MemoryRoute.History
MemoryRoute.Notes
MemoryRoute.StructuredLogs
```

`History` is already the default. `CaptureHistoryScreen` currently renders
the `Capturas recientes` heading, explanatory copy, side-by-side `Notas` and
`Registros` affordances, and the recent Capture list. Its list currently has
only `16.dp` bottom content padding; the new Search result list must use the
proven memory-screen FAB-clearance pattern instead.

Notes and Structured Logs already use a `160.dp` bottom content padding and
have focused tests that measure the final card against the global `Capturar`
FAB. Their route/back/reset patterns are the model for Search.

`AppDestination` is already exactly:

```text
Inicio · Pendientes · Listas · Memoria
```

No destination is added or removed. The global Capture FAB remains owned by
the app shell and is visible while a nested Memoria route is shown.

## 3. Change 016 LocalSearch boundary

The current application contract is:

```kotlin
interface LocalSearch {
    suspend fun search(
        query: String,
        limit: Int = DEFAULT_LOCAL_SEARCH_LIMIT,
    ): List<LocalSearchResult>
}
```

The four result variants already carry the data needed for presentation:

- Capture: kind, effective display text, timestamp;
- Note: exact text and timestamp;
- Structured Log: exact ordered field entries and timestamp;
- List Item: exact text, completion, list definition, optional session
  context, and item creation timestamp.

`RoomLocalSearch` already owns normalization, escaping, source queries,
de-duplication, merge ordering, and the 50-result limit. Search UI passes a
nonblank query to this boundary and renders the returned list without
reimplementing any of those rules.

## 4. Proposed Search route

Extend the private `MemoryRoute` enum with `Search`. Add the route branch in
`ManagementScreen` and route metadata in the app shell:

1. `MemoryRoute.History` remains the initial Memoria surface.
2. History's Search affordance sets `memoryRoute = Search`.
3. The top app bar title becomes `Buscar` while Search is active.
4. Toolbar Back and Android system Back return to `History`.
5. Bottom navigation selection calls the existing `resetMemoryRoute`, which
   returns Search to History just as it does Notes/Structured Logs.

No Navigation Compose dependency or generic navigation abstraction is needed.

## 5. Search entry UX

Add a full-width `OutlinedButton` to `CaptureHistoryScreen` before the existing
Notes/Registros row:

- Material search icon;
- visible copy `Buscar en Memoria`;
- content description `Abrir búsqueda en Memoria`;
- normal Material focus, ripple, and touch-target behavior.

Keep `Notas`, `Registros`, `Capturas recientes`, and the current History list
intact. The Search affordance is a prominent retrieval action, not a new
Memoria tab bar or a redesign of the History cards.

The Search screen uses one standard `OutlinedTextField` near the top, with
IME Search plus a visible trailing `IconButton`. A custom SearchBar is not
justified by the current dependency/library constraints or the explicit-submit
product requirement.

## 6. Search execution model

Use explicit submit, not live/debounced search:

1. Keep draft text in `rememberSaveable` state.
2. On IME Search or visible action, check only `draft.trim().isBlank()` in
   the UI. A blank input returns without a call or state transition.
3. For nonblank input, store the exact submitted input for retry and call
   `LocalSearch.search(draft)` from a coroutine.
4. Set Loading before the call and map the returned list to Results or Empty.
5. Do not call Room, filter an existing History list, re-rank, or highlight in
   Compose.

The UI does not pass an explicit limit; the existing boundary default remains
50. This keeps the UI independent of Change 016's limit semantics.

## 7. UI state model

Implement a focused state model in a new `ui/memory/SearchScreen.kt`:

```text
Initial
Loading(submittedInput)
Results(submittedInput, results)
Empty(submittedInput)
Failed(submittedInput)
```

State behavior:

- Initial composition has no submitted query and performs no search.
- Loading copy is `Buscando…`.
- Results show a flat list in boundary order.
- Empty copy includes the trimmed submitted query without exception details.
- Failed copy is `No se pudo buscar en Memoria.`.
- Failed state has a focused `Reintentar búsqueda` action.
- Retry calls the exact last submitted input, not a newer unsent draft.
- A blank submit does not clear an existing valid result state.
- `CancellationException` is rethrown; ordinary failures become Failed.

If the nullable test-compatibility injection is absent, show the same generic
Failed state without constructing a fallback Room implementation. Production
`MainActivity` always supplies the app-scoped boundary.

## 8. Result presentation by type

Create one read-only card renderer that branches only on the closed result
type. Keep a single LazyColumn and preserve the returned order.

### Capture

Render `displayText`, `Texto` or `Voz` from `captureKind`, and a local
timestamp. Do not recompute effective Voice text or display the Capture ID.

### Note

Render exact `displayText`, the `Nota` type label, and a local timestamp. Do
not add edit, delete, or source-opening affordances.

### Structured Log

Render the `Registro` label, every `StructuredLogSearchField` key/value pair,
and the result timestamp. Iterate the supplied `fields` list directly. Do not
sort it again, reduce it to `displayText`, or highlight matching fields.

### List Item

Render exact item `displayText`, `listDefinitionName` (`Mandado` or
`Compras`), visible completion text (`Completado`/`Pendiente`) plus a semantic
icon, and the item `createdAt` timestamp. If the returned session context is
present, show a human-readable session date/context based on the local start
and end values; never show the session ID. Continuous items have no fabricated
session context.

All cards are non-clickable and have no source mutation or navigation action.
The existing `NoteTimestampFormatter` is a suitable injectable local-time
formatter for result and optional session labels; ordering always remains the
boundary's Instant order.

## 9. App wiring

Production changes are planned in this direction:

```text
QuickAsideApplication.localSearch
  → MainActivity.localSearch argument
  → QuickAsideApp(localSearch = ...)
  → ManagementScreen
  → SearchScreen
```

`QuickAsideApplication` already owns one lazy `RoomLocalSearch(database)` and
does not need another database or owner. Add the argument to `MainActivity`
and `QuickAsideApp`; use a nullable default in the Composable signature only
to avoid forcing unrelated existing direct-Compose tests to construct a
search fake. Search-specific tests always inject a fake `LocalSearch`.

## 10. Accessibility

Plan semantics and test them for:

- `Abrir búsqueda en Memoria` on the History entry;
- `Buscar en Memoria` label and `Buscar` submit action on the field;
- `Volver a Memoria` on the Search top-bar Back button;
- result kind/context labels where a card's visible content is ambiguous;
- `Reintentar búsqueda` on failure;
- `Completado`/`Pendiente` text and semantics for List Items.

Use normal Material minimum touch targets, wrap long text, avoid fixed-height
cards, and verify that text scaling keeps the field, result fields, completion
state, and retry control usable.

## 11. FAB and layout strategy

Use the established memory-screen approach:

- keep the global `Capturar` FAB in the app shell;
- use a scrollable result list with bottom content padding of at least the
  proven `160.dp`;
- add a dedicated test that scrolls to the absolute end and asserts the final
  Search result card bottom is at or above the Capture FAB top.

The field and state copy may be outside the results list, but the result list
must own enough scroll range for the last card to clear the FAB. Do not fix
this with an overlay, hidden card, or a broad scaffold redesign.

## 12. Visual-reference alignment

The implementation preserves the v3 direction without copying its pixels:

- Memoria remains a calm unified memory surface;
- Search is prominent but subordinate to the Memoria destination rather than
  becoming a new global destination;
- Notes and Structured Logs remain adjacent secondary management surfaces;
- neutral Material surfaces, readable hierarchy, rounded native cards, and
  blue/teal semantic accents remain appropriate;
- the custom voice orb is not introduced into Search;
- native icons and behavior are used for Search, Back, type, and completion;
- the Search field/submit affordance is reachable with one hand and remains
  clear under accessibility text scaling.

Any device-level deviation needed for Material correctness must be recorded in
the implementation evidence rather than silently copying mockup dimensions.

## 13. Test contract and approach

Add `app/src/androidTest/java/com/edu/quickaside/SearchUiTest.kt` using a fake
`LocalSearch`. The fake should record query calls, return configured typed
results, expose a `CompletableDeferred` gate for Loading, and throw a private
exception for generic failure tests. UI tests must not open or seed Room.

Focused test coverage should include:

- default History and prominent Search entry;
- Notes and Registros remaining visible;
- Search route title, toolbar Back, Android Back, and bottom-navigation reset;
- exactly four bottom destinations and no Search destination;
- initial no-call state and blank-submit no-call state;
- submitted query capture and loading gate;
- Capture, Note, Structured Log, and List Item rendering;
- all Structured Log fields in supplied order;
- historical Mandado context and completed/noncompleted List Item semantics;
- exact mixed-result order;
- Empty, Failed, generic error copy, and Retry query reuse;
- cancellation propagation;
- no result click/mutation/source-opening actions;
- final-card/FAB clearance;
- global Capture FAB availability.

Change 016's `LocalSearchRoomIntegrationTest` remains the owner of Room
search correctness, wildcard behavior, durable history, result de-duplication,
ordering, and persistence evidence. Do not duplicate that integration suite
in UI tests.

## 14. Visual evidence contract

During implementation, capture these representative states on CPH2791 / Android
16 when available:

1. `memoria-search-affordance.png` — History with Search, Notes, Registros.
2. `search-initial.png` — Search route before submission.
3. `search-results-mixed.png` — multiple result kinds in boundary order.
4. `search-empty.png` — query-specific empty state.

Inspect the images against the written UX contract, canonical reference, text
scaling/touch-target expectations, and the FAB-clearance test. Record any
deviation and its native-platform reason.

## 15. Schema and dependency impact

Expected impact is none:

- Room version remains 4;
- schemas remain 1–4;
- no migrations, tables, indexes, FTS objects, entity changes, or new
  dependencies;
- `QuickAsideApplication.localSearch` remains the only production search
  instance.

If any of these assumptions changes, stop before implementation and request a
newly scoped change.

## 16. Change package and implementation file map

This planning package:

- `docs/changes/017-search-ui/SPEC.md`;
- `docs/changes/017-search-ui/PLAN.md`;
- `docs/changes/017-search-ui/TASKS.md`;
- `docs/ACTIVE_WORK.md`.

Expected later production/test touch points:

- `app/src/main/java/com/edu/quickaside/MainActivity.kt` — pass the existing
  app-scoped `LocalSearch`;
- `app/src/main/java/com/edu/quickaside/ui/QuickAsideApp.kt` — route state,
  app-bar/back wiring, History entry, and dependency pass-through;
- `app/src/main/java/com/edu/quickaside/ui/memory/SearchScreen.kt` — new
  stateful search surface and result renderers;
- `app/src/androidTest/java/com/edu/quickaside/SearchUiTest.kt` — fake-boundary
  deterministic UI coverage.

`QuickAsideApplication.kt` and `application/search/LocalSearch.kt` are
preflight verification points, not expected implementation changes.

## 17. Risks and open decisions

Resolved planning decisions:

- Explicit submit is preferred over debounce because M1 requires deterministic
  behavior and the repository already has a simple application boundary.
- `OutlinedTextField` is sufficient; no SearchBar complexity is needed.
- The result list is flat and boundary-ordered; no source grouping or ranking.
- Nullable Composable injection preserves existing direct UI-test setup while
  production wiring remains non-null through MainActivity.
- Existing `160.dp` FAB clearance is the starting point, verified rather than
  assumed.

Implementation risks:

- accidentally adding Search to bottom navigation;
- accidentally dropping Notes/Registros affordances when inserting Search;
- calling `LocalSearch` during initial composition or on blank submit;
- sorting Structured Log fields or result cards in Compose;
- leaking internal failure text or source IDs;
- allowing cards to look actionable;
- under-padding the result list so the global FAB obscures the final card.

The stop conditions in SPEC apply if any risk requires architecture or data
expansion.

## 18. Git status and diff expectations

Before this package, `git status --short` was clean on `chg-017-search-ui`.
After the docs-only work, the expected diff is limited to:

```text
docs/ACTIVE_WORK.md
docs/changes/017-search-ui/SPEC.md
docs/changes/017-search-ui/PLAN.md
docs/changes/017-search-ui/TASKS.md
```

No production source, test source, generated schema, build file, or
dependency-lock file should appear in the docs-only diff. Run `git diff --check`
and inspect `git diff --stat`; do not commit or push.

## 19. Exact next gate

The Change 017 package must be independently reviewed and committed before
production implementation begins.
