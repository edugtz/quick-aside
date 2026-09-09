# Change 019 — Reversible List Item Create + Undo — TASKS

Governance: HIGH-ASSURANCE
Status: COMPLETE — REVIEW PASS
Expected branch: chg-019-list-item-create-undo

This is a LIVE EXECUTION CHECKLIST. Mark [x] only when actual work/evidence
exists. Failed, skipped, blocked, and not-yet-run gates remain unchecked.

## Change package and preflight

- [x] Confirm branch and inspect starting worktree.
- [x] Read project contracts and Change 018 SPEC/PLAN/TASKS.
- [x] Inspect Action Ledger/list implementation, app wiring, UI, and tests.
- [x] Read UX_UI_REFERENCE.md and inspect the canonical v3 PNG.
- [x] Identify affected UX invariants in this SPEC.
- [x] Create this SPEC, PLAN, and TASKS package.
- [x] Point ACTIVE_WORK at Change 019.
- [x] Confirm no Room version/schema/dependency change is required.

## Application boundary and atomic implementation

- [x] Add ReversibleListItemActions and deterministic results.
- [x] Add injectable item-ID/clock support without broad refactoring.
- [x] Extract/share list-create validation with RoomListStore.
- [x] Implement atomic Room create with one item, entry, and mutation.
- [x] Implement exact-shape, target-checked atomic Undo.
- [x] Add only ListItemDao.deleteById.
- [x] Prove cancellation and deterministic failure outcomes.

## Production wiring and UI

- [x] Wire exactly one app-scoped implementation through the existing path.
- [x] Route both create paths only through the new boundary.
- [x] Preserve field, keyboard, list, session, completion, history, navigation,
      four destinations, and Capture FAB behavior.
- [x] Show native Producto agregado + Deshacer with stale-receipt replacement.
- [x] Pass exact receipt IDs to Undo and update only that local row.
- [x] Reconcile visible state and show concise Undo failure feedback.

## Focused tests

- [x] Add focused JVM result/input tests.
- [x] Add unique-database ReversibleListItemActionsDatabaseTest.
- [x] Cover both list creates, exact ledger shape, all validations, create
      rollback, successful/second/mismatch/missing/unsupported Undo, unrelated
      preservation, Undo rollback, reopen durability, and cancellation.
- [x] Update both Compose tests with fake reversible actions and cover success,
      receipt/Undo IDs, field/errors, reconciliation, completion, navigation,
      four destinations, and Capture FAB.

## Verification and evidence

- [x] Run focused JVM tests.
- [x] Run focused Room test on CPH2791 / Android 16 when available.
- [x] Run focused Mandado UI test on CPH2791 / Android 16 when available.
- [x] Record the authoritative focused Compras UI test result on CPH2791 /
      Android 16.
- [x] Capture and inspect mandado-create-undo.png and compras-create-undo.png.
- [x] Run testDebugUnitTest, assembleDebug, and lintDebug.
- [x] Verify Room remains version 5, schemas 1–5 are unchanged, and no
      migration/dependency/old-table/out-of-scope mutation exists.
- [x] Run git diff --check and inspect git status --short and git diff --stat.
- [x] Independent engineering review complete — PASS (BLOCKER 0 / MAJOR 0 / MINOR 0).

## Evidence log

- Focused JVM contract test and full `:app:testDebugUnitTest`: BUILD SUCCESSFUL.
- Focused Room `ReversibleListItemActionsDatabaseTest`: 9/9 passed on CPH2791 / Android 16.
- `:app:compileDebugAndroidTestKotlin`, `:app:assembleDebug`, and `:app:lintDebug`: BUILD SUCCESSFUL.
- Focused Mandado UI command, re-run in this builder turn: `./gradlew
  :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=com.edu.quickaside.MandadoUiTest#exactItemTextUsesReversibleBoundaryShowsReceiptAndUndoRemovesExactItem`.
  PASS, 1/1 on CPH2791 / Android 16, BUILD SUCCESSFUL. The test uses
  semantics-tree existence for the whitespace-bearing product row while
  preserving the exact submitted text and exact Undo IDs.
- Authoritative focused Compras UI command supplied for this change:
  `./gradlew :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=com.edu.quickaside.ComprasUiTest#exactTextUsesReversibleBoundaryWithNullSessionAndUndoRemovesExactItem`.
  PASS, 1/1 on CPH2791 / Android 16, BUILD SUCCESSFUL; this result was not
  rerun in this turn.
- The recovered `evidence/mandado-create-undo.png` was re-verified on
  2026-09-09: file exists, `file` reports PNG image data 1080 x 2354
  8-bit/color RGBA, 165179 bytes; `sips` confirms 1080 x 2354 png. Inspected
  against docs/UX_UI_REFERENCE.md,
  docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png, and SPEC.md. It shows
  Mandado / Mandado actual, legible product rows (Chobani, Arroz, Fruta),
  native `Producto agregado` + `Deshacer` snackbar, `Terminar mandado`,
  global mic Capture FAB, and exactly four destinations (Inicio, Pendientes,
  Listas selected, Memoria); no snackbar/FAB/navigation collision, clipping,
  or legibility defect observed; Capture remains an action, not a fifth
  destination.
- Evidence-closeout on 2026-09-09: CPH2791 / Android 16 was confirmed
  attached. The real production app was launched and used through Listas →
  Compras to add `Leche de avena`, and the direct device screenshot was saved.
  `file` and `sips` confirm both evidence PNGs are 1080 x 2354.
- Both saved screenshots were visually inspected against
  docs/UX_UI_REFERENCE.md, the canonical v3 PNG direction, and SPEC.md.
  Both PASS: product rows are readable; `Producto agregado` and `Deshacer`
  are fully legible; snackbar, Capture FAB, and bottom navigation do not
  materially collide; exactly four destinations remain with Listas selected;
  Capture remains an action rather than a fifth destination; no material
  clipping or unexpected visual regression was observed. Independent engineering
  review: PASS (BLOCKER 0 / MAJOR 0 / MINOR 0) recorded on docs-only closeout.

## Authority

- [x] Independent engineering review recorded as PASS (BLOCKER 0 / MAJOR 0 / MINOR 0).
- [ ] Do not commit, push, merge, or release.

Closeout: COMPLETE — REVIEW PASS — independent final review PASS (BLOCKER 0 /
MAJOR 0 / MINOR 0).
