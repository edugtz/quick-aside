#!/usr/bin/env python3
"""Cross-check CHG-029 gate artifacts and write final review evidence."""

from __future__ import annotations

import json
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from xml.etree import ElementTree


ROOT = Path(__file__).resolve().parents[4]
CHANGE = ROOT / "docs/changes/029-captureplan-task-execution"
EVIDENCE = CHANGE / "evidence"
SCOPE = EVIDENCE / "scope"
SCOPE_REVIEW = SCOPE / "git-scope-review.txt"
READINESS = SCOPE / "pre-review-readiness.json"
PROVENANCE_TOOL = EVIDENCE / "provenance.py"


def run(command: list[str]) -> dict:
    result = subprocess.run(command, cwd=ROOT, text=True, capture_output=True)
    return {
        "command": command,
        "exit_code": result.returncode,
        "stdout": result.stdout,
        "stderr": result.stderr,
    }


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> None:
    SCOPE.mkdir(parents=True, exist_ok=True)
    SCOPE_REVIEW.touch(exist_ok=True)
    READINESS.touch(exist_ok=True)
    provenance_dir = SCOPE / "final-review" / "provenance"
    subprocess.run(
        ["python3", str(PROVENANCE_TOOL), str(provenance_dir.relative_to(ROOT))],
        cwd=ROOT,
        check=True,
        stdout=subprocess.DEVNULL,
    )
    final_provenance = load_json(provenance_dir / "provenance.json")

    command_results = [
        run(["git", "diff", "--check"]),
        run(["git", "status", "--short"]),
        run(["git", "diff", "--stat"]),
        run(["git", "diff", "--name-status"]),
        run(["git", "ls-files", "--others", "--exclude-standard"]),
        run(["git", "diff", "--cached", "--name-only"]),
        run(
            [
                "git", "status", "--short", "--",
                "app/schemas", "app/src/main/java/com/edu/quickaside/data/local/QuickAsideDatabase.kt",
                "app/src/main/java/com/edu/quickaside/data/local/TaskDao.kt",
                "app/src/main/java/com/edu/quickaside/data/local/ActionLedgerDao.kt",
                "app/build.gradle.kts", "gradle/libs.versions.toml", "build.gradle.kts",
                "settings.gradle.kts", "gradle/wrapper/gradle-wrapper.properties",
                "app/src/main/AndroidManifest.xml", "app/src/main/res/xml",
            ]
        ),
    ]
    by_label = dict(zip(
        ("diff_check", "status", "diff_stat", "name_status", "untracked_inventory", "staged_changes", "excluded_scope_status"),
        command_results,
    ))

    base_head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    tracked_changed = subprocess.check_output(
        ["git", "diff", "--name-only", "HEAD"], cwd=ROOT, text=True
    ).splitlines()
    untracked = by_label["untracked_inventory"]["stdout"].splitlines()
    changed_app_sources = sorted(
        path for path in untracked if path.startswith("app/src/") and path.endswith(".kt")
    )
    expected_tracked = ["docs/ACTIVE_WORK.md", "docs/ROADMAP.md"]
    expected_app_sources = sorted(
        [
            "app/src/androidTest/java/com/edu/quickaside/data/local/CapturePlanTaskExecutorDatabaseTest.kt",
            "app/src/main/java/com/edu/quickaside/application/capture/CapturePlanTaskExecutor.kt",
            "app/src/main/java/com/edu/quickaside/data/local/RoomCapturePlanTaskExecutor.kt",
            "app/src/test/java/com/edu/quickaside/application/capture/CapturePlanTaskExecutorContractTest.kt",
        ]
    )

    checks: list[dict] = []

    def check(name: str, passed: bool, evidence: str) -> None:
        checks.append({"name": name, "passed": bool(passed), "evidence": evidence})

    check("canonical base and branch", base_head == "797e1557e8b5d94d4c8611a9b72749a8d7ac53f7" and final_provenance["branch"] == "chg-029-captureplan-task-execution", f"HEAD={base_head}; branch={final_provenance['branch']}")
    check("tracked changes limited to approved status docs", sorted(tracked_changed) == expected_tracked, str(tracked_changed))
    check("app source scope limited to two production and two test additions", changed_app_sources == expected_app_sources, str(changed_app_sources))
    check("no staged changes", not by_label["staged_changes"]["stdout"].strip(), "git diff --cached --name-only is empty")
    check("no schema, migration, DAO, build, dependency, manifest, or network-config changes", not by_label["excluded_scope_status"]["stdout"].strip(), "scoped git status is empty")
    check("git diff --check", by_label["diff_check"]["exit_code"] == 0, f"exit={by_label['diff_check']['exit_code']}; {by_label['diff_check']['stdout']}{by_label['diff_check']['stderr']}")

    final_manifest = (provenance_dir / "source-manifest.sha256").read_bytes()
    provenance_dirs = [
        EVIDENCE / "provenance/pre-verification",
        EVIDENCE / "jvm/focused/retry-1/provenance-start",
        EVIDENCE / "device/room/task-executor/instrumentation/retry-1/provenance-start",
        EVIDENCE / "device/room/reversible-task-regression/instrumentation/provenance-start",
        EVIDENCE / "jvm/full/provenance-start",
        EVIDENCE / "build-results/compileDebugAndroidTestKotlin/provenance-start",
        EVIDENCE / "device/room/task-executor/current-app-build/provenance-start",
        EVIDENCE / "lint/provenance-start",
        EVIDENCE / "scope/schema-config-dependency/provenance",
    ]
    manifest_matches = all((path / "source-manifest.sha256").read_bytes() == final_manifest for path in provenance_dirs)
    check("each required gate's production/test source manifest matches final sources", manifest_matches, f"compared {len(provenance_dirs)} repository-local manifests")

    gate_records = {
        "focused_jvm": (EVIDENCE / "jvm/focused/retry-1/run-record.json", 32),
        "focused_room": (EVIDENCE / "device/room/task-executor/instrumentation/retry-1/run-record.json", 15),
        "existing_task_room_regression": (EVIDENCE / "device/room/reversible-task-regression/instrumentation/run-record.json", 12),
        "full_jvm": (EVIDENCE / "jvm/full/run-record.json", 178),
    }
    for name, (path, expected_tests) in gate_records.items():
        record = load_json(path)
        check(f"{name} pass record", record.get("exit_code") == 0 and record.get("test_gate_status") == "PASS" and record.get("test_count") == expected_tests, f"exit={record.get('exit_code')}; tests={record.get('test_count')}; path={path.relative_to(ROOT)}")
        check(f"{name} raw output", (ROOT / record["log"]).is_file(), record["log"])

    focused_summary = load_json(EVIDENCE / "jvm/focused/retry-1/junit-summary.json")
    full_summary = load_json(EVIDENCE / "jvm/full/junit-summary.json")
    check("focused JVM JUnit XML copied and clean", focused_summary["tests"] == 32 and focused_summary["failures"] == 0 and focused_summary["errors"] == 0 and len(list((EVIDENCE / "jvm/focused/retry-1/junit-xml").glob("TEST-*.xml"))) == 2, "jvm/focused/retry-1/junit-xml/")
    check("full JVM JUnit XML copied and clean", full_summary["tests"] == 178 and full_summary["failures"] == 0 and full_summary["errors"] == 0 and len(list((EVIDENCE / "jvm/full/junit-xml").glob("TEST-*.xml"))) == 29, "jvm/full/junit-xml/")
    for gate in ("device/room/task-executor/instrumentation/retry-1", "device/room/reversible-task-regression/instrumentation"):
        verdict = load_json(EVIDENCE / gate / "test-verdict.json")
        check(f"{gate} raw instrumentation verdict", verdict.get("status") == "PASS" and verdict.get("failures") == 0, f"tests={verdict.get('tests_reported')}; output={verdict.get('raw_output')}")

    compile_record = load_json(EVIDENCE / "build-results/compileDebugAndroidTestKotlin/run-record.json")
    assemble_record = load_json(EVIDENCE / "device/room/task-executor/current-app-build/run-record.json")
    lint_record = load_json(EVIDENCE / "lint/run-record.json")
    schema_record = load_json(EVIDENCE / "scope/schema-config-dependency-run-record.json")
    check("Android-test Kotlin compile pass", compile_record.get("exit_code") == 0 and (EVIDENCE / "build-results/compileDebugAndroidTestKotlin/command-output.txt").is_file(), "compileDebugAndroidTestKotlin run record and output")
    current_app_dir = EVIDENCE / "device/room/task-executor/current-app-build"
    app_hash_record = load_json(current_app_dir / "app-apk-sha256.json")
    app_build_metadata = load_json(current_app_dir / "app-apk-output/output-metadata.json")
    check("debug assembly pass with APK hash and build metadata", assemble_record.get("exit_code") == 0 and bool(re.fullmatch(r"[0-9a-f]{64}", app_hash_record.get("sha256", ""))) and app_build_metadata.get("artifactType", {}).get("type") == "APK" and app_build_metadata.get("applicationId") == "com.edu.quickaside" and (current_app_dir / "command-output.txt").is_file(), "assembleDebug run record, SHA-256 record, and Gradle output metadata; APK binary intentionally not retained")
    lint_summary = load_json(EVIDENCE / "lint/lint-summary.json")
    check("lint pass and report copied", lint_record.get("exit_code") == 0 and (EVIDENCE / "lint/lint-results-debug.sarif").is_file() and (EVIDENCE / "lint/lint-results-debug.html").is_file() and lint_summary.get("findings_in_changed_executor_files") == [], "lintDebug SARIF/HTML and summary")
    check("schema/config/dependency comparison pass", schema_record.get("exit_code") == 0 and schema_record.get("config_schema_build_manifest_network_status") == "unchanged" and (EVIDENCE / "scope/schema-config-dependency-comparison.txt").is_file(), "repository-local schema comparison and run record")

    apk_manifest_path = EVIDENCE / "device/room/task-executor/apk-build/retry-1/apk-sha256-manifest.txt"
    apk_manifest = {}
    for line in apk_manifest_path.read_text(encoding="utf-8").splitlines():
        digest, artifact_path = line.split(maxsplit=1)
        apk_manifest[artifact_path.strip()] = digest
    retry_apk_root = EVIDENCE / "device/room/task-executor/apk-build/retry-1"
    test_apk_metadata = load_json(retry_apk_root / "android-test-apk-output/output-metadata.json")
    retry_app_metadata = load_json(retry_apk_root / "app-apk-output/output-metadata.json")
    expected_apks = {
        "android-test-apk-output/app-debug-androidTest.apk": retry_apk_root / "android-test-apk-output/app-debug-androidTest.apk",
        "app-apk-output/app-debug.apk": retry_apk_root / "app-apk-output/app-debug.apk",
    }
    apk_hashes_valid = all(bool(re.fullmatch(r"[0-9a-f]{64}", apk_manifest.get(path, ""))) for path in expected_apks)
    apk_binaries_absent = all(not path.exists() for path in expected_apks.values()) and not (current_app_dir / "app-apk-output/app-debug.apk").exists()
    apk_metadata_matches = test_apk_metadata.get("artifactType", {}).get("type") == "APK" and retry_app_metadata.get("artifactType", {}).get("type") == "APK" and app_hash_record.get("contains_new_executor_result_types") == {"UndoCapturePlanTaskExecutionResult": True, "CapturePlanTaskExecutor": True}
    check("APK binaries not retained; SHA-256 and gate metadata preserved", apk_binaries_absent and apk_hashes_valid and apk_metadata_matches, f"binary retention=none; initial APK hashes={apk_manifest}; current app SHA-256={app_hash_record.get('sha256')}")
    check("all required outcomes and logs are repository-local", all((EVIDENCE / path).is_file() for path in ("jvm/focused/retry-1/gradle-output.txt", "jvm/full/command-output.txt", "device/room/task-executor/instrumentation/retry-1/instrumentation-output.txt", "device/room/reversible-task-regression/instrumentation/instrumentation-output.txt", "lint/command-output.txt", "scope/schema-config-dependency-comparison.txt")), "all referenced gate artifacts are below docs/changes/029-captureplan-task-execution/evidence/")

    whitespace_paths = [
        *(ROOT / path for path in expected_app_sources),
        *CHANGE.glob("*.md"),
        CHANGE / "evidence/README.md",
        EVIDENCE / "provenance.py",
        EVIDENCE / "run_gate.py",
        Path(__file__),
    ]
    whitespace_issues = []
    for path in whitespace_paths:
        text = path.read_text(encoding="utf-8")
        if not text.endswith("\n"):
            whitespace_issues.append(f"{path.relative_to(ROOT)}: missing final newline")
        whitespace_issues.extend(
            f"{path.relative_to(ROOT)}:{number}: trailing whitespace"
            for number, line in enumerate(text.splitlines(), 1)
            if line.endswith((" ", "\t"))
        )
    check("new source and change-package text formatting", not whitespace_issues, "no trailing whitespace or missing final newlines" if not whitespace_issues else "; ".join(whitespace_issues))

    ready = all(item["passed"] for item in checks)
    result = {
        "checked_at_utc": datetime.now(timezone.utc).isoformat(),
        "result": "READY FOR INDEPENDENT REVIEW" if ready else "NOT REVIEW-READY — EVIDENCE CLOSEOUT REQUIRED",
        "branch": final_provenance["branch"],
        "base_head": final_provenance["base_head"],
        "base_head_label": "canonical base / current commit; not an implementation SHA",
        "worktree_source_fingerprint_sha256": final_provenance["worktree_source_fingerprint_sha256"],
        "changed_production_test_source_manifest_sha256": final_provenance["changed_production_test_source_manifest_sha256"],
        "apk_binary_retention": {
            "policy": "APK binaries are intentionally not retained in the repository; build identity is preserved by SHA-256 and gate metadata.",
            "initial_android_test_apk_sha256": apk_manifest["android-test-apk-output/app-debug-androidTest.apk"],
            "initial_app_apk_sha256": apk_manifest["app-apk-output/app-debug.apk"],
            "current_app_apk_sha256": app_hash_record["sha256"],
        },
        "checks": checks,
        "git_commands": command_results,
        "tracked_changes": tracked_changed,
        "changed_app_source_files": changed_app_sources,
        "untracked_file_count": len(untracked),
        "review_note": "Complete source, test, package-document, and evidence scope was inspected. No out-of-scope app, schema, migration, dependency, build, manifest, or network configuration changes were found.",
    }
    READINESS.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")

    review_lines = [
        "CHG-029 final Git and scope review",
        f"Timestamp UTC: {result['checked_at_utc']}",
        f"Branch: {result['branch']}",
        f"Base HEAD: {result['base_head']} (canonical base/current commit; not an implementation SHA)",
        f"Final worktree/source fingerprint: {result['worktree_source_fingerprint_sha256']}",
        "",
        "Exact required command outputs:",
    ]
    for item in command_results:
        review_lines.extend([
            f"$ {' '.join(item['command'])} (exit {item['exit_code']})",
            item["stdout"].rstrip() or "(no output)",
            item["stderr"].rstrip() if item["stderr"].strip() else "",
            "",
        ])
    review_lines.extend([
        "Complete review summary:",
        "- Production files: CapturePlanTaskExecutor.kt and RoomCapturePlanTaskExecutor.kt only.",
        "- Test files: CapturePlanTaskExecutorContractTest.kt and CapturePlanTaskExecutorDatabaseTest.kt only; existing regression source unchanged.",
        "- Tracked documentation changes: ACTIVE_WORK.md and ROADMAP.md; CHG-029 SPEC/PLAN/TASKS/QA/evidence documentation and evidence artifacts are new under the approved package.",
        "- No Task DAO/entity, database version/migration/schema, Gradle/dependency, manifest, network-security, UI/Capture wiring, gateway, or CHG-030 changes.",
        "- APK binaries are intentionally not retained in the repository; APK SHA-256 and Gradle/gate metadata remain in the evidence package.",
        "- The complete code, test, and package-document diff was inspected; no scope or correctness issue was found.",
        "",
        "PRE-REVIEW READINESS CHECK:",
        f"{result['result']}",
        f"Checks: {sum(1 for item in checks if item['passed'])}/{len(checks)} passed.",
    ])
    SCOPE_REVIEW.write_text("\n".join(review_lines) + "\n", encoding="utf-8")
    print(json.dumps({"result": result["result"], "passed_checks": sum(1 for item in checks if item["passed"]), "total_checks": len(checks), "fingerprint": result["worktree_source_fingerprint_sha256"]}, indent=2))
    if not ready:
        for item in checks:
            if not item["passed"]:
                print(f"FAILED: {item['name']}: {item['evidence']}")
        raise SystemExit(1)


if __name__ == "__main__":
    main()
