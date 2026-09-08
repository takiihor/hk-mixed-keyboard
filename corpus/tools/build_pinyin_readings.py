#!/usr/bin/env python3
"""Build the compact toned-Pinyin lookup used by the 拼音 learning hint.

The runtime Pinyin input corpus (``pinyin.csv``) is deliberately toneless, so it
cannot teach pronunciation. This tool derives a separate ``text -> toned pinyin``
asset from the same pinned CC-CEDICT snapshot, keeping the tone digits and the
syllable boundaries the learner needs.

Only text reachable through the Quick character and phrase corpora is kept, and
readings are taken verbatim from an exact CC-CEDICT headword — a phrase reading
is never fabricated by joining possibly polyphonic characters.
"""

from __future__ import annotations

import argparse
import csv
import gzip
import re
from pathlib import Path

# Trad Simp [pinyin] /gloss/…/
CEDICT_ENTRY = re.compile(r"^(\S+)\s+\S+\s+\[([^]]*)]\s+/(.*)/\s*$")
TONED_PINYIN = re.compile(r"[a-zü]+[1-5](?: [a-zü]+[1-5])*")

# A headword whose first gloss is one of these carries a reading that exists only
# for a name, a cross-reference, or an alternate written form. 綠 leads with
# "[lu4] /used in names/" and would otherwise teach lu4 instead of lü4 for "green".
SECONDARY_GLOSS = re.compile(
    r"^(surname\s|used in (given )?names?\b|(old |erhua |Japanese )?variant of\b|see\s)",
    re.IGNORECASE,
)

MAX_ASSET_BYTES = 2 * 1024 * 1024


def normalize_reading(raw: str) -> str:
    """Lowercase the reading and spell ``u:`` as ``ü``.

    CC-CEDICT capitalizes proper nouns and writes ``ü`` as the ASCII digraph
    ``u:``. The hint is a pronunciation aid rather than orthography, so it is
    presented lowercase to match the Jyutping hint beside it; ``ü`` is restored
    because ``lu:4`` is not something a learner should be shown.
    """
    return raw.replace("u:", "ü").replace("U:", "ü").lower()


def _quick_texts(path: Path) -> set[str]:
    lines = (
        line for line in path.read_text(encoding="utf-8-sig").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    )
    reader = csv.reader(lines)
    next(reader, None)
    return {row[0].strip() for row in reader if row and row[0].strip()}


def _cedict_rows(path: Path):
    opener = gzip.open if path.suffix == ".gz" else open
    with opener(path, "rt", encoding="utf-8") as source:
        for line in source:
            if line.startswith("#"):
                continue
            match = CEDICT_ENTRY.match(line.rstrip("\n"))
            if match:
                yield match.group(1), match.group(2), match.group(3)


def is_secondary(raw_reading: str, glosses: str) -> bool:
    """Whether this reading exists only for a name, variant, or cross-reference.

    CC-CEDICT capitalizes surnames and proper nouns, and marks the rest in the
    first gloss. Such a reading is kept only when a headword has no ordinary one.
    """
    return bool(raw_reading[:1].isupper() or SECONDARY_GLOSS.match(glosses.split("/", 1)[0]))


def build_readings(cedict: Path, quick_chars: Path, quick_phrases: Path) -> dict[str, str]:
    """Return ``text -> toned pinyin`` for every Quick-reachable headword.

    When a headword has several readings the first one in source order wins,
    except that a name/variant reading never displaces an ordinary one — 王 should
    teach ``wang2`` the word rather than ``Wang2`` the surname.

    CC-CEDICT ranks nothing beyond that, so a genuinely polyphonic headword whose
    readings are all ordinary (行 hang2/xing2, 重 chong2/zhong4) still resolves by
    source order. Multi-character text is unaffected: every phrase reading comes
    from an exact headword rather than from joining its characters.
    """
    reachable = _quick_texts(quick_chars) | _quick_texts(quick_phrases)
    readings: dict[str, str] = {}
    secondary_only: set[str] = set()

    for text, raw, glosses in _cedict_rows(cedict):
        if text not in reachable:
            continue
        reading = normalize_reading(raw)
        if not TONED_PINYIN.fullmatch(reading):
            continue
        secondary = is_secondary(raw, glosses)
        if text not in readings:
            readings[text] = reading
            if secondary:
                secondary_only.add(text)
        elif not secondary and text in secondary_only:
            readings[text] = reading
            secondary_only.discard(text)
    return readings


def write_readings(path: Path, readings: dict[str, str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as output:
        writer = csv.writer(output, lineterminator="\n")
        writer.writerow(("text", "pinyin"))
        writer.writerows(sorted(readings.items()))
    size = path.stat().st_size
    if size > MAX_ASSET_BYTES:
        raise ValueError(f"Pinyin reading asset size budget exceeded: {size} > {MAX_ASSET_BYTES}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cedict", type=Path, required=True)
    parser.add_argument("--quick-chars", type=Path, required=True)
    parser.add_argument("--quick-phrases", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    write_readings(
        args.output,
        build_readings(args.cedict, args.quick_chars, args.quick_phrases),
    )


if __name__ == "__main__":
    main()
