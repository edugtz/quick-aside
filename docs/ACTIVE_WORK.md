# Quick Aside — Active Work

## Active change

`docs/changes/018-action-ledger-foundation/`

Status: **IMPLEMENTATION IN PROGRESS — VERIFICATION PENDING**
- Governance: **HIGH-ASSURANCE**

## Proven baseline

- Product name accepted: **Quick Aside**.
- Product/UX baseline accepted for personal MVP.
- Canonical written UX contract: `docs/UX_UI_REFERENCE.md`.
- Canonical visual-direction reference: `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`.
- Architecture and roadmap are proposed/accepted baselines pending implementation evidence.

## Current implementation focus

Change 018 defines the durable local Action Ledger foundation required by M1
and later reversible mutations. It evolves the minimal domain entry contract,
adds a focused application store, and moves Room from version 4 to version 5
with only the new Action Ledger tables and their required indexes. It does not
wire existing product mutations into the ledger or implement Undo execution.

Changes 001–017 are the completed baseline for this change.

Implementation evidence so far:

- Action Ledger domain, application, Room, migration, and app-scoped wiring
  are present on this branch.
- Focused JVM domain/mapping tests pass (10 tests).
- The full debug JVM suite passes (63 tests); debug assemble and lint pass.
- Focused Android test sources compile, and schema 5 is generated with only
  the two planned Action Ledger tables and their required metadata.
- Focused connected execution was attempted with no connected device and
  stopped before test execution with `DeviceException: No connected devices!`.

The deterministic Room persistence and real 4→5 migration tests remain
unverified until a supported connected Android device is available. Generated
schema and migration-source scope, diff, and status evidence are otherwise
inspected locally.

## Exact next gate

Run the remaining Change 018 verification contract, then leave the final
engineering verdict to independent review. Commit/push/merge authority remains
with the user.
