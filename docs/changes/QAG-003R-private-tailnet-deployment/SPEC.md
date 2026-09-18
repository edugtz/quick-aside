# QAG-003R — Private Tailnet Gateway Deployment

Status: ENGINEERING COMPLETE — PASS_WITH_NOTES
Governance: HIGH-ASSURANCE

## Objective

Replace only the superseded public-Caddy ingress architecture from QAG-003
with private Tailscale Services + Tailscale Serve.

Preserve the valid QAG-003 work already implemented:

- Quick Aside gateway;
- QA1 request authentication;
- P-256 ECDSA signatures;
- timestamp validation;
- nonce/replay protection;
- pairing/revocation;
- application rate limits;
- packaging fixes;
- hardened localhost-only systemd service.

## Target architecture

    Quick Aside Android
      -> Tailscale
      -> svc:quickaside
      -> Tailscale Serve HTTPS
      -> http://127.0.0.1:2588
      -> Quick Aside gateway
      -> provider runtime

Expected private hostname:

    quickaside.taildc9db9.ts.net

Expected Service resource:

    svc:quickaside
    tcp:443

## Security boundaries

Quick Aside FastAPI must remain bound only to:

    127.0.0.1:2588

Tailscale Funnel is prohibited.

No public Quick Aside UFW rule is required.

QA1 remains enabled as defense in depth.

Caddy is not part of the target production architecture.

## Personal Admin isolation

The existing service is protected:

    svc:personal-admin-ack
      -> http://127.0.0.1:2587

QAG-003R must not reset, clear, replace or modify that Service.

Quick Aside must remain independent of Personal Admin, Hermes, ACK state,
cron jobs, databases and credentials.

## Control-plane boundary

Before activating `svc:quickaside`, Gate E must verify:

- the Service definition exists or creation is explicitly approved;
- resource is `tcp:443`;
- expected MagicDNS identity is correct;
- the VPS remains eligible as a tag-based Service host;
- the existing tailnet Access Controls are understood and preserved unless the
  user explicitly approves a broader policy change.

Gate E observed that the tailnet intentionally uses a broad existing
`* -> * -> *` grant. QAG-003R does not rewrite that policy solely to restrict
Quick Aside because doing so could affect Personal Admin and other existing
services. Tailscale is the private-network boundary; QA1 remains the
application-authorization boundary for protected Quick Aside operations.

Any required Service definition, tag or auto-approval change requires explicit
user approval before mutation.

## Privacy

Production logs must not contain raw capture text by default, provider/OAuth
credentials, QA1 authentication material or unrelated Personal Admin data.

Gate E must test normal and backend-unavailable paths using synthetic canary
values and inspect both:

    journalctl -u tailscaled
    journalctl -u quickaside-gateway

## Rollback

Normal rollback must affect Quick Aside only:

1. drain `svc:quickaside`;
2. allow active requests to complete;
3. clear only `svc:quickaside`;
4. stop/disable only `quickaside-gateway`;
5. verify `svc:personal-admin-ack` remains unchanged.

Global Tailscale Serve reset is prohibited.

## Acceptance criteria

1. Existing QA1 behavior remains unchanged.
2. Existing systemd hardening remains intact.
3. Quick Aside listens only on `127.0.0.1:2588`.
4. Active Caddy deployment artifacts are removed.
5. `svc:quickaside` provides private HTTPS only.
6. Funnel is not configured for Quick Aside.
7. No public Quick Aside firewall/listener is introduced.
8. Personal Admin remains unchanged.
9. Local health succeeds before Tailscale activation.
10. Private health/readiness succeeds from a tailnet client.
11. Non-tailnet clients cannot reach Quick Aside; tailnet connectivity follows
    the existing tailnet policy and QA1 remains the application authorization
    boundary.
12. QA1 signed request, replay rejection, pairing and revocation work privately.
13. Production logging does not expose raw capture content or QA1 secrets by
    default; deterministic tests continue to cover failure-path privacy.
14. Restart persistence is demonstrated.
15. Reboot persistence is demonstrated only when separately authorized.
16. Rollback remains strictly scoped to `svc:quickaside` and
    `quickaside-gateway`; the rollback contract is deterministically verified
    and must not disturb Personal Admin.

## Out of scope

- Android endpoint integration;
- QA1 redesign;
- provider/model redesign;
- DeepSeek fallback;
- public ingress;
- Tailscale Funnel;
- Personal Admin changes.
