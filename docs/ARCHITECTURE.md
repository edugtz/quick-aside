# Quick Aside — Architecture v0.2

Status: proposed implementation baseline derived from accepted product/UX decisions. Exact Android/API versions must be verified during implementation preflight. Runtime-AI sections reconciled 2026-09-12 (`docs/adr/0001-private-remote-ai-runtime.md`, `docs/adr/0002-quick-aside-owned-private-ai-gateway.md`, and `docs/adr/0003-codex-exec-ephemeral-runtime-protocol.md`).

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

Use the existing `AIProvider` / `CaptureInterpreter` boundary rather than
model-specific calls throughout Android. `CapturePlan` and stored domain
records remain provider-independent.

Runtime direction:

1. Primary model: **GPT-5.6 Luna — explicit Low reasoning** via
   ChatGPT/Codex OAuth.
2. Provider invocation for the first gateway version:
   **`codex exec --ephemeral`**, one fresh bounded process per
   interpretation request.
3. Fallback candidate: **DeepSeek V4 Flash via OpenCode Go**,
   evidence-triggered only.
4. Deterministic/local interpretation for simple known commands remains
   preferred when safe.

QAG-1 is complete and the provider invocation route is selected. The
production gateway itself, Android networking, and runtime execution path
are still not implemented.

### QAG-1 selected provider invocation

The validated runtime shape is:

```text
Quick Aside gateway
    -> fresh bounded codex exec --ephemeral
    -> Quick Aside-specific CODEX_HOME
    -> ChatGPT/Codex OAuth
    -> GPT-5.6 Luna / Low
    -> strict structured result
    -> process exits
```

The QAG-1 spike validated Codex SDK/CLI `0.154.0`. Production must pin and
test an explicit compatible version rather than silently floating.

Invocation invariants:

- explicit `gpt-5.6-luna`;
- explicit Low reasoning;
- ephemeral execution;
- unrelated user/project Codex config ignored;
- unrelated user/project exec rules ignored;
- read-only sandbox;
- strict JSON Schema output;
- dedicated Quick Aside provider-auth namespace;
- provider process terminated after each interpretation.

The official Python SDK/persistent app-server route remains technically
viable but is not selected for v1. QAG-1 observed resident-memory growth
across fresh ephemeral threads, while process-per-request `codex exec`
released Codex memory at request completion.

QAG-2 owns gateway-side process timeout/cancellation, exit-code handling,
output parsing, bounded concurrency, version pinning, health/readiness, and
safe diagnostics.

### Remote runtime boundary

```text
Quick Aside Android
    -> private interpretation request
    -> Quick Aside private AI gateway
        (hosted on shared personal VPS infrastructure)
    -> bounded codex exec --ephemeral
    -> Codex / ChatGPT OAuth
    -> GPT-5.6 Luna Low
    -> provider-neutral untrusted result
    -> Android validation / future execution
```

Quick Aside owns the gateway and all Quick Aside-specific runtime behavior.
Personal Admin/Hermes may share the VPS, but it is not an application
dependency.

The gateway must not use:

- Hermes conversation/context;
- Personal Admin prompts/state/cron/integrations;
- ACK delivery;
- `/home/hermes` as its app/config/auth namespace;
- Hermes global model/fallback policy.

Deployment and rollback must leave Personal Admin operationally unchanged.

Still unresolved for QAG-2/QAG-3:

- gateway server language/runtime;
- exact HTTP endpoint and request/result wire schema;
- private-network/auth mechanism;
- timeout value;
- concurrency/resource limits;
- production filesystem paths;
- systemd details;
- fallback implementation.

### Trusted request context

Trusted provenance remains Android-owned. The provider must not become
authoritative for `sourceCaptureId` or local persistence identity.

QAG-1 also exposed a contract gap: current `AIInterpretationRequest`
contains only `inputText`, while relative-date interpretation requires
trusted capture time/timezone context. QAG-2 must resolve that transport
requirement without delegating local identity/provenance to the provider.

### Fast-capture latency requirement

Runtime architecture must preserve:

```text
speak/type
    -> final local capture/transcript
    -> persist capture locally
    -> private gateway interpretation
    -> local validation
    -> UI reflects result / lightweight receipt
```

Local capture must become durable before remote interpretation.

QAG-1 measured the VPS/provider runtime in seconds-scale requests and proved
both selected/rejected provider-invocation paths. Those measurements are
not Android end-to-end measurements and do not freeze a numeric latency
budget.

QAG-4 must measure the actual:

`Android -> private network -> gateway -> provider -> gateway -> Android`

path before the runtime integration receives final fast-capture acceptance.

### AI safety boundary

- Remote model output is untrusted.
- Flow:
  `remote interpretation -> untrusted structured result -> Quick Aside validation -> future execution`.
- The remote runtime never directly mutates Room, Google Tasks, Google
  Calendar, or reminders.
- Android never receives/stores ChatGPT/Codex OAuth tokens or provider auth
  state.
- Model changes must not require domain-schema changes.
- Logs/diagnostics should avoid raw capture content and must never log
  tokens, keys, auth headers, or OAuth credentials.

### Local-first failure behavior

Remote interpretation must not make capture lossy:

```text
capture -> persist locally -> attempt remote interpretation
```

If interpretation is unavailable, the original Capture remains durable.

Retry/deferred-interpretation policy remains a future focused decision.
Collect lightweight diagnostics such as provider/model, latency, validation
outcome, and fallback/correction rates without unnecessarily storing raw
sensitive content.

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
