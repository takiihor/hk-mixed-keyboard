#!/usr/bin/env python3
"""Build the compact toned-Jyutping lookup used by Quick learning previews."""

from __future__ import annotations

import argparse
import csv
import re
from pathlib import Path
from typing import Iterable


RIME_DICTIONARIES = (
    "jyut6ping3.words.dict.yaml",
    "jyut6ping3.phrase.dict.yaml",
    "jyut6ping3.chars.dict.yaml",
)
TONED_JYUTPING = re.compile(r"[a-z]+[1-6](?: [a-z]+[1-6])*")


def _quick_texts(path: Path) -> set[str]:
    lines = (
        line for line in path.read_text(encoding="utf-8-sig").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    )
    reader = csv.reader(lines)
    next(reader, None)
    return {row[0].strip() for row in reader if row and row[0].strip()}


def _rime_rows(path: Path) -> Iterable[tuple[str, str]]:
    in_body = False
    for raw_line in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw_line.strip()
        if not in_body:
            in_body = line == "..."
            continue
        if not line or line.startswith("#"):
            continue
        fields = raw_line.split("\t")
        if len(fields) >= 2:
            yield fields[0].strip(), fields[1].strip()


def build_readings(
    rime_files: Iterable[Path],
    quick_chars: Path,
    quick_phrases: Path,
) -> dict[str, str]:
    reachable = _quick_texts(quick_chars) | _quick_texts(quick_phrases)
    readings: dict[str, str] = {}
    for path in rime_files:
        for text, jyutping in _rime_rows(path):
            if (
                text in reachable
                and text not in readings
                and TONED_JYUTPING.fullmatch(jyutping)
            ):
                readings[text] = jyutping
    return readings


def write_readings(path: Path, readings: dict[str, str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as output:
        writer = csv.writer(output, lineterminator="\n")
        writer.writerow(("text", "jyutping"))
        writer.writerows(sorted(readings.items()))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--rime-dir", type=Path, required=True)
    parser.add_argument("--quick-chars", type=Path, required=True)
    parser.add_argument("--quick-phrases", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    readings = build_readings(
        [args.rime_dir / name for name in RIME_DICTIONARIES],
        args.quick_chars,
        args.quick_phrases,
    )
    write_readings(args.output, readings)


if __name__ == "__main__":
    main()
