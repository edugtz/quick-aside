# Change 017 — Search UI — TASKS

Governance: **STANDARD**  
Status: **COMPLETE — REVIEW PASS_WITH_NOTES**
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

- [x] Add a local `MemoryRoute.Search` while preserving History as Memoria's
      default route.
- [x] Pass the existing app-scoped `LocalSearch` from
      `QuickAsideApplication` through `MainActivity` and `QuickAsideApp`.
- [x] Preserve the nullable direct-Compose test seam without creating a
      production fallback or second Room search instance.
- [x] Add toolbar Back and Android system Back from Search to Memoria/History.
- [x] Reset Search to History on bottom-navigation selection.
- [x] Preserve exactly Inicio, Pendientes, Listas, and Memoria as bottom
      destinations.

## Search entry and execution — later implementation

- [x] Add the prominent full-width `Buscar en Memoria` affordance to History.
- [x] Preserve the existing `Capturas recientes`, `Notas`, and `Registros`
      content and actions.
- [x] Add the native OutlinedTextField search surface with visible and IME
      submit actions.
- [x] Implement explicit submit only; do not add live search or debounce.
- [x] Prevent LocalSearch calls for initial composition and blank input.
- [x] Call only `LocalSearch.search` for nonblank input and preserve the
      boundary's ordering and matching semantics.
- [x] Implement Initial, Loading, Results, Empty, Failed, and Retry states.
- [x] Propagate CancellationException and hide ordinary exception details.
- [x] Retry the exact last submitted query.

## Result presentation — later implementation

- [x] Render a single flat list in the exact order supplied by LocalSearch.
- [x] Render Capture display text, Texto/Voz, and timestamp.
- [x] Render Note display text, Nota, and timestamp.
- [x] Render Structured Log label, all supplied fields in supplied order, and
      timestamp.
- [x] Render List Item text, list definition, completion state, timestamp, and
      available human-readable Mandado session context.
- [x] Keep result cards read-only and omit IDs, highlighting, actions, and
      deep links.
- [x] Communicate List Item completion with visible text and semantics, not
      color alone.
- [x] Use local-time formatting without deriving ordering from formatted text.

## Accessibility and layout — later implementation

- [x] Add semantics/content descriptions for opening Search, the field,
      submit, Back, result context, retry, and completion state.
- [x] Preserve Material touch targets, text wrapping, and text-scaling
      behavior.
- [x] Give the result list sufficient bottom padding for the global Capture FAB.
- [x] Verify the final result card can scroll fully above the FAB.

## Automated tests and evidence — later implementation

- [x] Add a fake-LocalSearch `SearchUiTest` without Room access.
- [x] Cover default History, Search prominence, Notes/Registros retention,
      route/back/reset, four destinations, and no Search bottom destination.
- [x] Cover initial no-call, blank no-call, submitted query, and Loading.
- [x] Cover Capture, Note, Structured Log, List Item, supplied order, all
      fields, session context, and non-color-only completion.
- [x] Cover Empty, Failed, generic error copy, Retry query reuse, and
      CancellationException behavior.
- [x] Cover read-only/no-mutation result behavior and global Capture action.
- [x] Cover final-result/FAB clearance on a sufficiently long result list.
- [x] Capture and inspect `memoria-search-affordance.png`.
- [x] Capture and inspect `search-initial.png`.
- [x] Capture and inspect `search-results-mixed.png`.
- [x] Capture and inspect `search-empty.png` when practical.
- [x] Use CPH2791 / Android 16 real-device evidence when available and record
      any native-layout deviations from the visual direction.

## Verification — later implementation

- [x] Run focused Search UI instrumentation with the fake boundary.
- [x] Run `./gradlew :app:testDebugUnitTest`.
- [x] Run `./gradlew :app:assembleDebug`.
- [x] Run `./gradlew :app:lintDebug`.
- [x] Run `./gradlew :app:connectedDebugAndroidTest` and report device
      infrastructure failures accurately.
- [x] Run `git diff --check`, inspect `git status --short`, and inspect diff
      statistics.
- [x] Confirm Room version 4 and schemas 1–4 remain unchanged.
- [x] Confirm no migration, FTS/index, table, dependency, second database,
      DAO-from-Compose path, or Search bottom destination was introduced.
- [x] Leave the final engineering verdict to independent review.

## Scope and authority

- [x] Do not implement production code in this planning turn.
- [x] Do not run unnecessary Gradle gates in this planning turn.
- [x] Do not commit, push, merge, or release.
- [x] Do not add AI, semantic/fuzzy search, ranking, filters, facets,
      highlighting, result mutations, deep links, archive/backup, reminders,
      Google behavior, schema changes, or a broader Memoria redesign.

## Exact next gate

Change 017 is complete. The next reviewable M1 change has not started yet.

## Verification note

Focused `SearchUiTest` evidence: 35/35 passed, 0 failed, 0 skipped on CPH2791
(Android 16). Unit tests, assemble, and lint passed. The broad connected suite
encountered the known Compose/device harness issue; it is not recorded as a
full connected-suite pass. Reviewed visual evidence: `memoria-search-affordance.png`,
`search-initial.png`, `search-results-mixed.png`, and `search-empty.png`.
Final independent verdict: `PASS_WITH_NOTES`.
