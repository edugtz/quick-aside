# Quick Aside — AI Workflow v0.2

This file distinguishes AI used to **build Quick Aside** from AI used **inside Quick Aside at runtime**.

## 1. Project orchestration

Planner / architect / orchestrator / final engineering reviewer:

- ChatGPT current strongest reasoning model available for the session.
- Current conversation baseline: GPT-5.6 Sol.

Responsibilities:

- product clarification;
- architecture;
- change scoping;
- verification contract;
- review findings;
- final `PASS` / `PASS_WITH_NOTES` / `BLOCKED` engineering verdict.

The user owns product decisions and merge/release authority by default.

## 2. Implementation routing

Implementation can use the best-fit available coding tool/model based on task and current cost/quality evidence, including:

- Cursor Pro;
- OpenCode Go;
- local models via the user's local stack;
- other approved cloud builders.

Do not make runtime product architecture depend on which coding agent implemented a change.

Prefer deterministic tools (build/tests/lint/static analysis) over builder self-report.


### Verification routing

Implementation agents verify the Change delta themselves and report exact commands/results. Default to changed-behavior tests, directly affected regressions when justified, and build/static/schema checks relevant to the changed surface. Full suites, broad lint, and real-device QA require a concrete reason; HIGH-ASSURANCE increases rigor for the affected risk, not generic test count.

Reuse valid results while the relevant source/config/environment is unchanged. A rerun requires an explicit invalidator. Do not treat a new session/agent, documentation edit, commit/push of identical source, review stage, or a wish for fresher evidence as an invalidator.

User-operated QA is exceptional. Ask only when a newly changed material property cannot reasonably be established by the agent through automated tests, emulator/device automation, source inspection, or existing valid evidence. State the changed property, automation gap, and one minimal human action before asking.

Normal Changes do not persist `docs/changes/<id>/` packages or evidence trees. Change-local SPEC/PLAN/TASKS/QA and implementation reports are ephemeral orchestration records unless the user explicitly requests persistence or a durable operational need justifies it.

After required Change-specific automated verification passes, the implementation is commit-ready. The user retains commit, push, merge, and release authority; ChatGPT/orchestrator remains responsible for independent engineering review unless the user chooses another reviewer.

## 3. Runtime interpretation models

QAG-1 and QAG-2 are complete.

Accepted runtime direction:

1. **GPT-5.6 Luna — explicit Low reasoning** — primary model via ChatGPT
   Plus / Codex OAuth.
2. **`codex exec --ephemeral`** — first gateway provider invocation route;
   one fresh bounded process per interpretation request.
3. **DeepSeek V4 Flash via OpenCode Go** — fallback candidate,
   evidence-triggered.
4. MiMo-V2.5 was not selected as primary because observed schema/contract
   reliability was worse than the finalists.
5. No automatic reasoning escalation; fallback, if implemented, is for
   eligible provider/runtime failures rather than semantic disagreement.

QAG-1 validated Codex SDK/CLI `0.154.0` and selected the process-per-request
CLI route. QAG-2 implemented the minimal repository gateway around that route
and verified bounded request/output behavior, strict provider validation,
timeout/cancellation, concurrency, health/readiness, safe diagnostics, and
isolated real-provider contract behavior.

Final QAG-2 verification:

- deterministic suite: **33 passed**;
- targeted Luna Low contract smoke: PASS;
- Personal/Trabajo semantic routing smoke: **5/5 PASS**;
- residual Codex process after requests: **NONE**;
- final engineering verdict: **PASS_WITH_NOTES**.

The notes are limited to upstream FastAPI/Starlette deprecation warnings.

The gateway is now live through the QAG-003R private-tailnet deployment:
`svc:quickaside -> Tailscale Serve HTTPS -> 127.0.0.1:2588`. QAG-003R completed
real signed Luna Low, replay-rejection, restart/reboot-persistence, isolation,
and log-privacy evidence with final `PASS_WITH_NOTES`. QAG-004 completed the
HIGH-ASSURANCE Android integration gate, including device pairing, QA1 signing,
the remote `AIProvider` adapter, local validation, and true Android end-to-end
latency measurement. QAG-004H subsequently closed the accepted client-side
trust-boundary hardening debt. CHG-027/028 then introduced validated list
execution and Capture wiring; CHG-029 added the local Task executor. The
published CHG-030 branch adds normal Capture wiring for validated all-`CreateTask`
plans and is awaiting independent review.

Provider auth and credentials belong to the isolated Quick Aside
gateway/runtime, not Android or Personal Admin/Hermes. Runtime code remains
provider-abstracted; stored domain records and `CapturePlan` remain
provider-independent.

Do not restart broad comparative model benchmarking unless real runtime use
shows a concrete blocker.

See:

- `docs/adr/0003-codex-exec-ephemeral-runtime-protocol.md`
- `docs/changes/QAG-001-runtime-protocol-decision/QA.md`
- `docs/changes/QAG-002-minimal-gateway/QA.md`

## 4. Runtime AI observability

Useful local metrics without storing unnecessary sensitive content:

- provider/model;
- request latency;
- schema validation result;
- fallback invoked yes/no;
- low-confidence clarification yes/no;
- user correction occurred yes/no;
- approximate request count/allowance use if available.

The goal is to know when the primary provider is insufficient without building a benchmark program first.

## 5. Visual asset workflow

Project-specific generative visuals:

`ChatGPT plans/briefs → Google AI Plus primary generation when available → ChatGPT reviews/integration → ChatGPT image generation fallback`

Canonical current UI reference is already stored at:

- `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`

The image was generated before the rename and may contain the legacy `VoiceApp` title inside the pixels. That text is non-canonical; implementation uses **Quick Aside**.

For future UI phases, agents MUST inspect this reference plus `docs/UX_UI_REFERENCE.md` before implementation.

Standard UI icons should come from Material/platform icon sets rather than AI-generated icon artwork.

## 6. UI implementation review

For every material UI change:

- builder references the canonical UX visual + written contract;
- reviewer compares actual visual result against those references and the active Change's orchestration acceptance criteria;
- visual differences that materially change the product direction require explicit product approval.
