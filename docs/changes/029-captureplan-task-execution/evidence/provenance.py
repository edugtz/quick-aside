#!/usr/bin/env python3
"""Write reproducible provenance for a CHG-029 worktree snapshot."""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[4]


def git(*args: str) -> bytes:
    return subprocess.run(
        ["git", *args], cwd=ROOT, check=True, stdout=subprocess.PIPE
    ).stdout


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("output_dir", type=Path)
    args = parser.parse_args()
    output_dir = args.output_dir if args.output_dir.is_absolute() else ROOT / args.output_dir
    output_dir.mkdir(parents=True, exist_ok=True)

    tracked_diff = git("diff", "--binary", "HEAD", "--")
    changed_tracked = git("diff", "--name-only", "--diff-filter=ACMRT", "HEAD", "--")
    untracked = git("ls-files", "--others", "--exclude-standard", "--")
    candidates = set(changed_tracked.decode().splitlines())
    candidates.update(untracked.decode().splitlines())
    source_paths = sorted(
        path
        for path in candidates
        if path.startswith(("app/src/main/", "app/src/test/", "app/src/androidTest/"))
        and (ROOT / path).is_file()
    )
    manifest = "".join(
        f"{hashlib.sha256((ROOT / path).read_bytes()).hexdigest()}  {path}\n"
        for path in source_paths
    )
    manifest_bytes = manifest.encode()
    diff_hash = hashlib.sha256(tracked_diff).hexdigest()
    manifest_hash = hashlib.sha256(manifest_bytes).hexdigest()
    fingerprint_hash = hashlib.sha256(
        tracked_diff + b"\0CHG-029-SOURCE-MANIFEST\0" + manifest_bytes
    ).hexdigest()

    manifest_path = output_dir / "source-manifest.sha256"
    manifest_path.write_bytes(manifest_bytes)
    status = git("status", "--short").decode()
    record = {
        "captured_at_utc": datetime.now(timezone.utc).isoformat(),
        "branch": git("branch", "--show-current").decode().strip(),
        "base_head": git("rev-parse", "HEAD").decode().strip(),
        "base_head_label": "canonical base / current commit; not an implementation SHA",
        "git_status_short": status.splitlines(),
        "tracked_git_diff_sha256": diff_hash,
        "changed_production_test_source_manifest": str(manifest_path.relative_to(ROOT)),
        "changed_production_test_source_manifest_sha256": manifest_hash,
        "worktree_source_fingerprint_sha256": fingerprint_hash,
        "changed_production_test_source_files": source_paths,
    }
    (output_dir / "provenance.json").write_text(
        json.dumps(record, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(record, indent=2))


if __name__ == "__main__":
    main()
