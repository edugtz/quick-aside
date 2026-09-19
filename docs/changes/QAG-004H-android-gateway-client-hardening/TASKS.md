# QAG-004H — Android Gateway Client Hardening — TASKS

- [x] Verify GitHub `main` and clean-worktree baseline.
- [x] Confirm QAG-004 is integrated and the three accepted review findings are
  the only requested hardening scope.
- [x] Read governing project docs and the QAG-004 change package.
- [x] Define the QAG-004H SPEC/PLAN/TASKS/QA package before implementation.
- [x] Add provider-neutral Android CapturePlan bounds.
- [x] Harden CapturePlanValidator while preserving blank/order behavior.
- [x] Bind pairing success to the local QA1 device ID.
- [x] Improve cancellation-state handling and document the bounded-I/O limit.
- [x] Add focused pairing and CapturePlan trust-boundary tests.
- [x] Preserve QAG-004 regression coverage.
- [x] Reconcile stale long-lived QAG status wording only.
- [x] Run focused JVM tests.
- [x] Run unit tests, Android-test Kotlin compilation, assemble, lint, and
  `git diff --check`.
- [x] Record original implementation evidence; user-authorized commit
  `f17058e9a01026a2fa258c05be3501c44fed0e69` was pushed and independently
  reviewed.
- [x] Remediate Round 1 MAJOR-1 with strict raw JSON string type checks.
- [x] Add field-by-field structural regressions and prove malformed output
  cannot reach a validated plan.
- [x] Correct cancellation guarantees without changing the network stack.
- [x] Correct QAG sequence and commit/push provenance.
- [x] Run Round 2 focused and deterministic verification.
- [x] Stop without committing or pushing the Round 2 remediation.
