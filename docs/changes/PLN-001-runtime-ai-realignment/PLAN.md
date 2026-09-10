# PLN-001 — Runtime AI Realignment — PLAN

Governance: **STANDARD**
Status: **PLANNING UPDATE COMPLETE — REVIEW PENDING**
Expected branch: `planning-runtime-ai-realignment`

## Purpose

Docs-only reconciliation of Quick Aside's canonical project documentation with
the runtime-AI decisions and evidence established after Change 021. No
production code, dependencies, schemas, tests, or UI are touched. Change 022
remains unselected.

## Ground truth inspected

Repository (current clean `main`, `7190d65 docs: close Change 021`, Change 021
confirmed merged):

- AGENTS.md
- docs/ACTIVE_WORK.md
- docs/PROJECT_SPEC.md
- docs/ARCHITECTURE.md
- docs/ROADMAP.md
- docs/ACCEPTANCE_CRITERIA.md
- docs/AI_WORKFLOW.md
- docs/NAMING.md
- docs/UX_UI_REFERENCE.md (read-only; no changes)
- docs/changes/020-capture-plan-foundation/\*
- docs/changes/021-capture-interpreter-boundary/\*

External decision evidence supplied by the user (read, not modified):

- `QUICK_ASIDE_RUNTIME_MODEL_EVALUATION.md` (2026-09-09)
- `QUICK_ASIDE_HANDOFF_AFTER_MODEL_EVALUATION.md`
- Sanitized Android Codex feasibility spike report (kept outside the Quick
  Aside repository)

No existing ADR convention was found; `docs/adr/` did not exist.

## Stale statements found

1. `AGENTS.md` — "Current starting order" names MiMo-V2.5 primary and implies
   provider work is current.
2. `docs/PROJECT_SPEC.md` §11 — MiMo-V2.5 primary; LongCat/Qwen candidates.
3. `docs/ARCHITECTURE.md` — topology names MiMo primary; §5 policy names MiMo
   primary and LongCat next; §5 credentials allow a direct BYOK path with
   device-keystore secrets; §12 lists "AI gateway for a private build" as
   something to avoid; §1 has no accepted private-runtime exception.
4. `docs/ROADMAP.md` — M2 lists "MiMo-V2.5 provider integration" as an active
   capability; no pause/blocker status on M2–M6.
5. `docs/ACCEPTANCE_CRITERIA.md` — "MiMo-V2.5 is the initial primary provider".
6. `docs/AI_WORKFLOW.md` §3 — MiMo primary order; §4 "know when MiMo is
   insufficient".
7. `docs/ACTIVE_WORK.md` — still points at Change 021 as ready for merge.

## Changes made

- Created `docs/adr/0001-private-remote-ai-runtime.md` recording context,
  decision, consequences, and unresolved infrastructure questions.
- Created this `PLN-001` package (SPEC/PLAN/TASKS).
- Updated `AGENTS.md` runtime AI policy: Luna Low primary target, DeepSeek
  fallback candidate, off-device secrets, pause, ADR pointer.
- Updated `docs/PROJECT_SPEC.md` §11 AI runtime policy.
- Updated `docs/ARCHITECTURE.md` goals, high-level topology, §5 runtime AI
  architecture (remote boundary, safety boundary, local-first failure
  behavior, pause), §12 backend strategy exception, and §13 security baseline.
- Updated `docs/ROADMAP.md` with per-milestone status, the cross-project VPS
  dependency, and a milestone dependency summary.
- Updated `docs/ACCEPTANCE_CRITERIA.md` AI interpretation and security/privacy
  criteria.
- Updated `docs/AI_WORKFLOW.md` §3 runtime interpretation models and §4
  observability wording.
- Set `docs/ACTIVE_WORK.md` to this planning update, review pending.

## Decisions intentionally not made here

- No gateway endpoint, authentication/network mechanism, deployment topology,
  process management, or Codex invocation contract is fixed in Quick Aside.
- No retry/outbox/deferred-interpretation mechanics are designed.
- No Change 022 scope is selected. Provider-independent execution/application
  foundations are noted as possible future candidates only.
- Android-local Codex is deferred, not declared impossible.
- No unrelated product, UX, naming, or data decision was altered.
- Completed Change 001–021 packages were not edited.

## Verification executed

- `git diff --check`
- `git status --short`
- `git diff --stat`
- Targeted textual checks for stale MiMo-as-primary wording, untouched Change
  001–021 packages, no source/schema/dependency changes, no Change 022
  creation, and internally consistent blocker wording.

No Android build, Gradle gate, or device test was run; this turn is docs-only.

## Exact next gate

Independent review of the planning/docs diff, then user-authorized merge.
Change 022 selection happens after that, from non-blocked roadmap work.
