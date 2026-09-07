# Change 016 — Local Search Foundation — SPEC

Governance: **STANDARD**  
Status: **PLAN/DOCS ONLY — IN PROGRESS**

## Objective

Define and implement the smallest deterministic local-search foundation needed
for M1. Search will retrieve durable local history across Captures, Notes,
Structured Logs, and List Items without depending on the recent records
already loaded by any UI screen.

This change establishes the application boundary and Room query path. Search
UI is a separate reviewable change.

## In scope

- A focused `LocalSearch` application boundary crossing Capture, Lists, and
  Memory ownership boundaries.
- A typed result contract distinguishing Capture, Note, Structured Log, and
  List Item results.
- Deterministic SQLite/Room substring queries against the existing schema-4
  tables.
- Search over all durable rows, including rows older than the current recent
  screen limits.
- Literal escaping for SQL `LIKE` wildcard and escape characters.
- One global deterministic result ordering and a bounded result contract.
- One result per source record when multiple searchable fields match.
- Room-backed implementation and focused persistence/integration coverage.
- Production application wiring for the boundary without adding UI.

## Out of scope

- Search UI, search bar, highlighting, filters, facets, tabs, or navigation.
- FTS, fuzzy search, stemming, tokenization, relevance ranking, or semantic
  and vector search.
- AI query interpretation, natural-language questions, or generated summaries.
- Tags, archive, backup, reminders, Google Tasks, Calendar, Action Ledger,
  Undo, or changes to existing source-store contracts.
- New tables, indexes created solely for search, schema version 5, or any
  migration.
- Deep-link behavior or source-specific editing/deletion behavior.
- A generic extensible search framework for hypothetical future sources.

## Baseline and repository findings

Room schema version 4 already persists every M1 source required here:

- `captures`: original text/transcript, optional corrected transcript, and
  `captured_at_epoch_millis`;
- `notes`: exact text and `created_at_epoch_millis`;
- `structured_logs` plus `structured_log_fields`: log timestamp and exact
  field keys/values;
- `list_items`: exact item text, completion state, list/session references,
  and `created_at_epoch_millis`, with list definitions and sessions available
  for context.

Existing `readRecent()`/`getRecent()` operations are intentionally not a
search implementation. The search DAOs must query their source tables using
the search predicate directly, with no recent-screen limit applied before the
predicate. Existing schema 4 is sufficient; no FTS or migration is justified
for the basic M1 contains-search requirement.

## Application boundary

The later Compose search UI will depend only on:

```kotlin
interface LocalSearch {
    suspend fun search(
        query: String,
        limit: Int = DEFAULT_LOCAL_SEARCH_LIMIT,
    ): List<LocalSearchResult>
}
```

`LocalSearch` belongs under the application search boundary. `RoomLocalSearch`
is the production data implementation and may access the Capture, List, and
Memory DAOs. Compose must not access those DAOs directly, and search must not
be added to `MemoryStore` merely because the first UI lives in `Memoria`.

The implementation reads all source results inside one Room read transaction
for a consistent snapshot. It requests at most the effective result limit
from each source, merges the source results, applies the one global ordering,
and returns the first effective-limit rows. Per-source limit `K` cannot hide a
global top-`K` result because any top-`K` result set contains at most `K` rows
from each source.

## Typed result contract

`LocalSearchResult` is a closed, purpose-built result type with exactly these
four source kinds:

```text
Capture      -> CaptureId, CaptureKind, effective searchable text, capturedAt
Note         -> NoteId, note text, optional source CaptureId, createdAt
StructuredLog -> StructuredLogId, deterministic field entries, optional
                 source CaptureId, createdAt
ListItem     -> ListItemId, item text, completion state, list definition and
                session context, createdAt
```

Every variant exposes:

- its result kind;
- a stable source ID (the typed ID above);
- one primary `displayText` suitable for later presentation;
- one durable timestamp used for global ordering;
- only the source context needed to identify and present the originating
  record.

The result contract does not expose match ranges, ranking scores, AI text, or
navigation/deep-link instructions.

Specific display/search values are deterministic:

- Capture `displayText` is the Text original text or the Voice effective
  transcript. For Voice, corrected transcript wins; otherwise original
  transcript is used.
- Note `displayText` is the exact Note text.
- Structured Log `displayText` is a stable summary of all exact fields in
  `field_key ASC` order, formatted as `key: value` entries. The result also
  retains the field entries so later UI does not have to re-query the record.
- List Item `displayText` is the exact item text. List name, completion state,
  and optional Mandado session identity/timestamps are context only.

No source record is fabricated if related context is missing. A malformed
persisted row should fail through the existing explicit mapping/error path;
search must not invent labels or IDs.

## Searchable source semantics

The normalized query is `query.trim()`. Only leading/trailing search-input
whitespace is removed; internal whitespace and all persisted source strings
are preserved exactly. A query that is blank after trimming returns an empty
list and performs no DAO reads.

The application creates a bound contains pattern:

