#!/usr/bin/env python3
"""Run Maven tests once for each distinct pending Java/dashboard source snapshot."""

from __future__ import annotations

import hashlib
import os
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE_PREFIXES = (
    "src/main/java/",
    "src/test/java/",
    "src/main/resources/static/",
)
SOURCE_FILES = {"pom.xml"}


def changed_paths() -> list[str]:
    result = subprocess.run(
        ["git", "-C", str(ROOT), "status", "--porcelain=v1", "-z", "--untracked-files=all"],
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(detail or f"git status exited with status {result.returncode}")

    paths: list[str] = []
    for record in result.stdout.split(b"\0"):
        if not record:
            continue
        if len(record) < 4:
            raise RuntimeError("Could not parse git status output.")
        path = os.fsdecode(record[3:])
        if path in SOURCE_FILES or path.startswith(SOURCE_PREFIXES):
            paths.append(path)
    return sorted(set(paths))


def source_fingerprint(paths: list[str]) -> str:
    digest = hashlib.sha256()
    for relative_path in paths:
        digest.update(relative_path.encode("utf-8", errors="surrogateescape"))
        digest.update(b"\0")
        path = ROOT / relative_path
        if path.is_file():
            digest.update(path.read_bytes())
        else:
            digest.update(b"<deleted>")
        digest.update(b"\0")
    return digest.hexdigest()


def state_file() -> Path:
    root_hash = hashlib.sha256(str(ROOT).encode()).hexdigest()[:16]
    directory = Path(tempfile.gettempdir()) / "copilot-sdlc-hooks"
    directory.mkdir(parents=True, exist_ok=True)
    return directory / f"{root_hash}-post-tool-use.sha256"


def main() -> int:
    try:
        paths = changed_paths()
        if not paths:
            return 0
        fingerprint = source_fingerprint(paths)
        marker = state_file()
        if marker.is_file() and marker.read_text(encoding="ascii").strip() == fingerprint:
            return 0
        marker.write_text(fingerprint + "\n", encoding="ascii")
    except (OSError, RuntimeError) as error:
        print(f"PostToolUse test hook could not inspect source changes: {error}", file=sys.stderr)
        return 1

    print("PostToolUse: Java/dashboard source changed; running `mvn --batch-mode test`.")
    result = subprocess.run(
        ["mvn", "--batch-mode", "test"],
        cwd=ROOT,
        check=False,
        text=True,
    )
    if result.returncode != 0:
        print(
            f"PostToolUse: Maven tests failed with exit status {result.returncode}. "
            "Review the test output before continuing.",
            file=sys.stderr,
        )
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
