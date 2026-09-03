# Quick Aside — Project Spec v0.2

Status: **Accepted baseline for personal MVP design**  
Product owner: user  
Primary goal: personal utility first; commercialization is explicitly not the MVP objective.
Accepted product name: **Quick Aside**. See `docs/NAMING.md`.

## 1. Problem

The user frequently remembers tasks, purchases, events, notes, and facts during the day and currently scatters them across WhatsApp, Google Docs, Google Tasks, Calendar, or memory. The friction is not just typing: it is deciding where each thought belongs and later finding it again.

Quick Aside should let the user get information out of their head immediately, organize it automatically, synchronize the appropriate external systems, and preserve a structured personal history that remains queryable later.

## 2. North-star job

> **“Recordé algo → lo dije o escribí → quedó correctamente guardado → seguí con mi vida.”**

Secondary job:

> **“Cuando necesito encontrar, corregir o gestionar lo que guardé, la app me da una interfaz explícita, estructurada y confiable.”**

UX thesis: **Capture invisibly. Manage explicitly.**

## 3. Primary interaction contract

### Happy path

`Invocar → Hablar/Escribir → Interpretar + Guardar → Receipt ligero → Fin`

For high-confidence captures, the user MUST NOT be forced through transcript review, interpretation review, or a confirmation screen.

### Optional review

The user may edit:

- the transcript when speech-to-text is wrong;
- the interpretation when classification, destination, date, or extracted fields are wrong.

Interpretation corrections may become routing examples/preferences.

### Ambiguity

When confidence is insufficient, ask the **smallest focused question** needed to continue. Example:

`¿Dónde guardo “Revisar PR”? [Trabajo] [Personal]`

Do not open a full editor unless necessary or explicitly requested.

### Receipt

Feedback adapts to capture complexity:

- simple action: snackbar/small bottom receipt + Undo;
- multi-intent capture: compact expandable receipt summarizing destinations;
- detailed editor only on demand.

## 4. Input modes

MVP:

- Voice — primary.
- Text — first-class alternative.
- Editable live/final transcript.

Post-MVP interaction surfaces may reuse the same capture engine:

- Quick Settings tile;
- home-screen widget;
- share sheet;
- lock-screen surface if platform support is practical;
- assistant/system invocation;
- Wear OS;
- hardware-button or overlay approaches only as later investigation, not committed scope.

## 5. Information types

### 5.1 Tasks

User-facing spaces:

- Personal
- Trabajo

Must synchronize bidirectionally with Google Tasks. Exact reminder time remains a Quick Aside concern where Google Tasks API cannot represent it.

### 5.2 Events

Events synchronize with Google Calendar and retain the normal Calendar lifecycle there.

### 5.3 Lists

Initial list definitions:

- **Mandado** — session-based list with historical sessions.
- **Compras** — continuous list.

Future list types are allowed by the data model but are not MVP UI scope unless needed by real use.

### 5.4 Notes

Freeform memory that may optionally have a local reminder. A Note does not have to become a Task simply because it has a reminder.

### 5.5 Structured Logs / Records

Facts captured as natural language but stored with structured fields when useful. MVP examples:

- `Hoy hice 210 lbs en press inclinado.`
- `Femoral: asiento 7, 65 lbs.`

The original capture should remain available where useful, while extracted fields make deterministic retrieval possible later.

Quick Aside is not becoming a dedicated fitness app; gym records are an example of general structured personal memory.

### 5.6 Commands / Queries

Examples:

- Deshaz lo último.
- Mueve X a Compras.
- ¿Cuándo compré detergente?
- ¿Cuánto hice la última vez en press inclinado?
- Recrea el mandado anterior, quita jabón y agrega aguacate.

Queries should prefer deterministic structured data retrieval; an LLM may help interpret the query but should not invent historical facts.

## 6. Initial capture examples

| User says | Expected result |
|---|---|
| Compra Chobani, pollo y leche | Mandado actual → 3 items |
| Necesito comprar cuerdas para la guitarra | Compras → cuerdas |
| Mañana revisa el PR | Trabajo task → mañana → Google Tasks |
| El jueves a las 5 tengo dentista | Google Calendar event |
| El viernes paga Totalplay | Personal task → viernes → Google Tasks |
| Tengo que ver lo del coche esta semana | Low-confidence Task/Inbox resolution |
| Compra huevos y pollo… bueno, pollo no | Mandado → huevos only |
| Dentista jueves… no, mejor viernes | Calendar → final corrected date |
| Agrega lo mismo del mandado anterior | New Mandado session derived from history |
| ¿Cuándo compré detergente? | Deterministic history query |
| Necesito un cable Fender pero no es del mandado | Compras |
| Revisar integración de Firebase mañana | Trabajo task |
| Eso último sólo guárdalo como nota | Note |
| Deshaz lo último | Action Ledger → Undo |
| Hoy hice 210 lbs en press inclinado | Structured Log + raw capture |
| El asiento del femoral es 7, 65 lbs | Structured Log + raw capture |
| Llamar al taller por la Forester, recuérdamelo mañana a las 10 | Note + local reminder |

