# Quick Aside — Active Work

## Active change

None.

Status: **NO ACTIVE CHANGE**

No CHG-027 or other next implementation change has been selected.

## Latest completed change

`docs/changes/026-local-pendientes-ui/`

CHG-026 — Local Pendientes UI Foundation — is complete, reviewed, closed,
and merged into `main`.

Current verified `main` baseline:

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
- The private Quick Aside AI gateway is being reconciled/planned before runtime
  implementation resumes.

## Proven baseline

- Product name: **Quick Aside**.
- Canonical product contract: `docs/PROJECT_SPEC.md`.
- Canonical architecture: `docs/ARCHITECTURE.md`, subject to the runtime
  ownership reconciliation now in progress.
- Canonical UX contract: `docs/UX_UI_REFERENCE.md`.
- Canonical visual reference:
  `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`.
- Runtime direction remains GPT-5.6 Luna Low via a private VPS gateway, with
  provider credentials kept off Android.
- `docs/adr/0001-private-remote-ai-runtime.md` remains accepted, but its
  cross-project ownership wording requires reconciliation with the user's newer
  explicit decision that Quick Aside owns its gateway and only shares VPS
  infrastructure with Personal Admin.

## Exact next gate

Reconcile the durable runtime-ownership documentation before selecting the next
implementation change.

Do not select CHG-027 or freeze gateway implementation details until that
documentation reconciliation is complete.