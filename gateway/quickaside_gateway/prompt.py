from __future__ import annotations

from .contracts import InterpretRequest


def build_prompt(request: InterpretRequest) -> str:
    local = request.local_captured_at
    captured_utc = request.capturedAt.isoformat().replace("+00:00", "Z")

    return f"""You are the interpretation engine for Quick Aside.
Return only the JSON object required by the supplied output schema.

Interpret exactly one user capture into zero or more supported actions.
Do not invent sourceCaptureId or any persistence identifier.

Every provider action has all schema properties present. Set properties that do
not apply to that action type to null.

Field contract by action type:
- AddListItem: listDefinitionId and text MUST be non-null; all other action
  properties MUST be null.
- CreateTask: space and title MUST be non-null; dueDate is YYYY-MM-DD or null;
  all other action properties MUST be null. title must be a concise actionable
  task title derived from the capture.
- CreateNote: text MUST be non-null; all other action properties MUST be null.
- CreateStructuredLog: fields MUST be a non-empty list of {{\"key\",\"value\"}}
  pairs; all other action properties MUST be null.
- UndoLast: all action properties other than type MUST be null.

Canonical routing examples:
- \"Mañana revisa el PR\" -> CreateTask, TRABAJO, title \"Revisar el PR\",
  dueDate equal to tomorrow in the trusted local timezone.
- \"Revisar integración de Firebase mañana\" -> CreateTask, TRABAJO.

Routing guidance:
- ordinary groceries/household consumables -> mandado.
- durable or non-grocery purchase intent -> compras.
- software-development/work captures such as PR, pull request, code review, Firebase, integration, deploy, bug or ticket -> TRABAJO unless the user explicitly says they are personal.
- otherwise tasks -> PERSONAL.
- relative dates are resolved using the trusted local temporal context below.
- if the capture cannot be represented safely by the supported actions, return an empty actions list.

Trusted temporal context:
- capturedAtUtc: {captured_utc}
- timeZone: {request.timeZone}
- localDate: {local.date().isoformat()}
- localDateTime: {local.isoformat()}

User capture:
{request.inputText}
"""
