# ACTIVE WORK

- Active Change: **CHG-031 — Mandado calendar-based weekly rollover**
- Governance: **STANDARD**
- State: product/architecture contract accepted; CHG-031 implementation **COMPLETE**; required Change-specific automated verification complete; commit-ready; independent review pending
- main accepted through: CHG-030 + Mandado fast-capture session lifecycle corrective
- CHG-030: COMPLETE — independent review PASS_WITH_NOTES — integrated
- Mandado fast-capture session lifecycle corrective: COMPLETE — independent review PASS
- CHG-031 goal: replace the interim 7-elapsed-day stale-session policy with the accepted calendar workflow: Sunday 00:00 formal period start, Saturday 14:00 rollover/cutoff, immediate next-period eligibility, and durable historical-session visibility.
- CHG-031 manual Finish rule: finishing Mandado before the weekly cutoff closes that Mandado for the remainder of the current period; Fast Capture MUST NOT auto-create another Mandado until the Saturday 14:00 rollover.
- Manual QA remaining: NONE; no genuinely unautomatable new property was identified.
- QA1 physical phone/pairing state is durable. The user explicitly authorized focused automated connected/instrumentation verification for CHG-031 against the connected Oppo QA1; QA1 was used only for those focused tests and its durable auth/install/pairing state was not intentionally altered. That authorization did not permit uninstall/reinstall, app-data clearing, Keystore/auth/device-identity mutation, pairing mutation, replacement pairing codes, or any other destructive lifecycle action. If QA1 reports an auth, pairing, or install-state problem, stop and report. Manual QA remains separate.
- Verification scope: CHG-031-specific changed-behavior tests, directly affected regressions only when justified, and applicable build/static/schema checks. Do not run full JVM/connected suites or unrelated cleanup/gates by default.
- Next action: user-authorized commit, followed by independent review of the committed diff; no commit or push was performed here.
