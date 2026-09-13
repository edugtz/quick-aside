# Change 026 — Local Pendientes UI Foundation — PLAN

Governance: **STANDARD**
Status: **COMPLETE — REVIEW PASS**
Expected branch: `chg-026-local-pendientes-ui`

## Preflight findings

- `git fetch origin` completed after repository-write authorization was
  required by the sandbox.
- `origin/main` and `HEAD` resolve to
  `b4333a11e05403c1afe96890e0ecf73ec1f634ea`.
- The worktree was clean before the branch was created.
- `chg-026-local-pendientes-ui` was created from the verified `origin/main`
  without a commit.
- The governing project, architecture, roadmap, acceptance, UX, naming, and
  AI workflow documents were read. The CHG-024 and CHG-025 contracts and UI
  precedents were inspected.
- The canonical v3 visual reference was inspected. Its embedded `VoiceApp`
  heading is obsolete and will not be implemented.
- `adb devices -l` shows an authorized Oppo CPH2791 / Android 16 device.
- Task persistence, action boundaries, Room version 7, and schemas are already
  sufficient; no production contract or schema change is expected.

## Minimal implementation sequence

1. Add the live CHG-026 documentation package and point ACTIVE_WORK at it.
2. Add `PendientesScreen` with local loading/error/loaded state, Personal /
   Trabajo filtering, deterministic ordering, due-date formatting, empty
   states, inline creation, and lifecycle controls.
3. Wire the existing app-scoped `taskStore` and `reversibleTaskActions` from
   `QuickAsideApplication` through `MainActivity` and `QuickAsideApp` into
   `ManagementScreen`.
4. Add focused Compose instrumentation fakes and acceptance coverage without
   changing unrelated tests.
5. Run narrow UI/device checks first, then directly affected regressions,
   full JVM/build/lint checks, schema/version and diff checks, and visual
   evidence.
6. Record only actual evidence and close at implementation-complete/review-
   pending. Leave the branch uncommitted.

## Expected files

Production:

- `app/src/main/java/com/edu/quickaside/MainActivity.kt`
- `app/src/main/java/com/edu/quickaside/ui/QuickAsideApp.kt`
- `app/src/main/java/com/edu/quickaside/ui/tasks/PendientesScreen.kt`

Tests:

- `app/src/androidTest/java/com/edu/quickaside/PendientesUiTest.kt`

Documentation:

- `docs/ACTIVE_WORK.md`
- `docs/changes/026-local-pendientes-ui/SPEC.md`
- `docs/changes/026-local-pendientes-ui/PLAN.md`
- `docs/changes/026-local-pendientes-ui/TASKS.md`

No Task entity/DAO/store, Room schema, migration, dependency, or unrelated
screen change is planned.

## Verification order

1. Focused `PendientesUiTest` on the authorized device.
2. `QuickAsideAppTest` and directly affected navigation/UI tests.
3. CHG-024 Task-create and CHG-025 Task-completion Room regressions.
4. `./gradlew :app:testDebugUnitTest`.
5. `./gradlew :app:connectedDebugAndroidTest` if healthy and practical.
6. `./gradlew :app:assembleDebug`.
7. `./gradlew :app:lintDebug`.
8. Verify Room remains v7 and `app/schemas` is unchanged from `origin/main`.
9. Run `git diff --check`, inspect status/stat and exact scope, then capture
   and inspect required visual evidence.

If a gate fails, identify the first root failure, apply the smallest scoped
fix, and rerun the affected gate. Do not fabricate counts or a device result.

## Verification evidence

- `:app:compileDebugKotlin` and `:app:compileDebugAndroidTestKotlin` passed.
- `PendientesUiTest`: 13/13 passed on the authorized Oppo CPH2791 / Android
  16 device.
- `QuickAsideAppTest` passed on an isolated rerun after one initial runner
  race reported no Compose hierarchy.
