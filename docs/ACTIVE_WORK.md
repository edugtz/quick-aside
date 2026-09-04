# Quick Aside — Active Work

## Active change

`docs/changes/008-transcript-correction-ui/`

Status: **IN PROGRESS**
Governance: **STANDARD**

## Proven baseline

- Product name accepted: **Quick Aside**.
- Product/UX baseline accepted for personal MVP.
- Canonical written UX contract: `docs/UX_UI_REFERENCE.md`.
- Canonical visual-direction reference: `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`.
- Architecture and roadmap are proposed/accepted baselines pending implementation evidence.

## Current implementation focus

Change 008 adds explicit-management transcript correction for saved Voice
Captures. It uses the existing Change 007 corrector and preserves the original
recognized transcript; the Voice capture happy path remains automatic.

## Exact next gate

Complete the Change 008 UI, integration tests, build/lint/instrumentation
checks, and screenshot evidence for independent review.
