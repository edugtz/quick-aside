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

Status: **IN PROGRESS — private gateway and Android integration/client hardening are complete; remaining product work is scope-selected, and normal-use gateway hardening is evidence-triggered.**

Outcome: natural-language input becomes validated structured actions with
minimal friction.

Already complete and valid:

- typed CapturePlan schema + validator (Change 020);
- provider-independent CaptureInterpreter / AIProvider boundary (Change 021);
- QAG-0 read-only VPS preflight;
- QAG-1 provider runtime/protocol decision;
- QAG-2 minimal repository gateway implementation and contract.

QAG-1/QAG-2 established:

- GPT-5.6 Luna with explicit Low reasoning;
- ChatGPT/Codex OAuth;
- `codex exec --ephemeral`;
- one fresh bounded provider process per interpretation request;
- strict structured output;
- Quick Aside-specific provider auth namespace;
- trusted capture time/timezone transport;
- bounded request/output/concurrency/timeout behavior;
- health/readiness and stable provider failure contracts.

Still pending:

- real-use evidence before deciding whether normal-use hardening is warranted;
- optional DeepSeek V4 Flash fallback;
- interpreter outcomes such as PLAN/CLARIFY/UNSUPPORTED where not already
  covered by local foundations;
- low-confidence policy, adaptive receipt, optional edit/review branch, and
  correction/routing examples where not already covered.

Current gateway gates:

- QAG-0 — read-only VPS preflight: **COMPLETE — PASS**
- QAG-1 — runtime/protocol decision: **COMPLETE — PASS**
- QAG-2 — minimal gateway implementation: **COMPLETE — PASS_WITH_NOTES**
- QAG-3 — public ingress deployment attempt: **SUPERSEDED**
- QAG-003R — private tailnet gateway deployment: **COMPLETE — PASS_WITH_NOTES**
- QAG-004 — Android integration: **COMPLETE — PASS_WITH_NOTES; integrated into `main`**
- QAG-004H — Android client trust-boundary hardening: **COMPLETE — PASS_WITH_NOTES; integrated into `main`**
- Normal-use hardening (historically Phase QAG-5 in the gateway initiative): pending real-use evidence; not a reserved Change ID

The specialized QAG gateway workstream is complete and closed for now.
Normal-use hardening is contingent on actual-use evidence and is not
automatically scheduled as the next implementation change.

M2 runtime interpretation is integrated end-to-end through QAG-004, and
Android client trust-boundary hardening is complete through QAG-004H. Both are
integrated into `main`. Execution remains intentionally absent: the flow stops
at validated `CapturePlan`, with no `ActionExecutor` or automatic mutation.
M2 remains open for the remaining fast-capture interpretation/UX policies
listed above and any separately selected work.

## M3 — Google Tasks + Calendar

Status: **NOT globally blocked** — runtime interpretation is available; end-to-end task/event mutation still requires the future execution and sync layers.

Outcome: Personal/Trabajo tasks and events synchronize reliably with Google.

Google OAuth, sync contracts, local/external mapping, outbox/retry,
idempotency/conflict behavior, and Calendar integration can be designed and
implemented independently of the AI provider when scoped coherently.

End-to-end natural-language capture → interpreted Task/Event → Google
acceptance is no longer blocked by provider integration, but still requires the
future validated-plan execution boundary plus Google sync implementation. M3 as
a whole does not need to wait for all remaining M2 polish.

Capabilities:

- OAuth/scopes;
- Google Tasks mapping + bidirectional sync;
- Calendar integration + incremental sync where applicable;
- offline outbox/retry;
- conflict/idempotency policy;
- real-account QA.

Because sync can create data-loss/idempotency risk, break this milestone into small high-confidence changes and elevate governance where required.

## M4 — Reminders and daily reliability

Status: **NOT globally blocked** — runtime interpretation is integrated; natural-language reminder creation still requires future reminder-domain/action work, validated-plan execution, and scheduling.

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

Status: **FINAL COMPLETION BLOCKED** — interpretation is integrated, but the full north-star happy path still requires validated action execution plus the remaining sync/reminder/product polish. Other polish may continue independently.

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
    -> Quick Aside private AI gateway
    -> bounded codex exec --ephemeral
    -> GPT-5.6 Luna / Low
```

The gateway may run on the same VPS as Personal Admin/Hermes, but this is
infrastructure reuse only. Personal Admin/Hermes is not an application
dependency of Quick Aside.

QAG-1 selected the provider invocation route. QAG-2 implemented and verified
the repository gateway server, HTTP contract, trusted temporal context,
timeout/cancellation, concurrency bounds, health/readiness, Codex version pin,
and safe diagnostics.

QAG-003R subsequently deployed the gateway through private Tailscale
Services/Serve, preserved localhost-only FastAPI and QA1, and proved service
restart plus VPS reboot persistence without making Personal Admin an
application dependency. QAG-004 completed Android integration and is
integrated into `main`. QAG-004H completed Android client trust-boundary
hardening and is integrated into `main`. Both intentionally stop at validated
`CapturePlan`; there is no `ActionExecutor` or automatic mutation.

## Milestone dependency summary

A milestone having dependencies is not the same as all development stopping.
Non-blocked, provider-independent work may continue.

| Milestone | Status |
|---|---|
| M1 | NOT BLOCKED — local foundation advanced through Change 026 |
| M2 | IN PROGRESS — gateway + Android integration/client hardening complete; remaining policy and UX scope is undecided; normal-use hardening is evidence-triggered |
| M3 | NOT globally blocked; runtime interpretation is available, but execution + Google sync remain |
| M4 | NOT globally blocked; runtime interpretation is integrated, while natural-language reminder creation awaits future reminder-domain/actions, validated-plan execution, and scheduling work |
| M5 | NOT blocked by AI runtime |
| M6 | FINAL COMPLETION BLOCKED; other polish may continue |

The normal global reviewable-change history currently runs through Change 026.
The user has selected CHG-027 — CapturePlan List Execution Foundation. Its
implementation and independent review are complete: Round 1 returned
**BLOCKED**, and Round 2 returned **PASS_WITH_NOTES** at
`eddbfcd505a89ff7f7f0d4a37d511ad92abdcd24`. The Round-1 evidence BLOCKER is
resolved; no production correctness finding remains. This documentation-only
closeout reconciles the remaining QA provenance wording. CHG-027 is ready for
user-authorized merge after the closeout is committed and pushed. No later
Change ID is selected or reserved.

## Candidate work after QAG-004H (not scheduled)

Candidate work only; none is automatically scheduled:

- Normal-use hardening (historically Phase QAG-5 in the gateway initiative): pending real-use evidence; not a reserved Change ID;
- M3 foundations: Google OAuth, sync contracts, local/external mapping,
  outbox/retry, idempotency/conflict behavior, Calendar integration;
- M4 foundations: reminder domain, scheduling, notification actions, and
  background/restart reliability;
- M5 foundations: backup/snapshot, export center, structured reimport format,
  and archive-before-prune verification;
- M2 provider-independent execution/application foundations, only if
  independently justified;
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
