# Quick Aside — Architecture v0.2

Status: proposed implementation baseline derived from accepted product/UX decisions. Exact Android/API versions must be verified during implementation preflight. Runtime-AI sections reconciled 2026-09-12 (`docs/adr/0001-private-remote-ai-runtime.md` and `docs/adr/0002-quick-aside-owned-private-ai-gateway.md`).

## 1. Architecture goals

- Personal-first Android app.
- Local-first structured memory.
- Reliable Google Tasks and Calendar integration.
- AI is replaceable interpretation infrastructure, not persistence.
- Fast capture path with optional correction.
- Offline-tolerant local functionality.
- No custom multi-user/SaaS backend for the initial personal MVP. A small
  Quick Aside-owned private runtime gateway hosted on shared personal VPS
  infrastructure is an accepted exception for AI interpretation only
  (ADR-0001, ADR-0002).
- Future interaction surfaces reuse one capture pipeline rather than duplicating logic.

## 2. High-level topology

```text
Interaction surfaces
  App / Text / Voice
  Future: Quick Settings / Widget / Share / other system surfaces
                |
                v
          Capture Engine
                |
        Speech-to-Text layer
                |
        Capture Interpreter
      /                     \
Local rules             AIProvider
                          |
               Quick Aside private
                  AI gateway
              (shared VPS host only)
                          |
              Codex / ChatGPT OAuth
                          |
                 GPT-5.6 Luna Low
            (DeepSeek V4 Flash fallback
              candidate, later)
                          |
          CapturePlan (typed)
                |
            Validator
                |
         Action Executor
      /        |         \
   Room    Google Tasks  Google Calendar
      |          |             |
 Reminders   Sync Engine    Sync Engine
      |
 Archive / Backup / Export
```

## 3. Android stack baseline

Expected stack, subject to official-doc preflight at implementation time:

- Kotlin.
- Jetpack Compose.
- Material 3 native behavior.
- Room/SQLite.
- WorkManager for deferrable background sync/retries where appropriate.
- AlarmManager / Android notification APIs for user-configured local reminders where timing semantics require them.
- Google Identity/OAuth and Google Workspace APIs for Tasks/Calendar.
- Android speech recognition with on-device path when available and suitable.

Do not lock exact library versions or min/target SDK in docs before repository bootstrap verifies current platform requirements.

## 4. Domain boundaries

### Capture

Stores original input, transcript, corrections, interpretation outcome, timestamps, and links to executed actions.

### CapturePlan

Typed, validated intermediate representation. AI output must be decoded into this contract before any mutation occurs.

Possible action families:

- AddListItem
- CreateTask / UpdateTask
- CreateEvent / UpdateEvent
- CreateNote
- CreateStructuredLog
- CreateReminder / SnoozeReminder
- Move/Complete/Reopen
- QueryHistory
- Undo

### Lists

- ListDefinition
- ListSession
- ListItem

`Mandado` = session-based.  
`Compras` = continuous.

### Tasks

Local mirror plus sync metadata for Google Tasks. User-facing task spaces Personal/Trabajo map to selected/created Google Task lists.

### Events

Local cache/mirror for relevant Google Calendar data; Google Calendar remains the external lifecycle system.

### Memory

- Note
- StructuredLog
- search/index metadata
- History/Archive references

### Reminder

Separate from Task due date. May point to a Note or Task.

### Action Ledger

Records user-visible mutations for activity/history and reversible operations where feasible.

## 5. Runtime AI architecture

Use an interface such as `AIProvider` / `CaptureInterpreter`, not model-specific calls throughout the app. `CapturePlan` and stored domain records remain provider-independent.

Runtime direction after the completed model evaluation (2026-09-09):

1. Primary target: **GPT-5.6 Luna — Low reasoning** — via ChatGPT Plus / Codex OAuth.
2. Fallback candidate: **DeepSeek V4 Flash via OpenCode Go** — evidence-triggered only.
3. MiMo-V2.5 is not primary; the evaluation observed worse schema/contract reliability.
4. Deterministic/local interpretation for simple known commands remains preferred when safe.

Provider/model choices are infrastructure concerns and must remain replaceable behind `AIProvider` without domain or stored-data changes. Do not restart broad comparative benchmarking without observed runtime evidence.

