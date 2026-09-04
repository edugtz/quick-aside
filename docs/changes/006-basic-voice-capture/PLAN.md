# Change 006 — Basic Voice/STT Capture — PLAN

## Preflight

- Confirm the expected `chg-006-basic-voice-capture` branch and clean working
  tree before implementation.
- Read the accepted project, architecture, roadmap, acceptance, naming, and
  UX contracts; inspect the canonical v3 visual reference.
- Read the existing Capture domain, `CaptureSubmission`, writer/reader,
  Room database/entity/DAO, application wiring, activity, manifest, and
  Change 004–005 packages without modifying historical packages.
- Verify current official Android guidance for `SpeechRecognizer`, runtime
  `RECORD_AUDIO`, on-device availability, main-thread calls, recognizer
  destruction, and Android 11 package visibility.
- Confirm Room 3.0.2, KSP 2.3.6, SQLite 2.7.0, database version 1, and the
  existing four-column schema.

## Implementation approach

1. Create a focused speech boundary (`SpeechTranscriber`, event/error
   contract, and `SpeechTranscriberFactory`) with no raw Android callbacks in
   `QuickAsideApp.kt`.
2. Implement `AndroidSpeechTranscriber` with a per-session recognizer,
   free-form locale-aware intent, partial results, meaningful error mapping,
   API 31+ on-device preference, normal system fallback, and `destroy()`
   cleanup.
3. Add only `RECORD_AUDIO` and the `RecognitionService` `<queries>` entry to
   the manifest.
4. Extend `CaptureSubmission` through a shared private input submission path
   so text behavior stays unchanged and voice results persist through the
   existing writer.
5. Replace the Capture placeholder with a temporary Compose voice surface.
   Keep permission request/checking injectable for deterministic denial tests;
   use the Android runtime permission launcher in production.
6. Wire the application-scoped speech factory from `QuickAsideApplication`,
   while keeping the existing database owner and Memoria reader path.
7. Add fake-transcriber Compose coverage and isolated Room-backed tests for
   permission denial, partial/no-partial behavior, final exactness, blank/
   error/cancel paths, success receipt, Memoria visibility, and cleanup.
8. Capture representative visual evidence, run all required gates, inspect
   schema/dependencies/permissions, and report real-device QA or its exact
   blocker without claiming PASS.

## Test approach

- Unit-test `CaptureSubmission.submitVoice` for exact text preservation,
  blank rejection, one saved Voice Capture, and failure behavior; retain all
  existing text tests.
- Use fake `SpeechTranscriberFactory`/permission controller in Compose tests;
  drive events directly rather than depending on a real microphone or service.
- Use a unique named Room database per UI/instrumentation test and close/delete
  it in teardown. Never use production `quick_aside.db` for fixtures.
- Keep database mapping/history coverage and existing text UI coverage in
  place; add only the assertions needed for Change 006.

## Verification sequence

Run targeted unit and instrumentation tests while implementing. Then run the
required unit test, debug assemble, lint, connected Android test, diff-check,
schema/dependency/manifest inspection, and real-device QA. Review screenshots
against the written UX contract and canonical v3 direction.

## Stop signals

Stop and report if implementation needs a Room migration/schema change, a
separate voice persistence model, raw audio, a service/background listener,
third-party/cloud STT SDK, AI/Google integration, or major navigation changes.
