from __future__ import annotations

import base64
import hashlib
import re
import time
from dataclasses import dataclass

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec

from quickaside_gateway.key_validation import (
    InvalidPublicKeyError,
    load_p256_public_key,
)

from quickaside_gateway.auth_store import (
    AuthStore,
    DeviceRecord,
    ReplayDetectedError,
)


_DEVICE_ID_RE = re.compile(r"^[A-Za-z0-9._-]{1,128}$")
_BASE64URL_RE = re.compile(r"^[A-Za-z0-9_-]+$")
_MAX_NONCE_HEADER_LENGTH = 256
_MAX_SIGNATURE_HEADER_LENGTH = 256


class AuthenticationError(RuntimeError):
    pass


@dataclass(frozen=True)
class AuthenticatedRequest:
    device: DeviceRecord
    timestamp: int
    nonce: str


def build_qa1_canonical_request(
    *,
    method: str,
    path: str,
    body: bytes,
    device_id: str,
    timestamp: str,
    nonce: str,
) -> bytes:
    body_hash = hashlib.sha256(body).hexdigest()

    canonical = "\n".join(
        (
            "QA1",
            method.upper(),
            path,
            device_id,
            timestamp,
            nonce,
            body_hash,
        )
    )

    return canonical.encode("utf-8")


class RequestAuthenticator:
    def __init__(
        self,
        store: AuthStore,
        *,
        timestamp_skew_seconds: int = 120,
        replay_ttl_seconds: int = 300,
    ):
        if timestamp_skew_seconds <= 0:
            raise ValueError("timestamp_skew_seconds must be positive")

        if replay_ttl_seconds <= timestamp_skew_seconds * 2:
            raise ValueError(
                "replay_ttl_seconds must exceed the full timestamp window"
            )

        self.store = store
        self.timestamp_skew_seconds = timestamp_skew_seconds
        self.replay_ttl_seconds = replay_ttl_seconds

    def authenticate(
        self,
        *,
        method: str,
        path: str,
        body: bytes,
        device_id: str,
        timestamp: str,
        nonce: str,
        signature: str,
        now: int | None = None,
    ) -> AuthenticatedRequest:
        current = int(time.time()) if now is None else now

        self._validate_device_id(device_id)
        request_timestamp = self._validate_timestamp(
            timestamp,
            current=current,
        )
        self._validate_nonce(nonce)

        device = self.store.get_device(device_id)

        if device is None or device.status != "active":
            raise AuthenticationError("authentication failed")

        try:
            public_key = load_p256_public_key(device.public_key_pem)
        except InvalidPublicKeyError as exc:
            raise AuthenticationError(
                "authentication failed"
            ) from exc
        signature_bytes = self._decode_signature(signature)

        canonical = build_qa1_canonical_request(
            method=method,
            path=path,
            body=body,
            device_id=device_id,
            timestamp=timestamp,
            nonce=nonce,
        )

        try:
            public_key.verify(
                signature_bytes,
                canonical,
                ec.ECDSA(hashes.SHA256()),
            )
        except InvalidSignature as exc:
            raise AuthenticationError(
                "authentication failed"
            ) from exc

        try:
            self.store.accept_nonce(
                device_id=device_id,
                nonce=nonce,
                ttl_seconds=self.replay_ttl_seconds,
                now=current,
            )
        except ReplayDetectedError as exc:
            raise AuthenticationError(
                "authentication failed"
            ) from exc

        return AuthenticatedRequest(
            device=device,
            timestamp=request_timestamp,
            nonce=nonce,
        )

    def _validate_timestamp(
        self,
        value: str,
        *,
        current: int,
    ) -> int:
        if not value or len(value) > 20:
            raise AuthenticationError("authentication failed")

        try:
            parsed = int(value)
        except ValueError as exc:
            raise AuthenticationError(
                "authentication failed"
            ) from exc

        if abs(current - parsed) > self.timestamp_skew_seconds:
            raise AuthenticationError("authentication failed")

        return parsed

    @staticmethod
    def _validate_device_id(device_id: str) -> None:
        if not _DEVICE_ID_RE.fullmatch(device_id):
            raise AuthenticationError("authentication failed")

    @staticmethod
    def _validate_nonce(nonce: str) -> None:
        if (
            not nonce
            or len(nonce) > _MAX_NONCE_HEADER_LENGTH
        ):
            raise AuthenticationError("authentication failed")

        decoded = _decode_base64url(nonce)

        if len(decoded) < 16:
            raise AuthenticationError("authentication failed")

    @staticmethod
    def _decode_signature(value: str) -> bytes:
        if (
            not value
            or len(value) > _MAX_SIGNATURE_HEADER_LENGTH
        ):
            raise AuthenticationError("authentication failed")

        decoded = _decode_base64url(value)

        if not decoded or len(decoded) > 128:
            raise AuthenticationError("authentication failed")

        return decoded

def _decode_base64url(value: str) -> bytes:
    if not value or not _BASE64URL_RE.fullmatch(value):
        raise AuthenticationError("authentication failed")

    try:
        raw = value.encode("ascii")
    except UnicodeEncodeError as exc:
        raise AuthenticationError("authentication failed") from exc

    padding = b"=" * (-len(raw) % 4)

    try:
        return base64.b64decode(
            raw + padding,
            altchars=b"-_",
            validate=True,
        )
    except ValueError as exc:
        raise AuthenticationError("authentication failed") from exc
