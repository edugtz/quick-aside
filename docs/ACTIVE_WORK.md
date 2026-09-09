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
- The recovered `evidence/mandado-create-undo.png` was re-verified on
  2026-09-09: valid PNG 1080 x 2354 RGBA, 165179 bytes. Visually inspected
  against the written UX contract, canonical v3 direction, and SPEC.md. It
  shows Mandado / Mandado actual, legible rows (Chobani, Arroz, Fruta),
  `Producto agregado`/`Deshacer`, `Terminar mandado`, global mic Capture FAB,
  and four-destination navigation with Listas selected; no snackbar/FAB/
  navigation collision, clipping, or legibility defect was observed, and
  Capture remains an action rather than a fifth destination.
- The required Compras screenshot remains blocked and missing: prior CPH2791
  disconnect after the focused test plus current 2026-09-09 `adb devices` /
  `adb devices -l` returning no devices attached, so no real-production-app
  Compras capture was possible. No fake/test-only UI was substituted and no
  invalid frame is counted as evidence.

## Exact next gate

Independent engineering review after the user commits/pushes. The reviewer
must treat the missing Compras screenshot as blocked evidence rather than as
a passing visual gate and may require a clean device capture before approval.
