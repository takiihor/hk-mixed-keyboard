#!/usr/bin/env python3
"""Build the production Jyutping corpus from a pinned rime-cantonese archive."""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import re
import tarfile
from dataclasses import dataclass
from pathlib import Path


CHAR_DICTIONARY = "jyut6ping3.chars.dict.yaml"
WORD_DICTIONARY = "jyut6ping3.words.dict.yaml"
ESSAY = "essay-cantonese.txt"
MIN_WORD_FREQUENCY = 101
MAX_ROWS = 100_000
MAX_BYTES = 2_621_440
MAX_FANOUT = 256
MAX_KEY_LENGTH = 72
CODE_PATTERN = re.compile(r"[a-z]+[1-6](?: [a-z]+[1-6])*")
COMMIT_PATTERN = re.compile(r"[0-9a-f]{40}")


@dataclass(frozen=True)
class BuildStats:
    rows: int
    bytes: int
    max_fanout: int
    max_key_length: int


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _read_archive_member(archive: tarfile.TarFile, filename: str) -> str:
    matches = [
        member
        for member in archive.getmembers()
        if member.isfile()
        and (member.name == filename or member.name.endswith(f"/{filename}"))
    ]
    if len(matches) != 1:
        raise ValueError(f"expected exactly one {filename} in source archive")
    handle = archive.extractfile(matches[0])
    if handle is None:
        raise ValueError(f"cannot read {filename} from source archive")
    try:
        return handle.read().decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ValueError(f"{filename} is not valid UTF-8") from exc


def _parse_essay(content: str) -> dict[str, int]:
    frequencies: dict[str, int] = {}
    for line_number, line in enumerate(content.splitlines(), 1):
        if not line:
            continue
        fields = line.rsplit("\t", 1)
        if len(fields) != 2 or not fields[0]:
            raise ValueError(f"malformed essay row {line_number}")
        text, raw_frequency = fields
        try:
            frequency = int(raw_frequency)
        except ValueError as exc:
            raise ValueError(f"invalid essay frequency at row {line_number}") from exc
        if frequency < 0:
            raise ValueError(f"negative essay frequency at row {line_number}")
        # The pinned upstream essay contains repeated headwords. File order is
        # stable under the source checksum, so the final row wins deterministically.
        frequencies[text] = frequency
    if not frequencies:
        raise ValueError("essay source is empty")
    return frequencies


def _parse_dictionary(content: str, filename: str) -> list[tuple[str, str]]:
    data_started = False
    rows: list[tuple[str, str]] = []
    for line_number, line in enumerate(content.splitlines(), 1):
        if line == "...":
            data_started = True
            continue
        if not data_started or not line or line.startswith("#"):
            continue
        fields = line.split("\t")
        if len(fields) < 2 or not fields[0]:
            raise ValueError(f"malformed {filename} row {line_number}")
        text, raw_code = fields[:2]
        if not CODE_PATTERN.fullmatch(raw_code):
            raise ValueError(
                f"invalid Jyutping code in {filename} row {line_number}: {raw_code}"
            )
        rows.append((text, re.sub(r"[1-6 ]", "", raw_code)))
    if not data_started or not rows:
        raise ValueError(f"{filename} contains no dictionary rows")
    return rows


def _score_units(count: int) -> int:
    return 2000 + (6000 * count) // (count + 10_000)


