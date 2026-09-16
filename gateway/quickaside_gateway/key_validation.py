from __future__ import annotations

from cryptography.exceptions import UnsupportedAlgorithm
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec


class InvalidPublicKeyError(ValueError):
    pass


def load_p256_public_key(
    public_key_pem: str,
) -> ec.EllipticCurvePublicKey:
    try:
        key = serialization.load_pem_public_key(
            public_key_pem.encode("ascii")
        )
    except (ValueError, TypeError, UnicodeEncodeError, UnsupportedAlgorithm) as exc:
        raise InvalidPublicKeyError("invalid public key") from exc

    if not isinstance(key, ec.EllipticCurvePublicKey):
        raise InvalidPublicKeyError("invalid public key")

    if not isinstance(key.curve, ec.SECP256R1):
        raise InvalidPublicKeyError("invalid public key")

    return key
