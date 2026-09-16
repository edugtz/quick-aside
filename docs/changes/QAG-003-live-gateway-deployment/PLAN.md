# QAG-003 — Live Authenticated Gateway Deployment — PLAN

Governance: **HIGH-ASSURANCE**

## Strategy

Proceed in explicit gates:

    A. live read-only preflight
    B. authenticated gateway implementation
    C. deterministic security verification
    D. independent security review
    E. explicit production-mutation approval
    F. isolated gateway/Codex deployment
    G. Caddy/TLS public ingress
    H. real external-network verification
    I. restart + rollback verification
    J. separately approved VPS reboot
    K. final review

No gate grants authorization for the next gate automatically.

## Gate A — Live preflight

Status: COMPLETE — PASS.

Verified live host facts include:

- Ubuntu 26.04.1 LTS;
- 2 vCPU;
- 3.7 GiB RAM;
- approximately 2.5 GiB available;
- no swap;
- approximately 32 GiB free disk;
- Python 3.14.4;
- no Quick Aside user/paths/service;
- proposed port `2588` free;
- Personal Admin ACK healthy on `127.0.0.1:2587`;
- Hermes healthy;
- UFW default deny incoming.

## Gate B — Authenticated gateway implementation

Implement locally before production mutation:

- device registry;
- device revocation;
- SQLite auth state;
- one-time pairing-code lifecycle;
- `POST /v1/pair`;
- QA1 canonical request format;
- ECDSA P-256 / SHA-256 verification;
- ±120 second timestamp validation;
- persistent nonce replay rejection;
- interpretation rate limiting;
- pairing rate limiting;
- privacy-safe auth errors/logging;
- local administration CLI.

Authentication must happen before Codex invocation.

QAG-2 provider interpretation behavior remains unchanged behind authentication.

## Gate C — Deterministic verification

Required tests include:

- valid signed request;
- unknown device rejected;
- revoked device rejected;
- invalid signature rejected;
- modified body rejected;
- stale timestamp rejected;
- excessive future timestamp rejected;
- replayed nonce rejected;
- replay remains rejected after app recreation/restart;
- malformed auth headers rejected;
- expired/invalid/used pairing code rejected;
- pairing rate limit;
- interpretation rate limit;
- auth failure never invokes provider;
- all existing QAG-2 tests remain green.

## Gate D — Independent security review

Review:

- canonicalization;
- cryptography usage;
- replay persistence;
- timestamp handling;
- pairing lifecycle;
- SQLite ownership/permissions;
- rate limiting;
- logging/privacy;
- Caddy boundary/public exposure;
- rollback behavior.

No unresolved BLOCKER/MAJOR may proceed.

## Gate E — Production approval

Present exact VPS mutation commands and effects.

No user creation, package installation, filesystem mutation, firewall change,
systemd change, Caddy installation, Codex OAuth login, or public exposure
occurs without explicit user approval.

## Gate F — Isolated deployment

Expected shape:

- user: `quickaside`;
- app/runtime: `/opt/quickaside-gateway`;
- state/auth: `/var/lib/quickaside`;
- config: `/etc/quickaside-gateway`;
- listener: `127.0.0.1:2588`;
- dedicated Codex installation;
- dedicated `CODEX_HOME`;
- dedicated systemd unit.

## Gate G — HTTPS ingress

Use standard Caddy as TLS terminator/reverse proxy.

Caddy proxies only approved Quick Aside routes to localhost.

Application authentication and rate limiting remain in the gateway.

A dedicated public Quick Aside hostname must resolve to the VPS before TLS
activation.

Only the minimum public firewall rules required for HTTPS/TLS bootstrap may be
added. Port `2588` is never publicly exposed.

Tailscale is not part of the Quick Aside request path.

## Gate H — External verification

From a network that does not depend on Tailscale:

- verify valid TLS;
- verify unsigned `/v1/interpret` is rejected;
- verify valid signed request succeeds;
- verify replay is rejected;
- verify rate limiting;
- verify provider remains bounded;
- verify no residual Codex process;
- verify privacy-safe logs;
- verify Personal Admin/Hermes remain healthy.

## Gate I — Rollback

Rollback affects only Quick Aside:

1. disable Quick Aside Caddy route;
2. stop/disable Quick Aside systemd service;
3. remove only Quick Aside firewall additions if applicable;
4. preserve Quick Aside auth/state unless explicit deletion is requested;
5. verify Personal Admin/Hermes remain healthy.

Tailscale configuration is not part of Quick Aside rollback.

## Gate J — Host reboot

Requires separate explicit user approval.

After reboot verify Quick Aside systemd auto-start, Caddy/TLS,
authentication/provider operation, Personal Admin/Hermes health, and firewall
posture.

## Gate K — Final review

Review actual branch diff, deterministic tests, security findings, deployed
revision, TLS/external evidence, rollback evidence, reboot evidence if required,
and durable documentation.

Final verdict must be exactly PASS, PASS_WITH_NOTES, or BLOCKED.
