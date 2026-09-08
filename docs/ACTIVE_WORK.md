# Quick Aside — Active Work

## Active change

`docs/changes/018-action-ledger-foundation/`

Status: **COMPLETE — REVIEW PASS**
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
- Focused connected `ActionLedgerPersistenceDatabaseTest` passed 10/10 on
  CPH2791 / Android 16 (Started 10 / Finished 10 / BUILD SUCCESSFUL in 16s).
- Real v4→v5 migration evidence passed; schema 5 inspection confirmed only
  the planned Action Ledger structures.
- Final independent review verdict: PASS (BLOCKER: 0 / MAJOR: 0 / MINOR: 0).

Change 018 verification is complete. Generated schema and migration-source
scope, diff, and status evidence are otherwise inspected locally.

## Exact next gate

Change 018 is complete. The next reviewable M1 change has not started yet.
