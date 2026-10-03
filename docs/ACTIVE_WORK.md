# ACTIVE WORK

- Active Change: **CHG-031 — Mandado calendar-based weekly rollover**
- Governance: **STANDARD**
- State: CHG-031 remains **ACTIVE**; first remediation published in commit `ab85b733f624cafc441b3ed4a113a05d03605925` and independently re-reviewed with **0 BLOCKER / 1 MAJOR / 1 MINOR / 1 NOTE — BLOCKED**; original two MINOR findings are closed; remediation round 2 **COMPLETE**; required focused verification **COMPLETE**; commit-ready; independent re-review round 2 pending
- main accepted through: CHG-030 + Mandado fast-capture session lifecycle corrective
- CHG-030: COMPLETE — independent review PASS_WITH_NOTES — integrated
- Mandado fast-capture session lifecycle corrective: COMPLETE — independent review PASS
- CHG-031 goal: replace the interim 7-elapsed-day stale-session policy with the accepted calendar workflow: Sunday 00:00 formal period start, Saturday 14:00 rollover/cutoff, immediate next-period eligibility, and durable historical-session visibility.
- CHG-031 manual Finish rule: finishing Mandado before the weekly cutoff closes that Mandado for the remainder of the current period; Fast Capture MUST NOT auto-create another Mandado until the Saturday 14:00 rollover.
- CHG-031 remediation round 2 complete: idempotent `setItemCompleted` now reconciles the calendar before its same-state return; completion `SessionNotActive` reloads Mandado state and shows an honest lifecycle message; focused Room/UI/activity regressions pass.
- Original first-review MINOR 1 and MINOR 2 findings remain closed; do not reopen them.
- CHG-031 remediation introduced **no Room schema/entity/migration change**.
- Manual QA pending: NONE; no genuinely unautomatable new property was identified.
- QA1 physical phone/pairing state is durable. The user explicitly authorized focused automated connected/instrumentation verification for CHG-031 against the connected Oppo QA1; QA1 was used only for those focused tests and its durable auth/install/pairing state was not intentionally altered by the original implementation or by this remediation. That authorization did not permit uninstall/reinstall, app-data clearing, Keystore/auth/device-identity mutation, pairing mutation, replacement pairing codes, or any other destructive lifecycle action. If QA1 reports an auth, pairing, or install-state problem, stop and report. Manual QA remains separate.
- Verification scope: CHG-031-specific changed-behavior tests, directly affected regressions only when justified, and applicable build/static/schema checks. Do not run full JVM/connected suites or unrelated cleanup/gates by default.
- Next action: user-authorized commit, followed by independent re-review round 2 of the committed diff; no commit or push was performed here.