**Current status: PAUSED.** Further AI-interpreter/runtime-provider implementation
(real `AIProvider` implementation, Codex/OpenCode Go clients, provider wire
protocol, provider authentication, Luna runtime integration, DeepSeek fallback,
production prompts/schemas, runtime network integration, and provider-only
interpreter-contract work) is paused until the Quick Aside-owned private runtime
gateway is planned and proven well enough to define the real remote integration
boundary. Changes 020 and 021 remain accepted and complete. The earlier pause
and its roadmap consequences are recorded in
`docs/changes/PLN-001-runtime-ai-realignment/`; ADR-0002 now makes Quick Aside
responsible for the gateway itself.

### Remote runtime boundary

The accepted target topology is:

```text
Quick Aside Android
    → private interpretation request
    → Quick Aside private AI gateway
        (hosted on shared personal VPS infrastructure)
        → fresh / bounded provider invocation
        → Codex / ChatGPT OAuth
        → GPT-5.6 Luna Low
```

Quick Aside owns the gateway and all Quick Aside-specific runtime behavior.
Personal Admin/Hermes may share the same VPS, but it is not an application
dependency of Quick Aside. The gateway must not use Hermes agent
conversation/context, Personal Admin prompts/state/cron/integrations, ACK
delivery, or `/home/hermes` as its application/configuration namespace.

The gateway should use its own service identity, configuration/state namespace,
provider auth state, process lifecycle, and private network exposure unless
later evidence justifies a different isolated design. Deployment and rollback
must leave Personal Admin operationally unchanged.

The exact gateway endpoint, wire protocol, authentication/network mechanism,
server runtime, process-management details, and provider invocation mechanism
remain unresolved. These are Quick Aside decisions and must be selected through
a focused runtime/protocol investigation using current supported behavior and
measured evidence rather than by assuming a CLI, SDK, app-server, or other
integration path in advance.

### Fast-capture latency requirement

Runtime architecture must preserve the product's zero-friction capture path:

```text
speak/type
    → final local capture/transcript
    → persist capture locally
    → remote interpretation
    → local validation
    → UI reflects interpreted result / lightweight receipt
```

The local capture must become durable without waiting for the remote model.
Remote interpretation should then complete quickly enough that automatic
classification/routing feels like part of the capture interaction rather than a
separate background workflow.

No numeric latency budget is frozen yet. Runtime/protocol selection must include
measured end-to-end latency on a supported Android device through the actual
private-network/VPS/provider path before the implementation is accepted. A
runtime must not be selected solely because its SDK/CLI is convenient if its
observed latency degrades the accepted fast-capture UX.

### AI safety boundary

- Remote model output is untrusted.
- Flow: `remote interpretation → untrusted structured result → Quick Aside validation → future execution`.
- The remote runtime never directly mutates Quick Aside Room, Google Tasks, Google Calendar, or local reminders.
- Quick Aside Android never receives or stores ChatGPT/Codex OAuth tokens, Codex auth state, OpenCode Go credentials, or other provider secrets. Those stay in the isolated Quick Aside runtime on the VPS.
- Trusted capture provenance remains Android-owned; the remote provider must not become authoritative for `sourceCaptureId` or other local identity.

Model changes must not require domain-schema changes.

### Local-first failure behavior

A remote AI dependency must not make capture lossy:

```text
capture → persist locally → attempt remote interpretation
```

If remote interpretation is unavailable, the original Capture remains durable and
user intent must not silently disappear. Retry/deferred-interpretation policy is
intentionally unspecified here and will be scoped in a future focused change.

No broad benchmark program is required before MVP use. Collect lightweight local diagnostics such as model used, latency, validation outcome, and correction/fallback rate without storing sensitive prompt content unnecessarily.

## 6. Persistence

Room is the local durable source of truth for Quick Aside-owned information:

- Captures/transcripts.
- Lists and list sessions.
- Notes.
- Structured logs.
- Reminders.
- Action ledger.
- Routing examples/preferences.
- Sync state/outbox.
- Local mirrors/caches of relevant Google Tasks/Calendar objects.

Rules:

- No silent TTL for durable personal data.
- No destructive migration fallback for user data.
- Schema migrations are explicit and tested.
- Raw/transient technical artifacts may have separate retention rules, but durable semantic data remains until user/archive policy says otherwise.

## 7. Google Tasks sync

Must-have.

Design target:

