# Change 019 — Reversible List Item Create + Undo — TASKS

Governance: HIGH-ASSURANCE
Status: IMPLEMENTATION COMPLETE — REVIEW PENDING
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
- [ ] Run focused Mandado UI test on CPH2791 / Android 16 when available.
- [ ] Run focused Compras UI test on CPH2791 / Android 16 when available.
- [ ] Capture and inspect mandado-create-undo.png and compras-create-undo.png.
- [x] Run testDebugUnitTest, assembleDebug, and lintDebug.
- [x] Verify Room remains version 5, schemas 1–5 are unchanged, and no
      migration/dependency/old-table/out-of-scope mutation exists.
- [x] Run git diff --check and inspect git status --short and git diff --stat.
- [ ] Leave independent engineering review unchecked.

## Evidence log

- Focused JVM contract test and full `:app:testDebugUnitTest`: BUILD SUCCESSFUL.
- Focused Room `ReversibleListItemActionsDatabaseTest`: 9/9 passed on CPH2791 / Android 16.
- `:app:compileDebugAndroidTestKotlin`, `:app:assembleDebug`, and `:app:lintDebug`: BUILD SUCCESSFUL.
- Mandado create/receipt/Undo device evidence is BLOCKED: the row was present
  in the captured hierarchy, but the focused test hit the same Compose timeout
  twice while resolving the preserved-whitespace row; the project stop rule
  prevented another blind retry.
- Compras UI evidence is BLOCKED: the connected run lost Compose hierarchies
  after an external UTP abort; this is not recorded as PASS.
- Screenshots were not captured because the focused device evidence did not
  reach a verified create/Undo checkpoint.

## Authority

- [ ] Do not commit, push, merge, release, or mark independent review passed.

Closeout target: IMPLEMENTATION COMPLETE — REVIEW PENDING, subject to actual
evidence and blocked-device reporting.
