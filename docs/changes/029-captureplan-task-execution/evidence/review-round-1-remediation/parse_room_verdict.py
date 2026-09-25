#!/usr/bin/env python3
"""Derive a machine-readable verdict for the CHG-029 Round-1 remediation Room gate.

Reads the raw AndroidJUnitRunner stream saved by `run_gate.py`, records the
discovered test count and per-method result, and writes `test-verdict.json`.
It never runs tests and never assumes an expected count.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[5]
NEW_REMEDIATION_TESTS = (
    "firstTaskIdCollisionFailsWithoutChangingExistingOrUnrelatedState",
    "duplicateGeneratedTaskIdsFailBeforeInsertionAndPreserveAllExistingState",
)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("gate_dir", type=Path)
    args = parser.parse_args()
    gate_dir = args.gate_dir if args.gate_dir.is_absolute() else ROOT / args.gate_dir
    log_path = gate_dir / "instrumentation-output.txt"
    run_record_path = gate_dir / "run-record.json"
    log = log_path.read_text(encoding="utf-8")
    run_record = json.loads(run_record_path.read_text(encoding="utf-8"))

    ok_match = re.search(r"^OK \((\d+) tests?\)$", log, flags=re.MULTILINE)
    counts_match = re.search(
        r"^Tests run: (\d+),\s+Failures: (\d+)$", log, flags=re.MULTILINE
    )
    failure_marker = "FAILURES!!!" in log
    result_code_match = re.search(
        r"^INSTRUMENTATION_CODE: (-?\d+)$", log, flags=re.MULTILINE
    )

    methods: list[str] = []
    for match in re.finditer(
        r"^INSTRUMENTATION_STATUS: test=(\S+)$", log, flags=re.MULTILINE
    ):
        if match.group(1) not in methods:
            methods.append(match.group(1))
    final_status_codes: dict[str, int] = {}
    for match in re.finditer(
        r"^INSTRUMENTATION_STATUS: test=(\S+)\nINSTRUMENTATION_STATUS_CODE: (-?\d+)$",
        log,
        flags=re.MULTILINE,
    ):
        final_status_codes[match.group(1)] = int(match.group(2))

    if ok_match and not failure_marker:
        status = "PASS"
        tests = int(ok_match.group(1))
        failures = 0
    elif failure_marker or counts_match:
        status = "FAIL"
        tests = int(counts_match.group(1)) if counts_match else None
        failures = int(counts_match.group(2)) if counts_match else None
    else:
        status = "INDETERMINATE"
        tests = None
        failures = None

    remediation_tests = {}
    for name in NEW_REMEDIATION_TESTS:
        code = final_status_codes.get(name)
        remediation_tests[name] = (
            "executed; final status code 0"
            if code == 0
            else f"not executed or final status code {code}"
        )

    verdict = {
        "status": status,
        "process_exit_code": run_record.get("exit_code"),
        "instrumentation_result_code": (
            int(result_code_match.group(1)) if result_code_match else None
        ),
        "instrumentation_result_code_meaning": (
            "Activity.RESULT_OK"
            if result_code_match and result_code_match.group(1) == "-1"
            else None
        ),
        "tests_reported": tests,
        "failures": failures,
        "test_methods": methods,
        "remediation_tests": remediation_tests,
        "raw_output": log_path.name,
    }
    (gate_dir / "test-verdict.json").write_text(
        json.dumps(verdict, indent=2) + "\n", encoding="utf-8"
    )
    run_record.update(
        {
            "test_gate_status": status,
            "test_count": tests,
            "failures": failures,
            "test_methods": methods,
            "instrumentation_result_code": verdict["instrumentation_result_code"],
            "instrumentation_result_code_meaning": verdict[
                "instrumentation_result_code_meaning"
            ],
            "raw_output": log_path.name,
        }
    )
    run_record_path.write_text(
        json.dumps(run_record, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(verdict, indent=2))
    if (
        status != "PASS"
        or tests is None
        or any(final_status_codes.get(name) != 0 for name in NEW_REMEDIATION_TESTS)
    ):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
