# Change 019 — Reversible List Item Create + Undo — PLAN

Governance: HIGH-ASSURANCE
Status: IMPLEMENTATION COMPLETE — REVIEW PENDING
Expected branch: chg-019-list-item-create-undo

The user-defined contract is authoritative; this plan records execution and
evidence without re-planning the feature.

## Preflight findings

- Expected branch is checked out and the starting worktree is clean.
- Change 018 is complete: Action Ledger is persisted at Room version 5 and has
  app-scoped ActionLedgerStore wiring.
- RoomListStore.addItem is the source of existing list/session validation and
  exact-text behavior.
- QuickAsideApp already owns one shared SnackbarHostState; list screens already
  update local state after successful creates.
- UX_UI_REFERENCE.md and the canonical v3 PNG were read/inspected.
- No dependency, schema, migration, old-table, or historical-package change is
  planned.

## Implementation sequence

1. Keep this SPEC/PLAN/TASKS package live and point ACTIVE_WORK at it.
2. Add ReversibleListItemActions plus deterministic create/Undo result types and
   minimal injectable item-ID/clock support.
3. Extract a small package-private data-local validation helper and use it from
   both RoomListStore.addItem and the new action implementation.
4. Add RoomReversibleListItemActions. Coordinate list and Action Ledger DAOs
   directly inside one write transaction for create and one for Undo. Add only
   ListItemDao.deleteById.
5. Wire one app-scoped implementation through QuickAsideApplication,
   MainActivity, QuickAsideApp, and ManagementScreen into both list screens.
6. Make create show Producto agregado with Deshacer using the shared snackbar;
   newer creates dismiss stale receipts. Call Undo with the receipt's exact IDs,
   remove only the exact local row on success, and reconcile on failure.
7. Add deterministic fake-based Compose coverage and a unique-database Room
   integration test with create-child and Undo-mark failure injection.
8. Capture device screenshots when available, run required verification, update
   TASKS and ACTIVE_WORK with actual evidence, and leave independent review open.

## Expected files

Production: application/lists/ReversibleListItemActions.kt,
data/local/ListItemMutationSupport.kt,
data/local/RoomReversibleListItemActions.kt, ListItemDao.kt,
RoomListStore.kt, QuickAsideApplication.kt, MainActivity.kt, QuickAsideApp.kt,
MandadoScreen.kt, ComprasScreen.kt.

Tests/docs: focused JVM action/result tests,
ReversibleListItemActionsDatabaseTest, MandadoUiTest, ComprasUiTest, this
package, ACTIVE_WORK, and evidence screenshots/README if captured.

QuickAsideDatabase, Action Ledger entities/mappers/schema/migrations,
dependencies, and unrelated Memory/Capture/Search files are not expected to
change.

## Verification matrix

JVM: focused result/input contract tests.

Room: Compras and Mandado create with exact text/session/ledger shape; all
validation outcomes; create rollback; successful Undo; unrelated preservation;
reopen durability; second Undo; mismatch/missing/unsupported shape; Undo
rollback; cancellation; unique named databases only.

Compose/device: fake action boundary on both screens; immediate row, input
clear, native receipt/action, exact-ID Undo, target-only removal, create/Undo
errors and reconciliation; completion remains on ListStore; back, four
destinations, and Capture FAB remain.

Host gates:

    ./gradlew :app:testDebugUnitTest
    ./gradlew :app:assembleDebug
    ./gradlew :app:lintDebug
    git diff --check
    git status --short
    git diff --stat

Focused connected tests and screenshots run on CPH2791 / Android 16 when
available; identical known UTP failures are recorded once as blocked.

## Closeout

If available implementation gates pass but independent engineering review has not
occurred, close with IMPLEMENTATION COMPLETE — REVIEW PENDING. Leave the review
task unchecked and state the next gate as independent engineering review after
the user commits/pushes.
