# Change 002 — Core Domain Contracts — SPEC

Governance: **STANDARD**
Status: **IN PROGRESS**

## Objective

Establish the smallest useful pure-Kotlin domain vocabulary for Quick Aside
before persistence and feature implementation. These contracts describe
application/domain concepts only; they are not the final Room schema.

## In scope

- Lightweight strongly typed local identities for the domain concepts that
  need them.
- Text and voice captures with their original textual input/transcript,
  identity, and capture timestamp.
- List definitions, sessions, and items, including the accepted Mandado
  session-based and Compras continuous semantics.
- Personal and Trabajo task spaces with a date-only due date.
- Minimal Note and StructuredLog contracts that can point back to an
  originating Capture.
- A separate Reminder contract that targets either a Task or a Note and uses
  an exact scheduled time.
- The minimum Action Ledger entry contract for the identity and occurrence of
  a user-visible mutation.
- Deterministic JVM unit tests for the accepted domain invariants.
- This change package and the active-work pointer.

## Out of scope

- Android UI, Compose, navigation, themes, or screenshots.
- Room, KSP, persistence annotations, repositories, mappers, DTOs,
  serialization, or schema/migration design.
- Speech-to-text, transcript correction workflows, AI interpretation,
  CapturePlan, routing, or runtime provider integration.
- Google Tasks, Google Calendar, OAuth, external IDs, or sync state.
- Android reminder scheduling, notifications, or undo execution.
- Raw-audio persistence.
- Final universal StructuredLog schema, generic typed-value frameworks, or
  feature use cases/services.

## Accepted domain invariants

1. Domain contracts are pure Kotlin and use no Android, Compose, Room, KSP,
   Google, AI-provider, network, or serialization types.
2. Related domain records use distinct lightweight ID wrappers where an
   accidental ID interchange would be harmful; no ID-generation mechanism is
   introduced.
3. A Capture distinguishes Text from Voice and retains the original textual
   input/transcript needed for later correction. It does not claim to persist
   raw audio.
4. Mandado is `SESSION_BASED`; Compras is `CONTINUOUS`. The model can attach
   items to a ListSession so historical Mandado sessions can be represented
   later, without implementing history behavior here.
5. Task spaces are `PERSONAL` and `TRABAJO`. A Task due date is a
   date-only `LocalDate`, independent from any exact reminder time.
6. A Note remains a Note when it has a Reminder. Reminder targets explicitly
   distinguish Task and Note.
7. Note and StructuredLog may retain an originating Capture identity; the
   StructuredLog shape remains intentionally generic and minimal.
8. ActionLedgerEntry records only its identity and occurrence needed for a
   later user-visible mutation history. Inverse payloads and undo execution
   are deferred.

## Acceptance scenarios

1. `ListDefinitionType.MANDADO` exposes `SESSION_BASED` behavior.
2. `ListDefinitionType.COMPRAS` exposes `CONTINUOUS` behavior.
3. `TaskSpace.PERSONAL` and `TaskSpace.TRABAJO` are distinct values.
4. A Task can carry a `LocalDate` due date while a Reminder carries a separate
   exact `Instant` scheduled time.
5. A Reminder can target a Note and the target remains a Note identity.
6. Text and Voice captures are distinguishable and retain their original
   textual input/transcript.
7. JVM unit tests compile and run, and a source-boundary review finds no
   Android/Room/Google/AI dependency in `com.edu.quickaside.domain`.

## Evidence required

- Deterministic output from `./gradlew :app:testDebugUnitTest`.
- Deterministic output from `./gradlew :app:assembleDebug`.
- Deterministic output from `./gradlew :app:lintDebug`.
- `git diff --check`, repository status, and the applicable diff-stat output.
- Source inspection confirming the domain package imports only pure Kotlin and
  `java.time` types; no visual evidence is required because UI is unchanged.

