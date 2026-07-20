#!/usr/bin/env python3
"""Build the tonal Jyutping reference from a pinned rime-cantonese archive.

The shipped ``jyutping.csv`` decode corpus is intentionally toneless so that
users can type without tone digits. That normalization discards the 1-6 tone
marks and multiple readings that are part of the cultural-preservation record.

This generator preserves that data as a versioned, checksummed reference export
under ``corpus/reference/``. The same compact file is bundled as a lazy runtime
asset for explicit-tone ranking. Every character keeps all of its explicit tonal
readings in source order, and supplementary-plane characters are handled by
Unicode code point rather than UTF-16 length.
"""

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
# One or more space-separated tonal syllables, each ending in a 1-6 tone digit.
CODE_PATTERN = re.compile(r"[a-z]+[1-6](?: [a-z]+[1-6])*")
COMMIT_PATTERN = re.compile(r"[0-9a-f]{40}")
MAX_BYTES = 2_621_440


@dataclass(frozen=True)
class BuildStats:
    characters: int
    readings: int
    supplementary: int
    bytes: int


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


def _parse_readings(content: str, filename: str) -> dict[str, list[str]]:
    """Group every character to its ordered, de-duplicated tonal readings."""
    data_started = False
    readings: dict[str, list[str]] = {}
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
        # A character maps to exactly one code point here (single grapheme),
        # but count by code point so supplementary-plane readings are kept.
        bucket = readings.setdefault(text, [])
        if raw_code not in bucket:
            bucket.append(raw_code)
    if not data_started or not readings:
        raise ValueError(f"{filename} contains no dictionary rows")
    return readings


def _code_points(text: str) -> str:
    return " ".join(f"U+{code_point:04X}" for code_point in (ord(ch) for ch in text))


def _is_supplementary(text: str) -> bool:
    return any(ord(ch) > 0xFFFF for ch in text)


def build_reference(
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
            readings = _parse_readings(
                _read_archive_member(archive, CHAR_DICTIONARY), CHAR_DICTIONARY
            )
    except (OSError, tarfile.TarError) as exc:
        raise ValueError(f"cannot read source archive: {exc}") from exc

    # Sort by the first code point so the output is stable across runs.
    ordered = sorted(readings.items(), key=lambda item: [ord(ch) for ch in item[0]])

    buffer = io.StringIO(newline="")
    header = (
        "# Jyutping tonal reference derived from rime-cantonese (CC BY 4.0).",
        "# Preservation/runtime export: full 1-6 tone marks and every explicit reading",
        "# per character. Loaded lazily; the primary decode corpus remains toneless.",
        f"# Source URL: {source_url}",
        f"# Source commit: {source_commit}",
        f"# Source date: {source_date}",
        f"# Source SHA-256: {source_sha256}",
        "# Fields: chinese, code_points, readings (space-separated, source order).",
    )
    for line in header:
        buffer.write(f"{line}\n")
    writer = csv.writer(buffer, lineterminator="\n")
    writer.writerow(("chinese", "code_points", "readings"))
    total_readings = 0
    supplementary = 0
    for text, tonal in ordered:
        writer.writerow((text, _code_points(text), " ".join(tonal)))
        total_readings += len(tonal)
        if _is_supplementary(text):
            supplementary += 1
    encoded = buffer.getvalue().encode("utf-8")

    stats = BuildStats(
        characters=len(ordered),
        readings=total_readings,
        supplementary=supplementary,
        bytes=len(encoded),
    )
    if stats.bytes > MAX_BYTES:
        raise ValueError(f"generated byte budget exceeded: {stats.bytes} > {MAX_BYTES}")

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
        default=Path("corpus/reference/jyutping_tonal_readings.csv"),
    )
    args = parser.parse_args()
    stats = build_reference(
        args.source,
        args.output,
        source_url=args.source_url,
        source_commit=args.source_commit,
        source_date=args.source_date,
        source_sha256=args.source_sha256,
    )
    print(
        f"Wrote {stats.characters} characters, {stats.readings} readings "
        f"({stats.supplementary} supplementary-plane), {stats.bytes} bytes "
        f"to {args.output}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
