#!/usr/bin/env python3
"""Build production Quick assets from pinned RIME sources and reviewed HK rows."""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import re
import tarfile
from dataclasses import dataclass
from pathlib import Path


CODE = re.compile(r"[a-z]+")
MAX_CHARS = 25_000
MAX_PHRASES = 41_000
PHRASE_LIMIT = 40_000


@dataclass(frozen=True)
class BuildStats:
    char_rows: int
    phrase_rows: int
    char_bytes: int
    phrase_bytes: int


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def archive_member(source: Path, filename: str) -> str:
    try:
        with tarfile.open(source, "r:gz") as archive:
            matches = [
                member
                for member in archive.getmembers()
                if member.isfile()
                and (member.name == filename or member.name.endswith(f"/{filename}"))
            ]
            if len(matches) != 1:
                raise ValueError(f"expected exactly one {filename} in {source}")
            handle = archive.extractfile(matches[0])
            if handle is None:
                raise ValueError(f"cannot read {filename} from {source}")
            return handle.read().decode("utf-8")
    except (OSError, tarfile.TarError, UnicodeDecodeError) as exc:
        raise ValueError(f"cannot read {filename} from {source}: {exc}") from exc


def validate_source(path: Path, expected_hash: str) -> None:
    actual = sha256(path)
    if actual != expected_hash:
        raise ValueError(
            f"source SHA-256 mismatch for {path}: expected {expected_hash}, got {actual}"
        )


def parse_essay(content: str) -> dict[str, int]:
    frequencies: dict[str, int] = {}
    for number, line in enumerate(content.splitlines(), 1):
        if not line or line.startswith("#"):
            continue
        fields = line.rsplit("\t", 1)
        if len(fields) == 1:
            text, raw = fields[0], "0"
        else:
            text, raw = fields
        try:
            frequency = int(raw)
        except ValueError as exc:
            raise ValueError(f"invalid essay frequency at row {number}") from exc
        if not text or frequency < 0:
            raise ValueError(f"invalid essay row {number}")
        frequencies[text] = frequency
    return frequencies


def parse_codes(contents: list[str]) -> dict[str, str]:
    ranked: dict[str, tuple[tuple[bool, int, str], str]] = {}
    for content in contents:
        started = False
        for line in content.splitlines():
            if line == "...":
                started = True
                continue
            if not started or not line or line.startswith("#"):
                continue
            fields = line.split("\t")
            if len(fields) < 2:
                raise ValueError("malformed Cangjie dictionary row")
            text, code = fields[:2]
            if len(text) != 1 or not CODE.fullmatch(code):
                continue
            rank = (code[0] in "xz", len(code), code)
            if text not in ranked or rank < ranked[text][0]:
                ranked[text] = (rank, code)
    if not ranked:
        raise ValueError("Cangjie source contains no usable character codes")
    return {text: value[1] for text, value in ranked.items()}


def reviewed_rows(path: Path, minimum_text_length: int) -> dict[str, tuple[int, int]]:
    rows: dict[str, tuple[int, int]] = {}
    with path.open("r", encoding="utf-8", newline="") as handle:
        content = (line for line in handle if line.strip() and not line.startswith("#"))
        for row in csv.reader(content):
            if not row or row[0] in {"char", "phrase"}:
                continue
            if len(row) < 4 or len(row[0]) < minimum_text_length:
                continue
            try:
                units = max(0, min(10_000, round(float(row[-2]) * 10_000)))
                hk_core = int(row[-1])
            except ValueError:
                continue
            previous = rows.get(row[0], (0, 0))
            rows[row[0]] = (max(previous[0], units), max(previous[1], hk_core))
    return rows


def reviewed_phrase_aliases(path: Path) -> dict[str, str]:
    aliases: dict[str, str] = {}
    with path.open("r", encoding="utf-8", newline="") as handle:
        content = (line for line in handle if line.strip() and not line.startswith("#"))
        for row in csv.reader(content):
            if len(row) >= 2 and row[0] != "phrase" and CODE.fullmatch(row[1]):
                aliases[row[0]] = row[1]
    return aliases


def score_units(count: int) -> int:
    return 2000 + (6000 * count) // (count + 10_000)


def quick_code(cangjie_code: str) -> str:
    return cangjie_code if len(cangjie_code) == 1 else cangjie_code[0] + cangjie_code[-1]


def validate_generated_rows(
    chars: list[tuple[str, str, str, int, int]],
    phrases: list[tuple[str, str, int, int]],
) -> None:
    seen_chars: set[str] = set()
    for text, quick, cangjie, _, hk_core in chars:
        if len(text) != 1 or not CODE.fullmatch(cangjie):
            raise ValueError(f"malformed generated Quick character row: {text!r}")
        if quick != quick_code(cangjie):
            raise ValueError(f"noncanonical generated Quick code: {text!r}->{quick!r}")
        if hk_core not in (0, 1) or text in seen_chars:
            raise ValueError(f"duplicate/invalid generated Quick character: {text!r}")
        seen_chars.add(text)

    seen_phrases: set[tuple[str, str]] = set()
    for text, code, _, hk_core in phrases:
        identity = (text, code)
        if len(text) < 2 or not CODE.fullmatch(code) or hk_core not in (0, 1):
            raise ValueError(f"malformed generated Quick phrase row: {identity!r}")
        if identity in seen_phrases:
            raise ValueError(f"duplicate generated Quick phrase/code: {identity!r}")
        seen_phrases.add(identity)