## 7. Navigation baseline

Main destinations:

1. **Inicio** — fast capture CTA + contextual summary.
2. **Pendientes** — Personal / Trabajo, Google Tasks sync.
3. **Listas** — Mandado / Compras.
4. **Memoria** — Search, Notes, Logs, History, Archive/Backup.

Global capture action is prominent and available from management screens. `Captura` is not a permanent bottom-navigation destination.

## 8. Home baseline

Home is a contextual launcher, not an analytics dashboard and not an AI-chat home screen.

Expected elements:

- dominant microphone CTA;
- fast text input;
- compact current state: Mandado, next Calendar event, Personal pending count/highlight, Trabajo pending count/highlight;
- minimal visual noise.

## 9. Reminders and notifications

MVP:

- user-configured local reminders on Notes and Tasks;
- due date and reminder time are different concepts;
- snooze support;
- appropriate notification actions (e.g. Task: Snooze/Complete; Note: Snooze/Open);
- no engagement notifications or unsolicited productivity nudges.

FCM remote notifications are deferred. Ackline currently owns the separate remote FCM + ACK experimentation/workflow.

## 10. Persistence and data durability

Durable personal information must not expire silently.

MVP/early product principles:

- Room/SQLite local structured persistence;
- backup/recovery strategy;
- explicit export/archive path;
- no destructive migrations for user data;
- no deletion before verified archival where retention policy says archival is required.

Archive should eventually support:

- machine-readable/reimportable structured backup;
- human-readable PDF and/or DOCX exports;
- warnings before data becomes eligible for pruning;
- default policy may be “never delete automatically”.

PDF/DOCX are human archives, not the sole restoration format.

## 11. AI runtime policy

AI should interpret captures into a validated structured plan. It must not directly mutate Google services or become the persistence layer.

Initial personal-MVP model strategy:

- MiMo-V2.5 primary;
- DeepSeek V4 Flash fallback if required by observed failure/low-confidence policy;
- LongCat-2.0 next candidate if evidence requires changing;
- Qwen3.8 Flash reserve.

Avoid up-front benchmark work unless real use shows that model quality is a blocker.

## 12. MVP must-have capabilities

- Fast voice capture.
- Text capture.
- Editable transcript.
- Multi-intent extraction.
- Auto-routing.
- Confidence-aware minimal clarification.
- Undo.
- Personal/Trabajo tasks with Google Tasks sync.
- Google Calendar event creation/sync.
- Mandado session history.
- Compras continuous list.
- Notes.
- Basic structured logs.
- Local reminders + snooze.
- Search/history basics sufficient to retrieve prior structured data.
- Durable local persistence.
- Backup/export foundation.

## 13. Post-MVP candidates

Evidence-triggered only:

- Quick Settings capture tile — likely first peripheral capture surface.
- Home widget.
- Share-to-Quick Aside.
- Richer natural-language history queries.
- Multiple/recurring reminders.
- Constant reminder / repeat-until-acknowledged.
- Better structured-log schemas and domain-specific views if real use warrants them.
- FCM remote notifications / potential agent integration.
- On-device local inference if latency/privacy/offline evidence justifies it.
- Cross-device/cloud sync if personal use demonstrates a need.

Investigation-only ideas, not commitments:

- lock-screen capture;
- OS overlay;
- hardware-button invocation;
- Wear OS;
- shared/social lists.

## 14. Explicit non-goals

- Another Todoist clone.
- Full calendar replacement.
- Time blocking.
- Habit tracker.
- Pomodoro.
- Productivity score/streaks.
- AI life coach.
- Email client.
- Always-listening microphone.
- Engagement spam notifications.
- Multi-user SaaS infrastructure in personal MVP.
- Commercialization work before personal value is proven.

## 15. Success criteria for personal MVP

The MVP succeeds if the user can use it as the default place to offload things they would otherwise put in WhatsApp/Docs/memory, with materially lower cognitive friction and reliable later retrieval.
