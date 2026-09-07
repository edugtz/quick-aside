# Change 017 — Search UI — TASKS

Governance: **STANDARD**  
Status: **PLAN/DOCS ONLY — IN PROGRESS**  
Expected branch: `chg-017-search-ui`

## Package and preflight

- [x] Confirm the expected `chg-017-search-ui` branch and clean starting
      worktree.
- [x] Read `AGENTS.md`, `docs/ACTIVE_WORK.md`, `docs/PROJECT_SPEC.md`,
      `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `docs/NAMING.md`,
      `docs/ACCEPTANCE_CRITERIA.md`, `docs/UX_UI_REFERENCE.md`, and the
      required Changes 005, 012, 014, 015, and 016 packages.
- [x] Inspect the canonical
      `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png` visual reference.
- [x] Inspect current `QuickAsideApp`, `MemoryRoute`,
      `CaptureHistoryScreen`, `NotesScreen`, `StructuredLogsScreen`,
      `QuickAsideApplication`, `MainActivity`, `LocalSearch`,
      `RoomLocalSearch`, and UI-test patterns.
- [x] Create the Change 017 SPEC, PLAN, and TASKS package.
- [x] Point `docs/ACTIVE_WORK.md` at Change 017 PLAN/DOCS ONLY — IN PROGRESS.
- [x] Record the exact next gate: the package must be independently reviewed
      and committed before production implementation begins.

## Navigation and app wiring — later implementation

- [ ] Add a local `MemoryRoute.Search` while preserving History as Memoria's
      default route.
- [ ] Pass the existing app-scoped `LocalSearch` from
      `QuickAsideApplication` through `MainActivity` and `QuickAsideApp`.
- [ ] Preserve the nullable direct-Compose test seam without creating a
      production fallback or second Room search instance.
- [ ] Add toolbar Back and Android system Back from Search to Memoria/History.
- [ ] Reset Search to History on bottom-navigation selection.
- [ ] Preserve exactly Inicio, Pendientes, Listas, and Memoria as bottom
      destinations.

## Search entry and execution — later implementation

- [ ] Add the prominent full-width `Buscar en Memoria` affordance to History.
- [ ] Preserve the existing `Capturas recientes`, `Notas`, and `Registros`
      content and actions.
- [ ] Add the native OutlinedTextField search surface with visible and IME
      submit actions.
- [ ] Implement explicit submit only; do not add live search or debounce.
- [ ] Prevent LocalSearch calls for initial composition and blank input.
- [ ] Call only `LocalSearch.search` for nonblank input and preserve the
      boundary's ordering and matching semantics.
- [ ] Implement Initial, Loading, Results, Empty, Failed, and Retry states.
- [ ] Propagate CancellationException and hide ordinary exception details.
- [ ] Retry the exact last submitted query.

## Result presentation — later implementation

- [ ] Render a single flat list in the exact order supplied by LocalSearch.
- [ ] Render Capture display text, Texto/Voz, and timestamp.
- [ ] Render Note display text, Nota, and timestamp.
- [ ] Render Structured Log label, all supplied fields in supplied order, and
      timestamp.
- [ ] Render List Item text, list definition, completion state, timestamp, and
      available human-readable Mandado session context.
- [ ] Keep result cards read-only and omit IDs, highlighting, actions, and
      deep links.
- [ ] Communicate List Item completion with visible text and semantics, not
      color alone.
- [ ] Use local-time formatting without deriving ordering from formatted text.

## Accessibility and layout — later implementation

- [ ] Add semantics/content descriptions for opening Search, the field,
      submit, Back, result context, retry, and completion state.
- [ ] Preserve Material touch targets, text wrapping, and text-scaling
      behavior.
- [ ] Give the result list sufficient bottom padding for the global Capture FAB.
- [ ] Verify the final result card can scroll fully above the FAB.

## Automated tests and evidence — later implementation

- [ ] Add a fake-LocalSearch `SearchUiTest` without Room access.
- [ ] Cover default History, Search prominence, Notes/Registros retention,
      route/back/reset, four destinations, and no Search bottom destination.
- [ ] Cover initial no-call, blank no-call, submitted query, and Loading.
- [ ] Cover Capture, Note, Structured Log, List Item, supplied order, all
      fields, session context, and non-color-only completion.
- [ ] Cover Empty, Failed, generic error copy, Retry query reuse, and
      CancellationException behavior.
- [ ] Cover read-only/no-mutation result behavior and global Capture action.
- [ ] Cover final-result/FAB clearance on a sufficiently long result list.
- [ ] Capture and inspect `memoria-search-affordance.png`.
- [ ] Capture and inspect `search-initial.png`.
- [ ] Capture and inspect `search-results-mixed.png`.
- [ ] Capture and inspect `search-empty.png` when practical.
- [ ] Use CPH2791 / Android 16 real-device evidence when available and record
      any native-layout deviations from the visual direction.

## Verification — later implementation

- [ ] Run focused Search UI instrumentation with the fake boundary.
- [ ] Run `./gradlew :app:testDebugUnitTest`.
- [ ] Run `./gradlew :app:assembleDebug`.
- [ ] Run `./gradlew :app:lintDebug`.
- [ ] Run `./gradlew :app:connectedDebugAndroidTest` and report device
      infrastructure failures accurately.
- [ ] Run `git diff --check`, inspect `git status --short`, and inspect diff
      statistics.
- [ ] Confirm Room version 4 and schemas 1–4 remain unchanged.
- [ ] Confirm no migration, FTS/index, table, dependency, second database,
      DAO-from-Compose path, or Search bottom destination was introduced.
- [ ] Leave the final engineering verdict to independent review.

## Scope and authority

- [x] Do not implement production code in this planning turn.
- [x] Do not run unnecessary Gradle gates in this planning turn.
- [x] Do not commit, push, merge, or release.
- [ ] Do not add AI, semantic/fuzzy search, ranking, filters, facets,
      highlighting, result mutations, deep links, archive/backup, reminders,
      Google behavior, schema changes, or a broader Memoria redesign.

## Exact next gate

The Change 017 package must be independently reviewed and committed before
production implementation begins.
