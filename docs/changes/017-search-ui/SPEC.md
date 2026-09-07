# Change 017 — Search UI — SPEC

Governance: **STANDARD**  
Status: **PLAN/DOCS ONLY — IN PROGRESS**  
Expected branch: `chg-017-search-ui`

## Objective

Expose the existing deterministic, durable `LocalSearch` boundary inside the
existing `Memoria` destination. A user can open a native local-search surface,
submit a text query, and review a flat read-only list of matching Captures,
Notes, Structured Logs, and List Items in the exact order returned by the
application boundary.

This is the M1 Search UI. It improves retrieval of already persisted personal
information; it does not add interpretation, ranking, mutation, or a new
top-level destination.

## Proven baseline and source of truth

Change 016 is complete and is the implementation boundary for this change:

- `LocalSearch.search(query, limit)` is the only search API consumed by UI.
- `LocalSearchResult` is closed over exactly `Capture`, `Note`,
  `StructuredLog`, and `ListItem`.
- The Room implementation searches durable tables directly, includes older
  rows, preserves effective Voice transcripts, de-duplicates Structured Logs,
  includes List definition/session context, escapes literal `\\`, `%`, and
  `_`, applies SQLite `NOCASE`, and caps results at 50.
- `LocalSearch` is already app-scoped at `QuickAsideApplication.localSearch`.
- Room remains version 4 with schemas `1.json` through `4.json`.

The UI must not duplicate any of those search semantics. It must not access a
DAO, load a recent screen list and filter it, instantiate `RoomLocalSearch`, or
add search to `MemoryStore`.

## References and affected UX contract

This change is reviewed against:

- `docs/UX_UI_REFERENCE.md`;
- `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`;
- the Memoria and navigation sections of `docs/PROJECT_SPEC.md`;
- the global criteria in `docs/ACCEPTANCE_CRITERIA.md`;
- the completed Memoria surfaces from Changes 005, 012, 014, and 015;
- the `LocalSearch` contract from Change 016.

The canonical image is directional only. Its obsolete embedded `VoiceApp`
label must not appear in the product. Android-native Material behavior,
accessibility, responsive layout, and this written contract take precedence
over incidental mockup pixels.

Affected accepted invariants are:

- `Memoria` remains the unified retrieval/history destination.
- `Capturas recientes`, `Notas`, and `Registros` remain available local
  surfaces; Search is additive and does not replace them.
- The four bottom destinations remain exactly `Inicio`, `Pendientes`,
  `Listas`, and `Memoria`.
- Capture remains a global action and remains available through the global
  Capture FAB on management and nested-memory screens.
- Management remains calm, structured, information-dense, and Android-native;
  Search is not an AI chat or a dashboard.
- Voice remains a capture-state motif, not the center of a search-management
  surface.
- Touch targets, text scaling, one-handed reach, and non-color-only state
  communication remain part of the UI contract.

## In scope

- Add a local `Search` route beside the existing `History`, `Notes`, and
  `StructuredLogs` `MemoryRoute` values.
- Keep `History` as the default Memoria route and reset the nested route to
  `History` whenever a bottom destination is selected.
- Add a prominent full-width `Buscar en Memoria` affordance near the top of
  the existing History surface, before the recent-history list and alongside
  the existing Notes/Registros affordances.
- Open a Search screen with a `Buscar` top-app-bar title and Back behavior to
  Memoria/History.
- Use an explicit, deterministic submit interaction through an
  `OutlinedTextField`: visible search action plus IME Search. No live query,
  debounce, or background search is planned.
- Render explicit Initial, Loading, Results, Empty, Failed, and Retry states.
- Render one flat, read-only result list in the exact `LocalSearch` order.
- Preserve the result context defined by Change 016 without exposing raw
  database/source IDs.
- Add focused Compose UI coverage with a fake `LocalSearch` and keep Room
  search semantics covered by Change 016 integration tests.
- Add representative visual evidence for the Memoria entry, Search initial
  state, mixed results, and empty result state.

