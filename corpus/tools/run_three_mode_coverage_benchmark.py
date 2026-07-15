#!/usr/bin/env python3
"""Run the deterministic, non-human-review three-mode coverage benchmark."""

from __future__ import annotations

import argparse
import csv
from dataclasses import dataclass
from pathlib import Path

import build_three_mode_coverage_cases as builder


DEFAULT_CASES = Path("corpus/benchmarks/three_mode_source_coverage_cases.csv")


@dataclass(frozen=True)
class Case:
    scheme: str
    input: str
    expected: str
    review_status: str


@dataclass(frozen=True)
class CaseResult:
    case: Case
    rank: int


@dataclass(frozen=True)
class BenchmarkReport:
    results: list[CaseResult]

    @property
    def total(self) -> int:
        return len(self.results)

    @property
    def top1(self) -> int:
        return sum(result.rank == 0 for result in self.results)

    @property
    def top3(self) -> int:
        return sum(0 <= result.rank < 3 for result in self.results)

    @property
    def misses(self) -> list[CaseResult]:
        return [result for result in self.results if result.rank != 0]


def read_cases(path: Path) -> list[Case]:
    with path.open("r", encoding="utf-8", newline="") as handle:
        lines = (line for line in handle if line.strip() and not line.lstrip().startswith("#"))
        reader = csv.DictReader(lines)
        cases = [
            Case(
                row.get("scheme", "").strip().lower(),
                row.get("input", "").strip().lower(),
                row.get("expected", "").strip(),
                row.get("review_status", "").strip(),
            )
            for row in reader
        ]
    if any(not all((case.scheme, case.input, case.expected, case.review_status)) for case in cases):
        raise ValueError("benchmark case has an empty required field")
    return cases


def evaluate(
    cases: list[Case] | None = None,
    *,
    chars: Path = builder.DEFAULT_CHARS,
    phrases: Path = builder.DEFAULT_PHRASES,
    jyutping: Path = builder.DEFAULT_JYUTPING,
    overrides: Path = builder.DEFAULT_OVERRIDES,
    hkscs: Path = builder.DEFAULT_HKSCS,
    pinyin: Path = builder.DEFAULT_PINYIN,
) -> BenchmarkReport:
    indexes = {
        "quick": builder.quick_index(chars, phrases, hkscs),
        "jyutping": builder.jyutping_index(jyutping, overrides, hkscs),
        "pinyin": builder.pinyin_index(pinyin),
    }
    evaluated: list[CaseResult] = []
    for case in cases or read_cases(DEFAULT_CASES):
        ranked = indexes.get(case.scheme, {}).get(case.input, [])
        rank = ranked.index(case.expected) if case.expected in ranked else -1
        evaluated.append(CaseResult(case, rank))
    return BenchmarkReport(evaluated)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cases", type=Path, default=DEFAULT_CASES)
    parser.add_argument("--chars", type=Path, default=builder.DEFAULT_CHARS)
    parser.add_argument("--phrases", type=Path, default=builder.DEFAULT_PHRASES)
    parser.add_argument("--jyutping", type=Path, default=builder.DEFAULT_JYUTPING)
    parser.add_argument("--overrides", type=Path, default=builder.DEFAULT_OVERRIDES)
    parser.add_argument("--hkscs", type=Path, default=builder.DEFAULT_HKSCS)
    parser.add_argument("--pinyin", type=Path, default=builder.DEFAULT_PINYIN)
    args = parser.parse_args()
    cases = read_cases(args.cases)
    report = evaluate(
        cases,
        chars=args.chars,
        phrases=args.phrases,
        jyutping=args.jyutping,
        overrides=args.overrides,
        hkscs=args.hkscs,
        pinyin=args.pinyin,
    )
    if report.total == 0:
        print("No three-mode coverage cases found.")
        return 1
    print(f"Automated source-derived three-mode coverage: {report.total} cases (not human review)")
    print(f"  Top-1: {report.top1}/{report.total} ({report.top1 / report.total:.2%})")
    print(f"  Top-3: {report.top3}/{report.total} ({report.top3 / report.total:.2%})")
    for miss in report.misses:
        location = "absent" if miss.rank < 0 else f"rank {miss.rank + 1}"
        print(f"  MISS [{miss.case.scheme}] {miss.case.input} -> {miss.case.expected} ({location})")
    return 0 if not report.misses else 1


if __name__ == "__main__":
    raise SystemExit(main())
