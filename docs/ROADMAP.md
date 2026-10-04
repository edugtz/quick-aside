# Quick Aside — Roadmap v0.4

Roadmap milestones are product outcomes, not branch/PR units. Each milestone is delivered through small reviewable Changes. The roadmap records product state and dependencies, not test/evidence history.

## M0 — Project foundation

Status: **COMPLETE**

Outcome: repository boots, builds, tests, and has canonical project/design context.

## M1 — Local capture and memory core

Status: **SUBSTANTIALLY IMPLEMENTED / NOT BLOCKED**

Implemented foundation includes:

- text capture;
- basic voice/STT capture and transcript correction;
- Mandado/Compras lists;
- notes and basic structured logs;
- action ledger / reversible local actions;
- local search/history basics;
- local Task persistence, completion/reopen, reversible create/completion;
- Pendientes UI;
- UI direction aligned with UX v3.

## M2 — AI interpretation and fast-capture flow

Status: **IN PROGRESS**

Implemented/integrated baseline:

- typed `CapturePlan` schema + validator;
- provider-independent `CaptureInterpreter` / `AIProvider`;
- private Quick Aside gateway;
- GPT-5.6 Luna Low through `codex exec --ephemeral`;
- QAG-003R private Tailnet deployment with QA1;
- QAG-004 Android gateway integration;
- QAG-004H Android trust-boundary hardening;
- CHG-027 local list execution foundation;
- CHG-028 normal text/voice auto-execution for validated all-`AddListItem` plans;
- CHG-029 local Task execution foundation;
- CHG-030 normal text/voice auto-execution for validated all-`CreateTask` plans;
- CHG-032 local Memory execution foundation — COMPLETE / reviewed PASS / integrated: ordered `CreateNote` / `CreateStructuredLog` execution, one atomic Action Ledger entry, and targeted atomic Undo;
- CHG-033 normal text/voice auto-execution for validated plans composed exclusively of `CreateNote` / `CreateStructuredLog`, with Notes/Structured Logs refresh — implementation COMPLETE, independent review pending;
- supported List, Task, and Memory execution paths use targeted Undo;
- mixed-family and unsupported plans execute nothing.

CHG-027/028 list execution and Capture wiring are integrated. CHG-029/030 Task
execution and Capture wiring are integrated. CHG-030 is COMPLETE. The subsequent
Mandado fast-capture lifecycle corrective is also COMPLETE and independently
reviewed PASS; it established no-prestart high-confidence Mandado capture and
reversible local execution as the accepted baseline. CHG-032 local Memory
execution foundation is COMPLETE / independently reviewed PASS / integrated.
CHG-033 Fast Capture Memory wiring is implemented and verified on its feature
branch; independent review and user-owned integration remain pending.

Completed and integrated:

- **CHG-031 — Mandado calendar-based weekly rollover** — COMPLETE / integrated / independent review `PASS_WITH_NOTES`. Published functional commit: `4608d71708464ad83330f07c4bda953153ff5c0d`. It replaced the interim 7-elapsed-day stale-session policy with the accepted calendar workflow: Sunday 00:00 formal period start, Saturday 14:00 rollover, immediate next-period eligibility, no Sunday double-reset, manual Finish blocking re-bootstrap until the next rollover, and durable historical-session visibility.

- **CHG-032 — Local Memory Capture execution foundation** — COMPLETE / independent review `PASS` / integrated into `main` at accepted baseline `897d8e13afadd980c59a2a7151ecb2a846344966`. Implementation commit: `9eba7167af0613cf3fd94b53a9a83d30fdbb93f7`. It implements provider-independent, ordered `CreateNote` / `CreateStructuredLog` execution with one atomic Action Ledger entry and targeted atomic Undo. CHG-033 adds the normal Fast Capture wiring on its feature branch.

Active selected Change:

- **CHG-033 — Fast Capture Memory execution wiring** — STANDARD; implementation and required owner verification COMPLETE on `chg/033-memory-fast-capture-wiring`; independent review pending. Normal text/voice Capture executes eligible pure Memory-family plans with lightweight receipts, ordered targeted Undo, and Memory-view refresh. Mixed-family/unsupported plans still execute nothing; Google sync, Calendar, and reminders are not included.

Still candidate/pending:

- low-confidence clarification policy and adaptive receipt/edit behavior where not already covered;
- evidence-triggered normal-use gateway hardening;
- optional DeepSeek V4 Flash fallback;
- additional supported action families only when selected as separate Changes.

