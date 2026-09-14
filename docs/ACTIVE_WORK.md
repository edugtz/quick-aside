# Quick Aside — Active Work

## Active change

None.

Status: **NO ACTIVE IMPLEMENTATION CHANGE**

No CHG-027 or later implementation change has been selected.

## Latest completed runtime-gateway change

`docs/changes/QAG-002-minimal-gateway/`

QAG-2 — Minimal Private Gateway — is implementation-complete, verified, and
reviewed for merge into `main`.

Final engineering review:

`PASS_WITH_NOTES — BLOCKER 0 / MAJOR 0 / MINOR 0`

Notes are limited to two upstream FastAPI/Starlette deprecation warnings in
the local test environment. They do not affect QAG-2 behavior.

## QAG-2 verified result

Repository-owned gateway foundation:

```text
Android (future QAG-4 integration)
    -> /v1/interpret
    -> bounded Quick Aside gateway
    -> codex exec --ephemeral
    -> GPT-5.6 Luna / Low
    -> strict provider-neutral result
    -> Android validation / future execution
```

Verified QAG-2 evidence includes:

- Python gateway package and deterministic HTTP/provider contracts;
- trusted `capturedAt` + IANA `timeZone` transport;
- strict provider output validation;
- bounded request/input/action/field/output sizes;
- bounded queue + execution wall clock;
- process termination/reaping and cancellation behavior;
- Codex CLI `0.154.0` pin/readiness checks;
- privacy-safe diagnostics;
- `33 passed` deterministic tests;
- real Luna Low contract smoke: PASS;
- semantic Personal/Trabajo routing smoke: `5/5 PASS`;
- residual Codex process check: `NONE`;
- `git diff --check`: PASS.

No new ADR was required. ADR-0003 remains the durable provider-invocation
decision; QAG-2 implementation details are captured in architecture and the
QAG-2 change package.

## Explicit stop boundary

QAG-2 did **not** authorize or perform:

- live VPS deployment;
- Quick Aside production Unix-user creation;
- systemd mutation;
- Tailscale mutation;
- UFW mutation;
- public exposure;
- Android remote-provider networking;
- DeepSeek fallback;
- Personal Admin/Hermes changes.

## Next gate

**QAG-3 — live private gateway deployment — is next, but is not active.**

QAG-3 is **HIGH-ASSURANCE** work and requires explicit user approval before
mutating live VPS/network/systemd state. It must preserve Personal Admin /
Hermes behavior and include deployment, rollback, private reachability, and
real-environment evidence.

QAG-4 Android integration remains later and must wait for an independently
healthy deployed gateway.