## Out of scope

- AI search, natural-language question answering, semantic/vector search,
  fuzzy search, stemming, tokenization, or relevance ranking.
- Filters, facets, tags, highlighting, archive UI, backup/export, or a
  broader Memoria redesign.
- Source mutations or actions: edit, delete, complete/uncomplete, transcript
  correction, deep links, opening Mandado sessions, opening Notes, opening
  Structured Logs, or result actions.
- A fifth bottom-navigation destination or a permanent Capture destination.
- Direct Room/DAO access from Compose, a second Room database, a second
  `RoomLocalSearch`, or a `MemoryStore` search API.
- FTS, new indexes, new tables, schema version 5, migrations, persistence
  model changes, or production dependencies.
- Changes to Change 016 query normalization, escaping, source matching,
  result ordering, field reconstruction, or the 50-result contract.

## Memoria information architecture and route contract

The existing `MemoryRoute.History` remains the first screen when the user
selects Memoria. The local route graph becomes:

```text
Memoria / History
  ├─ Search
  ├─ Notes
  └─ StructuredLogs
```

Search is a nested local route, not a bottom destination. Switching to any
bottom destination resets `memoryRoute` to `History`, matching the established
Notes/Structured Logs behavior. Returning from Search through the top-bar Back
button or Android system Back returns to History without leaving Memoria.

The top app bar on Search is titled `Buscar`. Its accessible navigation label
is `Volver a Memoria`. The existing bottom navigation and global Capture FAB
remain visible while Search is open, subject only to the existing capture
overlay behavior.

## Search entry UX

History gains a visually prominent, full-width Material affordance near the
top:

- leading Material search icon;
- user-facing label `Buscar en Memoria`;
- accessible action label `Abrir búsqueda en Memoria`;
- native touch target and focus/semantics behavior;
- no custom-rendered or decorative search widget.

The existing `Notas` and `Registros` buttons remain visible and retain their
current labels and actions. The recent-capture heading and content remain in
place; Search is inserted as a retrieval affordance, not used to replace the
History surface.

The Search screen uses an `OutlinedTextField` rather than introducing a
Material `SearchBar` abstraction. This is the smallest repository-compatible
native surface and provides a clear label, keyboard action, and visible submit
control without adding dependency or state complexity.

Planned field contract:

- label: `Buscar en Memoria`;
- concise placeholder such as `Escribe para buscar`;
- `ImeAction.Search`;
- trailing search action with accessible label `Buscar`;
- action disabled while a search is loading;
- field remains readable under text scaling and does not require horizontal
  scrolling.

## Search execution and state model

The screen keeps draft text separate from the last submitted query. Submission
is explicit through IME Search or the visible action.

1. Read the current draft.
2. If `draft.trim().isBlank()`, do nothing: do not call `LocalSearch`, do not
   enter Loading, and do not show a meaningful result state.
3. For a nonblank draft, preserve the submitted input for retry/copy and call
   `localSearch.search(draft)` exactly once. The application boundary remains
   responsible for trimming and all matching semantics.
4. Show Loading while the call is in flight.
5. Map a nonempty returned list to Results without changing its order.
6. Map an empty returned list to Empty for the submitted query.
7. Map ordinary failures to Failed with generic user-facing copy and a Retry
   action. Exception details, SQL, IDs, and personal diagnostic content never
   reach the screen.

The state model is intentionally small:

```text
Initial
Loading(submittedInput)
Results(submittedInput, results)
Empty(submittedInput)
Failed(submittedInput)
```

There is no automatic search on first composition. A later valid submission
replaces the previous result state. A blank submit does not clear or replace a
previous valid result state. Retry repeats the exact last submitted input and
does not use the current unsent draft.

`CancellationException` must be rethrown and continue normal coroutine
cancellation. Only ordinary failures are converted to Failed. The planned
failure copy is `No se pudo buscar en Memoria.` with an accessible
`Reintentar búsqueda` action.

