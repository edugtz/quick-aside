# Quick Aside — Roadmap v0.2

Roadmap milestones are product outcomes, not branch/PR units. Each milestone should be delivered through small reviewable changes.

## M0 — Project foundation

Outcome: repository boots, builds, tests, and has canonical project/design context.

Likely changes:

- project bootstrap + Compose/Room/test baseline;
- design system/navigation shell;
- core domain contracts.

## M1 — Local capture and memory core

Status: **NOT BLOCKED** — local foundation has advanced through Change 026.

Outcome: the app is useful locally without Google/AI dependencies.

Implemented/current foundation includes:

- text capture;
- basic voice/STT capture;
- editable transcript;
- lists (Mandado/Compras);
- notes;
- basic structured logs;
- action ledger/undo foundation;
- local search/history basics;
- local task persistence/completion/reopen;
- reversible task create/completion;
- Pendientes UI foundation;
- UI aligned with UX v3.

## M2 — AI interpretation and fast-capture flow

Status: **PARTIALLY BLOCKED** — provider/runtime implementation paused.

Outcome: natural-language input becomes validated structured actions with minimal friction.

Already complete and valid:

- typed CapturePlan schema + validator (Change 020);
- provider-independent CaptureInterpreter / AIProvider boundary (Change 021).

Deferred (not rejected) until the Quick Aside-owned private gateway
runtime/protocol is planned and proven enough to define the real remote
integration boundary:

- real AIProvider implementation;
- provider client/wire protocol and provider authentication;
- Luna runtime integration and optional DeepSeek V4 Flash fallback;
- production prompt/schema implementation and runtime network integration;
- interpreter outcomes (PLAN/CLARIFY/UNSUPPORTED) + temporal-context work;
- low-confidence policy, adaptive receipt, optional edit/review branch, and
  correction/routing examples where not already covered by local foundations.

Runtime model direction: GPT-5.6 Luna Low primary via ChatGPT Plus/Codex OAuth
behind the Quick Aside-owned private gateway; DeepSeek V4 Flash via OpenCode Go
is an evidence-triggered fallback candidate.

Current gateway gates:

- QAG-0 — read-only VPS preflight: **COMPLETE — PASS**
- QAG-1 — runtime/protocol decision: **NEXT**
- QAG-2 — minimal gateway implementation: pending QAG-1
- QAG-3 — live VPS deployment: pending implementation and explicit user approval
- QAG-4 — Android integration: pending an independently healthy gateway
- QAG-5 — normal-use hardening: pending real use

M2 cannot be considered complete until runtime interpretation resumes.
Provider-independent execution/application foundations may be candidate work if
independently justified, but they are not automatically scheduled here.

## M3 — Google Tasks + Calendar

Status: **NOT globally blocked** — end-to-end natural-language path blocked until interpretation resumes.

Outcome: Personal/Trabajo tasks and events synchronize reliably with Google.

Google OAuth, sync contracts, local/external mapping, outbox/retry,
idempotency/conflict behavior, and Calendar integration can be designed and
implemented independently of the AI provider when scoped coherently.

However, end-to-end natural-language capture → interpreted Task/Event → Google
acceptance remains blocked until interpretation resumes. M3 as a whole must not
wait for M2.

Capabilities:

- OAuth/scopes;
- Google Tasks mapping + bidirectional sync;
- Calendar integration + incremental sync where applicable;
- offline outbox/retry;
- conflict/idempotency policy;
- real-account QA.

Because sync can create data-loss/idempotency risk, break this milestone into small high-confidence changes and elevate governance where required.

## M4 — Reminders and daily reliability

Status: **NOT globally blocked** — natural-language reminder creation blocked until interpretation resumes.

Outcome: user-configured reminders reliably fire and are actionable.

Capabilities:

- Note/Task local reminders;
- snooze;
- notification actions;
- restart/background/idle behavior;
- real-device QA.

## M5 — Durable history, backup, and archive

Status: **NOT blocked by AI runtime.**

Outcome: years of personal memory can be recovered/exported without silent loss.

Capabilities:

- backup/snapshot foundation;
- export center;
- reimportable structured format;
- human-readable PDF/DOCX export;
- archive warnings;
- verified archive-before-prune contract.

## M6 — Personal MVP polish

Status: **FINAL COMPLETION BLOCKED** — the full north-star happy path requires automated natural-language interpretation. Other polish may continue independently.

Outcome: the user can adopt Quick Aside as the default capture tool in everyday life.

Capabilities driven by observed usage:

- latency/friction polish;
- one-handed/accessibility refinement;
- sync/reminder edge-case hardening;
- search/retrieval improvements;
- UI polish against canonical reference;
- real usage acceptance period.

## Quick Aside private runtime gateway

Quick Aside owns its private AI gateway.

```text
Quick Aside Android
    → Quick Aside private AI gateway
        → configured provider runtime
```

The gateway may run on the same VPS as Personal Admin/Hermes, but this is
infrastructure reuse only. Personal Admin/Hermes is not an application
dependency of Quick Aside.

Gateway endpoint, auth/network mechanism, deployment topology, process
management, provider invocation contract, timeout/concurrency policy, and
fallback implementation are Quick Aside decisions. They remain intentionally
unfrozen until QAG-1 produces current measured evidence.

## Milestone dependency summary

A milestone having dependencies is not the same as all development stopping.
Non-blocked, provider-independent work may continue.

| Milestone | Status |
|---|---|
| M1 | NOT BLOCKED — local foundation advanced through Change 026 |
| M2 | PARTIALLY BLOCKED — QAG-1 is the next runtime gate |
| M3 | NOT globally blocked; end-to-end natural-language path blocked until interpretation resumes |
| M4 | NOT globally blocked; natural-language reminder creation blocked until interpretation resumes |
| M5 | NOT blocked by AI runtime |
| M6 | FINAL COMPLETION BLOCKED; other polish may continue |

There is currently **no CHG-027 selected**.

QAG-1 is the next runtime/architecture investigation gate. It does not
automatically assign an implementation change number or authorize live VPS
mutation.

## Available work while AI interpretation is paused

Not automatically scheduled:

- QAG-1 runtime/protocol investigation.
- M3 foundations: Google OAuth, sync contracts, local/external mapping,
  outbox/retry, idempotency/conflict behavior, Calendar integration.
- M4 foundations: reminder domain, scheduling, notification actions, and
  background/restart reliability.
- M5 foundations: backup/snapshot, export center, structured reimport format,
  and archive-before-prune verification.
- M2 provider-independent execution/application foundations, only if
  independently justified.
- M1/M6 polish that does not depend on automated interpretation.

This list records options, not a schedule.

## Post-MVP — Evidence-triggered candidates

Likely early:

- Quick Settings capture tile.
- Home widget.
- Share-to-Quick Aside.
- richer history queries.
- recurring/multiple reminders.

Later only if evidence supports them:

- FCM remote notifications / agent integrations;
- on-device local model/routing;
- cross-device cloud sync;
- lock-screen capture;
- Wear OS;
- system overlays;
- hardware-button invocation;
- shared lists/collaboration.

Do not implement these simply because they are listed here.
