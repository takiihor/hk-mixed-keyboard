import csv
import hashlib
import importlib.util
import io
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = REPO_ROOT / "corpus/tools/build_jyutping_corpus.py"
SPEC = importlib.util.spec_from_file_location("build_jyutping_corpus", MODULE_PATH)
assert SPEC and SPEC.loader
builder = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = builder
SPEC.loader.exec_module(builder)

COMMIT = "c99b16e44d2df77a5cb8fb0867dd2bab7a112cb0"
SOURCE_URL = f"https://codeload.github.com/rime/rime-cantonese/tar.gz/{COMMIT}"
SOURCE_DATE = "2026-07-02T11:05:50Z"


class JyutpingCorpusBuilderTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.source = self.root / "source.tar.gz"
        files = {
            "essay-cantonese.txt": "喺\t10000\n係\t20000\n喺度\t1000\n罕詞\t100\n",
            "jyut6ping3.chars.dict.yaml": (
                "---\nname: chars\n...\n喺\thai2\n係\thai6\n喺\thai2\n"
            ),
            "jyut6ping3.words.dict.yaml": (
                "---\nname: words\n...\n喺度\thai2 dou6\n罕詞\thon2 ci4\n"
            ),
        }
        with tarfile.open(self.source, "w:gz") as archive:
            for filename, content in files.items():
                encoded = content.encode("utf-8")
                info = tarfile.TarInfo(f"rime-cantonese-test/{filename}")
                info.size = len(encoded)
                info.mtime = 0
                archive.addfile(info, io.BytesIO(encoded))
        self.source_hash = hashlib.sha256(self.source.read_bytes()).hexdigest()

    def tearDown(self):
        self.temp_dir.cleanup()

    def build(self, output: Path, source_hash: str | None = None):
        return builder.build_corpus(
            self.source,
            output,
            source_url=SOURCE_URL,
            source_commit=COMMIT,
            source_date=SOURCE_DATE,
            source_sha256=source_hash or self.source_hash,
        )

    @staticmethod
    def rows(path: Path):
        with path.open("r", encoding="utf-8", newline="") as handle:
            content = (line for line in handle if not line.startswith("#"))
            return list(csv.DictReader(content))

    def test_normalizes_tones_deduplicates_and_filters_rare_words(self):
        output = self.root / "jyutping.csv"
        stats = self.build(output)
        rows = self.rows(output)
        self.assertEqual(3, stats.rows)
        self.assertEqual(3, len(rows))
        self.assertEqual(1, sum(row["chinese"] == "喺" for row in rows))
        self.assertIn(
            {"jyutping": "haidou", "chinese": "喺度", "freq": "0.2545"},
            rows,
        )
        self.assertFalse(any(row["chinese"] == "罕詞" for row in rows))

    def test_generation_is_byte_for_byte_deterministic(self):
        first = self.root / "first.csv"
        second = self.root / "second.csv"
        self.build(first)
        self.build(second)
        self.assertEqual(first.read_bytes(), second.read_bytes())

    def test_rejects_source_checksum_mismatch_without_writing_output(self):
        output = self.root / "jyutping.csv"
        with self.assertRaisesRegex(ValueError, "source SHA-256 mismatch"):
            self.build(output, "0" * 64)
        self.assertFalse(output.exists())

    def test_rejects_key_over_composition_ceiling(self):
        long_code = " ".join(["a1"] * (builder.MAX_KEY_LENGTH + 1))
        files = {
            "essay-cantonese.txt": "超長\t10000\n甲乙\t10000\n",
            "jyut6ping3.chars.dict.yaml": f"---\nname: chars\n...\n超長\t{long_code}\n",
            "jyut6ping3.words.dict.yaml": "---\nname: words\n...\n甲乙\taa1 jat1\n",
        }
        with tarfile.open(self.source, "w:gz") as archive:
            for filename, content in files.items():
                encoded = content.encode("utf-8")
                info = tarfile.TarInfo(f"rime-cantonese-test/{filename}")
                info.size = len(encoded)
                info.mtime = 0
                archive.addfile(info, io.BytesIO(encoded))
        self.source_hash = hashlib.sha256(self.source.read_bytes()).hexdigest()

        with self.assertRaisesRegex(ValueError, "key length budget"):
            self.build(self.root / "jyutping.csv")


class RealJyutpingAssetTest(unittest.TestCase):
    def test_vendored_source_rebuilds_production_asset_exactly(self):
        source = (
            REPO_ROOT
            / "corpus/sources/upstream"
            / f"rime-cantonese-{COMMIT}.tar.gz"
        )
        source_hash = "83fddd062104abac17fbc0c039f2c22c57671e96c5395a00abc326fd9ceeedfd"
        production = REPO_ROOT / "android/app/src/main/assets/corpus/jyutping.csv"
        with tempfile.TemporaryDirectory() as temporary:
            rebuilt = Path(temporary) / "jyutping.csv"
            stats = builder.build_corpus(
                source,
                rebuilt,
                source_url=SOURCE_URL,
                source_commit=COMMIT,
                source_date=SOURCE_DATE,
                source_sha256=source_hash,
            )
            self.assertEqual(89_778, stats.rows)
            self.assertLessEqual(stats.bytes, builder.MAX_BYTES)
            self.assertLessEqual(stats.max_fanout, builder.MAX_FANOUT)
            self.assertLessEqual(stats.max_key_length, builder.MAX_KEY_LENGTH)
            self.assertEqual(production.read_bytes(), rebuilt.read_bytes())
