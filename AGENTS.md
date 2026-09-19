# Quick Aside — Agent Operating Rules

Quick Aside is a personal-first Android utility for zero-friction capture and explicit management of structured personal information.

## Source-of-truth order

When sources disagree, use this order unless the user explicitly overrides it:

1. The user's latest explicit product decision.
2. The active change package under `docs/changes/...`.
3. `docs/PROJECT_SPEC.md` for accepted product behavior and scope.
4. Accepted ADRs under `docs/adr/` for durable architectural decisions and supersession history.
5. `docs/NAMING.md` for the accepted product name and legacy-codename rule.
6. `docs/ARCHITECTURE.md` for system boundaries and invariants.
7. `docs/UX_UI_REFERENCE.md` + `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png` for UI/UX intent.
8. Actual repository code/manifests/tests for current implementation facts.
9. Platform/vendor documentation for API behavior.

Do not re-plan from zero when `docs/ACTIVE_WORK.md` already points to active work.

## Product naming hard rule

- Accepted product/project name: **Quick Aside**.
- `VoiceApp` is an obsolete pre-bootstrap codename and MUST NOT be introduced in new UI copy, code identifiers, repository names, or external-service identifiers.
- Read `docs/NAMING.md` before creating package/application IDs or brand-facing assets.
- The current v3 PNG may still contain the legacy name inside the image pixels; ignore that embedded label.

## Governance

Use `software-project-orchestrator` proportional governance:

- QUICK: trivial, local, reversible fixes with no durable contract impact.
- STANDARD: normal features, bugs, integrations, UI flows, persistence, or multi-file changes.
- HIGH-ASSURANCE: auth/permissions, data-loss risk, migrations, destructive archive/prune, sync correctness/idempotency, live VPS/network/runtime changes, release/cutover, or platform behavior requiring real-device evidence.

The user retains product, commit, push, merge, and release authority unless explicitly delegated.

## UI/UX hard rule

Any change that creates or materially alters UI/UX MUST, before implementation:

1. Read `docs/UX_UI_REFERENCE.md`.
2. Inspect `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`.
3. Identify which accepted UX invariants are affected in the active change `SPEC.md`.
4. Preserve the visual/product direction unless the user explicitly approves a deviation.
5. Verify the result visually with screenshots or real-device evidence when the change is reviewable.

The image is a **canonical visual-direction reference, not a pixel-perfect implementation spec**. Do not blindly copy generated text, dimensions, or impossible controls. Prefer native Android/Material behavior, accessibility, touch targets, responsive layout, and the written UX contract when the image is ambiguous.

For generative visual assets, follow the project AI workflow: Google AI Plus primary when available; ChatGPT image generation fallback. Standard semantic UI icons should come from Material/platform icon sets rather than generated artwork.

## Core UX invariants

- Primary happy path: **invoke → speak/type → interpret/save → lightweight receipt → continue**.
- Transcript review and interpretation review are optional; they are never mandatory gates for high-confidence captures.
- Low confidence asks the smallest possible clarifying question instead of opening a large editor.
- `Capture` is an action, not a permanent navigation destination.
- Main management destinations: `Inicio`, `Pendientes`, `Listas`, `Memoria`.
- Voice is primary, text is first-class.
- Undo must remain readily available after automated changes.
- UI should feel Android-native with custom personality, not like a generic AI chat app.
- The voice orb is a capture-state/brand motif, not the center of every management screen.

## Data invariants

- AI interprets data; it is not the system's memory.
- User data is stored as structured records with original/raw capture retained when useful.
- Never silently delete durable personal information.
- Never use destructive Room migrations for user data.
- Archive/prune must require a verified export/backup before destructive deletion.
- Google Tasks synchronization is a must-have for Personal/Trabajo tasks.
- Google Calendar synchronization is a must-have for events.
- Local reminders are the MVP mechanism for user-configured Note/Task reminders; FCM is deferred.

## Runtime AI policy

Runtime interpretation remains provider-abstracted. QAG-1 is now
**complete — PASS**.

Current runtime direction for the personal MVP:

1. GPT-5.6 Luna — explicit Low reasoning — primary model via ChatGPT Plus /
   Codex OAuth.
2. First gateway provider invocation: `codex exec --ephemeral`, one fresh
   bounded process per interpretation request.
3. The runtime uses a Quick Aside-specific `CODEX_HOME`, ignores unrelated
   user/project Codex config/rules for the invocation, uses a read-only
   sandbox, and requires strict structured output.
4. DeepSeek V4 Flash via OpenCode Go remains a fallback candidate only,
   evidence-triggered.
5. MiMo-V2.5 is not primary; the completed runtime evaluation observed
   worse schema/contract reliability than the finalists.
6. No automatic reasoning escalation. Fallback, if implemented, is for
   eligible provider/runtime failure classes rather than semantic
   disagreement.

The persistent Python SDK/app-server route was proven but is not selected
for v1 because QAG-1 observed increasing resident memory across fresh
ephemeral threads on the target VPS. It remains a future alternative if
requirements change.

Provider credentials and auth state stay on the isolated Quick Aside
gateway/runtime side and must never be stored in the Android app or owned by
Personal Admin/Hermes. Remote model output is untrusted and must be
validated locally before execution. A remote AI dependency must not make
capture lossy: persist the capture locally first.

QAG-2 minimal gateway implementation, QAG-003R private Tailnet deployment,
and QAG-004 Android gateway integration are complete. QAG-004H Android gateway
client hardening is the current remediation/review gate; it remains stopped at
a validated `CapturePlan` and does not execute actions.

Do not spend time on broad model benchmarking without observed product
evidence requiring it. Model/provider changes must not alter the domain
contract or stored data format.

See:

- `docs/adr/0001-private-remote-ai-runtime.md`
- `docs/adr/0002-quick-aside-owned-private-ai-gateway.md`
- `docs/adr/0003-codex-exec-ephemeral-runtime-protocol.md`
- `docs/changes/QAG-001-runtime-protocol-decision/`

## Verification

Treat tests/build/lint/static analysis/sync evidence/real-device checks as stronger than model self-report. Do not claim PASS for a required gate that was not run.
