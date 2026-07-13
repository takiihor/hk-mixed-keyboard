#!/usr/bin/env python3
"""Build the bundled toneless Mandarin Pinyin corpus from CC-CEDICT."""

from __future__ import annotations

import argparse
import collections
import csv
import datetime
import gzip
import hashlib
import io
import math
import re
from pathlib import Path
from typing import TextIO


CEDICT_ENTRY = re.compile(r"^(\S+)\s+\S+\s+\[([^]]*)]\s+/.*/\s*$")
CEDICT_METADATA = re.compile(r"^#!\s+([a-z]+)=(.*)$")
REQUIRED_METADATA = {"entries", "version", "subversion", "format", "charset", "date"}
MAX_ROWS = 130_000
MAX_ASSET_BYTES = 4 * 1024 * 1024
MAX_FANOUT = 256
CANONICAL_FIRST_CANDIDATES = {"mao": "貓"}
CANONICAL_OVERRIDE_FREQUENCY = 1.0
RAW_PINYIN_CHARACTERS = re.compile(r"[A-Za-züÜ0-9: ,·'\-]+")
RAW_LETTER_PART = r"(?:[A-Za-züÜ]+|[A-Za-züÜ]*[uU]:[A-Za-züÜ]*)"
CANONICAL_SYLLABLES = set(
    """
    a ai an ang ao
    ba bai ban bang bao bei ben beng bi bian biao bie bin bing bo bu
    ca cai can cang cao ce cen ceng cha chai chan chang chao che chen cheng chi chong chou chu chua chuai chuan chuang chui chun chuo ci cong cou cu cuan cui cun cuo
    da dai dan dang dao de dei den deng di dia dian diao die ding diu dong dou du duan dui dun duo
    e ei en eng er
    fa fan fang fei fen feng fiao fo fou fu
    ga gai gan gang gao ge gei gen geng gong gou gu gua guai guan guang gui gun guo
    ha hai han hang hao he hei hen heng hm hng hong hou hu hua huai huan huang hui hun huo
    ji jia jian jiang jiao jie jin jing jiong jiu ju juan jue jun
    ka kai kan kang kao ke kei ken keng kong kou ku kua kuai kuan kuang kui kun kuo
    la lai lan lang lao le lei leng li lia lian liang liao lie lin ling liu lo long lou lu lv luan lve lun luo
    m ma mai man mang mao me mei men meng mi mian miao mie min ming miu mo mou mu
    n na nai nan nang nao ne nei nen neng ng ni nian niang niao nie nin ning niu nong nou nu nv nuan nve nun nuo
    o ou
    pa pai pan pang pao pei pen peng pi pian piao pie pin ping po pou pu
    qi qia qian qiang qiao qie qin qing qiong qiu qu quan que qun
    r ran rang rao re ren reng ri rong rou ru rua ruan rui run ruo
    sa sai san sang sao se sei sen seng sha shai shan shang shao she shei shen sheng shi shou shu shua shuai shuan shuang shui shun shuo si song sou su suan sui sun suo
    ta tai tan tang tao te tei teng ti tian tiao tie ting tong tou tu tuan tui tun tuo
    wa wai wan wang wei wen weng wo wu
    xi xia xian xiang xiao xie xin xing xiong xiu xu xuan xue xun
    ya yan yang yao ye yi yin ying yo yong you yu yuan yue yun
    za zai zan zang zao ze zei zen zeng zha zhai zhan zhang zhao zhe zhei zhen zheng zhi zhong zhou zhu zhua zhuai zhuan zhuang zhui zhun zhuo zi zong zou zu zuan zui zun zuo
    """.split()
)
MAX_CANONICAL_SYLLABLE_LENGTH = max(map(len, CANONICAL_SYLLABLES))
RAW_PINYIN_TOKEN = re.compile(rf"(?:\d+|{RAW_LETTER_PART}|(?:{RAW_LETTER_PART}[1-5])+)")


def validate_runtime_budgets(row_count: int, asset_size: int, max_fanout: int) -> None:
    if row_count > MAX_ROWS:
        raise ValueError(f"Pinyin row budget exceeded: {row_count} > {MAX_ROWS}")
    if asset_size > MAX_ASSET_BYTES:
        raise ValueError(
            f"Pinyin asset size budget exceeded: {asset_size} > {MAX_ASSET_BYTES} bytes"
        )
    if max_fanout > MAX_FANOUT:
        raise ValueError(f"Pinyin fanout budget exceeded: {max_fanout} > {MAX_FANOUT}")