def build_corpora(
    *,
    cangjie_source: Path,
    cangjie_sha256: str,
    essay_source: Path,
    essay_sha256: str,
    reviewed_chars: Path,
    reviewed_phrases: Path,
    char_output: Path,
    phrase_output: Path,
) -> BuildStats:
    validate_source(cangjie_source, cangjie_sha256)
    validate_source(essay_source, essay_sha256)
    codes = parse_codes(
        [
            archive_member(cangjie_source, "cangjie5.base.dict.yaml"),
            archive_member(cangjie_source, "cangjie5.extended.dict.yaml"),
        ]
    )
    essay = parse_essay(archive_member(essay_source, "essay.txt"))
    curated_chars = reviewed_rows(reviewed_chars, 1)
    curated_phrases = reviewed_rows(reviewed_phrases, 2)
    curated_aliases = reviewed_phrase_aliases(reviewed_phrases)

    chars: list[tuple[str, str, str, int, int]] = []
    for text, code in codes.items():
        if essay.get(text, 0) < 1 and text not in curated_chars:
            continue
        units, hk_core = score_units(essay.get(text, 0)), 0
        if text in curated_chars:
            units = max(units, curated_chars[text][0])
            hk_core = curated_chars[text][1]
        chars.append((text, quick_code(code), code, units, hk_core))
    chars.sort(key=lambda row: (row[1], -row[3], row[0], row[2]))

    eligible = sorted(
        (
            (count, text)
            for text, count in essay.items()
            if 2 <= len(text) <= 8 and all(character in codes for character in text)
        ),
        key=lambda item: (-item[0], item[1]),
    )
    phrases = {text: (score_units(count), 0) for count, text in eligible[:PHRASE_LIMIT]}
    for text, (units, hk_core) in curated_phrases.items():
        if not (2 <= len(text) <= 8) or not all(character in codes for character in text):
            continue
        previous = phrases.get(text, (0, 0))
        phrases[text] = (max(previous[0], units), max(previous[1], hk_core))
    phrase_rows = [
        (
            text,
            "".join(quick_code(codes[character]) for character in text),
            units,
            hk_core,
        )
        for text, (units, hk_core) in phrases.items()
    ]
    existing = {(text, code) for text, code, _, _ in phrase_rows}
    for text, alias in curated_aliases.items():
        if text not in phrases or (text, alias) in existing:
            continue
        units, hk_core = phrases[text]
        phrase_rows.append((text, alias, units, hk_core))
    phrase_rows.sort(key=lambda row: (row[1], -row[2], row[0]))
    validate_generated_rows(chars, phrase_rows)

    metadata = (
        "# Quick character corpus derived from pinned rime-cangjie and rime-essay sources.",
        "# Canonical Cangjie code is converted to first+last Quick code; reviewed HK rows are merged by text.",
        "# rime-cangjie commit: 52d90a1b1312e74042b38c1cbc8142defbc53171; SHA-256: 18d989bf21d0bb86b402f18be4dd060bf2653896c95448afb01d4da2e6be5464",
        "# rime-essay commit: 0debef3f5ae37f081758dd8bbd1fe79719410f7c; SHA-256: 4f32414039eb7f8a876acd8e94bd81c78fefafde56a352f7a3b8b2b74ca09c71",
        "# Frequency units: 0.2000 + 0.6000 * count / (count + 10000), truncated to four decimals.",
    )

    def render(kind: str, header: tuple[str, ...], rows: list[tuple]) -> bytes:
        buffer = io.StringIO(newline="")
        for line in metadata:
            buffer.write(f"{line.replace('character corpus', f'{kind} corpus')}\n")
        buffer.write(f"# Fields: {','.join(header)}\n")
        writer = csv.writer(buffer, lineterminator="\n")
        writer.writerow(header)
        for row in rows:
            values = list(row)
            units = values[-2]
            values[-2] = f"{units // 10_000}.{units % 10_000:04d}"
            writer.writerow(values)
        return buffer.getvalue().encode("utf-8")

    char_bytes = render(
        "character", ("char", "quick_code", "cangjie_code", "freq", "hk_core"), chars
    )
    phrase_bytes = render(
        "phrase", ("phrase", "quick_code", "freq", "hk_core"), phrase_rows
    )
    stats = BuildStats(len(chars), len(phrase_rows), len(char_bytes), len(phrase_bytes))
    if stats.char_rows > MAX_CHARS or stats.phrase_rows > MAX_PHRASES:
        raise ValueError(f"generated Quick row budget exceeded: {stats}")

    for output, content in ((char_output, char_bytes), (phrase_output, phrase_bytes)):
        output.parent.mkdir(parents=True, exist_ok=True)
        temporary = output.with_name(f".{output.name}.tmp")
        try:
            temporary.write_bytes(content)
            temporary.replace(output)
        finally:
            temporary.unlink(missing_ok=True)
    return stats


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("cangjie-source", "cangjie-sha256", "essay-source", "essay-sha256"):
        parser.add_argument(f"--{name}", required=True, type=Path if name.endswith("source") else str)
    parser.add_argument("--reviewed-chars", type=Path, default=Path("corpus/internal_stage1/hk_core_chars.csv"))
    parser.add_argument("--reviewed-phrases", type=Path, default=Path("corpus/internal_stage1/hk_core_phrases.csv"))
    parser.add_argument("--char-output", type=Path, default=Path("android/app/src/main/assets/corpus/hk_core_chars.csv"))
    parser.add_argument("--phrase-output", type=Path, default=Path("android/app/src/main/assets/corpus/hk_core_phrases.csv"))
    args = parser.parse_args()
    stats = build_corpora(**vars(args))
    print(f"Wrote {stats.char_rows} Quick characters and {stats.phrase_rows} phrases")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
