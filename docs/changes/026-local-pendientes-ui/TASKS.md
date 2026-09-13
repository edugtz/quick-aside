# Change 026 — Local Pendientes UI Foundation — TASKS

Governance: **STANDARD**
Status: **COMPLETE — REVIEW PASS**
Expected branch: `chg-026-local-pendientes-ui`

This is the CHG-026 closeout checklist. Mark an item complete only after the
work or evidence exists; the independent engineering review is recorded below.

## Change package and preflight

- [x] Verify clean starting worktree and exact `origin/main` baseline.
- [x] Fetch `origin` and verify `origin/main == b4333a11e05403c1afe96890e0ecf73ec1f634ea`.
- [x] Create/switch to `chg-026-local-pendientes-ui` without committing.
- [x] Read governing docs, naming rules, UX reference, and CHG-024/025
      contracts.
- [x] Inspect the canonical v3 image, app wiring, Task boundaries, current
      Listas UI precedent, and applicable Compose tests.
- [x] Confirm authorized Oppo CPH2791 / Android 16 is available.
- [x] Create the CHG-026 SPEC/PLAN/TASKS package.
- [x] Point `docs/ACTIVE_WORK.md` at CHG-026 with its initial status IN PROGRESS.

## Production implementation

- [x] Add local Pendientes loading/error/loaded UI using `TaskStore.readAll()`.
- [x] Add Personal/Trabajo selection, in-memory filtering, deterministic
      pending/completed ordering, and due-date display.
- [x] Add inline manual create using exact text, selected space, and null date.
- [x] Add targeted create Undo and failure-safe receipts.
- [x] Add complete/reopen controls using exact Task IDs and targeted Undo.
- [x] Preserve global capture FAB and avoid a second Task FAB.
- [x] Wire app-scoped TaskStore and reversible Task actions through the shell.
- [x] Gate manual creation on a Loaded task snapshot and keep `withTask`
      defensive for Loading/Failed.
- [x] Confirm no Room, domain, sync, AI/runtime, dependency, or unrelated UI
      scope leakage.

## Focused UI tests

- [x] Add `PendientesUiTest` with local fakes in the test file.
- [x] Cover root UI/default Personal, Trabajo filtering, due dates, lifecycle
      sections, both empty states, and deterministic ordering.
- [x] Cover exact create title/space/null due date, blank input, success,
      create failure, and exact create Undo IDs.
- [x] Cover exact complete/reopen IDs, success Undo, completion Undo reload,
      failure/no-op behavior, and no invented success.
- [x] Cover persistent Google Tasks not-connected status and global capture.
- [x] Cover unavailable snapshot blocking, no create call, input preservation,
      Retry recovery, and existing-task retention.

## Verification gates

- [x] Review-patch `PendientesUiTest` passes 14/14 on Pixel API 35 and CPH2791 /
      Android 16.
- [x] Review-patch `QuickAsideAppTest` passes 1/1 on both connected devices.
- [x] CHG-024 Task-create Room regression passes.
- [x] CHG-025 Task-completion Room regression passes.
- [x] Full JVM unit tests pass.
- [x] Pre-review-patch connected Android suite passed 249/249 after the
      pre-existing Mandado test synchronization repair; the full suite was not
      rerun for this targeted Pendientes-only review patch. See evidence log.
- [x] `assembleDebug` succeeds.
- [x] `lintDebug` succeeds.
- [x] Room remains v7; migrations and schemas are unchanged.
- [x] `git diff --check`, status/stat, and exact-scope diff inspection are
      clean.
- [x] Representative CPH2791 screenshots are obtained and inspected against
      UX v3: Personal with pending/completed tasks and Trabajo when practical.
- [x] Builder report is prepared without assigning the independent verdict.
- [x] Independent engineering review complete: PASS (BLOCKER 0 / MAJOR 0 /
      MINOR 0), reviewed head `fe238b59d838c3967da572c7b61a9e43402354d6`.

## Evidence log

- Preflight is complete as recorded in PLAN.md.
- Production compile and Android-test compile passed.
- Original pre-review-patch `PendientesUiTest` passed 13/13 on CPH2791 /
  Android 16.
- Independent review of reviewed head `eb20cc18f1b832259e4a510b713327e524111f07`
  found exactly one **MINOR**: Loading/Failed could be promoted to a partial
  `Loaded(listOf(newTask))` after manual create.
- The targeted fix gates add/keyboard Done on Loaded, preserves input while
  Loading/Failed, and makes `withTask` preserve unavailable states.
- Review-patch `PendientesUiTest` passed 14/14 on `Pixel_9_Pro(AVD) - 15`
  (API 35) and 14/14 on `CPH2791 - 16` (API 36). It verifies no create action
  call while unavailable, Retry recovery, and existing-task retention.
- Review-patch `QuickAsideAppTest` passed 1/1 on both connected devices.
- Visual review passed for Personal empty, Personal pending/completed, Trabajo,
  Personal/Trabajo hierarchy, due dates, completed distinction, honest Google
  Tasks status, the global capture FAB, and no obsolete VoiceApp branding.
- `QuickAsideAppTest` passed on an isolated rerun; CHG-024 passed 12/12 and
  CHG-025 passed 14/14.
- Full JVM passed 120/120 with 0 skipped and 0 failures/errors.
- The retained connected result identified
  `com.edu.quickaside.MandadoUiTest#undoFailureReloadsVisibleStateAndShowsConciseError`
  failing at `MandadoUiTest.kt:198` with `ComposeTimeoutException` while
  waiting for the added product via a displayed-node assertion. The exact
  method passed on both CHG-026 and detached `origin/main`, so the failure was
  pre-existing rather than a CHG-026 regression.
- The smallest test-only fix uses the existing semantic-presence helper for
  that wait, avoiding the viewport/IME display assumption. The repaired method
  passed 3/3; `MandadoUiTest` passed 12/12, `MandadoHistoryUiTest` 7/7, and
  `QuickAsideAppTest` 1/1. No Mandado production code changed.
- The first post-fix full run had one unrelated Transcript correction timeout;
  its exact method passed in isolation. The pre-review-patch connected suite
  passed 249/249 with 0 skipped and 0 failures on `Pixel_9_Pro(AVD) - 15`
  (API 35); it was not rerun for this targeted review patch.
- `assembleDebug`, `lintDebug`, Room version/schema checks, and
  `git diff --check` passed. Lint reported no issue entries; remaining text
  output is version-availability guidance.
- Device screenshots were captured and inspected for Personal empty plus
  pending/completed states and Trabajo. They remain temporary evidence and
  were not added to the repository.

## Exact stop state

`COMPLETE — REVIEW PASS`

No commit, push, merge, release, or future-change selection was performed.

## Exact next gate

Implementation and obtainable verification are complete:

`COMPLETE — REVIEW PASS`

Exact next gate: user-authorized commit/push of the CHG-026 docs-only
closeout, followed by remote verification. After successful remote closeout
verification, the user may merge `chg-026-local-pendientes-ui` into `main`.
After merge, verify `main == branch` before selecting the next reviewable
change.
