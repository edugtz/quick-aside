# Quick Aside — Active Work

## Active change

`docs/changes/007-transcript-correction-persistence/`

Status: **IN PROGRESS**
Governance: **HIGH-ASSURANCE**

## Proven baseline

- Product name accepted: **Quick Aside**.
- Product/UX baseline accepted for personal MVP.
- Canonical written UX contract: `docs/UX_UI_REFERENCE.md`.
- Canonical visual-direction reference: `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`.
- Architecture and roadmap are proposed/accepted baselines pending implementation evidence.

## Current implementation focus

Change 007 adds durable transcript-correction support while preserving the
original Voice recognition result. It is persistence/domain support only; the
transcript-editing UI remains deferred to Change 008.

## Exact next gate

Complete and independently review the explicit Room 1→2 migration, correction
write boundary, effective-transcript read behavior, and required migration,
regression, build, lint, and connected-test evidence.
