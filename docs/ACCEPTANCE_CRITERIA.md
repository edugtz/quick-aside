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
- MiMo-V2.5 is the initial primary provider; fallback/provider changes are driven by observed failures, not speculative benchmark work.
- AI does not act as the source of historical truth.

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

## Engineering gates

Before a change can receive `PASS`, all required applicable gates must have actual evidence:

- build;
- targeted tests;
- static analysis/lint/type checks as configured;
- integration/sandbox checks where external APIs are touched;
- real-device QA when behavior depends on Android background/voice/notifications/system surfaces;
- visual evidence for material UI changes.

A model statement that something works is not evidence.
