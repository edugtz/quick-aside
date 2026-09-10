# PLN-001 — Runtime AI Realignment — TASKS

Governance: **STANDARD**
Status: **PLANNING UPDATE COMPLETE — REVIEW PENDING**
Expected branch: `planning-runtime-ai-realignment`

## Preflight

- [x] Confirm current clean `main` and that Change 021 is merged
      (`7190d65 docs: close Change 021`).
- [x] Read `AGENTS.md`, `docs/ACTIVE_WORK.md`, `docs/PROJECT_SPEC.md`,
      `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `docs/ACCEPTANCE_CRITERIA.md`,
      `docs/AI_WORKFLOW.md`, and `docs/NAMING.md`.
- [x] Inspect `docs/changes/020-capture-plan-foundation/*` and
      `docs/changes/021-capture-interpreter-boundary/*`.
- [x] Read the runtime model evaluation, the post-evaluation handoff, and the
      sanitized Android Codex spike report.
- [x] Create the `planning-runtime-ai-realignment` branch from clean `main`.

## Planning package

- [x] Create `docs/changes/PLN-001-runtime-ai-realignment/SPEC.md`.
- [x] Create `docs/changes/PLN-001-runtime-ai-realignment/PLAN.md`.
- [x] Create `docs/changes/PLN-001-runtime-ai-realignment/TASKS.md`.
- [x] Use a non-CHG identifier so Change 022 stays reserved for
      implementation.

## ADR

- [x] Confirm no existing ADR convention; create `docs/adr/`.
- [x] Create `docs/adr/0001-private-remote-ai-runtime.md` with context,
      decision, consequences, and open unresolved infrastructure questions.

## Canonical doc reconciliation

- [x] `AGENTS.md` runtime AI policy.
- [x] `docs/PROJECT_SPEC.md` §11 AI runtime policy.
- [x] `docs/ARCHITECTURE.md` goals, high-level topology, §5 runtime AI
      architecture, §12 backend strategy, §13 security baseline.
- [x] `docs/ROADMAP.md` milestone statuses, cross-project VPS dependency, and
      dependency summary.
- [x] `docs/ACCEPTANCE_CRITERIA.md` AI interpretation + security/privacy.
- [x] `docs/AI_WORKFLOW.md` runtime models and observability wording.
- [x] `docs/ACTIVE_WORK.md` final planning state.

## Explicitly not done

- [x] No production code, test, Gradle, Room, manifest, UI, schema, or
      dependency change.
- [x] No change to completed Change 001–021 packages.
- [x] No retry/outbox mechanics designed.
- [x] No gateway implementation detail invented or frozen in Quick Aside.
- [x] No Change 022 created or selected.
- [x] No commit, push, merge, or release.

## Verification

- [x] `git diff --check`
- [x] `git status --short`
- [x] `git diff --stat`
- [x] Targeted textual checks (see `PLAN.md`).
- [x] Confirm no Kotlin/Gradle/Room/manifest/test/schema file changed.
- [x] Leave the verdict to independent review.

## Exact next gate

Independent review of the planning/docs diff, then user-authorized merge.
After merge, the orchestrator selects the smallest coherent Change 022 from
non-blocked roadmap work.
