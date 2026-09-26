# Quick Aside — Global Acceptance Criteria v0.2

These are product-wide gates. Active changes add narrower acceptance scenarios.

## Capture UX

- High-confidence happy path does not require transcript review, interpretation review, or manual Save.
- Voice and text both reach the same capture/domain pipeline.
- User can correct transcript and interpretation separately.
- Automated changes provide immediate reversible feedback where feasible.
- Low-confidence handling asks the minimum necessary clarification.

## UI/UX consistency

- Any material UI change is reviewed against `docs/UX_UI_REFERENCE.md` and `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`.
- Main information architecture remains Inicio / Pendientes / Listas / Memoria + global capture action unless product approval changes it.
- UI remains Android-native in behavior and accessible; reference images guide direction rather than override platform correctness.
- Meaningful UI changes include screenshot or real-device visual evidence before PASS.

## Persistence

- Durable user data survives app restart/process death.
- Room migrations for user data are explicit and tested.
- No destructive fallback is accepted for durable user data.
- No durable personal information expires or is pruned silently.

## Google Tasks

- Personal/Trabajo tasks are synchronized with the correct Google Task lists.
- Local and external completion/edit/delete behavior follows the active sync contract.
- Offline local mutation remains visible and eventually synchronizes or surfaces a recoverable error.
- No duplicate/corrupt task creation under documented retry scenarios.

## Google Calendar

- Event creation/editing uses the intended Calendar/account and correct date/time/timezone.
- Incremental/background refresh strategy produces acceptable freshness under supported conditions.
- Offline/error states do not silently lose user intent.

## Reminders

- Due date and reminder time remain distinct.
- Scheduled reminders are observed on a real supported Android device under required background/restart scenarios before feature PASS.
- User can snooze.
- Notification actions match record type.
- No engagement/spam notifications are introduced.

## AI interpretation

- Model output is validated before mutations.
- Invalid/unsupported model output cannot directly alter Room/Google data.
- Runtime provider can be switched without migrating user-domain data.
- Primary runtime target is GPT-5.6 Luna Low via the Quick Aside-owned private
  gateway hosted on shared VPS infrastructure; DeepSeek V4 Flash via OpenCode
  Go is an evidence-triggered fallback candidate. Provider changes are driven by
  observed failures, not speculative benchmark work.
- Provider OAuth tokens and credentials are never stored on the Android device.
- Quick Aside provider credentials/auth state are isolated from Personal
  Admin/Hermes.
- A capture is persisted locally before remote interpretation; remote unavailability must not lose user intent or silently discard a capture.
- Remote interpretation cannot directly mutate Room, Google Tasks/Calendar, or local reminders.
- AI does not act as the source of historical truth.
- The gateway must provide fresh/bounded inference without depending on Hermes
  conversation history, memory, prompts, tools, cron, state, or integrations.
- Runtime integration PASS requires measured latency evidence for the real
  fast-capture path against the QAG-1 budget once that budget is established.

## History and retrieval

- Structured logs retain enough fields to answer supported deterministic queries.
- Original/raw capture is retained according to policy when needed to preserve information and correct extraction errors.
- Historical Mandado sessions remain independently retrievable.

## Backup/archive

- User can create a recoverable machine-readable backup before destructive archive/prune becomes available.
- Human-readable export is separate from restoration format.
- Archive/prune never deletes before the required export is successfully written and verified.
- User receives clear warning before any irreversible deletion.

## Security/privacy

- Least-privilege OAuth scopes.
- Secrets are not included in export/backup or logs.
- Diagnostics avoid raw personal/work capture content by default.
- Public distribution cannot use a client-extractable shared provider key architecture.
- Provider credentials and auth state stay on the isolated Quick Aside
  gateway/runtime; the Android client owns no provider secrets.
- Sharing VPS infrastructure with Personal Admin does not permit Quick Aside to
  reuse Hermes/Personal Admin credentials, memory, state, or application data.