def normalize_pinyin(value: str) -> str:
    """Return the lowercase, toneless, continuous keyboard spelling."""
    normalized = value.lower().replace("u:", "v").replace("ü", "v")
    return re.sub(r"[^a-z]", "", normalized)


def is_canonical_pinyin(value: str) -> bool:
    """Return whether a continuous key is fully covered by Mandarin syllables."""
    if not value:
        return False
    reachable = [False] * (len(value) + 1)
    reachable[0] = True
    for end in range(1, len(value) + 1):
        first_start = max(0, end - MAX_CANONICAL_SYLLABLE_LENGTH)
        reachable[end] = any(
            reachable[start] and value[start:end] in CANONICAL_SYLLABLES
            for start in range(first_start, end)
        )
    return reachable[-1]


def validate_raw_pinyin(value: str) -> None:
    """Reject syntax that lossy normalization could otherwise conceal."""
    if not value or RAW_PINYIN_CHARACTERS.fullmatch(value) is None:
        raise ValueError(f"invalid raw Pinyin syntax: {value!r}")
    token_source = value.replace(" : ", " ")
    tokens = [token for token in re.split(r"[ ,·'\-]+", token_source) if token]
    if not tokens or any(RAW_PINYIN_TOKEN.fullmatch(token) is None for token in tokens):
        raise ValueError(f"invalid raw Pinyin syntax: {value!r}")


def _read_frequency(path: Path, text_column: str) -> dict[str, float]:
    frequencies: dict[str, float] = {}
    with path.open(encoding="utf-8", newline="") as source:
        rows = csv.reader(line for line in source if not line.startswith("#"))
        header = next(rows)
        text_index = header.index(text_column)
        for row in rows:
            if len(row) <= text_index or len(row) < 3:
                continue
            text = row[text_index]
            try:
                frequency = float(row[-2])
                hk_core = int(row[-1])
            except ValueError as error:
                raise ValueError(f"Invalid frequency row in {path}: {row!r}") from error
            if not math.isfinite(frequency) or not 0.0 <= frequency <= 1.0:
                raise ValueError(
                    f"Frequency must be finite and between 0 and 1 in {path}: {frequency!r}"
                )
            if hk_core not in (0, 1):
                raise ValueError(f"hk_core must be 0 or 1 in {path}: {hk_core!r}")
            if not text or hk_core == 1:
                continue
            frequencies[text] = max(frequency, frequencies.get(text, 0.0))
    return frequencies


def _open_cedict(path: Path) -> TextIO:
    if path.suffix == ".gz":
        return gzip.open(path, mode="rt", encoding="utf-8")
    return path.open(encoding="utf-8")


def _fallback_frequency(traditional: str) -> float:
    if len(traditional) == 1:
        return 0.3
    if len(traditional) == 2:
        return 0.25
    return 0.2


def _validate_metadata(metadata: dict[str, str], source_date: str) -> int:
    missing = sorted(REQUIRED_METADATA - metadata.keys())
    if missing:
        raise ValueError(f"CC-CEDICT metadata missing required fields: {', '.join(missing)}")
    expected = {"version": "1", "subversion": "0", "format": "ts", "charset": "UTF-8"}
    wrong = {key: metadata[key] for key, value in expected.items() if metadata[key] != value}
    if wrong:
        raise ValueError(f"CC-CEDICT metadata has unsupported values: {wrong!r}")
    try:
        entries = int(metadata["entries"])
    except ValueError as error:
        raise ValueError("CC-CEDICT metadata entries must be an integer") from error
    if entries < 0:
        raise ValueError("CC-CEDICT metadata entries must be non-negative")
    try:
        datetime.datetime.fromisoformat(metadata["date"].replace("Z", "+00:00"))
    except ValueError as error:
        raise ValueError("CC-CEDICT metadata date must be ISO-8601") from error
    if source_date != metadata["date"]:
        raise ValueError(
            "CC-CEDICT source date mismatch: "
            f"expected embedded {metadata['date']!r}, got {source_date!r}"
        )
    return entries


