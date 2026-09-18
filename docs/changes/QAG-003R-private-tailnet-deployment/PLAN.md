# QAG-003R — Plan

Status: COMPLETE
Governance: HIGH-ASSURANCE

## Phase A — Repository replacement

No production mutation.

1. Preserve the existing gateway implementation and QA1 security behavior.
2. Preserve the existing hardened `quickaside-gateway.service`.
3. Remove only the superseded public-Caddy deployment artifacts.
4. Replace the deployment runbook with the private Tailscale contract.
5. Replace Caddy-specific deployment regression tests with Tailscale-focused
   deployment-contract tests.
6. Add ADR-0005 superseding ADR-0004.
7. Update ACTIVE_WORK to point to QAG-003R.
8. Run deterministic local verification.
9. Produce a review artifact.
10. Run independent HIGH-ASSURANCE Gate D review.

No VPS, Tailscale control-plane, OAuth, firewall or Android mutation occurs
in Phase A.

## Phase B — Gate E control-plane preflight

Read-only first.

Verify:

- current `svc:personal-admin-ack` configuration;
- current Quick Aside systemd state;
- current listeners and UFW state;
- `svc:quickaside` definition;
- Service resource `tcp:443`;
- expected MagicDNS identity;
- VPS eligibility as a tag-based Service host;
- existing tailnet Access Controls and the decision to preserve them;
- absence of Quick Aside Funnel configuration.

If `svc:quickaside` does not exist, or if a Service definition, tag or
auto-approval policy change is needed:

1. stop;
2. describe the exact control-plane mutation;
3. obtain explicit user approval;
4. perform only the approved mutation;
5. re-run the read-only preflight.

Observed Gate E decision: preserve the existing tailnet-wide allow policy; do
not introduce Quick Aside-specific grants in this change. QA1 remains the
application authorization boundary.

## Phase C — VPS activation

Requires explicit user approval.

1. Install/update only Quick Aside application, configuration and systemd
   files.
2. Verify ownership and restrictive permissions.
3. Start `quickaside-gateway`.
4. Verify it listens only on `127.0.0.1:2588`.
5. Verify local `/healthz`.
6. Verify Personal Admin remains unchanged.
7. Configure the Quick Aside Tailscale Service:

       sudo tailscale serve \
         --service=svc:quickaside \
         --https=443 \
         http://127.0.0.1:2588

8. Approve only the Quick Aside Service advertisement if required.
9. Verify the resulting Tailscale Service configuration.
10. Verify Personal Admin remains unchanged.
11. Verify Funnel is not configured for Quick Aside.
12. Verify no public Quick Aside listener or UFW rule exists.
13. Verify private `/healthz` from an authorized tailnet client.

## Phase D — Security/runtime evidence

While the deployment is active:

1. Exercise a valid QA1 signed request through the private endpoint.
2. Verify replay rejection.
3. Verify revocation.
4. Verify pairing behavior.
5. Verify application rate limits.
6. Exercise synthetic logging canaries with the backend healthy.
7. Stop only `quickaside-gateway`.
8. Exercise the same synthetic request through Tailscale Serve.
9. Restore the gateway immediately.
10. Inspect `tailscaled` and `quickaside-gateway` journals.
11. Fail the gate if fake signature, nonce or capture canaries appear.

Never use real secrets or personal capture text for failure-path testing.

## Phase E — Persistence

Verify:

- Quick Aside service restart;
- Service mapping still correct;
- Personal Admin still correct.

VPS reboot requires separate explicit approval.

After an approved reboot, verify both Quick Aside and Personal Admin again.

## Phase F — Rollback

Normal rollback:

1. drain only `svc:quickaside`;
2. wait for active Quick Aside requests to complete;
3. clear only `svc:quickaside`;
4. disable/stop only `quickaside-gateway`;
5. verify `svc:personal-admin-ack` is unchanged;
6. verify `127.0.0.1:2588` is gone;
7. verify Personal Admin remains on `127.0.0.1:2587`.

Emergency containment may skip graceful drain completion, but may still
remove only Quick Aside.

Global Tailscale Serve reset is prohibited.

## Phase G — Android

Separate reviewable change after the VPS deployment independently passes.

QAG-003R does not modify the Android endpoint or client networking.
