from __future__ import annotations

import argparse
import json
import os
from pathlib import Path

from .auth_store import AuthStore
from .config import (
    DEFAULT_PAIRING_CODE_TTL_SECONDS,
    MAX_PAIRING_CODE_TTL_SECONDS,
)


DEFAULT_AUTH_DB = "/var/lib/quickaside/auth.db"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="quickaside-admin",
        description="Local Quick Aside gateway administration",
    )

    parser.add_argument(
        "--db",
        type=Path,
        default=Path(
            os.environ.get(
                "QUICKASIDE_AUTH_DB",
                DEFAULT_AUTH_DB,
            )
        ),
        help="Quick Aside auth SQLite database",
    )

    commands = parser.add_subparsers(
        dest="command",
        required=True,
    )

    create = commands.add_parser(
        "create-pairing-code",
        help="Create a short-lived one-time pairing code",
    )
    create.add_argument(
        "--ttl",
        type=int,
        default=DEFAULT_PAIRING_CODE_TTL_SECONDS,
    )

    commands.add_parser(
        "list-devices",
        help="List registered devices",
    )

    revoke = commands.add_parser(
        "revoke-device",
        help="Revoke a registered device",
    )
    revoke.add_argument("device_id")

    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)

    store = AuthStore(args.db)
    store.initialize()

    if args.command == "create-pairing-code":
        if not (
            1 <= args.ttl <= MAX_PAIRING_CODE_TTL_SECONDS
        ):
            raise SystemExit(
                "--ttl must be between 1 and "
                f"{MAX_PAIRING_CODE_TTL_SECONDS} seconds"
            )

        print(store.create_pairing_code(args.ttl))
        return 0

    if args.command == "list-devices":
        devices = [
            {
                "deviceId": device.device_id,
                "label": device.label,
                "status": device.status,
                "createdAt": device.created_at,
                "revokedAt": device.revoked_at,
            }
            for device in store.list_devices()
        ]

        print(
            json.dumps(
                devices,
                separators=(",", ":"),
                sort_keys=True,
            )
        )
        return 0

    if args.command == "revoke-device":
        revoked = store.revoke_device(args.device_id)

        response = {
            "deviceId": args.device_id,
            "status": "revoked" if revoked else "not_changed",
        }

        print(
            json.dumps(
                response,
                separators=(",", ":"),
                sort_keys=True,
            )
        )

        return 0 if revoked else 2

    raise AssertionError(
        f"unhandled command: {args.command}"
    )


def entrypoint() -> None:
    raise SystemExit(main())


if __name__ == "__main__":
    entrypoint()
