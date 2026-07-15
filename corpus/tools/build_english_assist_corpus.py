#!/usr/bin/env python3
"""Build the English→Traditional-Chinese assist corpus from pinned sources.

The generator deliberately keeps two sources of authority separate:

* CC-CEDICT supplies Traditional Chinese headwords and English gloss tokens.
* A pinned English frequency vocabulary limits the exposed keys to ordinary
  English input, while pinned RIME essay counts rank competing Chinese outputs.

The small project-curated HK layer is merged as a higher-priority input. It is
not inferred from the dictionary and remains separately reviewable.
"""

from __future__ import annotations

import argparse
import csv
import datetime
import gzip
import hashlib
import io
import re
import tarfile
from dataclasses import dataclass
from pathlib import Path
from typing import TextIO


CEDICT_ENTRY = re.compile(r"^(\S+)\s+\S+\s+\[[^]]*]\s+/(.*)/\s*$")
CEDICT_METADATA = re.compile(r"^#!\s+([a-z]+)=(.*)$")
REQUIRED_CEDICT_METADATA = {"entries", "version", "subversion", "format", "charset", "date"}
TERM = re.compile(r"[A-Za-z]+(?:[-'][A-Za-z]+)*")
GLOSS_TAG = re.compile(r"\([^)]*\)")
MAX_ROWS = 80_000
MAX_ASSET_BYTES = 3 * 1024 * 1024
MAX_CANDIDATES_PER_KEY = 3
MAX_KEY_LENGTH = 32

# Dictionary markup and grammatical framing have poor value as keyboard keys.
# This is a documented filter, not a hidden translation or ranking policy.
STOP_TERMS = frozenset(
    """
    a an and are as at be by class classifier cl dialect for from in into is it
    of on or per prefix pronoun sb sth the to tw variant with without used also
    person people one see fig lit etc pr abbr coll adj adv aux conj det interj
    modal num prep pron rel subj obj pl sing esp usu chiefly infml informal
    formal math med chem phys biol zool bot geol comput internet hong kong
    taiwan mainland china chinese cantonese mandarin pinyin surname given name
    place county city province district state town village dynasty era emperor
    old new former latter same various other term word phrase expression idiom
    literary classical bound form loanword transliteration transcribed
    """.split()
)


@dataclass(frozen=True)
class Candidate:
    text: str
    units: int
    curated: bool
    gloss_quality: int


@dataclass(frozen=True)
class BuildStats:
    rows: int
    keys: int
    asset_bytes: int


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _validate_hash(path: Path, expected: str, description: str) -> None:
    actual = sha256(path)
    if actual != expected.lower():
        raise ValueError(
            f"{description} SHA-256 mismatch: expected {expected.lower()}, got {actual}"
        )


def _open_cedict(path: Path) -> TextIO:
    if path.suffix == ".gz":
        return gzip.open(path, mode="rt", encoding="utf-8")
    return path.open(encoding="utf-8")


def _is_han_only(value: str) -> bool:
    if not value:
        return False
    return all(
        0x3400 <= ord(character) <= 0x9FFF
        or 0xF900 <= ord(character) <= 0xFAFF
        or 0x20000 <= ord(character) <= 0x2EBEF
        or 0x30000 <= ord(character) <= 0x323AF
        or ord(character) == 0x3007
        for character in value
    )


def _validate_cedict_metadata(metadata: dict[str, str], source_date: str, parsed: int) -> None:
    missing = sorted(REQUIRED_CEDICT_METADATA - metadata.keys())
    if missing:
        raise ValueError(f"CC-CEDICT metadata missing required fields: {', '.join(missing)}")
    expected = {"version": "1", "subversion": "0", "format": "ts", "charset": "UTF-8"}
    wrong = {key: metadata[key] for key, value in expected.items() if metadata[key] != value}
    if wrong:
        raise ValueError(f"CC-CEDICT metadata has unsupported values: {wrong!r}")
    try:
        declared = int(metadata["entries"])
    except ValueError as error:
        raise ValueError("CC-CEDICT metadata entries must be an integer") from error
    if declared != parsed:
        raise ValueError(f"CC-CEDICT entry count mismatch: metadata={declared}, parsed={parsed}")
    try:
        datetime.datetime.fromisoformat(metadata["date"].replace("Z", "+00:00"))
    except ValueError as error:
        raise ValueError("CC-CEDICT metadata date must be ISO-8601") from error
    if metadata["date"] != source_date:
        raise ValueError(
            f"CC-CEDICT source date mismatch: expected embedded {metadata['date']!r}, got {source_date!r}"
        )


