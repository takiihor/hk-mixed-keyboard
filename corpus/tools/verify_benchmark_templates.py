#!/usr/bin/env python3
"""Validate public benchmark schemas without exposing locked answer rows."""

from __future__ import annotations

import csv
import re
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
BENCHMARKS = ROOT / "corpus/benchmarks"
CASE_FIELDS = [
    "case_id", "mode", "category", "frequency_band", "phrase_length",
    "hkscs_status", "input", "context", "acceptable_outputs", "split",
    "provenance", "reviewer_ids", "notes",
]
RESULT_FIELDS = [
    "case_id", "candidates", "committed_text", "keystrokes", "corrections",
    "wrong_auto_commits", "elapsed_ms", "keyboard_id", "keyboard_version",
    "device_id", "run_id",
]
COMPARATIVE_CLASSIFICATIONS = {"native", "competitor_comparative"}
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
COMMIT_RE = re.compile(r"^[0-9a-f]{40}$")


def verify_template(path: Path, expected_fields: list[str], require_header_only: bool) -> None:
    with path.open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if reader.fieldnames != expected_fields:
            raise ValueError(f"{path}: schema changed: {reader.fieldnames!r}")
        rows = list(reader)
    if require_header_only and rows:
        raise ValueError(
            f"{path}: public holdout must remain header-only; keep answer rows access-controlled"
        )


def validate_comparative_evidence(
    evidence: dict[str, Any], case_count: int
) -> str:
    """Return a safe evidence classification or reject an unsupported claim."""
    classification = str(evidence.get("classification", "open")).lower()
    if classification == "open":
        return "OPEN"
    if classification == "source_coverage":
        return "SOURCE_COVERAGE"
    if classification not in COMPARATIVE_CLASSIFICATIONS:
        raise ValueError(f"unsupported evidence classification: {classification}")
    if case_count < 1:
        raise ValueError("comparative evidence requires at least one locked case")

    required_text = (
        "locked_prompt_sha256",
        "test_lead",
        "device",
        "settings",
        "input_state",
        "candidate_commit",
        "result_commit",
        "candidate_aab_sha256",
        "result_aab_sha256",
        "raw_result_provenance",
    )
    for field in required_text:
        if not isinstance(evidence.get(field), str) or not evidence[field].strip():
            raise ValueError(f"comparative evidence requires {field}")

    reviewers = evidence.get("reviewer_ids")
    if not isinstance(reviewers, list) or not all(
        isinstance(reviewer, str) and reviewer.strip() for reviewer in reviewers
    ):
        raise ValueError("comparative evidence requires reviewer_ids")
    if any(reviewer.strip().lower() in {"self", "author", "project-team"} for reviewer in reviewers):
        raise ValueError("self-labelled native or competitor evidence is not independent")
    if len(reviewers) < 3:
        raise ValueError("comparative evidence requires at least three reviewer_ids")
    if not SHA256_RE.fullmatch(evidence["locked_prompt_sha256"]):
        raise ValueError("locked_prompt_sha256 must be a SHA-256 hash")
    if not COMMIT_RE.fullmatch(evidence["candidate_commit"]):
        raise ValueError("candidate_commit must be a full commit hash")
    if evidence["candidate_commit"] != evidence["result_commit"]:
        raise ValueError("candidate and result commit hashes do not match")
    if not SHA256_RE.fullmatch(evidence["candidate_aab_sha256"]):
        raise ValueError("candidate_aab_sha256 must be a SHA-256 hash")
    if evidence["candidate_aab_sha256"] != evidence["result_aab_sha256"]:
        raise ValueError("candidate and result AAB hashes do not match")
    return classification.upper()


def main() -> int:
    holdouts = sorted(BENCHMARKS.glob("*_holdout.tsv"))
    if len(holdouts) != 4:
        raise SystemExit(f"expected four public holdout templates; found {len(holdouts)}")
    try:
        for path in holdouts:
            verify_template(path, CASE_FIELDS, require_header_only=True)
        verify_template(
            BENCHMARKS / "results_template.tsv",
            RESULT_FIELDS,
            require_header_only=True,
        )
    except (OSError, ValueError) as error:
        raise SystemExit(f"benchmark template verification failed: {error}") from error
    print(
        "benchmark templates verified: 4 holdouts + results schema; "
        "no answers exposed; evidence status OPEN"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
