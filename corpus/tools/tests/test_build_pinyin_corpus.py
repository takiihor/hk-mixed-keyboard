import csv
import collections
import hashlib
import io
import sys
import tempfile
import unittest
from pathlib import Path


TOOLS_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS_DIR))

import build_pinyin_corpus as generator  # noqa: E402
from build_pinyin_corpus import (  # noqa: E402
    MAX_ASSET_BYTES,
    MAX_FANOUT,
    MAX_KEY_LENGTH,
    MAX_ROWS,
    build_corpus,
    normalize_pinyin,
    is_canonical_pinyin,
    validate_runtime_budgets,
)


FIXTURE = Path(__file__).parent / "fixtures" / "cedict_sample.u8"
REAL_ASSET = TOOLS_DIR.parents[1] / "android/app/src/main/assets/corpus/pinyin.csv"
REAL_CEDICT = TOOLS_DIR.parents[1] / "corpus/sources/upstream/cc-cedict-2026-07-12T162325Z.u8.gz"
REAL_CEDICT_URL = "https://cc-cedict.org/editor/editor_export_cedict.php?c=gz"
REAL_CEDICT_DATE = "2026-07-12T16:23:25Z"
REAL_CEDICT_SHA256 = "90e2881776366f606171a977a1f54786196f428ba604c728a279d4aaff7223a8"


class NormalizePinyinTest(unittest.TestCase):
    def test_removes_tones_separators_and_normalizes_umlaut(self):
        cases = {
            "Ni3 Hao3": "nihao",
            "Xi1'an1": "xian",
            "xi1-an1": "xian",
            "lu:3": "lv",
            "lü4": "lv",
            "Ai4 de2 hua2·Da2 la1 di4": "aidehuadaladi",
            "bai3 hua1 qi2 fang4, bai3 jia1 zheng1 ming2": "baihuaqifangbaijiazhengming",
        }
        for source, expected in cases.items():
            with self.subTest(source=source):
                self.assertEqual(normalize_pinyin(source), expected)

    def test_validates_pinned_raw_pinyin_syntax_before_normalization(self):
        valid = (
            "mao1",
            "shi2ke4",
            "nu:3",
            "lu:e4",
            "A C G",
            "yi1 bu4 zuo4 , er4 bu4 xiu1",
            "Ya4 li4 shan1 da4 · Du4 bu4 qie1 ke4",
            "11 Qu1",
            "Mao2 Ze2 dong1 : Xian1 wei2 ren2 zhi1 de5 Gu4 shi5",
        )
        for reading in valid:
            with self.subTest(reading=reading):
                generator.validate_raw_pinyin(reading)

    def test_rejects_lossy_or_malformed_raw_pinyin_syntax(self):
        invalid = (
            "mao1@@evil",
            "mao6",
            "ma0",
            "[mao1]",
            "123abc",
            "ma1o",
            "1mao",
            "mao11",
        )
        for reading in invalid:
            with self.subTest(reading=reading):
                with self.assertRaisesRegex(ValueError, "invalid raw Pinyin"):
                    generator.validate_raw_pinyin(reading)

    def test_canonical_inventory_accepts_real_spellings_and_rejects_placeholders(self):
        for reading in (
            "nihao", "tiananmen", "lv", "nve", "ju", "qu", "xu", "yu",
            "fiao", "sei", "tei",
        ):
            with self.subTest(reading=reading):
                self.assertTrue(is_canonical_pinyin(reading))
        for reading in ("xx", "xxxx", "acg"):
            with self.subTest(reading=reading):
                self.assertFalse(is_canonical_pinyin(reading))