- Bidirectional synchronization.
- Personal and Trabajo map to Google Task lists.
- Local edits appear immediately and are queued when offline.
- Sync engine handles retries/idempotency/conflicts explicitly.
- Completed/deleted external changes are reconciled.
- Google Tasks due-date limitations do not erase Quick Aside reminder-time semantics.

Conflict policy is an open implementation decision and must be specified before sync change implementation.

Because sync/idempotency can create correctness and data-loss risk, meaningful sync-engine changes may require HIGH-ASSURANCE governance.

## 8. Google Calendar sync

Must-have for events.

Target:

- create/update/read relevant events;
- incremental sync where supported;
- offline-aware local cache;
- no custom public webhook backend in initial MVP;
- refresh on appropriate app lifecycle/background policy.

Webhooks/push sync are post-MVP only if observed stale-data UX justifies backend complexity.

## 9. Reminders

MVP remote-server independence:

`Reminder record → Android scheduler → local notification`

Notification semantics:

- Task: Snooze / Complete.
- Note: Snooze / Open.
- Due date and reminder are distinct.

FCM remote notifications are deferred; they may later integrate with external agents or Ackline-related patterns, but that is not current Quick Aside scope.

Reminder reliability requires real-device QA under background/idle/restart conditions.

## 10. Backup, archive, and retention

Durability is an architectural invariant.

Layers:

1. Local Room database.
2. Recovery backup/snapshot mechanism.
3. Human-readable archive/export.

Archive design should eventually produce:

- reimportable structured backup (JSON/other versioned machine format);
- PDF and/or DOCX human archive;
- verification before destructive prune.

Default: no automatic destructive deletion of durable user information.

Potential archive workflow:

`select period → export structured backup → export human doc → verify → mark archive complete → optional user-approved prune`

Prune/data-loss paths require HIGH-ASSURANCE governance and rollback/recovery evidence.

## 11. UX architecture

Canonical written/visual reference:

- `docs/UX_UI_REFERENCE.md`
- `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`

UI changes must use those sources before implementation.

Navigation baseline:

- Inicio
- Pendientes
- Listas
- Memoria
- global Capture action

Future capture surfaces should call the same capture application/domain services rather than reimplementing business rules.

## 12. Backend strategy

Personal MVP: no general-purpose custom backend. The single accepted exception is
a small **Quick Aside-owned private AI gateway** hosted on shared personal VPS
infrastructure for AI interpretation only (ADR-0001, ADR-0002). It is not a
Personal Admin capability, not a Hermes service, and not a general-purpose
backend. Shared host/network patterns may be reused without sharing application
authority, state, credentials, or lifecycle.

Avoid premature:

- user account service;
- multi-user database;
- realtime sync server;
- SaaS billing;
- public/general-purpose AI gateway or public Quick Aside cloud.

Productization would be a separate architectural phase and likely requires backend auth, a hardened multi-user AI gateway, cloud persistence/sync, abuse protection, billing/quotas, privacy/compliance review, and release hardening.

## 13. Security/privacy baseline

- Least-privilege Google OAuth scopes.
- Secrets excluded from backup/export.
- Provider credentials never logged.
- Provider OAuth tokens, Codex auth state, and OpenCode Go credentials stay in the isolated Quick Aside runtime namespace on the VPS; the Android app and Personal Admin/Hermes do not own Quick Aside provider secrets.
- Captures/logs may contain sensitive personal/work information; logs and diagnostics should avoid raw content by default.
- Backups/exports need explicit user-visible destination and security posture.
- Public/commercial builds require re-review of credential ownership, scopes, and data handling.

## 14. Key evidence requirements

- deterministic parser/interpreter tests;
- CapturePlan schema/validator tests;
- runtime/gateway contract and failure-path tests before remote integration PASS;
- real-VPS proof that the gateway is privately reachable, restartable, and does not expose provider credentials to Android;
- measured end-to-end Android → private gateway → provider → Android latency for the fast-capture path before choosing/accepting the runtime integration;
- regression evidence that deploying/restarting/rolling back Quick Aside leaves Personal Admin/Hermes/ACK behavior unchanged;
- Room migration tests;
- sync tests + sandbox/real-account verification;
- reminder real-device verification;
- UI screenshot/interaction review against UX v3;
- backup/export round-trip checks before archive/prune is accepted.