def load_frequency_words(path: Path, expected_sha256: str) -> dict[str, int]:
    """Load the pinned `word count` English vocabulary snapshot."""
    _validate_hash(path, expected_sha256, "frequency vocabulary")
    words: dict[str, int] = {}
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        fields = line.split()
        if len(fields) != 2:
            raise ValueError(f"malformed frequency vocabulary row {number}")
        word, raw_count = fields
        key = word.lower()
        if TERM.fullmatch(key) is None:
            continue
        try:
            count = int(raw_count)
        except ValueError as error:
            raise ValueError(f"invalid frequency count at row {number}") from error
        if count < 0:
            raise ValueError(f"negative frequency count at row {number}")
        words[key] = max(count, words.get(key, 0))
    if not words:
        raise ValueError("frequency vocabulary contains no usable English terms")
    return words


def _archive_member(source: Path, filename: str) -> str:
    try:
        with tarfile.open(source, "r:gz") as archive:
            matches = [
                member for member in archive.getmembers()
                if member.isfile() and (member.name == filename or member.name.endswith(f"/{filename}"))
            ]
            if len(matches) != 1:
                raise ValueError(f"expected exactly one {filename} in {source}")
            handle = archive.extractfile(matches[0])
            if handle is None:
                raise ValueError(f"cannot read {filename} from {source}")
            return handle.read().decode("utf-8")
    except (OSError, tarfile.TarError, UnicodeDecodeError) as error:
        raise ValueError(f"cannot read {filename} from {source}: {error}") from error


def _load_essay(source: Path, expected_sha256: str) -> dict[str, int]:
    _validate_hash(source, expected_sha256, "essay source")
    frequencies: dict[str, int] = {}
    for number, line in enumerate(_archive_member(source, "essay.txt").splitlines(), 1):
        if not line or line.startswith("#"):
            continue
        text, separator, raw_count = line.rpartition("\t")
        if not separator:
            text, raw_count = line, "0"
        try:
            count = int(raw_count)
        except ValueError as error:
            raise ValueError(f"invalid essay frequency at row {number}") from error
        if not text or count < 0:
            raise ValueError(f"invalid essay row {number}")
        frequencies[text] = count
    return frequencies


def _load_curated(path: Path, expected_sha256: str) -> list[tuple[str, str, int]]:
    _validate_hash(path, expected_sha256, "curated English assist input")
    rows: list[tuple[str, str, int]] = []
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.reader(line for line in handle if line.strip() and not line.lstrip().startswith("#"))
        header = next(reader, None)
        if header != ["english", "chinese", "freq"]:
            raise ValueError("curated English assist input must use english,chinese,freq header")
        for number, row in enumerate(reader, 2):
            if len(row) < 3:
                raise ValueError(f"malformed curated English assist row {number}")
            key, text = row[0].strip().lower(), row[1].strip()
            try:
                frequency = float(row[2])
            except ValueError as error:
                raise ValueError(f"invalid curated English assist frequency at row {number}") from error
            if TERM.fullmatch(key) is None or not _is_han_only(text) or not 0.0 <= frequency <= 1.0:
                raise ValueError(f"invalid curated English assist row {number}")
            rows.append((key, text, round(frequency * 10_000)))
    return rows


def _term_is_eligible(term: str, vocabulary: dict[str, int]) -> bool:
    if len(term) < 2 or len(term) > MAX_KEY_LENGTH or term in STOP_TERMS:
        return False
    if term in vocabulary:
        return True
    # OpenSubtitles tokenizes contractions such as can't into `can` + `'t`.
    # Retain compound/apostrophe terms when at least one component is a real
    # frequency-vocabulary word; the IME lets these compose only after a real
    # corpus-prefix lookup succeeds.
    if "-" not in term and "'" not in term:
        return False
    return any(piece in vocabulary for piece in re.split(r"[-']", term) if piece)


def _essay_units(count: int) -> int:
    return 2000 + (6000 * count) // (count + 10_000)


def _gloss_quality(term: str, definition: str) -> int:
    """Prefer direct dictionary glosses over incidental words in long examples."""
    quality = 1
    for fragment in re.split(r"[/;,]", definition.lower()):
        cleaned = GLOSS_TAG.sub("", fragment).strip(" .:!")
        cleaned = re.sub(r"^(?:to|a|an|the)\s+", "", cleaned)
        if cleaned == term:
            return 3
        words = [word.lower() for word in TERM.findall(cleaned)]
        if words and words[0] == term:
            quality = max(quality, 2)
    return quality


def _add_candidate(
    candidates: dict[str, dict[str, Candidate]],
    key: str,
    text: str,
    units: int,
    curated: bool,
    gloss_quality: int,
) -> None:
    by_text = candidates.setdefault(key, {})
    previous = by_text.get(text)
    proposed = Candidate(text, units, curated, gloss_quality)
    if previous is None or (
        proposed.gloss_quality,
        proposed.units,
        proposed.curated,
    ) > (
        previous.gloss_quality,
        previous.units,
        previous.curated,
    ):
        by_text[text] = proposed


