# Change 002 — Core Domain Contracts — TASKS

Governance: **STANDARD**
Status: **IN PROGRESS**

- [x] Create the Change 002 SPEC, PLAN, and TASKS package before code changes.
- [x] Point `docs/ACTIVE_WORK.md` to Change 002 and record the next gate.
- [x] Add typed local identity wrappers without ID-generation or persistence
  infrastructure.
- [x] Implement the pure-Kotlin Capture contract for Text and Voice input.
- [x] Implement extensible ListDefinition, ListSession, and ListItem with
  Mandado and Compras behavior mapping.
- [x] Implement TaskSpace and date-only Task due dates.
- [x] Implement Note and minimal StructuredLog capture association.
- [x] Implement separate Task/Note Reminder targets and exact scheduled time.
- [x] Implement the minimal ActionLedgerEntry identity/occurrence contract.
- [x] Add deterministic JVM unit tests for the accepted invariants.
- [x] Review the domain dependency boundary.
- [x] Run `./gradlew :app:testDebugUnitTest`.
- [x] Run `./gradlew :app:assembleDebug`.
- [x] Run `./gradlew :app:lintDebug`.
- [x] Run `git diff --check`, `git status --short`, and applicable diff stats.
- [x] Prepare the implementation report without declaring the final
  engineering verdict.
- [ ] Final independent review; return PASS / PASS_WITH_NOTES / BLOCKED.
