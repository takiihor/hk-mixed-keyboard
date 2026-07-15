#!/usr/bin/env python3
"""Build the bundled Traditional→Simplified map from pinned OpenCC tables."""

from __future__ import annotations

import argparse
import hashlib
import io
import tarfile
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _archive_member(source: Path, filename: str) -> str:
    try:
        with tarfile.open(source, "r:gz") as archive:
            matches = [
                member for member in archive.getmembers()
                if member.isfile() and member.name.endswith(f"/{filename}")
            ]
            if len(matches) != 1:
                raise ValueError(f"expected exactly one {filename} in {source}")
            handle = archive.extractfile(matches[0])
            if handle is None:
                raise ValueError(f"cannot read {filename} from {source}")
            return handle.read().decode("utf-8")
    except (OSError, tarfile.TarError, UnicodeDecodeError) as error:
        raise ValueError(f"cannot read {filename} from {source}: {error}") from error


def _parse_dictionary(content: str, filename: str) -> dict[str, str]:
    entries: dict[str, str] = {}
    for number, line in enumerate(content.splitlines(), 1):
        if not line or line.startswith("#"):
            continue
        key, separator, values = line.partition("\t")
        target = values.split(maxsplit=1)[0] if separator else ""
        if not key or not target:
            raise ValueError(f"malformed {filename} row {number}")
        entries[key] = target
    if not entries:
        raise ValueError(f"{filename} contains no conversion rows")
    return entries


def build_map(source: Path, expected_sha256: str, output: Path) -> int:
    actual = sha256(source)
    if actual != expected_sha256.lower():
        raise ValueError(
            f"OpenCC source SHA-256 mismatch: expected {expected_sha256.lower()}, got {actual}"
        )
    characters = _parse_dictionary(_archive_member(source, "TSCharacters.txt"), "TSCharacters.txt")
    phrases = _parse_dictionary(_archive_member(source, "TSPhrases.txt"), "TSPhrases.txt")

    rendered = io.StringIO(newline="")
    rendered.write("# Traditional→Simplified map derived from pinned OpenCC TSCharacters + TSPhrases.\n")
    rendered.write("# Phrase rows are retained as greedy overrides; the first OpenCC target is used.\n")
    for key, value in sorted(phrases.items(), key=lambda row: (-len(row[0]), row[0])):
        rendered.write(f"{key}\t{value}\n")
    for key, value in sorted(characters.items()):
        rendered.write(f"{key}\t{value}\n")

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(rendered.getvalue().encode("utf-8"))
    return len(phrases) + len(characters)


def main() -> int:
    repository_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--source-sha256", required=True)
    parser.add_argument(
        "--output",
        type=Path,
        default=repository_root / "android/app/src/main/assets/t2s/t2s_map.tsv",
    )
    arguments = parser.parse_args()
    rows = build_map(arguments.source, arguments.source_sha256, arguments.output)
    print(f"Wrote {rows} OpenCC T2S source rows")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