def build_corpus(
    *,
    cedict_path: Path,
    cedict_sha256: str,
    frequency_words_path: Path,
    frequency_words_sha256: str,
    essay_source: Path,
    essay_sha256: str,
    curated_path: Path,
    curated_sha256: str | None = None,
    output_path: Path,
    source_url: str,
    source_date: str,
) -> BuildStats:
    """Build a deterministic CSV and return its bounded runtime statistics."""
    _validate_hash(cedict_path, cedict_sha256, "CC-CEDICT source")
    vocabulary = load_frequency_words(frequency_words_path, frequency_words_sha256)
    essay = _load_essay(essay_source, essay_sha256)
    curated = _load_curated(curated_path, curated_sha256 or sha256(curated_path))

    candidates: dict[str, dict[str, Candidate]] = {}
    metadata: dict[str, str] = {}
    parsed_entries = 0
    with _open_cedict(cedict_path) as source:
        for line_number, line in enumerate(source, 1):
            metadata_match = CEDICT_METADATA.match(line)
            if metadata_match:
                key, value = metadata_match.groups()
                metadata[key] = value.strip()
                continue
            if not line.strip() or line.startswith("#"):
                continue
            match = CEDICT_ENTRY.match(line)
            if not match:
                raise ValueError(f"malformed CC-CEDICT record at line {line_number}")
            parsed_entries += 1
            traditional, definitions = match.groups()
            if not _is_han_only(traditional):
                continue
            units = _essay_units(essay.get(traditional, 0))
            for term in TERM.findall(definitions):
                key = term.lower()
                if _term_is_eligible(key, vocabulary):
                    _add_candidate(
                        candidates,
                        key,
                        traditional,
                        units,
                        curated=False,
                        gloss_quality=_gloss_quality(key, definitions),
                    )
    _validate_cedict_metadata(metadata, source_date, parsed_entries)

    for key, text, units in curated:
        _add_candidate(candidates, key, text, units, curated=True, gloss_quality=4)

    rows: list[tuple[str, str, int]] = []
    for key in sorted(candidates):
        ranked = sorted(
            candidates[key].values(),
            key=lambda candidate: (
                -candidate.gloss_quality,
                -candidate.units,
                not candidate.curated,
                candidate.text,
            ),
        )[:MAX_CANDIDATES_PER_KEY]
        rows.extend((key, candidate.text, candidate.units) for candidate in ranked)

    output = io.StringIO(newline="")
    output.write("# English → Traditional-Chinese assist derived from pinned CC-CEDICT,\n")
    output.write("# FrequencyWords English vocabulary, and rime-essay ranking data.\n")
    output.write("# Curated Hong Kong rows are a separately pinned higher-priority input.\n")
    output.write(f"# CC-CEDICT source URL: {source_url}\n")
    output.write(f"# CC-CEDICT source date: {metadata['date']}\n")
    output.write(f"# CC-CEDICT SHA-256: {cedict_sha256}\n")
    output.write(
        f"# Per-key candidate cap: {MAX_CANDIDATES_PER_KEY}; key length<={MAX_KEY_LENGTH}.\n"
    )
    writer = csv.writer(output, lineterminator="\n")
    writer.writerow(("english", "chinese", "freq"))
    for key, text, units in rows:
        writer.writerow((key, text, f"{units // 10_000}.{units % 10_000:04d}"))

    asset = output.getvalue().encode("utf-8")
    stats = BuildStats(rows=len(rows), keys=len(candidates), asset_bytes=len(asset))
    if stats.rows > MAX_ROWS:
        raise ValueError(f"English assist row budget exceeded: {stats.rows} > {MAX_ROWS}")
    if stats.asset_bytes > MAX_ASSET_BYTES:
        raise ValueError(
            f"English assist asset size budget exceeded: {stats.asset_bytes} > {MAX_ASSET_BYTES}"
        )
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(asset)
    return stats


def main() -> int:
    repository_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cedict", type=Path, required=True)
    parser.add_argument("--cedict-sha256", required=True)
    parser.add_argument("--frequency-words", type=Path, required=True)
    parser.add_argument("--frequency-words-sha256", required=True)
    parser.add_argument("--essay-source", type=Path, required=True)
    parser.add_argument("--essay-sha256", required=True)
    parser.add_argument(
        "--curated",
        type=Path,
        default=repository_root / "corpus/internal_stage1/english_assist.csv",
    )
    parser.add_argument("--curated-sha256", required=True)
    parser.add_argument("--source-url", required=True)
    parser.add_argument("--source-date", required=True)
    parser.add_argument(
        "--output",
        type=Path,
        default=repository_root / "android/app/src/main/assets/corpus/english_assist.csv",
    )
    arguments = parser.parse_args()
    stats = build_corpus(
        cedict_path=arguments.cedict,
        cedict_sha256=arguments.cedict_sha256,
        frequency_words_path=arguments.frequency_words,
        frequency_words_sha256=arguments.frequency_words_sha256,
        essay_source=arguments.essay_source,
        essay_sha256=arguments.essay_sha256,
        curated_path=arguments.curated,
        curated_sha256=arguments.curated_sha256,
        output_path=arguments.output,
        source_url=arguments.source_url,
        source_date=arguments.source_date,
    )
    print(f"Wrote {stats.rows} English assist rows across {stats.keys} keys")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
