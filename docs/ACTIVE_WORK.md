# Quick Aside — Active Work

## Active change

`docs/changes/002-core-domain-contracts/`

Status: **COMPLETE — REVIEW PASS_WITH_NOTES**
Governance: **STANDARD**

## Proven baseline

- Product name accepted: **Quick Aside**.
- Product/UX baseline accepted for personal MVP.
- Canonical written UX contract: `docs/UX_UI_REFERENCE.md`.
- Canonical visual-direction reference: `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`.
- Architecture and roadmap are proposed/accepted baselines pending implementation evidence.

## Exact next gate

Implement and verify the pure-Kotlin domain contracts in
`docs/changes/002-core-domain-contracts/`:

1. add the minimal typed identities and Capture, Lists, Tasks, Memory,
   Reminder, and Action Ledger contracts;
2. add deterministic JVM unit tests for the accepted invariants;
3. run the required unit-test, assemble, lint, and diff checks.

Keep Android/UI, Room, integrations, runtime AI, persistence schema, and undo
execution deferred to later changes.
