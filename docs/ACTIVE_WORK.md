# Quick Aside — Active Work

## Active change

`docs/changes/013-memory-persistence-foundation/`

Status: **IN PROGRESS**
- Governance: **HIGH-ASSURANCE**

## Proven baseline

- Product name accepted: **Quick Aside**.
- Product/UX baseline accepted for personal MVP.
- Canonical written UX contract: `docs/UX_UI_REFERENCE.md`.
- Canonical visual-direction reference: `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`.
- Architecture and roadmap are proposed/accepted baselines pending implementation evidence.

## Current implementation focus

Change 013 adds durable local Note and StructuredLog persistence on Room schema
version 4. It preserves existing Capture/List tables and behavior, with no new
visible UI, search, AI extraction, reminders, or routing.

## Exact next gate

Complete the explicit 3→4 migration, deterministic memory persistence tests,
schema inspection, and required build/lint/connected-test evidence.
