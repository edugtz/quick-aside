# Quick Aside — Active Work

## Active change

`docs/changes/017-search-ui/`

Status: **PLAN/DOCS ONLY — IN PROGRESS**
- Governance: **STANDARD**

## Proven baseline

- Product name accepted: **Quick Aside**.
- Product/UX baseline accepted for personal MVP.
- Canonical written UX contract: `docs/UX_UI_REFERENCE.md`.
- Canonical visual-direction reference: `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`.
- Architecture and roadmap are proposed/accepted baselines pending implementation evidence.

## Current implementation focus

Change 017 defines the smallest deterministic local-search UI for M1. It
consumes the completed Change 016 `LocalSearch` boundary from the existing
Memoria destination, adds a nested Search route and prominent History
affordance, and keeps results flat, read-only, and in boundary order. It does
not add AI search, semantic/fuzzy behavior, mutations, filters, a new bottom
destination, a schema change, or a dependency.

Changes 001–016 are the completed baseline for this change.

## Exact next gate

The Change 017 package must be independently reviewed and committed before
production implementation begins.
