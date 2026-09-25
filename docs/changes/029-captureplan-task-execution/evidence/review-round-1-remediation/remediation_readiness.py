#!/usr/bin/env python3
"""Fresh CHG-029 Round-1 remediation readiness check.

Verifies that the remediation changed only the intended focused Room test plus
documentation/evidence, that the two MAJOR-1 scenarios exist and were executed
with a recorded count, that original Round-1 evidence was not overwritten, and
that no schema/dependency/config or CHG-030 work slipped in. Previously
accepted CHG-029 gates are treated as historical evidence and are not rerun.
"""

from __future__ import annotations

import hashlib
import json
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[5]
CHANGE = ROOT / "docs/changes/029-captureplan-task-execution"
EVIDENCE = CHANGE / "evidence"
REMEDIATION = EVIDENCE / "review-round-1-remediation"
TASK_EXECUTOR = REMEDIATION / "device/room/task-executor"
INSTRUMENTATION = TASK_EXECUTOR / "instrumentation"
READINESS = REMEDIATION / "readiness"
PROVENANCE_TOOL = EVIDENCE / "provenance.py"

REVIEWED_HEAD = "ad845759c85346c8fe4a976ba211a6f5f53a12c6"
BRANCH = "chg-029-captureplan-task-execution"
TEST_SOURCE = (
    "app/src/androidTest/java/com/edu/quickaside/data/local/"
    "CapturePlanTaskExecutorDatabaseTest.kt"
)
NEW_TESTS = (
    "firstTaskIdCollisionFailsWithoutChangingExistingOrUnrelatedState",
    "duplicateGeneratedTaskIdsFailBeforeInsertionAndPreserveAllExistingState",
)
BINARY_SUFFIXES = (".apk", ".aab", ".db", ".db-wal", ".db-shm")
EXCLUDED_SCOPE_PREFIXES = (
    "app/schemas",
    "app/src/main/java/com/edu/quickaside/data/local/QuickAsideDatabase.kt",
    "app/src/main/java/com/edu/quickaside/data/local/TaskDao.kt",
    "app/src/main/java/com/edu/quickaside/data/local/ActionLedgerDao.kt",
    "app/build.gradle.kts",
    "gradle/libs.versions.toml",
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle/wrapper/gradle-wrapper.properties",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/res/xml",
)


def git(*args: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["git", *args], cwd=ROOT, text=True, capture_output=True
    )


def changed_paths() -> tuple[list[str], list[str]]:
    tracked = git("diff", "--name-only", "HEAD", "--").stdout.splitlines()
    untracked = git(
        "ls-files", "--others", "--exclude-standard", "--"
    ).stdout.splitlines()
    return tracked, untracked


