#!/usr/bin/env python3
"""Run one CHG-029 gate while preserving its output and provenance."""

from __future__ import annotations

import argparse
import json
import re
import shlex
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[4]
PROVENANCE_TOOL = ROOT / "docs/changes/029-captureplan-task-execution/evidence/provenance.py"


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def snapshot(output_dir: Path, label: str) -> dict:
    destination = output_dir / f"provenance-{label}"
    subprocess.run(
        [sys.executable, str(PROVENANCE_TOOL), str(destination)],
        cwd=ROOT,
        check=True,
        stdout=subprocess.DEVNULL,
    )
    return json.loads((destination / "provenance.json").read_text(encoding="utf-8"))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("output_dir", type=Path)
    parser.add_argument("--device-info", default="not applicable")
    parser.add_argument("--log-name", default="command-output.txt")
    argv = sys.argv[1:]
    if "--" not in argv:
        parser.parse_args(argv)
        parser.error("a command is required after --")
    separator = argv.index("--")
    args = parser.parse_args(argv[:separator])
    command = argv[separator + 1 :]
    if not command:
        parser.error("a command is required after --")

    output_dir = args.output_dir if args.output_dir.is_absolute() else ROOT / args.output_dir
    output_dir.mkdir(parents=True, exist_ok=True)
    log_path = output_dir / args.log_name
    started = utc_now()
    before = snapshot(output_dir, "start")
    print(f"CHG-029 gate started: {started}", flush=True)
    print(f"Command: {shlex.join(command)}", flush=True)
    print(f"Source fingerprint: {before['worktree_source_fingerprint_sha256']}", flush=True)

    with log_path.open("w", encoding="utf-8") as log:
        log.write(f"CHG-029 gate started: {started}\n")
        log.write(f"Command: {shlex.join(command)}\n")
        log.write(f"Device/emulator: {args.device_info}\n\n")
        try:
            process = subprocess.Popen(
                command,
                cwd=ROOT,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
            )
            assert process.stdout is not None
            for line in process.stdout:
                sys.stdout.write(line)
                sys.stdout.flush()
                log.write(line)
                log.flush()
            exit_code = process.wait()
        except OSError as error:
            log.write(f"Runner could not start command: {error}\n")
            log.flush()
            exit_code = 127

    command_exit_code = exit_code
    log_text = log_path.read_text(encoding="utf-8")
    if (
        "FAILURES!!!" in log_text
        or re.search(r"INSTRUMENTATION_STATUS_CODE:\s*-2\b", log_text)
        or re.search(r"^OK \(0 tests?\)$", log_text, flags=re.MULTILINE)
    ):
        exit_code = 1

    ended = utc_now()
    after = snapshot(output_dir, "end")
    run_record = {
        "started_at_utc": started,
        "ended_at_utc": ended,
        "command": command,
        "command_display": shlex.join(command),
        "exit_code": exit_code,
        "command_exit_code": command_exit_code,
        "branch": before["branch"],
        "base_head": before["base_head"],
        "device_emulator_api": args.device_info,
        "start_worktree_source_fingerprint_sha256": before[
            "worktree_source_fingerprint_sha256"
        ],
        "end_worktree_source_fingerprint_sha256": after[
            "worktree_source_fingerprint_sha256"
        ],
        "log": str(log_path.relative_to(ROOT)),
        "runner_exit_detail": "command launch failed; see output log" if exit_code == 127 else None,
    }
    (output_dir / "run-record.json").write_text(
        json.dumps(run_record, indent=2) + "\n", encoding="utf-8"
    )
    (output_dir / "exit-code.txt").write_text(f"{exit_code}\n", encoding="utf-8")
    print(f"CHG-029 gate finished: {ended}; exit code {exit_code}", flush=True)
    raise SystemExit(exit_code)


if __name__ == "__main__":
    main()