## Engineering gates

Before a change can receive `PASS`, its required applicable gates must have actual evidence. Test the Change delta by default: changed-behavior tests, directly affected regressions, and compile/build/static checks when applicable. Do not make full JVM/connected suites, unrelated regressions, repository-wide lint, or real-device QA automatic gates. Require repository/CI policy, justified cross-cutting risk, a concrete failure/reviewer finding, or explicit user request to add them. HIGH-ASSURANCE strengthens correctness and review of the affected risk, not generic test count. Possible gates include:

- build;
- targeted tests;
- static analysis/lint/type checks as configured;
- integration/sandbox checks where external APIs are touched;
- current official vendor/runtime documentation verification where provider behavior matters;
- real-device QA when the Change modifies a device/platform property that reasonable automated checks cannot prove;
- real-VPS evidence where gateway/network/auth/systemd behavior is changed;
- visual evidence for material UI changes.

A model statement that something works is not evidence.

### Verification reuse and ownership

A passed gate remains valid while the facts it depends on remain unchanged. Reuse its recorded source manifest/fingerprint and provenance by default. A rerun needs a concrete invalidator: relevant production source or material test-seam change; a dependency, build, schema, or configuration change affecting that gate; relevant environment change; artifact mismatch; missing, corrupt, or unreviewable evidence; or a concrete new defect/finding the old gate did not cover. Invalidate only the affected claims.

Another session or agent, HIGH-ASSURANCE by itself, implementation moving to review, a commit/push of identical tested source, documentation/evidence-only edits, and a wish for fresh timestamps do not invalidate evidence. Reviewers must identify a source/evidence mismatch, provenance gap, missing/corrupt artifact, relevant environment change, or concrete uncovered failure mode before requesting repetition. HIGH-ASSURANCE strengthens provenance and independence, not the number of executions.

Assign each property one primary owner gate: the strongest inexpensive gate capable of proving it. A requirement may aggregate complementary gates; one end-to-end run need not re-prove lower-level invariants. Real devices, networks, and services verify the delta that requires that environment. Physical QA does not repeat deterministic Room, JVM, or Compose guarantees; use an emulator for routine connected instrumentation. After an operator, harness, or environment error, recover existing evidence and repeat only the missing observation before asking for another human acceptance interaction.

Every future Change plan (and QA notes when useful, held in the agent/orchestrator work context) includes a Verification Ownership Matrix with **Requirement**, **Owner gate**, **Why this gate is sufficient**, **Supplemental real-environment evidence, if any**, and **Invalidation trigger**. Verify newly changed behavior, directly affected regressions, repository-required baseline gates, and unique real-environment risks. Do not copy a previous Change's whole gate suite into a cumulative burden.

QA1 pairing on the physical phone is persistent environment state. Never revoke, reset, or delete it during test, acceptance, evidence cleanup, or Change closeout. Alter it only when the active Change explicitly tests pairing, revocation, key rotation, or another auth lifecycle behavior.

User-operated QA is exceptional. Request it only when the Change alters a material property that inherently needs genuine human or unavailable external interaction and no reasonable automated substitute proves it. State the newly changed property, why automation is insufficient, and the single minimal human action before asking. Reuse historical acceptance of unchanged voice, Android, network, and device subsystems.

Change-local SPEC/PLAN/TASKS/QA and verification results are ephemeral orchestration records, not repository documentation; they live in the agent/orchestrator work context and the implementation report. Normal Changes MUST NOT create or persist `docs/changes/<id>/` packages or repository-local evidence directories by default, and must not commit per-test run records, JUnit XML, source manifests, status snapshots, attempt diaries, raw command logs, or low-value screenshots. Persist a separate artifact only when it has lasting value that source/tests/report/CI cannot reasonably represent. Complete required Change-specific automated verification before the implementation commit; optional manual QA, documentation polish, and independent review do not delay that provenance checkpoint. Commit, push, review, and merge remain user-controlled.
