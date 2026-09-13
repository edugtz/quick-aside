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

QAG-1 is **complete — PASS**. Runtime interpretation now has a selected
provider invocation route, although the production gateway and Android
integration are not yet implemented.

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

QAG-1 validated Codex SDK/CLI `0.154.0` on the target VPS. The persistent
Python SDK/app-server path produced correct results but resident memory grew
across fresh ephemeral threads. The CLI ephemeral route produced 3/3 valid
strict-schema controls, left no Codex process after each request, and used
effectively the same token/context amount as the equivalent SDK request.

These are VPS/runtime measurements, not Android end-to-end latency.

Provider auth and credentials belong to the isolated Quick Aside
gateway/runtime, not Android or Personal Admin/Hermes. Runtime code remains
provider-abstracted; stored domain records and `CapturePlan` remain
provider-independent.

QAG-2 is the next gate and must implement the minimal gateway contract and
bounded provider-process lifecycle before QAG-3 live deployment or QAG-4
Android integration.

Do not restart broad comparative model benchmarking unless real runtime use
shows a concrete blocker.

See `docs/adr/0003-codex-exec-ephemeral-runtime-protocol.md` and
`docs/changes/QAG-001-runtime-protocol-decision/QA.md`.

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
