# Quick Aside — Active Work

## Active change

`docs/changes/016-local-search-foundation/`

Status: **IN PROGRESS**
- Governance: **STANDARD**

## Proven baseline

- Product name accepted: **Quick Aside**.
- Product/UX baseline accepted for personal MVP.
- Canonical written UX contract: `docs/UX_UI_REFERENCE.md`.
- Canonical visual-direction reference: `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`.
- Architecture and roadmap are proposed/accepted baselines pending implementation evidence.

## Current implementation focus

Change 016 defines the smallest deterministic local-search foundation for M1.
It will search durable Room history across Captures, Notes, Structured Logs,
and all List Items through a focused application boundary. It does not add
Search UI, FTS, a schema migration, or broader memory-management behavior.
Changes 001–015 are the completed baseline for this change.

## Exact next gate

Implement and verify the committed Change 016 local-search foundation without
adding Search UI, FTS, schema changes, migrations, or broader memory behavior.
