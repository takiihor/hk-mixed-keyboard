import hashlib
import io
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path


TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

import build_t2s_map as generator


REPO_ROOT = Path(__file__).resolve().parents[3]
OPENCC = REPO_ROOT / "corpus/sources/upstream/opencc-ea6af199fefd6ab9b79015b5b93fef8b6b95c8b5.tar.gz"
SHIPPED = REPO_ROOT / "android/app/src/main/assets/t2s/t2s_map.tsv"


class T2SMapBuilderTest(unittest.TestCase):
    def test_phrase_entries_and_first_opencc_target_build_deterministically(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = self._archive(
                root / "opencc.tar.gz",
                {
                    "fixture/data/dictionary/TSCharacters.txt": "# comment\n乾\t干 乾\n發\t发\n",
                    "fixture/data/dictionary/TSPhrases.txt": "乾隆\t乾隆\n發現\t发现\n",
                },
            )
            output = root / "t2s.tsv"

            generator.build_map(source, self._sha(source), output)

            self.assertEqual(
                "# Traditional→Simplified map derived from pinned OpenCC TSCharacters + TSPhrases.\n"
                "# Phrase rows are retained as greedy overrides; the first OpenCC target is used.\n"
                "乾隆\t乾隆\n"
                "發現\t发现\n"
                "乾\t干\n"
                "發\t发\n",
                output.read_text(encoding="utf-8"),
            )

    @unittest.skipUnless(OPENCC.is_file() and SHIPPED.is_file(), "OpenCC source unavailable")
    def test_real_shipped_map_rebuilds_byte_for_byte(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "t2s.tsv"
            generator.build_map(OPENCC, self._sha(OPENCC), output)
            self.assertEqual(SHIPPED.read_bytes(), output.read_bytes())

    @staticmethod
    def _archive(path: Path, files: dict[str, str]) -> Path:
        with tarfile.open(path, "w:gz") as archive:
            for name, content in files.items():
                encoded = content.encode("utf-8")
                info = tarfile.TarInfo(name)
                info.size = len(encoded)
                archive.addfile(info, io.BytesIO(encoded))
        return path

    @staticmethod
    def _sha(path: Path) -> str:
        return hashlib.sha256(path.read_bytes()).hexdigest()


if __name__ == "__main__":
    unittest.main()
