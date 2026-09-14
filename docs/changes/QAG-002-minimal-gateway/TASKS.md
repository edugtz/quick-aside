# QAG-002 — Minimal Private Gateway — TASKS

## Preflight

- [x] Confirm QAG-1 is merged to `main`.
- [x] Confirm Android provider-neutral action boundary.
- [x] Confirm trusted temporal-context gap.
- [x] Confirm target Python 3.14 compatibility of proposed HTTP stack.
- [x] Detect stale QAG-1 wording in `PROJECT_SPEC.md`.
- [x] Define QAG-2 scope without QAG-3/Android/fallback work.

## Change setup

- [x] Create `qag-002-minimal-gateway` from QAG-1 baseline.
- [x] Create QAG-2 SPEC / PLAN / TASKS.
- [x] Reconcile stale runtime status in `PROJECT_SPEC.md`.
- [x] Point `ACTIVE_WORK.md` at QAG-2 during implementation.

## Gateway implementation

- [x] Add isolated `gateway/` Python package.
- [x] Pin direct server/test dependencies.
- [x] Implement request/response contracts.
- [x] Validate trusted timestamp + IANA timezone.
- [x] Implement strict provider output schema.
- [x] Implement structured-log pair -> map conversion.
- [x] Reject duplicate structured-log field keys.
- [x] Build Luna Low ephemeral Codex prompt/command.
- [x] Use allowlisted child-process environment.
- [x] Implement timeout + process-group termination.
- [x] Preserve coroutine cancellation.
- [x] Implement provider-output validation.
- [x] Implement provider concurrency bound (default 1).
- [x] Implement `/healthz`.
- [x] Implement `/readyz`.
- [x] Implement `/v1/interpret`.
- [x] Implement stable provider failure responses.
- [x] Ensure normal logs avoid raw capture/provider/auth content.

## Automated verification

- [x] Python package installs in a clean local venv.
- [x] `python -m compileall` passes.
- [x] Deterministic contract tests pass.
- [x] Deterministic runner tests pass.
- [x] Timeout/reaping test passes.
- [x] HTTP endpoint tests pass.
- [x] Concurrency bound test passes.
- [x] Non-UTF-8 provider output regression test passes.
- [x] Final deterministic suite: 33 passed.

## Real provider contract evidence

- [x] Exercise final prompt/schema against isolated Codex CLI 0.154.0.
- [x] Verify `gpt-5.6-luna`.
- [x] Verify explicit Low reasoning.
- [x] Verify representative current action families.
- [x] Verify structured-log conversion path.
- [x] Verify targeted Personal/Trabajo semantic routing: 5/5 PASS.
- [x] Verify no residual Codex process after request.

## Review remediation

- [x] Bound raw HTTP body and capture text size.
- [x] Bound action count, structured-log fields, and provider output file size.
- [x] Bound total request wall clock including semaphore wait.
- [x] Reject non-applicable non-null provider fields before conversion.
- [x] Enforce RFC3339 string wire format for `capturedAt`.
- [x] Persist real-provider/environment evidence in `QA.md`.
- [x] Re-run deterministic tests after remediation.
- [x] Re-run targeted real Luna Low smoke after remediation.
- [x] Map invalid UTF-8 provider output to the stable invalid-output contract.

## Review / close

- [x] Review actual branch diff.
- [x] Resolve BLOCKER/MAJOR findings.
- [x] Resolve remaining MINOR finding.
- [x] Decide whether QAG-2 warrants a new ADR: **no new ADR required**.
- [x] Reconcile architecture/roadmap/active-work with proven result.
- [x] Final engineering review: **PASS_WITH_NOTES**.

Commit, push, merge, and release authority remain user-owned and occur after
this repository checklist; they are not implementation steps claimed on the
user's behalf.

## Stop state

QAG-2 does not deploy the gateway.

QAG-3 remains the first gate allowed to mutate live VPS/network/systemd state
and remains **HIGH-ASSURANCE** with explicit user approval required.
