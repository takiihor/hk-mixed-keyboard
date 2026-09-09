#!/usr/bin/env python3
"""Report exact-key Jyutping ranking accuracy against a reviewed case set.

This measures the exact full-key lookup that the reviewed
``jyutping_overrides.csv`` layer drives (single characters and direct phrases),
ranked exactly like the Android ``buildJyutpingIndex``: frequency-descending,
de-duplicated by output text, overrides merged over the base snapshot.

It is a small, human-reviewed regression set — it reports Top-1/Top-3 hit
counts and never claims population-wide accuracy. The continuous-segmentation
path is exercised by the Android JVM tests, not here.
"""

from __future__ import annotations

import argparse
import csv
from dataclasses import dataclass
from pathlib import Path


DEFAULT_BASE = Path("android/app/src/main/assets/corpus/jyutping.csv")
DEFAULT_OVERRIDES = Path("android/app/src/main/assets/corpus/jyutping_overrides.csv")
DEFAULT_CASES = Path("corpus/benchmarks/jyutping_ranking_cases.csv")


@dataclass(frozen=True)
class CaseResult:
    key: str
    expected: str
    rank: int  # 0-based rank of expected, or -1 when absent
    category: str


@dataclass(frozen=True)
class BenchmarkReport:
    results: list[CaseResult]

    @property
    def total(self) -> int:
        return len(self.results)

    @property
    def top1(self) -> int:
        return sum(1 for r in self.results if r.rank == 0)

    @property
    def top3(self) -> int:
        return sum(1 for r in self.results if 0 <= r.rank < 3)

    @property
    def misses(self) -> list[CaseResult]:
        return [r for r in self.results if r.rank != 0]


def _read_rows(path: Path) -> list[tuple[str, str, float]]:
    if not path.is_file():
        return []
    rows: list[tuple[str, str, float]] = []
    with path.open("r", encoding="utf-8", newline="") as handle:
        lines = (line for line in handle if line.strip() and not line.lstrip().startswith("#"))
        reader = csv.reader(lines)
        header_skipped = False
        for cols in reader:
            if not header_skipped:
                header_skipped = True
                continue
            if len(cols) < 3:
                continue
            key, text = cols[0].strip().lower(), cols[1].strip()
            try:
                freq = float(cols[2])
            except ValueError:
                continue
            if key and text:
                rows.append((key, text, freq))
    return rows


def build_index(base: Path, overrides: Path) -> dict[str, list[str]]:
    """Group by key, sort by frequency desc, dedupe by text — like the app.

    Mirrors ``JyutpingOverrides.merge``: an override row replaces the snapshot row
    for the same (key, text) pair, so it can demote as well as promote.
    """
    override_rows = _read_rows(overrides)
    overridden = {(key, text) for key, text, _ in override_rows}
    merged = [row for row in _read_rows(base) if (row[0], row[1]) not in overridden]
    merged += override_rows
    grouped: dict[str, list[tuple[str, float]]] = {}
    for key, text, freq in merged:
        grouped.setdefault(key, []).append((text, freq))
    index: dict[str, list[str]] = {}
    for key, entries in grouped.items():
        ordered = sorted(entries, key=lambda item: -item[1])
        seen: set[str] = set()
        ranked: list[str] = []
        for text, _ in ordered:
            if text not in seen:
                seen.add(text)
                ranked.append(text)
        index[key] = ranked
    return index


def read_cases(path: Path) -> list[tuple[str, str, str]]:
    cases: list[tuple[str, str, str]] = []
    with path.open("r", encoding="utf-8", newline="") as handle:
        lines = (line for line in handle if line.strip() and not line.lstrip().startswith("#"))
        reader = csv.reader(lines)
        header_skipped = False
        for cols in reader:
            if not header_skipped:
                header_skipped = True
                continue
            if len(cols) < 2:
                continue
            key, expected = cols[0].strip().lower(), cols[1].strip()
            category = cols[2].strip() if len(cols) > 2 else ""
            cases.append((key, expected, category))
    return cases


def evaluate(base: Path, overrides: Path, cases_path: Path) -> BenchmarkReport:
    index = build_index(base, overrides)
    results: list[CaseResult] = []
    for key, expected, category in read_cases(cases_path):
        ranked = index.get(key, [])
        rank = ranked.index(expected) if expected in ranked else -1
        results.append(CaseResult(key, expected, rank, category))
    return BenchmarkReport(results)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path, default=DEFAULT_BASE)
    parser.add_argument("--overrides", type=Path, default=DEFAULT_OVERRIDES)
    parser.add_argument("--cases", type=Path, default=DEFAULT_CASES)
    parser.add_argument(
        "--require-top1",
        type=float,
        default=None,
        help="Exit non-zero if Top-1 accuracy falls below this fraction (0-1).",
    )
    args = parser.parse_args()
    report = evaluate(args.base, args.overrides, args.cases)
    if report.total == 0:
        print("No benchmark cases found.")
        return 1
    top1_acc = report.top1 / report.total
    print(f"Reviewed Jyutping exact-key benchmark: {report.total} cases")
    print(f"  Top-1: {report.top1}/{report.total} ({top1_acc:.2%})")
    print(f"  Top-3: {report.top3}/{report.total} ({report.top3 / report.total:.2%})")
    for miss in report.misses:
        where = "absent" if miss.rank < 0 else f"rank {miss.rank + 1}"
        print(f"  MISS [{miss.category}] {miss.key} -> {miss.expected} ({where})")
    if args.require_top1 is not None and top1_acc < args.require_top1:
        print(f"FAIL: Top-1 {top1_acc:.2%} below required {args.require_top1:.2%}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
