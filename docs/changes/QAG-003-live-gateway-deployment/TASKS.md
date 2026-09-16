# QAG-003 — Live Authenticated Gateway Deployment — TASKS

## Change setup

- [x] Create `qag-003-live-gateway-deployment` from merged QAG-2 main.
- [x] Confirm base commit `3469adb7212127e89ed896d7601bd37c360dc2b3`.
- [x] Select HIGH-ASSURANCE governance.
- [x] Create QAG-3 SPEC / PLAN / TASKS / QA.
- [x] Point `ACTIVE_WORK.md` at QAG-3.
- [x] Accept ADR-0004 public authenticated ingress.

## Gate A — Live preflight

- [x] Capture host/OS/kernel/uptime.
- [x] Capture CPU/RAM/swap/disk.
- [x] Capture Python/runtime.
- [x] Capture TCP listeners.
- [x] Capture relevant users/groups.
- [x] Capture Quick Aside namespace state.
- [x] Capture relevant systemd baseline.
- [x] Capture Personal Admin ACK health baseline.
- [x] Capture UFW posture.
- [x] Confirm Quick Aside is not deployed.
- [x] Confirm port `2588` is free.
- [x] Gate A verdict: PASS.

## Gate B — Authenticated gateway

- [x] Add auth SQLite schema/storage.
- [x] Add device registry.
- [x] Add device revocation.
- [x] Add one-time pairing-code lifecycle.
- [x] Add `POST /v1/pair`.
- [x] Implement QA1 canonical signing.
- [x] Verify ECDSA P-256 / SHA-256 signatures.
- [x] Enforce ±120 second timestamp window.
- [x] Persist accepted nonce state.
- [x] Reject replayed nonce.
- [x] Add interpretation rate limiting.
- [x] Add pairing rate limiting.
- [x] Ensure failed authentication never invokes provider.
- [x] Keep auth/provider logs privacy-safe.
- [x] Add local administration CLI.

## Gate C — Deterministic verification

- [x] Valid signature succeeds.
- [x] Unknown device rejected.
- [x] Revoked device rejected.
- [x] Invalid signature rejected.
- [x] Modified body rejected.
- [x] Stale timestamp rejected.
- [x] Excessive future timestamp rejected.
- [x] Replayed nonce rejected.
- [x] Replay remains rejected after restart/recreation.
- [x] Expired pairing code rejected.
- [x] Invalid pairing code rejected.
- [x] Used pairing code rejected.
- [x] Pairing rate limit verified.
- [x] Interpretation rate limit verified.
- [x] Auth failure does not invoke provider.
- [x] Existing QAG-2 tests remain green.
- [x] Compile/static checks pass.

## Gate D — Security review

- [ ] Review canonicalization and crypto usage.
- [ ] Review replay persistence.
- [ ] Review timestamp handling.
- [ ] Review pairing lifecycle.
- [ ] Review SQLite permissions.
- [ ] Review rate limiting.
- [ ] Review Caddy/TLS boundary.
- [ ] Review privacy/logging.
- [ ] Resolve all BLOCKER/MAJOR findings.

## Gate E — User approval

- [ ] Present exact VPS mutation commands.
- [ ] Obtain explicit user approval.

## Gate F — Deployment

- [ ] Create dedicated `quickaside` identity.
- [ ] Create Quick Aside namespaces.
- [ ] Install reviewed gateway revision.
- [ ] Create isolated Python environment.
- [ ] Install Codex CLI `0.154.0`.
- [ ] Establish Quick Aside-specific Codex OAuth.
- [ ] Install dedicated systemd unit.
- [ ] Verify gateway binds only to `127.0.0.1:2588`.

## Gate G — HTTPS ingress

- [ ] Select Quick Aside public hostname.
- [ ] Verify DNS points to VPS.
- [ ] Install/configure standard Caddy.
- [ ] Open only required public HTTP/HTTPS firewall ports.
- [ ] Proxy only approved Quick Aside routes.
- [ ] Keep FastAPI port private.
- [ ] Verify Tailscale is irrelevant to Quick Aside operation.

## Gate H — External verification

- [ ] Verify TLS from a non-Tailscale network.
- [ ] Verify unsigned interpretation rejected.
- [ ] Verify valid signed interpretation.
- [ ] Verify replay rejected.
- [ ] Verify rate limiting.
- [ ] Verify no residual Codex process.
- [ ] Verify privacy-safe logs.
- [ ] Verify Personal Admin ACK still returns HTTP 200.
- [ ] Verify Hermes remains active.

## Gate I — Rollback

- [ ] Review rollback commands.
- [ ] Rehearse/execute approved rollback boundary.
- [ ] Verify only Quick Aside changes.
- [ ] Restore Quick Aside if needed.

## Gate J — Host reboot

- [ ] Obtain separate explicit reboot approval.
- [ ] Verify Quick Aside after reboot.
- [ ] Verify TLS/auth/provider after reboot.
- [ ] Verify Personal Admin/Hermes after reboot.

## Review / close

- [ ] Review actual diff and deployment evidence.
- [ ] Reconcile durable project docs.
- [ ] Final verdict: PASS / PASS_WITH_NOTES / BLOCKED.
- [ ] Merge/release only under user authority.