def build_corpus(
    *,
    cedict_path: Path,
    char_frequency_path: Path,
    phrase_frequency_path: Path,
    output_path: Path,
    source_url: str,
    source_date: str,
    source_sha256: str,
) -> None:
    actual_sha256 = hashlib.sha256(cedict_path.read_bytes()).hexdigest()
    if actual_sha256 != source_sha256.lower():
        raise ValueError(
            "CC-CEDICT SHA-256 mismatch: "
            f"expected {source_sha256.lower()}, got {actual_sha256}"
        )

    frequencies = _read_frequency(char_frequency_path, "char")
    frequencies.update(_read_frequency(phrase_frequency_path, "phrase"))

    entries: set[tuple[str, str]] = set()
    metadata: dict[str, str] = {}
    parsed_entry_count = 0
    with _open_cedict(cedict_path) as source:
        for line_number, line in enumerate(source, start=1):
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
            parsed_entry_count += 1
            traditional, raw_pinyin = match.groups()
            try:
                validate_raw_pinyin(raw_pinyin)
            except ValueError as error:
                raise ValueError(f"{error} at line {line_number}") from error
            pinyin = normalize_pinyin(raw_pinyin)
            if not pinyin:
                raise ValueError(f"invalid normalized reading at line {line_number}")
            if not is_canonical_pinyin(pinyin):
                continue
            entries.add((pinyin, traditional))

    expected_entry_count = _validate_metadata(metadata, source_date)
    if parsed_entry_count != expected_entry_count:
        raise ValueError(
            "CC-CEDICT entry count mismatch: "
            f"metadata={expected_entry_count}, parsed={parsed_entry_count}"
        )

    version = metadata["version"]
    subversion = metadata["subversion"]

    scored_entries = [
        (key, text, frequencies.get(text, _fallback_frequency(text))) for key, text in entries
    ]
    for key, canonical_text in CANONICAL_FIRST_CANDIDATES.items():
        if (key, canonical_text) not in entries:
            raise ValueError(f"Canonical Pinyin candidate missing: {key}->{canonical_text}")
        competing = [score for entry_key, text, score in scored_entries if entry_key == key and text != canonical_text]
        if competing and max(competing) >= CANONICAL_OVERRIDE_FREQUENCY:
            raise ValueError(f"Canonical override cannot exceed competing score for {key}")
    scored_entries = [
        (
            key,
            text,
            CANONICAL_OVERRIDE_FREQUENCY
            if CANONICAL_FIRST_CANDIDATES.get(key) == text
            else frequency,
        )
        for key, text, frequency in scored_entries
    ]
    ranked_entries = sorted(scored_entries, key=lambda entry: (entry[0], -entry[2], entry[1]))

    output = io.StringIO(newline="")
    output.write("# Mandarin Pinyin dictionary derived from CC-CEDICT (CC BY-SA 4.0)\n")
    output.write("# Toneless continuous keys; u: and ü are normalized to v.\n")
    output.write("# Noncanonical readings are excluded using a Mandarin syllable inventory.\n")
    output.write(f"# Source URL: {source_url}\n")
    output.write(f"# Source date: {metadata['date']}\n")
    output.write(f"# Source SHA-256: {source_sha256}\n")
    output.write(f"# CC-CEDICT format: version={version}; subversion={subversion}\n")
    output.write(
        "# Fallback frequency: single character=0.3000; two-character word=0.2500; "
        "multi-character phrase=0.2000.\n"
    )
    output.write("# Canonical persisted override: mao->貓=1.0000.\n")
    output.write(
        f"# Runtime budgets: rows<={MAX_ROWS}; bytes<={MAX_ASSET_BYTES}; "
        f"fanout<={MAX_FANOUT}.\n"
    )
    writer = csv.writer(output, lineterminator="\n")
    writer.writerow(("pinyin", "chinese", "freq"))
    for pinyin, traditional, frequency in ranked_entries:
        writer.writerow((pinyin, traditional, f"{frequency:.4f}"))

    asset = output.getvalue().encode("utf-8")
    fanout = collections.Counter(entry[0] for entry in ranked_entries)
    validate_runtime_budgets(
        len(ranked_entries),
        len(asset),
        max(fanout.values(), default=0),
    )
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(asset)


def _arguments() -> argparse.Namespace:
    repository_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cedict", type=Path, required=True)
    parser.add_argument("--source-url", required=True)
    parser.add_argument("--source-date", required=True)
    parser.add_argument("--source-sha256", required=True)
    parser.add_argument(
        "--char-frequencies",
        type=Path,
        default=repository_root / "android/app/src/main/assets/corpus/hk_core_chars.csv",
    )
    parser.add_argument(
        "--phrase-frequencies",
        type=Path,
        default=repository_root / "android/app/src/main/assets/corpus/hk_core_phrases.csv",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=repository_root / "android/app/src/main/assets/corpus/pinyin.csv",
    )
    return parser.parse_args()


def main() -> None:
    arguments = _arguments()
    build_corpus(
        cedict_path=arguments.cedict,
        char_frequency_path=arguments.char_frequencies,
        phrase_frequency_path=arguments.phrase_frequencies,
        output_path=arguments.output,
        source_url=arguments.source_url,
        source_date=arguments.source_date,
        source_sha256=arguments.source_sha256,
    )


if __name__ == "__main__":
    main()
