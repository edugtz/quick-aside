# Change 020 — Typed CapturePlan + Validator Foundation — SPEC

Governance: **STANDARD**
Status: **IMPLEMENTATION COMPLETE — REVIEW PENDING**
Expected branch: `chg-020-capture-plan-foundation`

## Objective

Establish the provider-independent, typed intermediate representation between
future capture interpretation and future action execution:

```text
Persisted Capture
→ future CaptureInterpreter / AIProvider
→ CapturePlanDraft
→ CapturePlanValidator
→ validated CapturePlan
→ future ActionExecutor
```

This change stops at a validated `CapturePlan`. A validated plan is data only;
it does not execute actions or mutate persistence.

## In scope

- A pure-Kotlin `CapturePlan` containing a mandatory source `CaptureId` and an
  ordered, non-empty list of typed actions.
- A draft/candidate contract that can represent structurally decoded but
  semantically invalid values for validator tests and future provider output.
- The initial typed action families supported by existing domain semantics:
  `AddListItem`, `CreateTask`, `CreateNote`, `CreateStructuredLog`, and
  `UndoLast`.
- A deterministic focused validator returning either a validated plan or
  structured validation issues.
- Exact-content and action-order preservation.
- Deterministic JVM tests for valid plans, invalid candidates, ordering,
  whitespace, date/task-space semantics, and source-boundary independence.
- This change package and the active-work pointer.

## Out of scope

- Live capture interpretation, `CaptureInterpreter`, `AIProvider`, model calls,
  prompts, credentials, networking, retries, or token accounting.
- JSON, DTOs, serialization libraries, provider annotations, or schema work.
- `ActionExecutor`, Action Ledger reads/writes, target reversal, or any action
  execution.
- Room, SQLite, migrations, schema changes, stores, repositories, or task/list
  persistence.
- Android, Compose, UI, navigation, receipts, reminders, Calendar, Google
  Tasks, Google Calendar, sync, archive/export, or screen/device evidence.
- Calendar event actions, reminder actions, Google-specific actions,
  archive/export, `QueryHistory`, Move/Complete/Reopen, or arbitrary target
  mutations.
- A generic validation framework, domain-specific structured-log schemas,
  units, numeric hierarchies, JSON values, or exhaustive list-definition
  enums.

## Accepted contract

`CapturePlan` contains:

- `sourceCaptureId: CaptureId`;
- `actions: List<CapturePlanAction>`, ordered and non-empty.

Typed actions are:

- `AddListItem(listDefinitionId: ListDefinitionId, text: String)`;
- `CreateTask(space: TaskSpace, title: String, dueDate: LocalDate?)`;
- `CreateNote(text: String)`;
- `CreateStructuredLog(fields: Map<String, String>)`;
- `UndoLast` with no target payload.

The draft contract mirrors these fields using raw values where needed so it can
represent blank IDs/text, empty action lists, and invalid structured-log
fields. Validation is structural and does not query Room to resolve list
definitions.

## Validation contract

The validator returns `Valid(plan)` or `Invalid(issues)`. Validation is
deterministic and reports plan-level or action-level issues with an action index
when applicable. Issue categories identify the first-class structural reason
without embedding rejected personal text in messages.

At minimum, validation rejects:

- blank source Capture ID or an empty action list;
- blank AddListItem list-definition ID or item text;
- blank CreateTask title;
- blank CreateNote text;
- empty StructuredLog fields, blank keys, or blank values.

`UndoLast` is valid without payload. `isBlank()` is used only for validation;
valid surrounding whitespace is preserved exactly in the resulting plan. The
draft action order is copied unchanged into the validated plan without sorting,
grouping, deduplicating, or normalization.

## Existing-domain reuse

The change reuses `CaptureId`, `ListDefinitionId`, `TaskSpace`, and
`LocalDate`. List-definition existence/session checks remain future execution
responsibilities. Task due dates remain date-only and distinct from reminder
times. Structured logs remain generic `Map<String, String>` values.

## Verification and authority

Required applicable evidence:

```text
./gradlew :app:testDebugUnitTest --tests '<actual Change 020 focused test class>'
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
git diff --check
git status --short
git diff --stat
```

No connected Android test or visual evidence is required because this change
has no Android or UI behavior. Room must remain at version 5, schemas 1–5 must
remain unchanged, and no schema 6 or migration may be introduced.

Do not commit, push, merge, release, or declare the final engineering verdict;
independent review remains the next gate after implementation.
