#!/usr/bin/env python3
"""Measure HKSCS-2016 Han coverage of the shipped Quick and Jyutping corpora.

The official Hong Kong Supplementary Character Set is the fixed baseline for the
cultural-preservation character layer. This tool loads the pinned HKSCS-2016
open-data JSON, isolates its Han ideographs by Unicode code point (supplementary
plane included), and reports how many are reachable as single-character entries
in the shipped Quick (`hk_core_chars.csv`) and Jyutping (`jyutping.csv`)
corpora.

It reports measured numbers only — it never asserts "100% Cantonese". With
`--emit-supplement` writes every official HKSCS Han character together with its
official Quick code (first + last Cangjie letter) and toneless Jyutping reading.
Records with neither source mapping receive an explicitly technical Unicode
escape at runtime, rather than an invented linguistic mapping.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import unicodedata
from dataclasses import dataclass
from pathlib import Path


DEFAULT_SOURCE = Path("corpus/sources/upstream/hkscs2016-a28a3b74.json")
DEFAULT_SOURCE_SHA = "a28a3b74e469726c7df2174346e9a3992760f5842c02ac07b834529bc5b931e5"
DEFAULT_QUICK = Path("android/app/src/main/assets/corpus/hk_core_chars.csv")
DEFAULT_JYUTPING = Path("android/app/src/main/assets/corpus/jyutping.csv")
DEFAULT_SUPPLEMENT = Path("android/app/src/main/assets/corpus/hkscs_supplement.csv")


@dataclass(frozen=True)
class HkscsChar:
    codepoint: int
    char: str
    cangjie: str
    cantonese: str

    @property
    def supplementary(self) -> bool:
        return self.codepoint > 0xFFFF


@dataclass(frozen=True)
class SupplementEntry:
    char: str
    codepoint: str
    quick_code: str
    jyutping: tuple[str, ...]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _is_han(codepoint: int) -> bool:
    try:
        name = unicodedata.name(chr(codepoint))
    except ValueError:
        return False
    return name.startswith("CJK UNIFIED IDEOGRAPH") or name.startswith(
        "CJK COMPATIBILITY IDEOGRAPH"
    )


def load_han(source: Path, source_sha256: str) -> list[HkscsChar]:
    actual = sha256(source)
    if actual != source_sha256:
        raise ValueError(f"source SHA-256 mismatch: expected {source_sha256}, got {actual}")
    # The government export carries a UTF-8 BOM.
    records = json.loads(source.read_text(encoding="utf-8-sig"))
    han: list[HkscsChar] = []
    for record in records:
        codepoint = int(record["codepoint"], 16)
        if not _is_han(codepoint):
            continue
        han.append(
            HkscsChar(
                codepoint=codepoint,
                char=chr(codepoint),
                cangjie=record.get("cangjie", "") or "",
                cantonese=record.get("cantonese", "") or "",
            )
        )
    han.sort(key=lambda item: item.codepoint)
    return han


def _single_char_texts(path: Path, text_col: int) -> set[str]:
    """Single-code-point outputs from the given column (0=char, 1=chinese)."""
    texts: set[str] = set()
    if not path.is_file():
        return texts
    with path.open("r", encoding="utf-8", newline="") as handle:
        lines = (line for line in handle if line.strip() and not line.lstrip().startswith("#"))
        reader = csv.reader(lines)
        header_skipped = False
        for cols in reader:
            if not header_skipped:
                header_skipped = True
                continue
            if len(cols) <= text_col:
                continue
            text = cols[text_col].strip()
            # Python strings are code-point sequences, so len()==1 already treats
            # a supplementary-plane character as a single character.
            if len(text) == 1:
                texts.add(text)
    return texts


def load_supplement(path: Path) -> list[SupplementEntry]:
    entries: list[SupplementEntry] = []
    with path.open("r", encoding="utf-8", newline="") as handle:
        lines = (line for line in handle if line.strip() and not line.lstrip().startswith("#"))
        reader = csv.reader(lines)
        next(reader, None)
        for cols in reader:
            if len(cols) < 4:
                continue
            char = cols[0].strip()
            if len(char) != 1:
                continue
            readings = tuple(
                reading.strip().lower()
                for reading in cols[3].split(";")
                if reading.strip()
            )
            entries.append(
                SupplementEntry(
                    char=char,
                    codepoint=cols[1].strip(),
                    quick_code=cols[2].strip().lower(),
                    jyutping=readings,
                )
            )
    return entries


def quick_code(cangjie: str) -> str | None:
    """First + last letter of the first Cangjie reading (the 速成 code)."""
    first = cangjie.replace(",", " ").split()
    if not first:
        return None
    code = first[0].strip().lower()
    if not code.isalpha():
        return None
    return code[0] + code[-1] if len(code) >= 2 else code


def toneless_jyutping(cantonese: str) -> list[str]:
    readings: list[str] = []
    for token in cantonese.replace(",", " ").split():
        toneless = "".join(ch for ch in token if ch.isalpha()).lower()
        if toneless and toneless not in readings:
            readings.append(toneless)
    return readings


@dataclass(frozen=True)
class Coverage:
    total: int
    supplementary_total: int
    quick_covered: int
    quick_supplementary_covered: int
    jyutping_covered: int
    jyutping_supplementary_covered: int
    missing_both: list[HkscsChar]
    unicode_fallbacks: list[HkscsChar]

    @property
    def runtime_reachable(self) -> int:
        """Characters reachable through a source mapping or Unicode fallback."""
        return self.total - len(
            [entry for entry in self.missing_both if unicode_fallback_code(entry) is None]
        )


def unicode_fallback_code(entry: HkscsChar) -> str | None:
    """Technical route for an official record with no official input mapping."""
    if entry.cangjie or entry.cantonese:
        return None
    return f"u{entry.codepoint:x}"


def measure(han: list[HkscsChar], quick: set[str], jyutping: set[str]) -> Coverage:
    supp = [c for c in han if c.supplementary]
    quick_hit = [c for c in han if c.char in quick]
    jyut_hit = [c for c in han if c.char in jyutping]
    missing_both = [c for c in han if c.char not in quick and c.char not in jyutping]
    return Coverage(
        total=len(han),
        supplementary_total=len(supp),
        quick_covered=len(quick_hit),
        quick_supplementary_covered=sum(1 for c in quick_hit if c.supplementary),
        jyutping_covered=len(jyut_hit),
        jyutping_supplementary_covered=sum(1 for c in jyut_hit if c.supplementary),
        missing_both=missing_both,
        unicode_fallbacks=[entry for entry in missing_both if unicode_fallback_code(entry)],
    )


def emit_supplement(han: list[HkscsChar], quick: set[str], jyutping: set[str], output: Path) -> int:
    """Write the full official HKSCS overlay with only source-derived routes."""
    # Retain the inputs in the public API so existing manifest verification calls
    # remain stable. The full overlay intentionally does not depend on coverage of
    # legacy corpora: it needs to retain an official route even where that corpus
    # already contains a different historical mapping.
    del quick, jyutping
    rows: list[tuple[str, str, str, str]] = []
    for c in han:
        qc = quick_code(c.cangjie) or ""
        jp = ";".join(toneless_jyutping(c.cantonese))
        rows.append((c.char, f"U+{c.codepoint:04X}", qc, jp))
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as handle:
        handle.write("# HKSCS-2016 official input overlay (authoritative, generated).\n")
        handle.write("# Every HKSCS Han record, with only official Quick code and toneless\n")
        handle.write("# Jyutping readings. Blank route fields mean no official input mapping.\n")
        handle.write("# Fields: chinese, code_point, quick_code, jyutping.\n")
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow(("chinese", "code_point", "quick_code", "jyutping"))
        writer.writerows(rows)
    return len(rows)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--source-sha256", default=DEFAULT_SOURCE_SHA)
    parser.add_argument("--quick", type=Path, default=DEFAULT_QUICK)
    parser.add_argument("--jyutping", type=Path, default=DEFAULT_JYUTPING)
    parser.add_argument("--supplement", type=Path, default=DEFAULT_SUPPLEMENT)
    parser.add_argument("--emit-supplement", type=Path, default=None)
    args = parser.parse_args()

    han = load_han(args.source, args.source_sha256)
    # hk_core_chars.csv: char is column 0. jyutping.csv: chinese is column 1.
    base_quick = _single_char_texts(args.quick, text_col=0)
    base_jyutping = _single_char_texts(args.jyutping, text_col=1)
    supplement = load_supplement(args.supplement)
    quick = base_quick | {entry.char for entry in supplement if entry.quick_code}
    jyutping = base_jyutping | {entry.char for entry in supplement if entry.jyutping}
    cov = measure(han, quick, jyutping)

    def pct(part: int, whole: int) -> str:
        return f"{part / whole:.2%}" if whole else "n/a"

    print(f"HKSCS-2016 Han ideographs: {cov.total} ({cov.supplementary_total} supplementary-plane)")
    print(
        f"  Quick coverage:    {cov.quick_covered}/{cov.total} ({pct(cov.quick_covered, cov.total)}); "
        f"supplementary {cov.quick_supplementary_covered}/{cov.supplementary_total}"
    )
    print(
        f"  Jyutping coverage: {cov.jyutping_covered}/{cov.total} ({pct(cov.jyutping_covered, cov.total)}); "
        f"supplementary {cov.jyutping_supplementary_covered}/{cov.supplementary_total}"
    )
    print(f"  Missing from both corpora: {len(cov.missing_both)}")
    if cov.missing_both:
        print("  No official input mapping: " + ", ".join(
            f"{entry.char} (U+{entry.codepoint:04X})" for entry in cov.missing_both
        ))
    print(f"  Runtime reachable (official routes or Unicode fallback): {cov.runtime_reachable}/{cov.total}")
    if cov.unicode_fallbacks:
        print("  Unicode fallback (tap-only): " + ", ".join(
            f"{entry.char} ({unicode_fallback_code(entry)})" for entry in cov.unicode_fallbacks
        ))

    if args.emit_supplement is not None:
        written = emit_supplement(han, base_quick, base_jyutping, args.emit_supplement)
        print(f"  Wrote {written} official overlay rows to {args.emit_supplement}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