Initial state copy explains the searchable sources concisely:
`Busca en tus capturas, notas, registros y listas.`

Loading copy is `Buscando…`. Empty state copy is query-specific but restrained,
for example `No encontramos resultados para «<query>».` The query is the
user's own submitted text; no exception or hidden source data is interpolated.

## Result presentation contract

Results use one flat `LazyColumn`. Compose does not group, re-sort, cap,
filter, or highlight. The list order is the list returned by `LocalSearch`.
Cards are read-only and have no click action.

Every card uses a distinguishable Material icon/label, readable typography,
and local timestamp context. The result's source ID is never shown.

| Result kind | Required visible content | Context/state rules |
|---|---|---|
| `CAPTURE` | `displayText`, `Texto` or `Voz`, and timestamp | Use `captureKind`; do not re-derive Voice correction or query semantics in UI. |
| `NOTE` | `displayText`, `Nota`, and timestamp | Preserve exact returned Note text; no edit/delete affordance. |
| `STRUCTURED_LOG` | `Registro`, every returned key/value field, and timestamp | Render `fields` in the order supplied by `LocalSearch`; do not sort or reconstruct from `displayText`. |
| `LIST_ITEM` | `displayText`, list definition name, completion state, and timestamp | Show `Mandado`/`Compras`; when session context exists, show a human-readable session date/context without raw IDs. |

For List Items, `createdAt` is the result timestamp. A session-backed item
may additionally show context such as `Sesión del <local start time>` and a
historical/current cue when the returned session end value permits that
distinction. Continuous list items show their list definition without a
fabricated session. The UI never uses session timestamps to reorder results.

Completion is communicated with both a semantic icon and visible text such
as `Completado` or `Pendiente`; color is supplemental only. No result card
offers a checkbox, mutation action, or source-opening action.

## Application wiring

Production dependency direction remains:

```text
QuickAsideApplication.localSearch
        → MainActivity
        → QuickAsideApp
        → ManagementScreen / SearchScreen
```

`MainActivity` will pass the already app-scoped `localSearch` property. The
Composable API may retain a nullable default for existing direct Compose test
setups, following the repository's existing optional `listStore`/
`memoryStore` compatibility pattern; the production activity always supplies
the real boundary. Search UI tests inject a fake `LocalSearch` and never open
Room. No Compose code may construct `RoomLocalSearch` or call a DAO.

No change to `QuickAsideApplication` ownership or database creation is
planned; the existing `localSearch` property is the one production instance.

## Accessibility, layout, and one-handed safety

The implementation must provide useful semantics/content descriptions for:

- opening Search from History (`Abrir búsqueda en Memoria`);
- the Search field and its visible/IME submit action (`Buscar`);
- the Search top-bar Back action (`Volver a Memoria`);
- result kind/context when the visible text is insufficient;
- retry (`Reintentar búsqueda`);
- List Item completion state through text and semantics, not color alone.

The field is near the top of Memoria's nested surface and the primary submit
action is reachable without a precision gesture. Material minimum touch-target
behavior is preserved. Cards use normal wrapping text and a `LazyColumn` so
large text does not require a fixed-height layout or hide required fields.

Search results use the proven memory-screen scaffold/list pattern and at least
the existing `160.dp` bottom content padding. The final result card must be
able to scroll fully above the global Capture FAB, with a focused UI assertion
against the FAB bounds. The exact padding may be adjusted during visual/device
verification only if the final card remains fully reachable and no unrelated
memory screen is redesigned.

## Persistence and architecture guardrails

- `QuickAsideDatabase.version` remains `4`.
- Tracked schemas remain exactly `1.json`, `2.json`, `3.json`, and `4.json`.
- No migration, schema 5, table, index, FTS object, or entity change is
  allowed.
- `LocalSearch` remains the application boundary; `RoomLocalSearch` remains
  the sole Room implementation.
- Search is read-only and does not call `MemoryStore`, source write APIs, or
  any result mutation operation.
- No production dependency is added.

