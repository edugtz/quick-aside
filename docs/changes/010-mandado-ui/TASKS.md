# Change 010 — Mandado UI — TASKS

## Package and preflight

- [x] Confirm `chg-010-mandado-ui` and the initial clean working tree.
- [x] Read required project, architecture, roadmap, acceptance, UX, Change
      002, and Change 009 guidance.
- [x] Inspect the canonical UX/UI reference image.
- [x] Create the Change 010 SPEC, PLAN, and TASKS package.
- [x] Mark `docs/ACTIVE_WORK.md` as Change 010 IN PROGRESS.

## Listas and Mandado UI

- [x] Replace the Listas placeholder with the two-surface list hub.
- [x] Keep Compras visibly deferred and non-interactive.
- [x] Add focused Mandado state/loading/empty/active/error rendering.
- [x] Add explicit session start and safe result handling.
- [x] Add exact-text item insertion with blank-input guard.
- [x] Add completion through `setItemCompleted` with visible checked rows.
- [x] Add confirmation-gated finish through `finishActiveSession`.
- [x] Add nested back/explicit navigation to the Listas root.
- [x] Add accessible labels and native touch targets.

## Application wiring and scope

- [x] Pass the app-scoped ListStore through MainActivity into the UI.
- [x] Keep Compose free of direct Room/DAO access.
- [x] Avoid changes to Capture, Memoria, or Voice behavior beyond wiring.
- [x] Keep four bottom-navigation destinations and no framework/dependency
      changes.

## Tests and evidence

- [x] Add deterministic fake ListStore Compose tests for the Change 010
      acceptance scenarios.
- [x] Add a real RoomListStore named-database integration flow with close,
      reopen, finish, and history retention.
- [x] Run `./gradlew :app:testDebugUnitTest`.
- [x] Run `./gradlew :app:assembleDebug`.
- [x] Run `./gradlew :app:lintDebug`.
- [x] Run `./gradlew :app:connectedDebugAndroidTest` on `CPH2791` / Android
      16; 72 tests completed successfully.
- [x] Capture and inspect representative visual evidence on the connected
      device.
- [x] Verify DB/schema/dependency invariants and direct DAO boundaries.
- [x] Run `git diff --check`, `git status --short`, and diff stats.
- [x] Prepare the implementation report without declaring the final verdict.

## Authority and deferred scope

- [x] Historical Change 001–009 packages and schema files remain untouched.
- [x] History, Compras behavior, AI, routing, sync, reminders, delete,
  reorder, editing, and schema changes remain deferred.
- [x] Final independent review; verdict: PASS_WITH_NOTES.
