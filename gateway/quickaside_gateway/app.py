from __future__ import annotations

import logging
import time
import uuid

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from .codex_runner import (
    CodexRunner,
    ProviderInvalidOutputError,
    ProviderTimeoutError,
    ProviderUnavailableError,
    ReadinessError,
)
from .config import GatewaySettings
from .contracts import InterpretRequest, InterpretResponse


logger = logging.getLogger("quickaside.gateway")


class RequestBodyTooLargeError(ValueError):
    pass


async def _read_bounded_body(request: Request, max_bytes: int) -> bytes:
    body = bytearray()
    async for chunk in request.stream():
        if len(body) + len(chunk) > max_bytes:
            raise RequestBodyTooLargeError("request body too large")
        body.extend(chunk)
    return bytes(body)


def create_app(
    settings: GatewaySettings | None = None,
    runner: CodexRunner | None = None,
) -> FastAPI:
    resolved_settings = settings or GatewaySettings.from_env()
    resolved_runner = runner or CodexRunner(resolved_settings)

    app = FastAPI(
        title="Quick Aside Gateway",
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
    )

    @app.get("/healthz")
    async def healthz() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/readyz")
    async def readyz():
        try:
            await resolved_runner.readiness()
        except (ReadinessError, ProviderUnavailableError):
            return JSONResponse(status_code=503, content={"status": "not_ready"})
        return {"status": "ready"}

    @app.post("/v1/interpret", response_model=InterpretResponse)
    async def interpret(http_request: Request):
        try:
            body = await _read_bounded_body(
                http_request,
                resolved_settings.max_request_body_bytes,
            )
            request = InterpretRequest.model_validate_json(body)
        except RequestBodyTooLargeError:
            return JSONResponse(status_code=413, content={"code": "request_too_large"})
        except ValidationError:
            # Pydantic validation detail may echo rejected request values.
            return JSONResponse(status_code=422, content={"code": "invalid_request"})

        request_id = uuid.uuid4().hex
        started = time.monotonic()

        try:
            response = await resolved_runner.interpret(request)
        except ProviderTimeoutError:
            _log_failure(request_id, started, "provider_timeout")
            return JSONResponse(status_code=504, content={"code": "provider_timeout"})
        except ProviderUnavailableError:
            _log_failure(request_id, started, "provider_unavailable")
            return JSONResponse(status_code=503, content={"code": "provider_unavailable"})
        except ProviderInvalidOutputError:
            _log_failure(request_id, started, "provider_invalid_output")
            return JSONResponse(status_code=502, content={"code": "provider_invalid_output"})

        duration_ms = round((time.monotonic() - started) * 1000)
        logger.info(
            "interpret_completed request_id=%s duration_ms=%s action_count=%s",
            request_id,
            duration_ms,
            len(response.actions),
        )
        return response

    return app


def _log_failure(request_id: str, started: float, code: str) -> None:
    duration_ms = round((time.monotonic() - started) * 1000)
    logger.warning(
        "interpret_failed request_id=%s duration_ms=%s code=%s",
        request_id,
        duration_ms,
        code,
    )


app = create_app()