```text
input `\\` -> pattern `\\\\`
input `%`  -> pattern `\\%`
input `_`  -> pattern `\\_`
pattern    = `%` + escaped query + `%`
SQL suffix = `ESCAPE '\\' COLLATE NOCASE`
```

The escape character is one literal backslash; the SQL string in `ESCAPE` must
contain exactly that one character. `%`, `_`, and backslash supplied by the
user are therefore searched literally and are never treated as SQL wildcards.
Values are bound parameters; user text is never concatenated into SQL.

Case behavior follows SQLite `NOCASE`: ASCII letters match without regard to
case. Non-ASCII characters use exact Unicode code-point matching. There is no
locale-specific case folding, accent/diacritic folding, transliteration, or
Unicode normalization in this change. Spanish text such as `niño`, `árbol`,
and `acción` is searchable when the corresponding characters are present;
`n` does not match `ñ`, and `á` does not match `a` or `Á` solely by accent or
case folding.

Each source participates as follows:

- Captures search Text `original_text` and Voice `COALESCE(corrected_transcript,
  original_text)`. A corrected Voice capture is searched by its effective
  transcript; its superseded original is not a second searchable field.
- Notes search `notes.text`.
- Structured Logs search `structured_log_fields.field_key` or
  `structured_log_fields.field_value`. A matching log is returned once even
  when several fields match, using an `EXISTS`/distinct-parent query rather
  than one result per matching field.
- List Items search `list_items.text`, including completed items, current and
  historical Mandado items, and continuous Compras items. List definition and
  session columns are display context, not additional searchable text in this
  change. A List Item therefore produces one result keyed by `list_items.id`.

## Ordering and limit

The effective result limit is the default `50`, capped at `50`. A caller may
request a smaller positive limit. A non-positive limit returns an empty list
without querying. The foundation never returns more than 50 results.

After source mapping, every result uses this global order:

1. durable source timestamp descending;
2. stable source/result ID descending;
3. result-kind name ascending only if timestamps and IDs are identical across
   source types.

Timestamps are selected from the record that the result represents:

- Capture: `capturedAt`;
- Note: `createdAt`;
- Structured Log: `createdAt`;
- List Item: `createdAt` from `list_items`, not the parent session's start or
  end time.

List item creation time remains meaningful for both current and historical
items. Mandado session identity and completion state provide context without
changing the global ordering.

## DAO/query strategy

The implementation may add read-only DAO methods and small Room projection
types, but it must not change entity definitions or schema version. The
planned queries are:

- `CaptureDao`: effective-text predicate with `captured_at_epoch_millis DESC,
  id DESC`;
- `NoteDao`: `text` predicate with `created_at_epoch_millis DESC, id DESC`;
- `StructuredLogDao`: parent rows selected through `EXISTS` against
  `structured_log_fields`, ordered by log timestamp and ID;
- `ListItemDao`: item-text predicate with joins/projections for the list
  definition and optional session context, ordered by item creation time and
  ID.

The leading `%` contains predicate is intentionally a simple table query. No
new search index is introduced in this change; FTS remains unnecessary for
the bounded personal-M1 requirement and would add tokenizer/schema/migration
scope.

## Acceptance scenarios

1. A query finds a Capture older than the 50 rows shown by Capture History.
2. Text Capture matches its original text; Voice matches corrected text when
   present and original transcript otherwise.
3. A Note is returned by exact text containment with its Note ID and timestamp.
4. A Structured Log is returned by either field key or field value, once even
   when multiple fields match, with all exact fields retained.
5. Completed and historical Mandado items and continuous Compras items are
   searchable by item text, with list/session context preserved.
6. Blank and whitespace-only queries return no results; surrounding query
   whitespace is ignored while internal whitespace is preserved.
7. Literal `%`, `_`, and backslash queries match literal stored characters and
   do not expand into wildcard matches.
8. ASCII case matching is case-insensitive; Spanish/non-ASCII behavior follows
   the explicit code-point rules above.
9. Results from all four source kinds merge into the documented global order,
   with stable tie behavior and no duplicate source records.
10. The result count never exceeds the effective limit, and a small caller
    limit is respected.
11. Search reads durable tables directly and does not call recent-screen APIs.
12. Room schema version 4 and schemas 1–4 remain unchanged; no migration, FTS
    table, dependency, UI route, or search bar is added.

## Verification and authority

Implementation verification must include focused unit tests for query escaping,
normalization, limit handling, and ordering; Android Room integration coverage
for all source kinds and durable-history behavior; the existing unit, assemble,
lint, and connected-test gates; `git diff --check`; and schema/dependency
inspection. Report unavailable device evidence accurately.

Do not merge or push. Do not declare a final engineering verdict; leave final
review to the independent reviewer.

## Stop conditions

Stop and report if implementation requires schema 5, a migration, FTS, a new
production dependency, loading recent UI lists and filtering in memory, a
MemoryStore redesign, UI work, fuzzy/semantic search, or a ranking policy
beyond the documented timestamp/ID ordering.
