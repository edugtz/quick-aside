# ACTIVE WORK

- Active Change: **CHG-031 — Mandado calendar-based weekly rollover**
- Governance: **STANDARD**
- State: product/architecture contract accepted; implementation **NOT STARTED**
- main accepted through: CHG-030 + Mandado fast-capture session lifecycle corrective
- CHG-030: COMPLETE — independent review PASS_WITH_NOTES — integrated
- Mandado fast-capture session lifecycle corrective: COMPLETE — independent review PASS
- CHG-031 goal: replace the interim 7-elapsed-day stale-session policy with the accepted calendar workflow: Sunday 00:00 formal period start, Saturday 14:00 rollover/cutoff, immediate next-period eligibility, and durable historical-session visibility.
- CHG-031 manual Finish rule: finishing Mandado before the weekly cutoff closes that Mandado for the remainder of the current period; Fast Capture MUST NOT auto-create another Mandado until the Saturday 14:00 rollover.
- Manual QA remaining from prior accepted work: NONE
- QA1 physical phone/pairing state is durable and OUT OF SCOPE for CHG-031. Do not clean, reinstall, clear app data, rotate identity, re-pair, revoke pairing, or run connected tests on QA1.
- Verification scope: CHG-031-specific changed-behavior tests, directly affected regressions only when justified, and applicable build/static/schema checks. Do not run full JVM/connected suites or unrelated cleanup/gates by default.
- Next action: preflight the existing Mandado session/DAO/executor/history/Undo implementation, determine whether any Room schema change is actually required, then implement only CHG-031.
