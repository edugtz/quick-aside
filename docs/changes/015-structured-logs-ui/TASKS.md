# Change 015 — Structured Logs UI — TASKS

## Change package and preflight

- [x] Confirm expected branch and repository state.
- [x] Read project, architecture, roadmap, acceptance, naming, UX, and
      Changes 013–014 guidance.
- [x] Inspect the canonical v3 UI reference before implementation planning.
- [x] Create the Change 015 SPEC, PLAN, and TASKS package.
- [x] Point `docs/ACTIVE_WORK.md` at Change 015 IN PROGRESS.

## Navigation and discoverability

- [x] Add the local Memory History/Notes/StructuredLogs route while preserving
      default Capture History behavior.
- [x] Reset the Memory route on bottom-navigation selection.
- [x] Return from Registros through toolbar and system back.
- [x] Add restrained accessible `Notas` + `Registros` affordances.
- [x] Preserve the four bottom destinations and global Capture action.

## Registros UI

- [x] Add loading, loaded, empty, failure, and retry states.
- [x] Add one-row Campo/Valor editor with add/remove controls.
- [x] Keep at least one row available.
- [x] Validate complete pairs, blank/partial rows, exact duplicate keys, and
      exact whitespace preservation.
- [x] Pass `sourceCaptureId = null` and handle every creation result safely.
- [x] Show Saved records immediately and reset the editor.
- [x] Retain rows and generic feedback on failure.
- [x] Render read-only cards with local time and key-ascending fields.
- [x] Reuse the deterministic timestamp formatter with fixed test inputs.
- [x] Add focused Notes bottom padding for FAB clearance.

## Automated tests and evidence

- [x] Add deterministic fake-MemoryStore Compose coverage for Change 015
      acceptance scenarios and existing memory navigation regressions.
- [x] Add a named v4 Room create/read/close/reopen StructuredLog integration
      regression.
- [x] Run `./gradlew :app:testDebugUnitTest`.
- [x] Run `./gradlew :app:assembleDebug`.
- [x] Run `./gradlew :app:lintDebug`.
- [x] Run `./gradlew :app:connectedDebugAndroidTest` (started; blocked by the
      known device Compose startup/idling harness failure before full
      execution).
- [x] Capture representative real-device screenshots and review against the
      written UX contract and v3 visual direction.
- [x] Confirm version 4, unchanged schemas 1–4, no schema 5/migration,
      dependency/AI/STT/Google changes, and isolated databases.
- [x] Run `git diff --check`, `git status --short`, and diff statistics.

## Scope and authority

- [x] Do not modify historical Change 001–014 documentation packages.
- [x] Do not add AI, Capture routing, editing, deletion, reminders, search,
      archive/backup, Action Ledger, Google behavior, or a fifth destination.
- [x] Do not merge or push.
- [x] Prepare the implementation report without declaring the final verdict.
- [ ] Final independent review remains authoritative.

## Verification note

Focused `StructuredLogsUiTest` completed with 14/14 functional tests passing
on CPH2791 (Android 16), the named-database StructuredLog integration
completed with 1/1 test passing, and the existing `NotesUiTest` completed with
13/13 tests passing before the final test-only FAB assertion was added. The
required JVM unit-test, assemble, and lint gates completed on the exact final
tree.

The full connected suite was started at 138 tests and stopped after repeated
`No compose hierarchies found in the app` startup failures in unrelated
existing tests. A bounded six-class regression run was also stopped after the
same device harness family produced `ComposeNotIdleException` before useful
execution. The final Notes rerun also hit the startup failure before reaching
the new FAB assertion. No Structured Logs or Notes product assertion failed
in those blocked runs.
