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

Status: **IN PROGRESS — private gateway, Android integration/client hardening, and CHG-028 list-only CapturePlan auto-execution are integrated into main; remaining product work is candidate scope, and normal-use gateway hardening is evidence-triggered.**

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
integrated into `main`. CHG-027 provides the provider-independent list
execution foundation, and CHG-028 connects it to normal text and voice
Capture: validated plans whose every action is `AddListItem` now auto-execute
with targeted batch Undo. Other CapturePlan action types are not covered. M2
remains open for the remaining fast-capture interpretation/UX policies listed
above and any separately selected work.

## M3 — Google Tasks + Calendar

Status: **NOT globally blocked** — runtime interpretation is available.
Task execution foundation: **implemented by CHG-029** (provider-independent,
currently unwired to Capture). Capture wiring / Google Tasks synchronization /
Event execution: **pending**. End-to-end task/event natural-language mutation
is therefore not complete.

Outcome: Personal/Trabajo tasks and events synchronize reliably with Google.

Google OAuth, sync contracts, local/external mapping, outbox/retry,
idempotency/conflict behavior, and Calendar integration can be designed and
implemented independently of the AI provider when scoped coherently.

End-to-end natural-language capture → interpreted Task/Event → Google
acceptance is no longer blocked by provider integration. CHG-029 supplies the
Task-specific validated-plan execution foundation, but it is not wired to
normal text/voice Capture and no Google synchronization exists yet, so
end-to-end Task mutation is not complete. Event execution remains pending. The
CHG-027/CHG-028 list path and the CHG-029 Task foundation do not provide Event
coverage or Google sync. M3 as a whole does not need to wait for all remaining
M2 polish.

Capabilities:

- OAuth/scopes;
- Google Tasks mapping + bidirectional sync;
- Calendar integration + incremental sync where applicable;
- offline outbox/retry;
- conflict/idempotency policy;
- real-account QA.

Because sync can create data-loss/idempotency risk, break this milestone into small high-confidence changes and elevate governance where required.

## M4 — Reminders and daily reliability

Status: **NOT globally blocked** — runtime interpretation is integrated; natural-language reminder creation still requires reminder-domain/action work, reminder-specific validated-plan execution, and scheduling.

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

Status: **FINAL COMPLETION BLOCKED** — interpretation and list-only normal capture/UI execution are integrated, but the full north-star happy path still requires Capture-wired Task/Event/Reminder action support (CHG-029 provides only an unwired Task execution foundation) and the remaining sync/reminder/product polish. Other polish may continue independently.

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
application dependency. QAG-004 completed Android integration and QAG-004H
completed Android client trust-boundary hardening; both are integrated into
`main` and remain historical QAG boundaries. CHG-027 adds the provider-
independent executor foundation, and CHG-028 wires validated all-`AddListItem`
plans from normal text and voice Capture into that executor with targeted batch
Undo. Other action types remain outside its scope.

## Milestone dependency summary

A milestone having dependencies is not the same as all development stopping.
Non-blocked, provider-independent work may continue.

| Milestone | Status |
|---|---|
| M1 | NOT BLOCKED — local foundation advanced through Change 026 |
| M2 | IN PROGRESS — gateway + Android integration/client hardening and CHG-028 list-only auto-execution integrated; remaining policy and UX scope is undecided; normal-use hardening is evidence-triggered |
| M3 | NOT globally blocked; Task execution foundation implemented by CHG-029 but unwired to Capture; Capture wiring, Google Tasks/Calendar sync, and Event execution remain pending |
| M4 | NOT globally blocked; runtime interpretation is integrated, while natural-language reminder creation awaits reminder-domain/actions, reminder-specific plan execution, and scheduling work |
| M5 | NOT blocked by AI runtime |
| M6 | FINAL COMPLETION BLOCKED; other polish may continue |

The normal global reviewable-change history now runs through Change 029.
CHG-027 — CapturePlan List Execution Foundation remains **COMPLETE —
PASS_WITH_NOTES — INTEGRATED INTO main**. Its verified base was
`cb67494a7b57d0f7a939ec06396ccbc665edff7c`; its implementation commit was
`b9ba067ff442259225127645c8a4c04eeb65dfc6`. Round 1 reviewed that
implementation commit and returned **BLOCKED** with 1 BLOCKER / 0 MAJOR /
1 MINOR / 2 NOTE. The evidence/remediation commit and Round-2 reviewed SHA was
`eddbfcd505a89ff7f7f0d4a37d511ad92abdcd24`; Round 2 returned
**PASS_WITH_NOTES**. The Round-1 evidence BLOCKER was resolved and no
production correctness defect remained. Final review debt was documentation/
evidence provenance only. The final documentation closeout was integrated into
`main` at `979b8e6abb9a57ac5936559c252726a1ca84a98c`.

CHG-027 provides the provider-independent list execution foundation limited to
validated plans whose every action is `AddListItem`, plus targeted batch Undo.
CHG-028 — Capture List Auto-Execution + Receipt/Undo Integration is **COMPLETE
— PASS_WITH_NOTES — INTEGRATED INTO main** at
`44c4d3befd97ad37dadc8fcf93fb2dc7a5ab8232`; its implementation SHA is
`b8a14bf4a435b33870ee9bbf2127a2fd8f7b1d67` and evidence remediation SHA is
`d8d5acdd9ae6982cb790054bdccaefdc0b1701be`. CHG-028 connects the list-only
executor to normal text and voice Capture with exact targeted batch Undo.
Other CapturePlan actions remain non-executable. CHG-029 — CapturePlan Task
Execution Foundation is **COMPLETE — PASS_WITH_NOTES — INTEGRATED INTO main**
at `bcaa53d1993c44304029f6be29937ce42dbaa1e5` (fast-forward; the same commit
as the pre-merge review-closeout HEAD). Round-1 independent review returned
**BLOCKED** (0 BLOCKER / 1 MAJOR / 2 MINOR / 3 NOTE); the test/evidence/docs
remediation is complete, and Round-2 independent review returned
**PASS_WITH_NOTES** (0 BLOCKER / 0 MAJOR / 0 MINOR / 3 NOTE) at reviewed HEAD
`b466407ae8b98400fe52da40750d18529ff9a3b9`. The foundation remains unwired to
normal Capture, Google Tasks synchronization and Event execution remain
pending, and end-to-end Task natural-language mutation is not complete. No
CHG-030 is reserved.

## Other candidate work (not scheduled)

The options below remain unscheduled. CHG-029 is complete and integrated into
`main`; no Change is currently selected.

- Normal-use hardening (historically Phase QAG-5 in the gateway initiative): pending real-use evidence; not a reserved Change ID;
- M3 foundations: Google OAuth, sync contracts, local/external mapping,
  outbox/retry, idempotency/conflict behavior, Calendar integration;
- M4 foundations: reminder domain, scheduling, notification actions, and
  background/restart reliability;
- M5 foundations: backup/snapshot, export center, structured reimport format,
  and archive-before-prune verification;
- Further M2 provider-independent execution/application foundations beyond
  CHG-029, only if independently justified;
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
