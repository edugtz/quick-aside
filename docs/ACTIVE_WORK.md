# Quick Aside — Active Work

## Active change

None.

Status: **NO ACTIVE CHANGE**

No CHG-027 or other next implementation change has been selected.

## Latest completed change

`docs/changes/026-local-pendientes-ui/`

CHG-026 — Local Pendientes UI Foundation — is complete, reviewed, closed,
and merged into `main`.

Latest completed implementation baseline:

`17ee862de90c19a54338397d239332ec908cef25`

Commit:

`docs: close Change 026`

Independent engineering review: **PASS**

`BLOCKER 0 / MAJOR 0 / MINOR 0`

## Current state

- Changes 020 through 026 relevant to the current capture/task foundation are
  merged/complete.
- Local CapturePlan validation and the provider-neutral
  `CaptureInterpreter` / `AIProvider` boundary remain accepted.
- Local Task persistence, completion/reopen, reversible Task actions, and the
  Pendientes UI foundation are complete.
- Room remains version 7.
- Google Tasks synchronization is not implemented.
- Runtime AI integration is not implemented.
- Android has no real remote `AIProvider` implementation yet.
- Quick Aside owns its private AI gateway; Personal Admin/Hermes shares VPS
  infrastructure only and is not an application dependency.

## Proven baseline

- Product name: **Quick Aside**.
- Canonical product contract: `docs/PROJECT_SPEC.md`.
- Canonical architecture: `docs/ARCHITECTURE.md`.
- Canonical UX contract: `docs/UX_UI_REFERENCE.md`.
- Canonical visual reference:
  `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`.
- Runtime direction remains GPT-5.6 Luna Low via the Quick Aside-owned private
  gateway, with provider credentials kept off Android.
- `docs/adr/0001-private-remote-ai-runtime.md` remains accepted for the private
  remote-runtime decision.
- `docs/adr/0002-quick-aside-owned-private-ai-gateway.md` supersedes ADR-0001
  only where the older ADR assigned gateway ownership/planning to Personal
  Admin/shared-runtime work.
- QAG-0 read-only VPS preflight is complete and passed.

## Exact next gate

QAG-1 — runtime/protocol decision.

Use current supported provider/runtime behavior plus measured evidence to choose
the smallest viable Quick Aside gateway runtime/protocol. Do not freeze SDK vs
CLI vs app-server, server language, endpoint schema, auth details, timeout,
concurrency, fallback implementation, or numeric latency budget before QAG-1
evidence supports those choices.

Do not select CHG-027 merely because QAG-1 is next. Live VPS/network/auth changes
remain HIGH-ASSURANCE and require explicit user approval at the applicable gate.
