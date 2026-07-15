#!/usr/bin/env python3
"""Validate public benchmark schemas without exposing locked answer rows."""

from __future__ import annotations

import csv
from pathlib import Path

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
    print("benchmark templates verified: 4 holdouts + results schema; no answers exposed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