class BuildCorpusTest(unittest.TestCase):
    def _build(
        self,
        output: Path,
        *,
        source: Path = FIXTURE,
        source_date: str = "2026-07-12T00:00:00Z",
        char_frequencies: Path | None = None,
        phrase_frequencies: Path | None = None,
    ) -> bytes:
        source_sha256 = hashlib.sha256(source.read_bytes()).hexdigest()
        build_corpus(
            cedict_path=source,
            char_frequency_path=char_frequencies
            or TOOLS_DIR.parent / "internal_stage1/hk_core_chars.csv",
            phrase_frequency_path=phrase_frequencies
            or TOOLS_DIR.parent / "internal_stage1/hk_core_phrases.csv",
            output_path=output,
            source_url="https://example.test/cedict.gz",
            source_date=source_date,
            source_sha256=source_sha256,
        )
        return output.read_bytes()

    def test_emits_metadata_header_and_traditional_examples(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "pinyin.csv"
            content = self._build(output).decode("utf-8")

        self.assertIn("# Source URL: https://example.test/cedict.gz", content)
        self.assertIn("# Source date: 2026-07-12T00:00:00Z", content)
        self.assertIn(f"# Source SHA-256: {hashlib.sha256(FIXTURE.read_bytes()).hexdigest()}", content)
        self.assertIn("# CC-CEDICT format: version=1; subversion=0", content)
        self.assertIn("# Canonical persisted override: mao->貓=1.0000", content)
        rows = list(csv.DictReader(line for line in io.StringIO(content) if not line.startswith("#")))
        values = {(row["pinyin"], row["chinese"]) for row in rows}
        self.assertTrue(
            {
                ("nihao", "你好"),
                ("xianggang", "香港"),
                ("putonghua", "普通話"),
                ("mao", "貓"),
                ("lv", "呂"),
                ("lv", "綠"),
            }.issubset(values)
        )

    def test_deduplicates_normalized_key_and_traditional_text(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "pinyin.csv"
            content = self._build(output).decode("utf-8")

        rows = list(csv.DictReader(line for line in io.StringIO(content) if not line.startswith("#")))
        self.assertEqual(
            [(row["pinyin"], row["chinese"]) for row in rows].count(("xian", "西安")),
            1,
        )

    def test_filters_noncanonical_placeholder_readings(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "placeholder.u8"
            source.write_text(
                FIXTURE.read_text(encoding="utf-8")
                .replace("#! entries=8", "#! entries=9")
                + "昔 昔 [xx] /placeholder/\n",
                encoding="utf-8",
            )
            content = self._build(root / "pinyin.csv", source=source).decode("utf-8")

        rows = list(csv.DictReader(line for line in io.StringIO(content) if not line.startswith("#")))
        self.assertNotIn(("xx", "昔"), {(row["pinyin"], row["chinese"]) for row in rows})

    def test_filters_non_han_headwords_before_shipping(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "non-han.u8"
            source.write_text(
                FIXTURE.read_text(encoding="utf-8")
                .replace("#! entries=8", "#! entries=9")
                + "P P [pi1] /letter P/\n",
                encoding="utf-8",
            )
            content = self._build(root / "pinyin.csv", source=source).decode("utf-8")

        rows = list(csv.DictReader(line for line in io.StringIO(content) if not line.startswith("#")))
        self.assertNotIn(("pi", "P"), {(row["pinyin"], row["chinese"]) for row in rows})

    def test_rejects_canonical_key_over_the_composition_ceiling(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "overlong.u8"
            source.write_text(
                FIXTURE.read_text(encoding="utf-8")
                .replace("#! entries=8", "#! entries=9")
                + f"長 長 [{'a1' * (MAX_KEY_LENGTH + 1)}] /overlong/\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "key length budget"):
                self._build(root / "pinyin.csv", source=source)

    def test_uses_hk_core_frequency_before_length_class_fallbacks(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "pinyin.csv"
            content = self._build(output).decode("utf-8")

        rows = list(csv.DictReader(line for line in io.StringIO(content) if not line.startswith("#")))
        frequencies = {(row["pinyin"], row["chinese"]): row["freq"] for row in rows}
        self.assertEqual(frequencies[("nihao", "你好")], "0.9400")
        self.assertEqual(frequencies[("mao", "貓")], "1.0000")
        self.assertEqual(frequencies[("lv", "綠")], "0.3000")
        self.assertEqual(frequencies[("xianggang", "香港")], "0.2500")
        self.assertEqual(frequencies[("putonghua", "普通話")], "0.2000")

    def test_rejects_source_checksum_mismatch(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "CC-CEDICT SHA-256 mismatch"):
                build_corpus(
                    cedict_path=FIXTURE,
                    char_frequency_path=TOOLS_DIR.parent / "internal_stage1/hk_core_chars.csv",
                    phrase_frequency_path=TOOLS_DIR.parent / "internal_stage1/hk_core_phrases.csv",
                    output_path=Path(directory) / "pinyin.csv",
                    source_url="https://example.test/cedict.gz",
                    source_date="2026-07-12T00:00:00Z",
                    source_sha256="0" * 64,
                )

    def test_rejects_missing_or_wrong_required_metadata(self):
        base = FIXTURE.read_text(encoding="utf-8")
        cases = {
            "missing charset": base.replace("#! charset=UTF-8\n", ""),
            "wrong format": base.replace("#! format=ts", "#! format=st"),
        }
        with tempfile.TemporaryDirectory() as directory:
            for name, content in cases.items():
                with self.subTest(name=name):
                    source = Path(directory) / f"{name}.u8"
                    source.write_text(content, encoding="utf-8")
                    with self.assertRaisesRegex(ValueError, "CC-CEDICT metadata"):
                        self._build(Path(directory) / f"{name}.csv", source=source)

    def test_rejects_source_date_different_from_embedded_date(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "source date mismatch"):
                self._build(
                    Path(directory) / "pinyin.csv",
                    source_date="2026-07-13T00:00:00Z",
                )

    def test_rejects_malformed_record_invalid_reading_and_count_mismatch(self):
        base = FIXTURE.read_text(encoding="utf-8")
        cases = {
            "malformed record": base + "not a CC-CEDICT record\n",
            "invalid reading": base.replace("[mao1]", "[123]"),
            "count mismatch": base.replace("#! entries=8", "#! entries=9"),
        }
        expected = {
            "malformed record": "malformed CC-CEDICT record",
            "invalid reading": "invalid normalized reading",
            "count mismatch": "entry count mismatch",
        }
        with tempfile.TemporaryDirectory() as directory:
            for name, content in cases.items():
                with self.subTest(name=name):
                    source = Path(directory) / f"{name}.u8"
                    source.write_text(content, encoding="utf-8")
                    with self.assertRaisesRegex(ValueError, expected[name]):
                        self._build(Path(directory) / f"{name}.csv", source=source)

    def test_ignores_frequency_from_hk_core_rows(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            chars = root / "chars.csv"
            phrases = root / "phrases.csv"
            chars.write_text(
                "char,quick_code,cangjie_code,freq,hk_core\n綠,vm,vfd,0.999,1\n",
                encoding="utf-8",
            )
            phrases.write_text("phrase,quick_code,freq,hk_core\n", encoding="utf-8")
            content = self._build(
                root / "pinyin.csv",
                char_frequencies=chars,
                phrase_frequencies=phrases,
            ).decode("utf-8")
        rows = list(csv.DictReader(line for line in io.StringIO(content) if not line.startswith("#")))
        frequencies = {(row["pinyin"], row["chinese"]): row["freq"] for row in rows}
        self.assertEqual(frequencies[("lv", "綠")], "0.3000")

    def test_rejects_non_finite_or_out_of_range_frequency(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            phrases = root / "phrases.csv"
            phrases.write_text("phrase,quick_code,freq,hk_core\n", encoding="utf-8")
            for value in ("nan", "inf", "-0.1", "1.1"):
                with self.subTest(value=value):
                    chars = root / f"chars-{value}.csv"
                    chars.write_text(
                        f"char,quick_code,cangjie_code,freq,hk_core\n貓,bw,bhtw,{value},0\n",
                        encoding="utf-8",
                    )
                    with self.assertRaisesRegex(ValueError, "finite and between 0 and 1"):
                        self._build(
                            root / f"pinyin-{value}.csv",
                            char_frequencies=chars,
                            phrase_frequencies=phrases,
                        )

    def test_generation_is_byte_for_byte_deterministic(self):
        with tempfile.TemporaryDirectory() as directory:
            first = self._build(Path(directory) / "first.csv")
            second = self._build(Path(directory) / "second.csv")

        self.assertEqual(hashlib.sha256(first).digest(), hashlib.sha256(second).digest())


class RuntimeBudgetTest(unittest.TestCase):
    def test_rejects_each_budget_overrun(self):
        cases = (
            (MAX_ROWS + 1, 1, 1, "row budget"),
            (1, MAX_ASSET_BYTES + 1, 1, "asset size budget"),
            (1, 1, MAX_FANOUT + 1, "fanout budget"),
        )
        for rows, size, fanout, message in cases:
            with self.subTest(message=message):
                with self.assertRaisesRegex(ValueError, message):
                    validate_runtime_budgets(rows, size, fanout)

    def test_real_asset_stays_within_budgets(self):
        with REAL_ASSET.open(encoding="utf-8", newline="") as source:
            rows = list(csv.DictReader(line for line in source if not line.startswith("#")))
        fanout = collections.Counter(row["pinyin"] for row in rows)
        validate_runtime_budgets(len(rows), REAL_ASSET.stat().st_size, max(fanout.values()))
        self.assertLessEqual(max(len(row["pinyin"]) for row in rows), MAX_KEY_LENGTH)
        self.assertTrue(all(generator.is_han_only(row["chinese"]) for row in rows))


class RealAssetRankingTest(unittest.TestCase):
    def test_pinned_inputs_regenerate_the_shipped_asset(self):
        with tempfile.TemporaryDirectory() as directory:
            generated = Path(directory) / "pinyin.csv"
            build_corpus(
                cedict_path=REAL_CEDICT,
                char_frequency_path=TOOLS_DIR.parents[1]
                / "android/app/src/main/assets/corpus/hk_core_chars.csv",
                phrase_frequency_path=TOOLS_DIR.parents[1]
                / "android/app/src/main/assets/corpus/hk_core_phrases.csv",
                output_path=generated,
                source_url=REAL_CEDICT_URL,
                source_date=REAL_CEDICT_DATE,
                source_sha256=REAL_CEDICT_SHA256,
            )
            self.assertEqual(REAL_ASSET.read_bytes(), generated.read_bytes())

    def test_persisted_frequency_ranks_mandarin_candidates_first(self):
        with REAL_ASSET.open(encoding="utf-8", newline="") as source:
            rows = list(csv.DictReader(line for line in source if not line.startswith("#")))
        for key, expected in (("mao", "貓"), ("ge", "個"), ("di", "的")):
            with self.subTest(key=key):
                candidates = [row for row in rows if row["pinyin"] == key]
                ranked = sorted(candidates, key=lambda row: float(row["freq"]), reverse=True)
                self.assertEqual(ranked[0]["chinese"], expected)
                self.assertGreater(
                    float(ranked[0]["freq"]),
                    max(float(row["freq"]) for row in ranked[1:]),
                )


if __name__ == "__main__":
    unittest.main()
