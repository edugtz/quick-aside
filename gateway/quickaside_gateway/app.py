from __future__ import annotations

import logging
import time
import uuid

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from .auth_store import (
    AuthStore,
    DeviceAlreadyRegisteredError,
    PairingCodeError,
)
from .codex_runner import (
    CodexRunner,
    ProviderInvalidOutputError,
    ProviderTimeoutError,
    ProviderUnavailableError,
    ReadinessError,
)
from .config import GatewaySettings
from .contracts import InterpretRequest, InterpretResponse
from .key_validation import (
    InvalidPublicKeyError,
    load_p256_public_key,
)
from .pairing import PairRequest, PairResponse
from .rate_limit import SlidingWindowRateLimiter
from .request_auth import (
    AuthenticationError,
    RequestAuthenticator,
)


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



def _client_rate_limit_key(request: Request) -> str:
    if request.client is None:
        return "unknown"

    return request.client.host

def create_app(
    settings: GatewaySettings | None = None,
    runner: CodexRunner | None = None,
) -> FastAPI:
    resolved_settings = settings or GatewaySettings.from_env()

    if resolved_settings.auth_db_path is None:
        raise ValueError("auth_db_path is required")

    resolved_runner = runner or CodexRunner(resolved_settings)

    auth_store = AuthStore(resolved_settings.auth_db_path)
    auth_store.initialize()

    authenticator = RequestAuthenticator(
        auth_store,
        timestamp_skew_seconds=(
            resolved_settings.auth_timestamp_skew_seconds
        ),
        replay_ttl_seconds=(
            resolved_settings.replay_nonce_ttl_seconds
        ),
    )

    auth_global_rate_limiter = SlidingWindowRateLimiter(
        resolved_settings.auth_global_rate_limit_count,
        resolved_settings.auth_global_rate_limit_window_seconds,
        max_keys=1,
    )

    auth_attempt_rate_limiter = SlidingWindowRateLimiter(
        resolved_settings.auth_attempt_rate_limit_count,
        resolved_settings.auth_attempt_rate_limit_window_seconds,
    )

    pair_rate_limiter = SlidingWindowRateLimiter(
        resolved_settings.pair_rate_limit_count,
        resolved_settings.pair_rate_limit_window_seconds,
    )

    interpret_rate_limiter = SlidingWindowRateLimiter(
        resolved_settings.interpret_rate_limit_count,
        resolved_settings.interpret_rate_limit_window_seconds,
    )

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
            return JSONResponse(
                status_code=503,
                content={"status": "not_ready"},
            )

        return {"status": "ready"}

    @app.post("/v1/pair", response_model=PairResponse)
    async def pair(http_request: Request):
        try:
            body = await _read_bounded_body(
                http_request,
                resolved_settings.max_request_body_bytes,
            )
        except RequestBodyTooLargeError:
            return JSONResponse(
                status_code=413,
                content={"code": "request_too_large"},
            )

        if not pair_rate_limiter.allow("pair"):
            return JSONResponse(
                status_code=429,
                content={"code": "rate_limited"},
            )

        try:
            request = PairRequest.model_validate_json(body)
        except ValidationError:
            return JSONResponse(
                status_code=422,
                content={"code": "invalid_request"},
            )

        try:
            load_p256_public_key(request.public_key_pem)

            device = auth_store.consume_pairing_code(
                code=request.pairing_code,
                device_id=request.device_id,
                label=request.label,
                public_key_pem=request.public_key_pem,
            )
        except (
            InvalidPublicKeyError,
            PairingCodeError,
            DeviceAlreadyRegisteredError,
        ):
            return JSONResponse(
                status_code=400,
                content={"code": "pairing_failed"},
            )

        logger.info(
            "device_paired device_id=%s",
            device.device_id,
        )

        return PairResponse(
            deviceId=device.device_id,
            status=device.status,
        )

    @app.post("/v1/interpret", response_model=InterpretResponse)
    async def interpret(http_request: Request):
        try:
            body = await _read_bounded_body(
                http_request,
                resolved_settings.max_request_body_bytes,
            )
        except RequestBodyTooLargeError:
            return JSONResponse(
                status_code=413,
                content={"code": "request_too_large"},
            )

        if not auth_attempt_rate_limiter.allow(
            _client_rate_limit_key(http_request)
        ):
            return JSONResponse(
                status_code=429,
                content={"code": "rate_limited"},
            )

        if not auth_global_rate_limiter.allow("global"):
            return JSONResponse(
                status_code=429,
                content={"code": "rate_limited"},
            )

        try:
            authenticated = authenticator.authenticate(
                method=http_request.method,
                path=http_request.url.path,
                body=body,
                device_id=http_request.headers.get(
                    "X-QA-Device-Id",
                    "",
                ),
                timestamp=http_request.headers.get(
                    "X-QA-Timestamp",
                    "",
                ),
                nonce=http_request.headers.get(
                    "X-QA-Nonce",
                    "",
                ),
                signature=http_request.headers.get(
                    "X-QA-Signature",
                    "",
                ),
            )
        except AuthenticationError:
            return JSONResponse(
                status_code=401,
                content={"code": "authentication_failed"},
            )

        if not interpret_rate_limiter.allow(
            authenticated.device.device_id
        ):
            return JSONResponse(
                status_code=429,
                content={"code": "rate_limited"},
            )

        try:
            request = InterpretRequest.model_validate_json(body)
        except ValidationError:
            # Pydantic validation detail may echo rejected request values.
            return JSONResponse(
                status_code=422,
                content={"code": "invalid_request"},
            )

        request_id = uuid.uuid4().hex
        started = time.monotonic()

        try:
            response = await resolved_runner.interpret(request)
        except ProviderTimeoutError:
            _log_failure(
                request_id,
                started,
                "provider_timeout",
            )
            return JSONResponse(
                status_code=504,
                content={"code": "provider_timeout"},
            )
        except ProviderUnavailableError:
            _log_failure(
                request_id,
                started,
                "provider_unavailable",
            )
            return JSONResponse(
                status_code=503,
                content={"code": "provider_unavailable"},
            )
        except ProviderInvalidOutputError:
            _log_failure(
                request_id,
                started,
                "provider_invalid_output",
            )
            return JSONResponse(
                status_code=502,
                content={"code": "provider_invalid_output"},
            )

        duration_ms = round(
            (time.monotonic() - started) * 1000
        )

        logger.info(
            "interpret_completed "
            "request_id=%s duration_ms=%s action_count=%s",
            request_id,
            duration_ms,
            len(response.actions),
        )

        return response

    return app


def _log_failure(
    request_id: str,
    started: float,
    code: str,
) -> None:
    duration_ms = round(
        (time.monotonic() - started) * 1000
    )

    logger.warning(
        "interpret_failed "
        "request_id=%s duration_ms=%s code=%s",
        request_id,
        duration_ms,
        code,
    )


