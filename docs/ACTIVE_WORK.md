# Quick Aside — Active Work

## Active change

`docs/changes/019-list-item-create-undo/`

Status: **IMPLEMENTATION COMPLETE — REVIEW PENDING**
- Governance: **HIGH-ASSURANCE**

## Proven baseline

- Product name accepted: **Quick Aside**.
- Product/UX baseline accepted for personal MVP.
- Canonical written UX contract: `docs/UX_UI_REFERENCE.md`.
- Canonical visual-direction reference: `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`.
- Architecture and roadmap are proposed/accepted baselines pending implementation evidence.

## Current implementation focus

Change 019 is the first user-visible Action Ledger integration. It adds a
narrow atomic create/undo boundary for manual Mandado and Compras items while
preserving the completed Change 018 version-5 database and schema. The changed
UI uses the existing shared Material snackbar and keeps the four-destination
navigation and global Capture action unchanged.

Preflight evidence:

- Required project contracts and Change 018 guidance read.
- Current Action Ledger/list implementation, Room DAOs, app wiring, list UI,
  and focused tests inspected.
- docs/UX_UI_REFERENCE.md and the canonical v3 PNG inspected.
- No Room version, schema, migration, dependency, or historical package
  modification is planned.

Implementation evidence:

- The narrow reversible list-item boundary, Room transactions, app wiring, UI
  receipt/Undo flow, and fake/integration tests are implemented.
- Focused JVM and full debug JVM tests pass; the focused Room suite passes 9/9
  on CPH2791 / Android 16; assemble, lint, Android-test compilation, schema,
  and diff checks pass.
- Focused Mandado UI was re-run and passes 1/1 on CPH2791 / Android 16 with the
  semantics-existence assertion for the preserved-whitespace row. The exact
  command was `./gradlew :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=com.edu.quickaside.MandadoUiTest#exactItemTextUsesReversibleBoundaryShowsReceiptAndUndoRemovesExactItem`.
- The authoritative focused Compras result is PASS 1/1 on CPH2791 / Android
  16 for `exactTextUsesReversibleBoundaryWithNullSessionAndUndoRemovesExactItem`;
  it was not rerun in this turn.
- The existing `mandado-create-undo.png` was visually inspected against the
  written UX contract and canonical v3 direction. It includes the product row,
  `Producto agregado`/`Deshacer`, Capture FAB, and four-destination navigation;
  no obvious snackbar/FAB/navigation collision or clipping was observed.
- The required Compras screenshot remains blocked: after the focused test,
  CPH2791 disconnected while launching the real app; `adb devices -l` returned
  no devices and `adb mdns services` returned no discovered services. The CUA
  surface reset before exposing a usable device session. No invalid frame is
  counted as evidence.

## Exact next gate

Independent engineering review after the user commits/pushes. The reviewer
must treat the missing Compras screenshot as blocked evidence rather than as
a passing visual gate and may require a clean device capture before approval.
