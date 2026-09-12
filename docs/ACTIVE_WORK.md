# Quick Aside — Active Work

## Active change

`docs/changes/025-reversible-task-completion/`

Status: **IMPLEMENTATION COMPLETE — REVIEW PENDING**

Governance: **HIGH-ASSURANCE**

CHG-025 is being implemented on `chg-025-reversible-task-completion` from the
verified `origin/main` baseline `9d411908ec529117c9daf9fa23a3c8509a719346`.
`origin/chg-024-reversible-task-create` resolves to the same SHA and is an
ancestor of `origin/main`. The worktree was clean before branch creation.

The change extends the existing provider-independent `ReversibleTaskActions`
boundary with atomic Task completion/reopen and targeted completion Undo. It
keeps `TaskStore` stable-ID UPSERT behavior, Room at version 7, schemas 1–7,
and runtime AI paused. No UI, CapturePlan execution, Google sync, reminders,
generic Undo, or provider/runtime work is included.

Implementation and obtainable verification are complete. Focused and full
JVM, device, regression, build, lint, schema, and diff evidence is recorded in
the CHG-025 package. The independent-review checklist remains open.

## State

- Changes 020, 021, 023, and 024 are merged/complete at this verified base.
- CHG-024's Task CREATE ledger contract remains unchanged.
- Completion UPDATE payload v1 is frozen to `pending` and
  `completed:<epochMillis>` with strict targeted Undo validation.
- Independent review, commit, push, merge, release, and final verdict remain
  user-owned actions.

## Proven baseline

- Product name accepted: **Quick Aside**.
- Canonical written UX contract: `docs/UX_UI_REFERENCE.md`.
- Canonical visual-direction reference:
  `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`.
- Durable runtime decision: `docs/adr/0001-private-remote-ai-runtime.md`.
- Roadmap and acceptance sources: `docs/ROADMAP.md` and
  `docs/ACCEPTANCE_CRITERIA.md`.

## Exact next gate

The implementation and all obtainable verification work is complete. The
package is at:

`IMPLEMENTATION COMPLETE — REVIEW PENDING`

The independent-review checklist item must remain unchecked. The next gate is
user-authorized commit/push of the combined CHG-025 implementation, tests,
docs, and evidence, followed by independent engineering review of
`main...chg-025-reversible-task-completion`.
