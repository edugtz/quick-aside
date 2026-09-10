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

## 3. Runtime interpretation models

Runtime interpretation is currently **paused** at the provider/runtime boundary
pending the shared private VPS runtime gateway (see `docs/ARCHITECTURE.md` §5 and
`docs/adr/0001-private-remote-ai-runtime.md`).

Accepted decision after the completed runtime model evaluation (2026-09-09):

1. **GPT-5.6 Luna — Low reasoning** — primary target, via ChatGPT Plus / Codex OAuth.
2. **DeepSeek V4 Flash via OpenCode Go** — fallback candidate, evidence-triggered.
3. MiMo-V2.5 was not selected as primary because observed schema/contract reliability was worse than the finalists.

Do not restart broad comparative benchmarking unless real runtime use shows a
concrete blocker. Provider auth and credentials belong to the personal runtime,
not the Android app.

Runtime code uses a provider abstraction. Stored domain records and CapturePlan schema must be provider-independent.

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
- reviewer compares actual screenshot/device result against those references and the active change spec;
- visual differences that materially change the product direction require explicit product approval.
