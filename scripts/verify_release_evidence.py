#!/usr/bin/env python3
"""Fail closed unless every external release record matches one candidate AAB."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any


REQUIRED_RECORDS = (
    ("native_review", "reviewer"),
    ("device_beta_matrix", "owner"),
    ("accessibility_review", "reviewer"),
    ("legal_signoff", "owner"),
    ("signed_aab", "owner"),
    ("beta_result", "owner"),
)
COMMIT_RE = re.compile(r"^[0-9a-f]{40}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_record(path: Path, record_name: str) -> dict[str, Any]:
    if not path.is_file():
        raise ValueError(f"missing external gate: {record_name}")
    try:
        record = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ValueError(f"{record_name} is not valid JSON") from error
    if not isinstance(record, dict):
        raise ValueError(f"{record_name} must be a JSON object")
    return record


def require_nonempty_text(record: dict[str, Any], field: str, record_name: str) -> str:
    value = record.get(field)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{record_name} requires {field}")
    return value


def verify_evidence(evidence_dir: Path, commit: str, aab: Path) -> str:
    if not COMMIT_RE.fullmatch(commit):
        raise ValueError("candidate commit must be a full 40-character hash")
    if not aab.is_file():
        raise ValueError(f"candidate AAB does not exist: {aab}")
    aab_sha256 = sha256(aab)
    for record_name, actor_field in REQUIRED_RECORDS:
        record = load_record(evidence_dir / f"{record_name}.json", record_name)
        if record.get("status") != "APPROVED":
            raise ValueError(f"{record_name} status must be APPROVED")
        require_nonempty_text(record, actor_field, record_name)
        date = require_nonempty_text(record, "date", record_name)
        try:
            dt.date.fromisoformat(date)
        except ValueError as error:
            raise ValueError(f"{record_name} date must be ISO-8601") from error
        if record.get("commit") != commit:
            raise ValueError(f"{record_name} commit does not match candidate")
        if record.get("aab_sha256") != aab_sha256:
            raise ValueError(f"{record_name} AAB hash does not match candidate")
    return aab_sha256


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--aab", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        aab_sha256 = verify_evidence(args.evidence_dir, args.commit, args.aab)
    except (OSError, ValueError) as error:
        print(f"release evidence verification failed: {error}", file=sys.stderr)
        return 1
    print(f"release evidence verified: commit={args.commit} aab_sha256={aab_sha256}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
