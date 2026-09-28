# Quick Aside — Global Acceptance Criteria v0.3

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
- Meaningful UI changes receive a visual check appropriate to the touched UI. Emulator screenshots are the default; real-device evidence is required only when the changed property depends on device/platform behavior that reasonable automation cannot prove.

## Persistence

- Durable user data survives app restart/process death.
- Room migrations for user data are explicit and tested.
- No destructive fallback is accepted for durable user data.
- No durable personal information expires or is pruned silently.

## List lifecycle

- Mandado remains session-based with independently retrievable historical sessions; Compras remains continuous.
- A valid high-confidence Capture targeting Mandado does not require the user to manually start a Mandado session first.
- With no active Mandado, applying the Capture creates exactly one active session and its captured Mandado items atomically; failure/cancellation cannot leave a partial session/item result.
- With an active Mandado whose last material activity is no more than 7 elapsed days old, Capture reuses that session.
- With an active Mandado whose last material activity is more than 7 elapsed days old, Quick Aside performs no Mandado mutation until it obtains the focused `Continuar` / `Nuevo` lifecycle choice.
- `Continuar` reuses the existing Mandado. `Nuevo` ends the old session, creates the new active session, and applies the pending captured items as one atomic local operation.
- Canceling the stale-session question leaves the original Capture durable and leaves Mandado sessions/items unchanged.
- The 7-day stale threshold never silently expires, ends, deletes, or prunes durable Mandado data. Passive reads/navigation do not count as material activity.
- Automated Mandado mutations continue to provide targeted reversible feedback without deleting or altering unrelated list data.

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

Before a Change can receive `PASS`, the applicable Change-specific gates must have actual evidence.

Default verification is the minimum sufficient delta:

- automated tests for changed/new behavior;
- directly affected regressions when justified by the touched boundary;
- compile/build/static/schema checks that apply to the changed surface;
- integration/sandbox checks only when an external API or service contract is changed;
- real-VPS or other live-environment checks only when that environment is part of the Change;
- visual checks for material UI changes, normally on the emulator;
- real-device checks only for newly changed device/platform behavior that reasonable automation cannot establish.

Do **not** automatically require full JVM suites, full connected suites, unrelated regressions, repository-wide lint, broad privacy rescans, or physical-device acceptance. HIGH-ASSURANCE strengthens verification of the affected risk, provenance, failure-path coverage, and independent review; it does not mean maximizing test count or human involvement.

A model statement that something works is not evidence. The implementation agent must report exact automated verification commands and results.

### Verification ownership and reuse

For every nontrivial Change, define in orchestration context which gate owns each acceptance property. Use the strongest inexpensive gate that actually proves the property; do not make multiple gates re-prove the same invariant unless they cover a distinct failure mode.

A passed result remains valid while the facts it depends on remain unchanged. Rerun only when there is a concrete invalidator, such as:

- relevant production source changed;
- a materially relevant test seam changed;
- dependency/build/schema/config changed in a way that affects the claim;
- the tested artifact differs from the reviewed artifact;
- a relevant environment changed;
- retained evidence is missing/corrupt/unreviewable;
- a concrete new defect or review finding exposes a failure mode the old gate did not cover.

Not invalidators:

- another chat/session/agent;
- HIGH-ASSURANCE by itself;
- moving from implementation to review;
- commit/push of identical tested source;
- documentation-only edits;
- wanting a newer timestamp.

Use commit SHA/diff and existing test provenance to establish identity. A separate source-manifest artifact is optional and must not be generated by default.

### Manual QA and real environments

User-operated QA is exceptional. Request it only when the Change modifies a material property that inherently requires genuine human or unavailable external interaction and no reasonable automated substitute proves it. Before asking the user, identify the exact newly changed property, why automation is insufficient, and the single minimal human action required.

Voice support, Android use, networking, or physical-device availability alone do not make human QA mandatory. Reuse historical acceptance for unchanged subsystems.

QA1 pairing on the physical Android phone is durable environment state. Never revoke, reset, or delete it during routine testing, evidence cleanup, acceptance cleanup, or Change closeout. Alter it only when the active work explicitly tests pairing/revocation/key rotation or another auth-lifecycle behavior.

### Documentation and evidence

Normal Changes do not create `docs/changes/<id>/` packages or evidence trees by default. Change-local specification, planning, task decomposition, verification notes, and implementation reports remain ephemeral orchestration context.

Persist repository documentation only when the Change alters a durable project contract. Keep a separate evidence artifact only when it has lasting review value that cannot reasonably be represented by source/tests, CI, or the implementation report.

Once the implementation and required Change-specific automated verification are complete, the Change is commit-ready. Optional manual QA, documentation polish, and independent review do not delay that provenance checkpoint. The user retains commit/push/merge/release authority.
