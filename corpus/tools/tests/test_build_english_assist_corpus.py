import csv
import hashlib
import io
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path


TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

import build_english_assist_corpus as generator


REPO_ROOT = Path(__file__).resolve().parents[3]
CEDICT = REPO_ROOT / "corpus/sources/upstream/cc-cedict-2026-07-12T162325Z.u8.gz"
FREQUENCY_WORDS = (
    REPO_ROOT / "corpus/sources/upstream/"
    "frequencywords-en-50k-525f9b560de45753a5ea01069454e72e9aa541c6.txt"
)
ESSAY = REPO_ROOT / "corpus/sources/upstream/rime-essay-0debef3f5ae37f081758dd8bbd1fe79719410f7c.tar.gz"
CURATED = REPO_ROOT / "corpus/internal_stage1/english_assist.csv"
SHIPPED = REPO_ROOT / "android/app/src/main/assets/corpus/english_assist.csv"


class EnglishAssistCorpusBuilderTest(unittest.TestCase):
    def test_builds_only_frequency_vocabulary_glosses_and_preserves_curated_rows(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cedict = root / "cedict.u8"
            cedict.write_text(
                "#! version=1\n"
                "#! subversion=0\n"
                "#! format=ts\n"
                "#! charset=UTF-8\n"
                "#! date=2026-07-14T00:00:00Z\n"
                "#! entries=2\n"
                "原子彈 原子弹 [yuan2 zi3 dan4] /atomic bomb; A-bomb/\n"
                "溝通 沟通 [gou1 tong1] /to communicate/noise/\n",
                encoding="utf-8",
            )
            words = root / "words.txt"
            words.write_text("a-bomb 10\ncommunicate 100\n", encoding="utf-8")
            essay = self._essay_archive(root / "essay.tar.gz", "原子彈\t20\n溝通\t10\n")
            curated = root / "curated.csv"
            curated.write_text(
                "english,chinese,freq\ncommunication,溝通,0.850\n",
                encoding="utf-8",
            )
            output = root / "assist.csv"

            generator.build_corpus(
                cedict_path=cedict,
                cedict_sha256=self._sha(cedict),
                frequency_words_path=words,
                frequency_words_sha256=self._sha(words),
                essay_source=essay,
                essay_sha256=self._sha(essay),
                curated_path=curated,
                output_path=output,
                source_url="https://example.invalid/cedict",
                source_date="2026-07-14T00:00:00Z",
            )

            rows = self._rows(output)
            self.assertIn(("a-bomb", "原子彈"), rows)
            self.assertIn(("communicate", "溝通"), rows)
            self.assertIn(("communication", "溝通"), rows)
            self.assertNotIn(("noise", "溝通"), rows)

    def test_rejects_a_frequency_source_hash_mismatch(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            words = root / "words.txt"
            words.write_text("hello 1\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "frequency vocabulary SHA-256 mismatch"):
                generator.load_frequency_words(words, "0" * 64)

    @unittest.skipUnless(all(path.is_file() for path in (CEDICT, FREQUENCY_WORDS, ESSAY, CURATED, SHIPPED)), "sources unavailable")
    def test_real_shipped_asset_rebuilds_byte_for_byte(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "english_assist.csv"
            generator.build_corpus(
                cedict_path=CEDICT,
                cedict_sha256=self._sha(CEDICT),
                frequency_words_path=FREQUENCY_WORDS,
                frequency_words_sha256=self._sha(FREQUENCY_WORDS),
                essay_source=ESSAY,
                essay_sha256=self._sha(ESSAY),
                curated_path=CURATED,
                output_path=output,
                source_url="https://cc-cedict.org/editor/editor_export_cedict.php?c=gz",
                source_date="2026-07-12T16:23:25Z",
            )
            self.assertEqual(SHIPPED.read_bytes(), output.read_bytes())

    @staticmethod
    def _essay_archive(path: Path, content: str) -> Path:
        with tarfile.open(path, "w:gz") as archive:
            encoded = content.encode("utf-8")
            info = tarfile.TarInfo("fixture/essay.txt")
            info.size = len(encoded)
            archive.addfile(info, io.BytesIO(encoded))
        return path

    @staticmethod
    def _rows(path: Path) -> set[tuple[str, str]]:
        with path.open(encoding="utf-8", newline="") as handle:
            reader = csv.reader(line for line in handle if line.strip() and not line.startswith("#"))
            next(reader)
            return {(row[0], row[1]) for row in reader}

    @staticmethod
    def _sha(path: Path) -> str:
        return hashlib.sha256(path.read_bytes()).hexdigest()


if __name__ == "__main__":
    unittest.main()
