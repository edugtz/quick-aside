# QAG-003R — Tasks

Status: ENGINEERING COMPLETE — PASS_WITH_NOTES

## Ground truth

- [x] Confirm canonical base `13442a90d818511c072b316a77c1398fbc93089b`.
- [x] Confirm QAG-003R branch created from that base.
- [x] Confirm baseline gateway suite passes: 101 tests.
- [x] Confirm VPS Quick Aside baseline was disabled/inactive.
- [x] Confirm Caddy package/service was absent from VPS.
- [x] Confirm `svc:personal-admin-ack` baseline and `127.0.0.1:2587`.
- [x] Confirm no Quick Aside listener on `2588` before activation.
- [x] Confirm VPS Tailscale identity and installed Tailscale CLI behavior.
- [x] Confirm UFW required no new public Quick Aside rule.

## Repository replacement

- [x] Create QAG-003R SPEC / PLAN / TASKS / QA.
- [x] Add ADR-0005 superseding ADR-0004.
- [x] Mark historical QAG-003 public-ingress work as superseded.
- [x] Update `docs/ACTIVE_WORK.md`.
- [x] Remove active Caddy deployment artifacts.
- [x] Replace deployment README with private Tailscale runbook.
- [x] Update `gateway/README.md` deployment authority.
- [x] Replace Caddy-specific deployment contract tests.
- [x] Preserve QA1 implementation.
- [x] Preserve hardened `quickaside-gateway.service` invariants.

## Deterministic verification / Gate D

- [x] `git diff --check`.
- [x] Deployment-contract tests: 19 passed after remediation.
- [x] Full gateway suite: 108 passed, 2 known dependency deprecation warnings.
- [x] Python compileall: PASS.
- [x] Active-contract stale-reference audit: PASS.
- [x] Gate D round 1 independent review completed.
- [x] GDR1-001 remediated.
- [x] GDR1-002 remediated before the later Gate E access-policy product decision.
- [x] Gate D focused round 2 completed.
- [x] Gate D final: 0 BLOCKER, 0 MAJOR, 2 accepted MINOR, PASS_WITH_NOTES.

## Gate E — control plane

- [x] Capture existing `svc:personal-admin-ack` state.
- [x] Create/verify `svc:quickaside`.
- [x] Verify resource `tcp:443`.
- [x] Verify MagicDNS identity `quickaside.taildc9db9.ts.net`.
- [x] Verify VPS can host the Service.
- [x] Inspect existing tailnet Access Controls.
- [x] Explicit decision: preserve existing `* -> * -> *` tailnet policy to avoid
      affecting Personal Admin or other services.
- [x] Confirm QA1 remains the protected-operation authorization boundary.
- [x] Verify Quick Aside remains tailnet-only and no Funnel activation exists.
- [x] Preserve `svc:personal-admin-ack` unchanged.

## Gate E — VPS activation

- [x] Verify deployed Quick Aside application/configuration and permissions.
- [x] Verify `quickaside-gateway.service`.
- [x] `systemd-analyze verify`: no Quick Aside unit error observed.
- [x] Start `quickaside-gateway`.
- [x] Verify `127.0.0.1:2588` loopback-only listener.
- [x] Verify local `/healthz` = `{"status":"ok"}`.
- [x] Verify local `/readyz` = `{"status":"ready"}`.
- [x] Configure `svc:quickaside -> http://127.0.0.1:2588`.
- [x] Approve Quick Aside Service host advertisement.
- [x] Verify private `/healthz` and `/readyz` from Mac via Tailscale.
- [x] Verify `svc:personal-admin-ack -> http://127.0.0.1:2587` unchanged.
- [x] Verify both Services report `tailnet only`.
- [x] Verify no public Quick Aside listener or new public UFW ingress.

## Gate E — QA1 / runtime evidence

- [x] Anonymous `/v1/interpret` rejected with HTTP 401.
- [x] Create short-lived one-time pairing code.
- [x] Pair synthetic P-256 device `gate-e-smoke-1`.
- [x] Valid QA1 signed request succeeds privately with HTTP 200.
- [x] Controlled Luna Low request returns provider-neutral `AddListItem` result.
- [x] Exact replay rejected with HTTP 401.
- [x] Revoke `gate-e-smoke-1`.
- [x] Confirm synthetic Gate E device is stored as revoked.
- [x] Confirm deterministic gateway suite continues to cover revoked-device and
      rate-limit behavior.
- [x] Confirm raw capture `Agrega leche al mandado` is absent from
      `quickaside-gateway` journal.
- [x] Remove temporary QA1 key/payload material from the Mac.

## Persistence

- [x] Controlled `quickaside-gateway` restart.
- [x] Verify Quick Aside active after restart.
- [x] Verify Personal Admin active after restart.
- [x] Verify Tailscale Service mapping after restart.
- [x] Enable `quickaside-gateway.service` for boot.
- [x] Verify dependent existing services are enabled before reboot.
- [x] User-approved VPS reboot.
- [x] Verify `quickaside-gateway`, `personal-admin-ack`, `hermes-gateway` and
      `tailscaled` active after reboot.
- [x] Verify `127.0.0.1:2587` and `127.0.0.1:2588` after reboot.
- [x] Verify both Tailscale Service mappings persisted after reboot.
- [x] Verify private `/healthz` and `/readyz` from Mac after reboot.

## Rollback contract

- [x] Deterministic deployment contract requires drain-before-clear.
- [x] All rollback operations are scoped to `svc:quickaside`.
- [x] Global `tailscale serve reset` is prohibited.
- [x] `svc:personal-admin-ack` is explicitly outside rollback scope.
- [x] Live destructive rollback drill intentionally not performed after a
      successful production activation; use the reviewed scoped procedure if
      rollback becomes operationally necessary.

## Closure

- [x] Run final repository verification after these Gate E evidence updates.
- [x] Record final deterministic results in QA.
- [ ] User decides commit/push/merge.
- [ ] Start separate QAG-4 Android integration change only after QAG-003R closes.

QAG-003R does not include Android endpoint changes.
