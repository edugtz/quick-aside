# Quick Aside — Active Work

## Active planning update

`docs/changes/PLN-001-runtime-ai-realignment/`

Status: **COMPLETE — REVIEW PASS**

Independent verdict: **PASS — BLOCKER 0 / MAJOR 0 / MINOR 0**

Governance: **STANDARD**

This is a docs-only planning package. It reconciles canonical documentation with
the post-Change-021 runtime-AI decisions and evidence. It does not implement
production code and does not select or create Change 022.

## State

- Change 020 (Typed CapturePlan + Validator Foundation) is merged and complete.
- Change 021 (Capture Interpreter + AIProvider Boundary) is merged and complete
  (`7190d65 docs: close Change 021`).
- Runtime AI provider/interpreter continuation is **paused** until the shared
  private VPS runtime gateway is planned and proven enough to define the real
  remote integration boundary.
- Runtime model direction: GPT-5.6 Luna Low primary via ChatGPT Plus / Codex
  OAuth; DeepSeek V4 Flash via OpenCode Go is an evidence-triggered fallback
  candidate. MiMo-V2.5 is no longer primary.
- Shared private VPS runtime gateway is the target runtime direction; Quick
  Aside shares infrastructure with Personal Admin/Hermes but not Hermes agent
  conversation/context.
- Android-local Codex is deferred pending materially better upstream evidence or
  official Android support.
- No Change 022 has been selected yet.

## Proven baseline

- Product name accepted: **Quick Aside**.
- Product/UX baseline accepted for personal MVP.
- Canonical written UX contract: `docs/UX_UI_REFERENCE.md`.
- Canonical visual-direction reference: `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`.
- Durable runtime decision: `docs/adr/0001-private-remote-ai-runtime.md`.
- Roadmap milestone dependency status: `docs/ROADMAP.md`.

## Exact next gate

User-authorized commit/push of closeout docs, then merge PLN-001 into main.
After merge, the orchestrator reviews current non-blocked roadmap work and
selects the smallest coherent Change 022.
