# ACTIVE WORK

Status: **ACTIVE HIGH-ASSURANCE CHANGE**

## Active change

`docs/changes/QAG-003-live-gateway-deployment/`

Branch:

`qag-003-live-gateway-deployment`

Base:

`3469adb7212127e89ed896d7601bd37c360dc2b3`

## Change

QAG-3 — Live Authenticated Gateway Deployment.

QAG-3 deploys the verified QAG-2 gateway behind a Quick Aside-owned
authenticated HTTPS boundary.

Governance: **HIGH-ASSURANCE**

## Proven baseline

- QAG-0: COMPLETE — PASS.
- QAG-1: COMPLETE — PASS.
- QAG-2: COMPLETE — PASS_WITH_NOTES.
- QAG-2 deterministic gateway suite: 33 passed.
- Real Luna Low contract smoke: PASS.
- Residual Codex process check: NONE.
- QAG-3 live VPS preflight: PASS.
- Personal Admin ACK baseline: HTTP 200.
- Hermes gateway baseline: active/running.

## Current architecture

ADR-0004 supersedes the previous private-network-only ingress assumption.

Quick Aside must work independently of Tailscale.

Target:

    Android
      -> HTTPS + device-bound ECDSA signature
      -> Caddy
      -> 127.0.0.1:2588
      -> Quick Aside gateway
      -> Codex exec --ephemeral
      -> GPT-5.6 Luna Low

Personal Admin and Hermes are unrelated applications that only share the VPS.

Quick Aside must not use their users, credentials, paths, services, prompts,
state, Tailscale service configuration, or application runtime.

## Completed gate

Gate A — live VPS preflight: **PASS**.

## Current gate

Gate E — explicit production-mutation approval.

Gate D — independent security review: COMPLETE — PASS_WITH_NOTES.

Final Gate D round 6:
- 0 BLOCKER;
- 0 MAJOR;
- 4 MINOR;
- 5 NOTE;
- verdict: PASS_WITH_NOTES.

Current deterministic evidence:
- 99/99 tests passed;
- deployment contract 12/12 passed;
- pairing concurrency 20/20 passed;
- replay concurrency 20/20 passed;
- compileall passed;
- git diff --check passed.

No VPS mutation is authorized merely by opening Gate E.
The user retains explicit approval authority for production changes.

## Current authorization boundary

No production VPS mutation is authorized yet.

Not yet authorized:

- Quick Aside Unix user creation;
- directory creation;
- package installation;
- Caddy installation;
- firewall modification;
- systemd modification;
- Codex OAuth login;
- public TLS activation;
- VPS reboot.

## Next gate

Independent security review.

No production mutation is authorized until Gate D has no unresolved
BLOCKER or MAJOR findings.

Only after deterministic verification and security review pass will exact
production mutation commands be presented for explicit user approval.