- CHG-024 Task-create Room regression: 12/12 passed.
- CHG-025 Task-completion Room regression: 14/14 passed.
- `:app:testDebugUnitTest`: 120 tests passed, 0 skipped, 0 failures/errors.
- Independent review of reviewed head `eb20cc18f1b832259e4a510b713327e524111f07`
  found exactly one **MINOR**: manual create could promote Loading/Failed to
  `Loaded(listOf(newTask))`, losing the unavailable task snapshot.
- The targeted fix gates creation on `PendientesState.Loaded` and makes
  `PendientesState.withTask` preserve Loading/Failed. The focused test covers
  blocked create/no action call, input preservation, Retry recovery, and
  existing-task retention.
- Post-patch `PendientesUiTest` passed 14/14 on both `Pixel_9_Pro(AVD) - 15`
  (API 35) and `CPH2791 - 16` (API 36). Post-patch `QuickAsideAppTest` passed
  1/1 on both devices.
- Post-patch `:app:testDebugUnitTest` passed 120/120 with 0 skipped and 0
  failures/errors; `assembleDebug` and `lintDebug` passed; no Room schema
  changes were present.
- The retained connected result identified
  `com.edu.quickaside.MandadoUiTest#undoFailureReloadsVisibleStateAndShowsConciseError`;
  it timed out at `MandadoUiTest.kt:198` while waiting for the added product
  with `assertIsDisplayed()`. The exact method passed on both CHG-026 and the
  detached `origin/main` baseline, establishing a pre-existing test
  synchronization defect/flake rather than a CHG-026 regression.
- The test-only repair changed that wait to the existing semantic-presence
  helper, removing the brittle viewport/IME display assumption. The exact
  method passed 3/3 focused repetitions after the fix; `MandadoUiTest` passed
  12/12, `MandadoHistoryUiTest` 7/7, and `QuickAsideAppTest` 1/1.
- The first post-fix full run had one unrelated Transcript correction timeout;
  that exact method passed in isolation. The pre-review-patch
  `:app:connectedDebugAndroidTest` passed 249/249 with 0 skipped and 0
  failures on `Pixel_9_Pro(AVD) - 15` (API 35). The full connected suite was
  not rerun for this targeted Pendientes-only review patch.
- `:app:assembleDebug` and `:app:lintDebug` passed. The lint report contains
  no `<issue>` entries; its text suggestions are available-version notices.
- Room remains version 7 and there are no changes under `app/schemas`.
- `git diff --check` passed. Representative CPH2791 screenshots were captured
  and inspected for Personal empty/pending/completed states and Trabajo.

## Independent engineering review closeout

Independent engineering review: **PASS**

Reviewed head: `fe238b59d838c3967da572c7b61a9e43402354d6`

`BLOCKER 0`
`MAJOR 0`
`MINOR 0`

Remote review confirmed that the targeted patch resolved the prior MINOR:

- manual create requires a successfully Loaded task snapshot;
- Loading and Failed cannot be promoted to a partial Loaded state;
- entered text is preserved while the snapshot is unavailable;
- Retry remains available;
- after successful Retry, existing tasks remain present;
- creation becomes available only after a complete local read.

Visual review passed for Personal empty, Personal pending/completed, Trabajo,
Personal/Trabajo hierarchy, due dates, completed distinction, honest Google
Tasks status, the global capture FAB, and the absence of obsolete VoiceApp
branding.

The pre-review-patch connected suite remains recorded as 249/249; it was not
rerun after the targeted review patch. Runtime AI remains paused. No CHG-027 or
other next change has been selected.

## Stop state

`COMPLETE — REVIEW PASS`

No commit, push, merge, release, or future-change selection was performed.

Exact next gate: user-authorized commit/push of the CHG-026 docs-only
closeout, followed by remote verification. After successful remote closeout
verification, the user may merge `chg-026-local-pendientes-ui` into `main`.
After merge, verify `main == branch` before selecting the next reviewable
change.
