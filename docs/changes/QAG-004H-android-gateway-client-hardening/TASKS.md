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
- [x] Record final evidence and stop without commit or push.