def main() -> None:
    READINESS.mkdir(parents=True, exist_ok=True)
    provenance_dir = READINESS / "provenance"
    subprocess.run(
        ["python3", str(PROVENANCE_TOOL), str(provenance_dir.relative_to(ROOT))],
        cwd=ROOT,
        check=True,
        stdout=subprocess.DEVNULL,
    )
    final_provenance = json.loads(
        (provenance_dir / "provenance.json").read_text(encoding="utf-8")
    )

    command_results = [
        git("diff", "--check"),
        git("status", "--short"),
        git("diff", "--stat"),
        git("diff", "--name-status"),
        git("ls-files", "--others", "--exclude-standard"),
        git("diff", "--cached", "--name-only"),
    ]
    diff_check, status, diff_stat, name_status, untracked_inventory, staged = (
        command_results
    )

    tracked, untracked = changed_paths()
    all_changed = sorted(set(tracked) | set(untracked))
    app_changed = sorted(
        path for path in all_changed if path.startswith("app/")
    )
    production_changed = [
        path for path in all_changed if path.startswith("app/src/main/")
    ]
    non_app_changed = [
        path for path in all_changed if not path.startswith("app/")
    ]
    allowed_doc_prefixes = (
        "docs/changes/029-captureplan-task-execution/",
        "docs/ACTIVE_WORK.md",
        "docs/ROADMAP.md",
    )
    out_of_scope_docs = [
        path
        for path in non_app_changed
        if not path.startswith(allowed_doc_prefixes)
    ]

    test_source_path = ROOT / TEST_SOURCE
    test_source_text = test_source_path.read_text(encoding="utf-8")
    test_source_sha = hashlib.sha256(test_source_path.read_bytes()).hexdigest()
    tests_defined = {
        name: bool(
            re.search(
                rf"@Test\n\s*fun {re.escape(name)}\(",
                test_source_text,
            )
        )
        for name in NEW_TESTS
    }

    original_verdict_path = (
        EVIDENCE
        / "device/room/task-executor/instrumentation/retry-1/test-verdict.json"
    )
    original_record_path = (
        EVIDENCE
        / "device/room/task-executor/instrumentation/retry-1/run-record.json"
    )
    original_verdict = json.loads(original_verdict_path.read_text(encoding="utf-8"))
    original_record = json.loads(original_record_path.read_text(encoding="utf-8"))

    remediation_verdict = json.loads(
        (INSTRUMENTATION / "test-verdict.json").read_text(encoding="utf-8")
    )
    remediation_record = json.loads(
        (INSTRUMENTATION / "run-record.json").read_text(encoding="utf-8")
    )
    raw_output = (INSTRUMENTATION / "instrumentation-output.txt").read_text(
        encoding="utf-8"
    )
    ok_match = re.search(
        r"^OK \((\d+) tests?\)$", raw_output, flags=re.MULTILINE
    )
    raw_count = int(ok_match.group(1)) if ok_match else None
    new_test_codes = {}
    for match in re.finditer(
        r"^INSTRUMENTATION_STATUS: test=(\S+)\nINSTRUMENTATION_STATUS_CODE: (-?\d+)$",
        raw_output,
        flags=re.MULTILINE,
    ):
        if match.group(1) in NEW_TESTS:
            new_test_codes[match.group(1)] = int(match.group(2))
    expected_count = original_verdict.get("tests_reported", 0) + len(NEW_TESTS)

    original_artifacts = [
        EVIDENCE / "device/room/task-executor",
        EVIDENCE / "device/room/reversible-task-regression",
        EVIDENCE / "jvm",
        EVIDENCE / "lint",
        EVIDENCE / "scope",
        EVIDENCE / "provenance",
    ]
    original_artifacts_untouched = {
        str(path.relative_to(ROOT)): not git(
            "diff", "--name-only", "HEAD", "--", str(path.relative_to(ROOT))
        ).stdout.strip()
        for path in original_artifacts
    }

    required_artifacts = [
        REMEDIATION / "README.md",
        REMEDIATION / "parse_room_verdict.py",
        TASK_EXECUTOR / "device-identity.txt",
        TASK_EXECUTOR / "apk-content-verification.txt",
        TASK_EXECUTOR / "compile-android-test-kotlin/run-record.json",
        TASK_EXECUTOR / "compile-android-test-kotlin/gradle-output.txt",
        TASK_EXECUTOR / "apk-build/run-record.json",
        TASK_EXECUTOR / "apk-build/apk-sha256-manifest.txt",
        TASK_EXECUTOR / "app-build/run-record.json",
        TASK_EXECUTOR / "app-build/app-apk-sha256.json",
        TASK_EXECUTOR / "install-app/run-record.json",
        TASK_EXECUTOR / "install-test-apk/run-record.json",
        INSTRUMENTATION / "instrumentation-output.txt",
        INSTRUMENTATION / "test-verdict.json",
        INSTRUMENTATION / "run-record.json",
        INSTRUMENTATION / "provenance-start/provenance.json",
        INSTRUMENTATION / "provenance-end/provenance.json",
    ]
    missing_artifacts = [
        str(path.relative_to(ROOT))
        for path in required_artifacts
        if not path.is_file()
    ]

    binaries = sorted(
        str(path.relative_to(ROOT))
        for path in EVIDENCE.rglob("*")
        if path.is_file() and path.name.lower().endswith(BINARY_SUFFIXES)
    )

    excluded_scope = git(
        "diff", "--name-only", "HEAD", "--", *EXCLUDED_SCOPE_PREFIXES
    ).stdout.splitlines()

    chg030_paths = sorted(
        str(path.relative_to(ROOT))
        for path in (ROOT / "docs/changes").glob("030*")
    )

    original_evidence_changes = sorted(
        path
        for path in tracked
        if path.startswith(
            "docs/changes/029-captureplan-task-execution/evidence/"
        )
    )
    allowed_original_evidence_changes = [
        "docs/changes/029-captureplan-task-execution/evidence/README.md"
    ]

    checks: list[dict] = []

    def check(name: str, passed: bool, evidence: str) -> None:
        checks.append({"name": name, "passed": bool(passed), "evidence": evidence})

    check(
        "branch and reviewed Round-1 HEAD",
        final_provenance["branch"] == BRANCH
        and final_provenance["base_head"] == REVIEWED_HEAD,
        f"branch={final_provenance['branch']}; HEAD={final_provenance['base_head']}",
    )
    check(
        "no staged changes",
        not staged.stdout.strip(),
        "git diff --cached --name-only is empty",
    )
    check("git diff --check", diff_check.returncode == 0, "exit=0")
    check(
        "production source unchanged",
        not production_changed,
        f"changed production paths={production_changed}",
    )
    check(
        "exactly the intended test source change",
        app_changed == [TEST_SOURCE],
        f"changed app paths={app_changed}",
    )
    check(
        "docs/evidence remediation only otherwise",
        not out_of_scope_docs,
        f"out-of-scope changed paths={out_of_scope_docs}",
    )
    check(
        "both requested MAJOR-1 test scenarios exist",
        all(tests_defined.values()),
        json.dumps(tests_defined),
    )
    check(
        "new focused Room artifact is repository-local",
        str(INSTRUMENTATION).startswith(str(EVIDENCE))
        and all(
            (INSTRUMENTATION / name).is_file()
            for name in (
                "instrumentation-output.txt",
                "test-verdict.json",
                "run-record.json",
            )
        ),
        str(INSTRUMENTATION.relative_to(ROOT)),
    )
    check(
        "actual discovered test count recorded and matches raw output",
        remediation_verdict["tests_reported"] == raw_count
        and remediation_record.get("test_count") == raw_count,
        f"verdict={remediation_verdict['tests_reported']}; "
        f"run-record={remediation_record.get('test_count')}; raw={raw_count}",
    )
    check(
        "exactly two remediation tests were added",
        remediation_verdict["tests_reported"] == expected_count,
        f"discovered={remediation_verdict['tests_reported']}; "
        f"original={original_verdict.get('tests_reported')}; expected={expected_count}",
    )
    check(
        "remediation gate PASS with zero failures",
        remediation_verdict["status"] == "PASS"
        and remediation_verdict["failures"] == 0
        and remediation_record.get("test_gate_status") == "PASS",
        json.dumps(
            {
                "status": remediation_verdict["status"],
                "failures": remediation_verdict["failures"],
                "run_record_status": remediation_record.get("test_gate_status"),
            }
        ),
    )
    check(
        "both new tests executed with final status code 0",
        all(new_test_codes.get(name) == 0 for name in NEW_TESTS),
        json.dumps(new_test_codes),
    )
    check(
        "no required artifact relies only on terminal prose",
        not missing_artifacts
        and (ROOT / remediation_record["log"]).is_file(),
        "missing artifacts=" + json.dumps(missing_artifacts),
    )
    check(
        "original Round-1 evidence not overwritten",
        original_evidence_changes == allowed_original_evidence_changes
        and all(original_artifacts_untouched.values())
        and original_verdict.get("tests_reported") == 15
        and original_record.get("test_count") == 15,
        json.dumps(
            {
                "single_allowed_evidence_doc_change": original_evidence_changes
                == allowed_original_evidence_changes,
                "historical_artifact_diffs": original_artifacts_untouched,
                "original_verdict_tests": original_verdict.get("tests_reported"),
                "original_run_record_tests": original_record.get("test_count"),
            }
        ),
    )
    check(
        "no APK/AAB/DB/WAL/SHM binaries retained in evidence",
        not binaries,
        "binary scan=" + json.dumps(binaries),
    )
    check(
        "schema/dependency/config surfaces untouched",
        not excluded_scope,
        f"changed excluded paths={excluded_scope}",
    )
    check(
        "CHG-030 not started",
        not chg030_paths
        and not [
            path for path in all_changed if "/030-" in path or path.endswith("/030")
        ],
        f"CHG-030 paths={chg030_paths}",
    )
    check(
        "historical gates remain historical, not rerun",
        remediation_record.get("base_head") == REVIEWED_HEAD,
        "remediation gates record the reviewed HEAD and their own artifacts",
    )

    ready = all(item["passed"] for item in checks)
    result = {
        "checked_at_utc": datetime.now(timezone.utc).isoformat(),
        "result": (
            "REMEDIATION READY FOR ROUND-2 INDEPENDENT REVIEW"
            if ready
            else "REMEDIATION NOT REVIEW-READY"
        ),
        "branch": final_provenance["branch"],
        "reviewed_round1_head": REVIEWED_HEAD,
        "head_at_readiness": final_provenance["base_head"],
        "gate_worktree_source_fingerprint_sha256": remediation_record.get(
            "start_worktree_source_fingerprint_sha256"
        ),
        "final_readiness_worktree_source_fingerprint_sha256": final_provenance[
            "worktree_source_fingerprint_sha256"
        ],
        "remediation_test_source": {
            "path": TEST_SOURCE,
            "sha256": test_source_sha,
            "new_tests": tests_defined,
        },
        "discovered_room_test_count": remediation_verdict["tests_reported"],
        "remediation_room_gate_status": remediation_verdict["status"],
        "historical_round1_room_test_count": original_verdict.get("tests_reported"),
        "checks": checks,
        "tracked_changes": tracked,
        "untracked_changes": untracked,
        "git_commands": [
            {
                "command": list(item.args),
                "exit_code": item.returncode,
                "stdout": item.stdout,
                "stderr": item.stderr,
            }
            for item in command_results
        ],
        "note": (
            "Previously accepted CHG-029 gates were not rerun: production source "
            "is unchanged from the published implementation and Round-1 evidence "
            "remains valid. The only newly required behavioral gate is the "
            "Round-1 remediation focused Room run."
        ),
    }
    (READINESS / "remediation-readiness.json").write_text(
        json.dumps(result, indent=2) + "\n", encoding="utf-8"
    )

    review_lines = [
        "CHG-029 Round-1 remediation readiness check",
        f"Timestamp UTC: {result['checked_at_utc']}",
        f"Branch: {result['branch']}",
        f"Reviewed Round-1 HEAD: {REVIEWED_HEAD}",
        f"Final worktree/source fingerprint: "
        f"{result['final_readiness_worktree_source_fingerprint_sha256']}",
        f"Remediation gate fingerprint: "
        f"{result['gate_worktree_source_fingerprint_sha256']}",
        f"Discovered Room test count: {result['discovered_room_test_count']}",
        f"Original Round-1 Room test count: {result['historical_round1_room_test_count']}",
        "",
        "Exact command outputs:",
    ]
    for item in command_results:
        review_lines.extend(
            [
                f"$ {' '.join(item.args)} (exit {item.returncode})",
                item.stdout.rstrip() or "(no output)",
                item.stderr.rstrip() if item.stderr.strip() else "",
                "",
            ]
        )
    review_lines.extend(["Checks:"])
    for item in checks:
        review_lines.append(
            f"- [{'PASS' if item['passed'] else 'FAIL'}] {item['name']} — "
            f"{item['evidence']}"
        )
    review_lines.extend(["", result["result"]])
    (READINESS / "git-scope-review.txt").write_text(
        "\n".join(review_lines) + "\n", encoding="utf-8"
    )

    print(
        json.dumps(
            {
                "result": result["result"],
                "passed_checks": sum(1 for item in checks if item["passed"]),
                "total_checks": len(checks),
                "discovered_tests": result["discovered_room_test_count"],
                "final_fingerprint": result[
                    "final_readiness_worktree_source_fingerprint_sha256"
                ],
            },
            indent=2,
        )
    )
    if not ready:
        for item in checks:
            if not item["passed"]:
                print(f"FAILED: {item['name']}: {item['evidence']}")
        raise SystemExit(1)


if __name__ == "__main__":
    main()