def build_corpus(
    source: Path,
    output: Path,
    *,
    source_url: str,
    source_commit: str,
    source_date: str,
    source_sha256: str,
) -> BuildStats:
    if not COMMIT_PATTERN.fullmatch(source_commit):
        raise ValueError("source commit must be a lowercase 40-character SHA")
    if source_commit not in source_url:
        raise ValueError("source URL must contain the pinned commit")
    actual_source_hash = sha256(source)
    if actual_source_hash != source_sha256:
        raise ValueError(
            f"source SHA-256 mismatch: expected {source_sha256}, got {actual_source_hash}"
        )

    try:
        with tarfile.open(source, "r:gz") as archive:
            essay = _parse_essay(_read_archive_member(archive, ESSAY))
            characters = _parse_dictionary(
                _read_archive_member(archive, CHAR_DICTIONARY), CHAR_DICTIONARY
            )
            words = _parse_dictionary(
                _read_archive_member(archive, WORD_DICTIONARY), WORD_DICTIONARY
            )
    except (OSError, tarfile.TarError) as exc:
        raise ValueError(f"cannot read source archive: {exc}") from exc

    pairs: dict[tuple[str, str], int] = {}
    for text, key in characters:
        units = _score_units(essay.get(text, 0))
        pairs[(key, text)] = max(pairs.get((key, text), 0), units)
    for text, key in words:
        count = essay.get(text, 0)
        if count < MIN_WORD_FREQUENCY:
            continue
        units = _score_units(count)
        pairs[(key, text)] = max(pairs.get((key, text), 0), units)

    grouped: dict[str, list[tuple[str, int]]] = {}
    for (key, text), units in pairs.items():
        grouped.setdefault(key, []).append((text, units))

    rows: list[tuple[str, str, int]] = []
    for key in sorted(grouped):
        candidates = sorted(grouped[key], key=lambda item: (-item[1], item[0]))
        rows.extend((key, text, units) for text, units in candidates[:MAX_FANOUT])

    buffer = io.StringIO(newline="")
    header = (
        "# Jyutping dictionary derived from rime-cantonese (CC BY 4.0).",
        "# Toneless continuous keys from explicit chars/words readings; phrase rows without readings are excluded.",
        f"# Source URL: {source_url}",
        f"# Source commit: {source_commit}",
        f"# Source date: {source_date}",
        f"# Source SHA-256: {source_sha256}",
        "# Word inclusion: essay-cantonese frequency >= 101; all explicit character readings included.",
        "# Frequency units: 0.2000 + 0.6000 * count / (count + 10000), truncated to four decimals.",
        "# Runtime budgets: rows<=100000; bytes<=2621440; fanout<=256; key_length<=72.",
    )
    for line in header:
        buffer.write(f"{line}\n")
    writer = csv.writer(buffer, lineterminator="\n")
    writer.writerow(("jyutping", "chinese", "freq"))
    for key, text, units in rows:
        writer.writerow((key, text, f"{units // 10_000}.{units % 10_000:04d}"))
    encoded = buffer.getvalue().encode("utf-8")

    max_fanout = max((min(len(values), MAX_FANOUT) for values in grouped.values()), default=0)
    max_key_length = max((len(key) for key, _, _ in rows), default=0)
    stats = BuildStats(
        rows=len(rows),
        bytes=len(encoded),
        max_fanout=max_fanout,
        max_key_length=max_key_length,
    )
    if stats.rows > MAX_ROWS:
        raise ValueError(f"generated row budget exceeded: {stats.rows} > {MAX_ROWS}")
    if stats.bytes > MAX_BYTES:
        raise ValueError(f"generated byte budget exceeded: {stats.bytes} > {MAX_BYTES}")
    if stats.max_fanout > MAX_FANOUT:
        raise ValueError(
            f"generated fanout budget exceeded: {stats.max_fanout} > {MAX_FANOUT}"
        )
    if stats.max_key_length > MAX_KEY_LENGTH:
        raise ValueError(
            "generated key length budget exceeded: "
            f"{stats.max_key_length} > {MAX_KEY_LENGTH}"
        )

    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_name(f".{output.name}.tmp")
    try:
        temporary.write_bytes(encoded)
        temporary.replace(output)
    finally:
        temporary.unlink(missing_ok=True)
    return stats


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--source-url", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--source-date", required=True)
    parser.add_argument("--source-sha256", required=True)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("android/app/src/main/assets/corpus/jyutping.csv"),
    )
    args = parser.parse_args()
    stats = build_corpus(
        args.source,
        args.output,
        source_url=args.source_url,
        source_commit=args.source_commit,
        source_date=args.source_date,
        source_sha256=args.source_sha256,
    )
    print(
        f"Wrote {stats.rows} rows, {stats.bytes} bytes, max fanout {stats.max_fanout}, "
        f"max key length {stats.max_key_length} "
        f"to {args.output}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
