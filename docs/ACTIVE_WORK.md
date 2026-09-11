# Quick Aside — Active Work

## Active change

`docs/changes/022-local-task-persistence/`

Status: **COMPLETE — REVIEW PASS_WITH_NOTES**

Governance: **HIGH-ASSURANCE**

Change 022 establishes only the local durable Room-backed Task persistence
foundation for the current Task domain. Runtime AI implementation remains
paused; no Google sync, ActionExecutor, reminders, or Task UI is in scope.

Change 022 implementation and HIGH-ASSURANCE verification are complete.
Independent review: `PASS_WITH_NOTES` — `BLOCKER 0`, `MAJOR 0`, `MINOR 0`.
Focused Task/migration instrumentation and the applicable Room/data.local
connected suite passed on the authorized Oppo CPH2791 / Android 16 device
(5/5 and 84/84, respectively).

A broader all-app connected run completed 208/209 tests and observed one
`MandadoUiTest` Compose timeout. `MandadoUiTest` and its fake list/action
dependencies are unchanged by Change 022; the failure is outside the required
Task/Room verification gates, and current evidence does not attribute it to
Change 022. No baseline-main reproduction was performed, so it is not
classified here as pre-existing.

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
- Change 022 is complete from the verified PLN-001 merge baseline.
- PLN-001 is merged and complete; runtime AI implementation remains paused.
- Change 022 adds only the local durable Task persistence foundation. No
  Google sync, AI provider, ActionExecutor, reminders, or Task UI is in scope.
- Change 022 independent review is complete: `PASS_WITH_NOTES` — 0/0/0.
- No Change 023 has been selected yet.

## Proven baseline

- Product name accepted: **Quick Aside**.
- Product/UX baseline accepted for personal MVP.
- Canonical written UX contract: `docs/UX_UI_REFERENCE.md`.
- Canonical visual-direction reference: `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`.
- Durable runtime decision: `docs/adr/0001-private-remote-ai-runtime.md`.
- Roadmap milestone dependency status: `docs/ROADMAP.md`.

## Exact next gate

User-authorized commit/push of Change 022 closeout docs, then merge
`chg-022-local-task-persistence` into `main`. After merge, the orchestrator will
inspect the current roadmap/repository state and select the next reviewable
change. Change 023 has not been selected yet.