If implementation appears to require any persistence or dependency expansion,
stop and report rather than widening Change 017.

## Acceptance scenarios

The implementation plan must provide deterministic evidence for at least the
following scenarios:

1. Selecting Memoria still opens History by default.
2. History shows the prominent full-width `Buscar en Memoria` affordance.
3. The existing Notes affordance remains visible and available.
4. The existing Registros affordance remains visible and available.
5. Activating Search opens the local Search route.
6. Search shows the `Buscar` top-app-bar title and Back returns to Memoria.
7. Initial Search composition does not call `LocalSearch.search`.
8. Blank and whitespace-only submission does not call `LocalSearch.search`.
9. A nonblank submitted query reaches the injected `LocalSearch` boundary.
10. Loading is visible while the boundary call is pending.
11. A Capture result renders its text, Texto/Voz label, and timestamp.
12. A Note result renders its text, Nota label, and timestamp.
13. A Structured Log renders every returned field in supplied field order.
14. A List Item renders list definition, item text, completion, timestamp, and
    available human-readable session context.
15. A completed List Item has a non-color-only completion indication.
16. Mixed result cards preserve the exact boundary order.
17. An empty result shows restrained query-specific empty copy.
18. A failure shows generic user-facing copy and no exception detail.
19. Retry repeats the intended submitted query.
20. Cancellation is not converted into a user-facing failure state.
21. A long result list can scroll its final card fully above the Capture FAB.
22. The bottom navigation still has exactly Inicio, Pendientes, Listas, and
    Memoria.
23. The global Capture action remains available from Search.
24. Search is not exposed as a bottom-navigation destination.
25. Search UI performs no source mutation and result cards expose no edit,
    delete, completion, correction, deep-link, or source-opening action.

## Visual evidence contract

Because this is a material UI change, later implementation must capture and
inspect at least:

1. Memoria with the prominent Search affordance and Notes/Registros still
   available.
2. Search initial state with field, helper copy, Back, bottom navigation, and
   global Capture action.
3. Search results containing all four result kinds, including a historical
   Mandado item and a completed item when practical.
4. Search empty state.

Use a real CPH2791 / Android 16 device when available. Compare screenshots
against `docs/UX_UI_REFERENCE.md`, the canonical v3 image's Memoria hierarchy,
and the acceptance scenarios. Record any necessary native-layout deviation;
do not copy the image's obsolete product label or incidental pixels.

## Verification and authority

This planning turn must not run unnecessary Gradle gates and must not change
production code. The later implementation turn should run focused Search UI
instrumentation with a fake `LocalSearch`, then the repository-required unit,
assemble, lint, and connected Android-test gates as applicable, plus:

- `git diff --check`;
- `git status --short` and diff statistics;
- inspection that Room version 4 and schemas 1–4 are unchanged;
- inspection that there is one app-scoped `LocalSearch` and no DAO access from
  Compose;
- visual/device evidence and accessibility/layout review.

The user retains commit, merge, release, and push authority. Do not merge or
push this change from implementation work.

## Risks and stop conditions

Risks to watch during implementation:

- the existing app shell hides nested routes differently from the intended
  Search Back behavior;
- a result card can appear readable but still be occluded by the Capture FAB;
- nullable test wiring accidentally becomes a production fallback instead of
  passing the app-scoped boundary;
- adding a Material SearchBar introduces unnecessary state or dependency
  complexity;
- structured-log field order or List session context is silently changed by
  UI formatting;
- result cards accidentally become interactive while trying to add semantics.

Stop and report if the change requires a schema/migration/FTS/index, a new
database or dependency, direct DAO access, a `MemoryStore` redesign, a fifth
destination, AI/semantic behavior, result ranking, source mutation, or a
broader Memoria redesign.

## Exact next gate

The Change 017 package must be independently reviewed and committed before
production implementation begins.

## Authority

This package defines the planned scope only. The independent reviewer owns the
final verdict after implementation evidence; this planning turn does not
declare an engineering PASS.
