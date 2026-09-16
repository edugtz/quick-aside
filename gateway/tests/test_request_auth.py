import base64
import secrets

import pytest
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

from quickaside_gateway.auth_store import AuthStore
from quickaside_gateway.request_auth import (
    AuthenticationError,
    RequestAuthenticator,
    build_qa1_canonical_request,
)


BODY = b'{"inputText":"Comprar leche"}'
DEVICE_ID = "oppo-test-1"
PATH = "/v1/interpret"
TIMESTAMP = "1000"


def b64url(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def create_key_pair():
    private_key = ec.generate_private_key(ec.SECP256R1())

    public_key_pem = (
        private_key.public_key()
        .public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo,
        )
        .decode("ascii")
    )

    return private_key, public_key_pem


def register_device(store, public_key_pem):
    code = store.create_pairing_code(600, now=900)

    store.consume_pairing_code(
        code=code,
        device_id=DEVICE_ID,
        label="Test Oppo",
        public_key_pem=public_key_pem,
        now=901,
    )


def signed_headers(
    private_key,
    *,
    body=BODY,
    timestamp=TIMESTAMP,
    nonce=None,
):
    if nonce is None:
        nonce = b64url(secrets.token_bytes(16))

    canonical = build_qa1_canonical_request(
        method="POST",
        path=PATH,
        body=body,
        device_id=DEVICE_ID,
        timestamp=timestamp,
        nonce=nonce,
    )

    signature = private_key.sign(
        canonical,
        ec.ECDSA(hashes.SHA256()),
    )

    return {
        "device_id": DEVICE_ID,
        "timestamp": timestamp,
        "nonce": nonce,
        "signature": b64url(signature),
    }


def make_authenticator(tmp_path):
    store = AuthStore(tmp_path / "auth.db")
    store.initialize()

    private_key, public_key_pem = create_key_pair()
    register_device(store, public_key_pem)

    auth = RequestAuthenticator(
        store,
        timestamp_skew_seconds=120,
        replay_ttl_seconds=300,
    )

    return store, auth, private_key


def test_valid_signed_request_authenticates(tmp_path):
    _, auth, private_key = make_authenticator(tmp_path)
    headers = signed_headers(private_key)

    result = auth.authenticate(
        method="POST",
        path=PATH,
        body=BODY,
        now=1000,
        **headers,
    )

    assert result.device.device_id == DEVICE_ID
    assert result.timestamp == 1000


def test_modified_body_is_rejected(tmp_path):
    _, auth, private_key = make_authenticator(tmp_path)
    headers = signed_headers(private_key)

    with pytest.raises(AuthenticationError):
        auth.authenticate(
            method="POST",
            path=PATH,
            body=b'{"inputText":"different"}',
            now=1000,
            **headers,
        )


def test_stale_timestamp_is_rejected(tmp_path):
    _, auth, private_key = make_authenticator(tmp_path)
    headers = signed_headers(private_key, timestamp="879")

    with pytest.raises(AuthenticationError):
        auth.authenticate(
            method="POST",
            path=PATH,
            body=BODY,
            now=1000,
            **headers,
        )


def test_future_timestamp_is_rejected(tmp_path):
    _, auth, private_key = make_authenticator(tmp_path)
    headers = signed_headers(private_key, timestamp="1121")

    with pytest.raises(AuthenticationError):
        auth.authenticate(
            method="POST",
            path=PATH,
            body=BODY,
            now=1000,
            **headers,
        )


def test_unknown_device_is_rejected(tmp_path):
    store = AuthStore(tmp_path / "auth.db")
    store.initialize()

    private_key, _ = create_key_pair()
    headers = signed_headers(private_key)

    auth = RequestAuthenticator(store)

    with pytest.raises(AuthenticationError):
        auth.authenticate(
            method="POST",
            path=PATH,
            body=BODY,
            now=1000,
            **headers,
        )


def test_revoked_device_is_rejected(tmp_path):
    store, auth, private_key = make_authenticator(tmp_path)
    store.revoke_device(DEVICE_ID, now=950)

    headers = signed_headers(private_key)

    with pytest.raises(AuthenticationError):
        auth.authenticate(
            method="POST",
            path=PATH,
            body=BODY,
            now=1000,
            **headers,
        )


def test_replayed_request_is_rejected_after_store_recreation(tmp_path):
    db_path = tmp_path / "auth.db"

    first_store = AuthStore(db_path)
    first_store.initialize()

    private_key, public_key_pem = create_key_pair()
    register_device(first_store, public_key_pem)

    headers = signed_headers(private_key)

    first_auth = RequestAuthenticator(first_store)

    first_auth.authenticate(
        method="POST",
        path=PATH,
        body=BODY,
        now=1000,
        **headers,
    )

    second_store = AuthStore(db_path)
    second_store.initialize()

    second_auth = RequestAuthenticator(second_store)

    with pytest.raises(AuthenticationError):
        second_auth.authenticate(
            method="POST",
            path=PATH,
            body=BODY,
            now=1001,
            **headers,
        )


def test_short_nonce_is_rejected(tmp_path):
    _, auth, private_key = make_authenticator(tmp_path)

    headers = signed_headers(
        private_key,
        nonce=b64url(b"too-short"),
    )

    with pytest.raises(AuthenticationError):
        auth.authenticate(
            method="POST",
            path=PATH,
            body=BODY,
            now=1000,
            **headers,
        )


def test_wrong_curve_public_key_is_rejected(tmp_path):
    store = AuthStore(tmp_path / "auth.db")
    store.initialize()

    private_key = ec.generate_private_key(ec.SECP384R1())

    public_key_pem = (
        private_key.public_key()
        .public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo,
        )
        .decode("ascii")
    )

    register_device(store, public_key_pem)

    headers = signed_headers(private_key)

    auth = RequestAuthenticator(store)

    with pytest.raises(AuthenticationError):
        auth.authenticate(
            method="POST",
            path=PATH,
            body=BODY,
            now=1000,
            **headers,
        )


def test_base64url_rejects_padding():
    from quickaside_gateway.request_auth import _decode_base64url

    with pytest.raises(AuthenticationError):
        _decode_base64url("YWJjZA==")


def test_base64url_rejects_standard_base64_alphabet():
    from quickaside_gateway.request_auth import _decode_base64url

    with pytest.raises(AuthenticationError):
        _decode_base64url("+/8")