M2 remains IN PROGRESS only for remaining interpretation/policy/UX work and
evidence-triggered runtime hardening/fallback candidates. Google sync,
Calendar/Event execution, reminders, and other action families remain pending.

## M3 — Google Tasks + Calendar

Status: **NOT globally blocked; synchronization/event work remains pending**

Outcome: Personal/Trabajo tasks and events synchronize reliably with Google.

Current facts:

- local Task execution is no longer a blocker;
- natural-language Task Capture reaches local Task creation;
- Google Tasks OAuth/sync, external mapping, outbox/retry/idempotency/conflicts are not implemented;
- Calendar/Event execution and sync are not implemented.

Candidate capabilities:

- OAuth/scopes;
- Google Tasks mapping + bidirectional sync;
- offline outbox/retry;
- conflict/idempotency policy;
- Calendar event execution + incremental sync where applicable.

Because sync/idempotency can create correctness and data-loss risk, implement this milestone through small focused Changes and elevate governance only for the affected risk.

## M4 — Reminders and daily reliability

Status: **NOT globally blocked**

Outcome: user-configured reminders reliably fire and are actionable.

Pending capabilities:

- Note/Task local reminder actions;
- scheduler integration;
- snooze;
- notification actions;
- background/restart/idle reliability.

Real-device verification is appropriate only for reminder behaviors whose Android scheduling/background semantics cannot be established by emulator automation alone; user-operated QA remains exceptional.

## M5 — Durable history, backup, and archive

Status: **NOT blocked**

Outcome: years of personal memory can be recovered/exported without silent loss.

Candidate capabilities:

- backup/snapshot foundation;
- reimportable structured format;
- human-readable PDF/DOCX export;
- archive warnings;
- verified archive-before-prune contract.

## M6 — Personal MVP polish

Status: **FINAL COMPLETION BLOCKED BY REMAINING SYNC / EVENT / REMINDER CAPABILITIES**

Outcome: Quick Aside becomes the user's default low-friction capture tool.

Polish is evidence-driven:

- latency/friction;
- one-handed/accessibility refinement;
- sync/reminder edge cases;
- search/retrieval;
- visual polish against canonical UX reference;
- normal-use observations.

## Runtime gateway status

The specialized gateway workstream is complete and closed for now:

- QAG-0: **PASS**
- QAG-1: **PASS**
- QAG-2: **PASS_WITH_NOTES**
- QAG-3 public-ingress attempt: **SUPERSEDED**
- QAG-003R private Tailnet deployment: **PASS_WITH_NOTES**
- QAG-004 Android integration: **PASS_WITH_NOTES**
- QAG-004H Android client hardening: **PASS_WITH_NOTES**

Normal-use hardening remains evidence-triggered and is not automatically scheduled.

## Milestone dependency summary

| Milestone | Current state |
|---|---|
| M0 | COMPLETE |
| M1 | SUBSTANTIALLY IMPLEMENTED / NOT BLOCKED |
| M2 | IN PROGRESS; CHG-030 + Mandado fast-capture corrective COMPLETE; CHG-031 calendar rollover COMPLETE / integrated; CHG-032 local Memory execution foundation COMPLETE / integrated; CHG-033 Fast Capture Memory wiring implemented / review pending; remaining interpretation/policy/UX and evidence-triggered runtime work pending |
| M3 | NOT globally blocked; Google Tasks/Calendar sync and Event execution pending |
| M4 | NOT globally blocked; reminder-domain/execution/scheduling pending |
| M5 | NOT blocked |
| M6 | Final completion blocked by remaining sync/event/reminder capabilities |

CHG-031 is COMPLETE / integrated with independent review PASS_WITH_NOTES and
engineering closeout complete. CHG-032 is COMPLETE / integrated with independent
review PASS. CHG-033 now wires that Memory foundation into normal Capture on its
feature branch; independent review is the next gate. Protected-branch integration
remains user-owned.

## Post-MVP — evidence-triggered candidates

Likely early candidates:

- Quick Settings capture tile;
- home widget;
- share-to-Quick Aside;
- richer history queries;
- recurring/multiple reminders.

Later only if evidence supports them:

- FCM remote notifications / agent integrations;
- on-device local inference;
- cross-device cloud sync;
- lock-screen capture;
- Wear OS;
- system overlays;
- hardware-button invocation;
- shared lists/collaboration.

Do not implement future scope merely because it appears here.
