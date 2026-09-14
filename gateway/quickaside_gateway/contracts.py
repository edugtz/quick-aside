from __future__ import annotations

import re
from datetime import UTC, date, datetime
from typing import Annotated, Literal, Union
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


MAX_INPUT_CHARS = 4_000
MAX_ACTIONS = 16
MAX_LIST_ITEM_CHARS = 1_000
MAX_TASK_TITLE_CHARS = 500
MAX_NOTE_CHARS = 4_000
MAX_STRUCTURED_LOG_FIELDS = 32
MAX_STRUCTURED_LOG_KEY_CHARS = 128
MAX_STRUCTURED_LOG_VALUE_CHARS = 1_000

_RFC3339_PATTERN = re.compile(
    r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$"
)


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class InterpretRequest(StrictModel):
    inputText: str = Field(max_length=MAX_INPUT_CHARS)
    capturedAt: datetime
    timeZone: str = Field(max_length=128)

    @field_validator("inputText")
    @classmethod
    def validate_input_text(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("inputText must not be blank")
        return value

    @field_validator("capturedAt", mode="before")
    @classmethod
    def validate_captured_at_wire_format(cls, value: object) -> object:
        if not isinstance(value, str) or _RFC3339_PATTERN.fullmatch(value) is None:
            raise ValueError("capturedAt must be an RFC3339 string")
        return value

    @field_validator("capturedAt")
    @classmethod
    def validate_captured_at(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("capturedAt must be offset-aware")
        return value.astimezone(UTC)

    @field_validator("timeZone")
    @classmethod
    def validate_timezone(cls, value: str) -> str:
        try:
            ZoneInfo(value)
        except ZoneInfoNotFoundError as exc:
            raise ValueError("timeZone must be a valid IANA timezone") from exc
        return value

    @property
    def local_captured_at(self) -> datetime:
        return self.capturedAt.astimezone(ZoneInfo(self.timeZone))


class AddListItemAction(StrictModel):
    type: Literal["AddListItem"]
    listDefinitionId: Literal["mandado", "compras"]
    text: str = Field(max_length=MAX_LIST_ITEM_CHARS)

    @field_validator("text")
    @classmethod
    def validate_text(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("text must not be blank")
        return value


class CreateTaskAction(StrictModel):
    type: Literal["CreateTask"]
    space: Literal["PERSONAL", "TRABAJO"]
    title: str = Field(max_length=MAX_TASK_TITLE_CHARS)
    dueDate: date | None = None

    @field_validator("title")
    @classmethod
    def validate_title(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("title must not be blank")
        return value


class CreateNoteAction(StrictModel):
    type: Literal["CreateNote"]
    text: str = Field(max_length=MAX_NOTE_CHARS)

    @field_validator("text")
    @classmethod
    def validate_text(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("text must not be blank")
        return value


class CreateStructuredLogAction(StrictModel):
    type: Literal["CreateStructuredLog"]
    fields: dict[str, str] = Field(
        min_length=1,
        max_length=MAX_STRUCTURED_LOG_FIELDS,
    )

    @model_validator(mode="after")
    def validate_fields(self) -> "CreateStructuredLogAction":
        if any(not key.strip() for key in self.fields):
            raise ValueError("field keys must not be blank")
        if any(not value.strip() for value in self.fields.values()):
            raise ValueError("field values must not be blank")
        if any(len(key) > MAX_STRUCTURED_LOG_KEY_CHARS for key in self.fields):
            raise ValueError("field key too long")
        if any(len(value) > MAX_STRUCTURED_LOG_VALUE_CHARS for value in self.fields.values()):
            raise ValueError("field value too long")
        return self


class UndoLastAction(StrictModel):
    type: Literal["UndoLast"]


InterpretAction = Annotated[
    Union[
        AddListItemAction,
        CreateTaskAction,
        CreateNoteAction,
        CreateStructuredLogAction,
        UndoLastAction,
    ],
    Field(discriminator="type"),
]


class InterpretResponse(StrictModel):
    actions: list[InterpretAction] = Field(max_length=MAX_ACTIONS)


class ProviderFieldPair(StrictModel):
    key: str = Field(max_length=MAX_STRUCTURED_LOG_KEY_CHARS)
    value: str = Field(max_length=MAX_STRUCTURED_LOG_VALUE_CHARS)

    @field_validator("key", "value")
    @classmethod
    def validate_nonblank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("structured-log key/value must not be blank")
        return value


ProviderFields = Annotated[
    list[ProviderFieldPair],
    Field(max_length=MAX_STRUCTURED_LOG_FIELDS),
]


class ProviderAction(StrictModel):
    # Flat, all-fields-present shape to stay within strict structured-output
    # constraints. Non-applicable properties must be null and are enforced
    # again locally before conversion to provider-neutral actions.
    type: Literal[
        "AddListItem",
        "CreateTask",
        "CreateNote",
        "CreateStructuredLog",
        "UndoLast",
    ]
    listDefinitionId: Literal["mandado", "compras"] | None
    text: Annotated[str, Field(max_length=MAX_NOTE_CHARS)] | None
    space: Literal["PERSONAL", "TRABAJO"] | None
    title: Annotated[str, Field(max_length=MAX_TASK_TITLE_CHARS)] | None
    dueDate: date | None
    fields: ProviderFields | None


class ProviderCandidate(StrictModel):
    actions: list[ProviderAction] = Field(max_length=MAX_ACTIONS)


class ProviderOutputError(ValueError):
    pass


_PROVIDER_FIELDS = {
    "listDefinitionId",
    "text",
    "space",
    "title",
    "dueDate",
    "fields",
}

_ALLOWED_NON_NULL_FIELDS = {
    "AddListItem": {"listDefinitionId", "text"},
    "CreateTask": {"space", "title", "dueDate"},
    "CreateNote": {"text"},
    "CreateStructuredLog": {"fields"},
    "UndoLast": set(),
}


def _require_text(value: str | None, field_name: str) -> str:
    if value is None or not value.strip():
        raise ProviderOutputError(f"{field_name} missing")
    return value


def _reject_non_applicable_fields(action: ProviderAction) -> None:
    allowed = _ALLOWED_NON_NULL_FIELDS[action.type]
    for field_name in _PROVIDER_FIELDS - allowed:
        if getattr(action, field_name) is not None:
            raise ProviderOutputError(
                f"{field_name} must be null for {action.type}"
            )


def to_interpret_response(candidate: ProviderCandidate) -> InterpretResponse:
    actions: list[InterpretAction] = []

    for action in candidate.actions:
        _reject_non_applicable_fields(action)

        if action.type == "AddListItem":
            if action.listDefinitionId is None:
                raise ProviderOutputError("listDefinitionId missing")
            actions.append(
                AddListItemAction(
                    type="AddListItem",
                    listDefinitionId=action.listDefinitionId,
                    text=_require_text(action.text, "text"),
                )
            )
            continue

        if action.type == "CreateTask":
            if action.space is None:
                raise ProviderOutputError("space missing")
            actions.append(
                CreateTaskAction(
                    type="CreateTask",
                    space=action.space,
                    title=_require_text(action.title, "title"),
                    dueDate=action.dueDate,
                )
            )
            continue

        if action.type == "CreateNote":
            actions.append(
                CreateNoteAction(
                    type="CreateNote",
                    text=_require_text(action.text, "text"),
                )
            )
            continue

        if action.type == "CreateStructuredLog":
            if not action.fields:
                raise ProviderOutputError("fields missing")
            fields: dict[str, str] = {}
            for pair in action.fields:
                if pair.key in fields:
                    raise ProviderOutputError("duplicate structured-log field key")
                fields[pair.key] = pair.value
            actions.append(
                CreateStructuredLogAction(
                    type="CreateStructuredLog",
                    fields=fields,
                )
            )
            continue

        if action.type == "UndoLast":
            actions.append(UndoLastAction(type="UndoLast"))
            continue

        raise ProviderOutputError("unsupported action type")

    return InterpretResponse(actions=actions)
