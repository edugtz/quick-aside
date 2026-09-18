# ADR-0005 — Private Tailnet Gateway Ingress

Status: Accepted
Date: 2026-09-18

Supersedes: ADR-0004 public authenticated gateway ingress.

## Context

Quick Aside requires a server-side AI gateway without storing provider
credentials on Android and without depending on Personal Admin.

ADR-0004 selected public authenticated HTTPS ingress through Caddy.

That architecture was implemented and extensively reviewed, but it was not
adopted as the final production deployment path. Review of the public proxy
boundary materially increased operational complexity and attack surface.

The live VPS already uses Tailscale for an independent Personal Admin
Service.

Current preflight confirms:

- the VPS has a tag-based Tailscale identity;
- Personal Admin is independently exposed through
  `svc:personal-admin-ack`;
- Personal Admin remains on `127.0.0.1:2587`;
- Quick Aside can retain its backend on `127.0.0.1:2588`;
- Quick Aside currently has no active public ingress;
- Caddy is absent from the live VPS.

## Decision

Quick Aside production ingress will use private Tailscale Services and
Tailscale Serve.

Target:

    svc:quickaside
      -> HTTPS tcp:443
      -> http://127.0.0.1:2588

Expected private hostname:

    quickaside.taildc9db9.ts.net

Tailscale Funnel is prohibited.

QA1 application authentication remains enabled as defense in depth.

Quick Aside continues to use its dedicated gateway process and systemd
service.

Personal Admin remains an independent Service and must not be reset,
replaced or otherwise modified by Quick Aside deployment.

No public Quick Aside firewall opening is required.

## Trusted platform boundary

For this deployment architecture, the following are treated as trusted
platform components:

- authorized VPS root administration;
- Ubuntu/kernel;
- systemd;
- authorized Tailscale client/tailscaled installation;
- authorized package-management state;
- authorized Tailscale control-plane administration.

Configuration mistakes, incorrect exposure, failure paths and deployment
procedures remain in scope for review.

## Consequences

### Positive

- removes Quick Aside public ingress;
- removes Caddy from the Quick Aside runtime request path;
- preserves localhost-only FastAPI;
- preserves QA1;
- reuses the existing private-network platform already present on the VPS;
- avoids provider credentials on Android;
- keeps Quick Aside operational ownership separate from Personal Admin.

### Trade-offs

- Android requires authorized tailnet connectivity;
- Tailscale availability becomes part of ingress availability;
- Service definition and access policy become deployment state;
- the shared VPS requires rollback operations to be carefully scoped.

## Rollback consequence

Normal rollback must affect only Quick Aside:

1. drain `svc:quickaside`;
2. allow active requests to complete;
3. clear only `svc:quickaside`;
4. stop/disable only `quickaside-gateway`;
5. verify `svc:personal-admin-ack` remains unchanged.

Global Tailscale Serve reset is not permitted for Quick Aside operations.

## Superseded decision

ADR-0004 is retained as historical decision and review evidence.

It must not be used as the active production ingress architecture after this
ADR.
