import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("build_quick_corpus", ROOT / "corpus/tools/build_quick_corpus.py")
assert SPEC and SPEC.loader
builder = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = builder
SPEC.loader.exec_module(builder)


class RealQuickAssetTest(unittest.TestCase):
    def test_generated_row_validation_rejects_duplicates_and_noncanonical_codes(self):
        with self.assertRaisesRegex(ValueError, "duplicate"):
            builder.validate_generated_rows(
                [("時", "aj", "aij", 1, 1), ("時", "aj", "aij", 1, 1)],
                [],
            )
        with self.assertRaisesRegex(ValueError, "noncanonical"):
            builder.validate_generated_rows([("時", "aa", "aij", 1, 1)], [])

    def test_pinned_sources_rebuild_both_assets_exactly(self):
        upstream = ROOT / "corpus/sources/upstream"
        with tempfile.TemporaryDirectory() as temporary:
            temporary = Path(temporary)
            chars = temporary / "chars.csv"
            phrases = temporary / "phrases.csv"
            stats = builder.build_corpora(
                cangjie_source=upstream / "rime-cangjie-52d90a1b1312e74042b38c1cbc8142defbc53171.tar.gz",
                cangjie_sha256="18d989bf21d0bb86b402f18be4dd060bf2653896c95448afb01d4da2e6be5464",
                essay_source=upstream / "rime-essay-0debef3f5ae37f081758dd8bbd1fe79719410f7c.tar.gz",
                essay_sha256="4f32414039eb7f8a876acd8e94bd81c78fefafde56a352f7a3b8b2b74ca09c71",
                reviewed_chars=ROOT / "corpus/internal_stage1/hk_core_chars.csv",
                reviewed_phrases=ROOT / "corpus/internal_stage1/hk_core_phrases.csv",
                char_output=chars,
                phrase_output=phrases,
            )
            self.assertEqual(18_200, stats.char_rows)
            self.assertEqual(40_120, stats.phrase_rows)
            assets = ROOT / "android/app/src/main/assets/corpus"
            self.assertEqual((assets / "hk_core_chars.csv").read_bytes(), chars.read_bytes())
            self.assertEqual((assets / "hk_core_phrases.csv").read_bytes(), phrases.read_bytes())

    def test_rejects_unpinned_source_before_writing(self):
        upstream = ROOT / "corpus/sources/upstream"
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(ValueError, "source SHA-256 mismatch"):
                builder.build_corpora(
                    cangjie_source=upstream / "rime-cangjie-52d90a1b1312e74042b38c1cbc8142defbc53171.tar.gz",
                    cangjie_sha256="0" * 64,
                    essay_source=upstream / "rime-essay-0debef3f5ae37f081758dd8bbd1fe79719410f7c.tar.gz",
                    essay_sha256="4f32414039eb7f8a876acd8e94bd81c78fefafde56a352f7a3b8b2b74ca09c71",
                    reviewed_chars=ROOT / "corpus/internal_stage1/hk_core_chars.csv",
                    reviewed_phrases=ROOT / "corpus/internal_stage1/hk_core_phrases.csv",
                    char_output=Path(temporary) / "chars.csv",
                    phrase_output=Path(temporary) / "phrases.csv",
                )
