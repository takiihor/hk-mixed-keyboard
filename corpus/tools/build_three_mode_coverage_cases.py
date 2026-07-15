#!/usr/bin/env python3
"""Generate a deterministic, source-derived three-mode coverage benchmark.

This is intentionally an automated corpus-consistency benchmark, not a human
language-quality benchmark. Its cases are labelled accordingly so release
evidence cannot mistake them for native-speaker review.
"""

from __future__ import annotations

import argparse
import csv
from dataclasses import dataclass
from pathlib import Path


DEFAULT_CHARS = Path("android/app/src/main/assets/corpus/hk_core_chars.csv")
DEFAULT_PHRASES = Path("android/app/src/main/assets/corpus/hk_core_phrases.csv")
DEFAULT_JYUTPING = Path("android/app/src/main/assets/corpus/jyutping.csv")
DEFAULT_OVERRIDES = Path("android/app/src/main/assets/corpus/jyutping_overrides.csv")
DEFAULT_HKSCS = Path("android/app/src/main/assets/corpus/hkscs_supplement.csv")
DEFAULT_PINYIN = Path("android/app/src/main/assets/corpus/pinyin.csv")
DEFAULT_OUTPUT = Path("corpus/benchmarks/three_mode_source_coverage_cases.csv")
DEFAULT_CASES_PER_SCHEME = 180
AUTOMATED_REVIEW_STATUS = "automated-source-derived"


@dataclass(frozen=True)
class CoverageCase:
    scheme: str
    input: str
    expected: str
    review_status: str = AUTOMATED_REVIEW_STATUS


def _read_rows(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8", newline="") as handle:
        lines = (line for line in handle if line.strip() and not line.lstrip().startswith("#"))
        return list(csv.DictReader(lines))


def _dedupe_text(rows: list[tuple[str, float]]) -> list[str]:
    seen: set[str] = set()
    ranked: list[str] = []
    for text, _ in rows:
        if text not in seen:
            seen.add(text)
            ranked.append(text)
    return ranked


def quick_index(chars: Path, phrases: Path, hkscs: Path) -> dict[str, list[str]]:
    grouped: dict[str, list[tuple[str, int, int, float]]] = {}
    for row in _read_rows(chars):
        code = row.get("quick_code", "").strip().lower()
        text = row.get("char", "").strip()
        if code and text:
            grouped.setdefault(code, []).append(
                (text, int(row.get("hk_core", "0") or 0), 0, float(row.get("freq", "0") or 0))
            )
    for row in _read_rows(phrases):
        code = row.get("quick_code", "").strip().lower()
        text = row.get("phrase", "").strip()
        if code and text:
            grouped.setdefault(code, []).append(
                (text, int(row.get("hk_core", "0") or 0), 1, float(row.get("freq", "0") or 0))
            )
    for row in _read_rows(hkscs):
        code = row.get("quick_code", "").strip().lower()
        text = row.get("chinese", "").strip()
        if code and text:
            grouped.setdefault(code, []).append((text, 0, 0, 0.0))

    index: dict[str, list[str]] = {}
    for code, entries in grouped.items():
        ranked = sorted(entries, key=lambda entry: (-entry[1], -entry[2], -entry[3], entry[0]))
        index[code] = _dedupe_text([(text, frequency) for text, _, _, frequency in ranked])
    return index


def jyutping_index(jyutping: Path, overrides: Path, hkscs: Path) -> dict[str, list[str]]:
    grouped: dict[str, list[tuple[str, float]]] = {}
    for path in (jyutping, overrides):
        for row in _read_rows(path):
            code = row.get("jyutping", "").strip().lower()
            text = row.get("chinese", "").strip()
            if code and text:
                grouped.setdefault(code, []).append((text, float(row.get("freq", "0") or 0)))
    for row in _read_rows(hkscs):
        text = row.get("chinese", "").strip()
        for code in row.get("jyutping", "").lower().split(";"):
            if code and text:
                grouped.setdefault(code, []).append((text, 0.0))

    return {
        code: _dedupe_text(sorted(entries, key=lambda entry: (-entry[1], entry[0])))
        for code, entries in grouped.items()
    }


def pinyin_index(pinyin: Path) -> dict[str, list[str]]:
    grouped: dict[str, list[tuple[str, float]]] = {}
    for row in _read_rows(pinyin):
        code = row.get("pinyin", "").strip().lower()
        text = row.get("chinese", "").strip()
        if code and text:
            grouped.setdefault(code, []).append((text, float(row.get("freq", "0") or 0)))
    return {
        code: _dedupe_text(sorted(entries, key=lambda entry: (-entry[1], entry[0])))
        for code, entries in grouped.items()
    }


def _even_sample(index: dict[str, list[str]], count: int) -> list[tuple[str, str]]:
    eligible = [key for key in sorted(index) if index[key]]
    if count <= 0 or len(eligible) < count:
        raise ValueError(f"need at least {count} populated keys, found {len(eligible)}")
    positions = [(offset * (len(eligible) - 1)) // (count - 1) for offset in range(count)] if count > 1 else [0]
    return [(eligible[position], index[eligible[position]][0]) for position in positions]


def build_cases(
    *,
    chars: Path = DEFAULT_CHARS,
    phrases: Path = DEFAULT_PHRASES,
    jyutping: Path = DEFAULT_JYUTPING,
    overrides: Path = DEFAULT_OVERRIDES,
    hkscs: Path = DEFAULT_HKSCS,
    pinyin: Path = DEFAULT_PINYIN,
    per_scheme: int = DEFAULT_CASES_PER_SCHEME,
) -> list[CoverageCase]:
    indexes = {
        "quick": quick_index(chars, phrases, hkscs),
        "jyutping": jyutping_index(jyutping, overrides, hkscs),
        "pinyin": pinyin_index(pinyin),
    }
    cases: list[CoverageCase] = []
    for scheme in ("quick", "jyutping", "pinyin"):
        cases.extend(
            CoverageCase(scheme, key, expected)
            for key, expected in _even_sample(indexes[scheme], per_scheme)
        )
    return cases


def write_cases(cases: list[CoverageCase], output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as handle:
        handle.write("# Deterministic source-derived coverage cases for Quick, Jyutping and Pinyin.\n")
        handle.write("# NOT human-reviewed linguistic quality cases; see docs release gates.\n")
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow(("scheme", "input", "expected", "review_status"))
        writer.writerows(
            (case.scheme, case.input, case.expected, case.review_status) for case in cases
        )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--chars", type=Path, default=DEFAULT_CHARS)
    parser.add_argument("--phrases", type=Path, default=DEFAULT_PHRASES)
    parser.add_argument("--jyutping", type=Path, default=DEFAULT_JYUTPING)
    parser.add_argument("--overrides", type=Path, default=DEFAULT_OVERRIDES)
    parser.add_argument("--hkscs", type=Path, default=DEFAULT_HKSCS)
    parser.add_argument("--pinyin", type=Path, default=DEFAULT_PINYIN)
    parser.add_argument("--per-scheme", type=int, default=DEFAULT_CASES_PER_SCHEME)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    cases = build_cases(
        chars=args.chars,
        phrases=args.phrases,
        jyutping=args.jyutping,
        overrides=args.overrides,
        hkscs=args.hkscs,
        pinyin=args.pinyin,
        per_scheme=args.per_scheme,
    )
    write_cases(cases, args.output)
    print(f"Wrote {len(cases)} automated source-derived three-mode coverage cases")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
