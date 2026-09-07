# Quick Aside — Active Work

## Active change

`docs/changes/018-action-ledger-foundation/`

Status: **PLAN/DOCS ONLY — IN PROGRESS**
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

## Exact next gate

The Change 018 planning package must be independently reviewed and committed
before production implementation begins.
