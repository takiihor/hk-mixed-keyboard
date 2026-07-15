import hashlib
import importlib.util
import io
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = REPO_ROOT / "corpus/tools/build_jyutping_tones.py"
SPEC = importlib.util.spec_from_file_location("build_jyutping_tones", MODULE_PATH)
assert SPEC and SPEC.loader
builder = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = builder
SPEC.loader.exec_module(builder)

COMMIT = "c99b16e44d2df77a5cb8fb0867dd2bab7a112cb0"
SOURCE_URL = f"https://codeload.github.com/rime/rime-cantonese/tar.gz/{COMMIT}"
SOURCE_DATE = "2026-07-02T11:05:50Z"
PINNED_SOURCE = (
    REPO_ROOT
    / "corpus/sources/upstream"
    / f"rime-cantonese-{COMMIT}.tar.gz"
)
SHIPPED_REFERENCE = REPO_ROOT / "corpus/reference/jyutping_tonal_readings.csv"


class JyutpingTonesBuilderTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.source = self.root / "source.tar.gz"
        # 你 (BMP), 啊 (multi-reading), 𠮟 (supplementary plane U+20B9F).
        chars = (
            "---\nname: chars\n...\n"
            "你\tnei5\n"
            "啊\ta3\n"
            "啊\taa3\n"
            "啊\ta3\n"  # duplicate reading must collapse
            "𠮟\thot3\n"
        )
        with tarfile.open(self.source, "w:gz") as archive:
            encoded = chars.encode("utf-8")
            info = tarfile.TarInfo("rime-cantonese-test/jyut6ping3.chars.dict.yaml")
            info.size = len(encoded)
            info.mtime = 0
            archive.addfile(info, io.BytesIO(encoded))
        self.source_hash = hashlib.sha256(self.source.read_bytes()).hexdigest()

    def tearDown(self):
        self.temp_dir.cleanup()

    def build(self, output: Path, source_hash: str | None = None):
        return builder.build_reference(
            self.source,
            output,
            source_url=SOURCE_URL,
            source_commit=COMMIT,
            source_date=SOURCE_DATE,
            source_sha256=source_hash or self.source_hash,
        )

    @staticmethod
    def data_rows(path: Path):
        rows = {}
        with path.open("r", encoding="utf-8", newline="") as handle:
            for line in handle:
                if line.startswith("#") or line.startswith("chinese,"):
                    continue
                text, code_points, readings = line.rstrip("\n").split(",")
                rows[text] = (code_points, readings)
        return rows

    def test_preserves_tones_dedupes_and_orders_readings(self):
        output = self.root / "tones.csv"
        stats = self.build(output)
        rows = self.data_rows(output)
        # Every tonal reading is kept with its 1-6 digit, in source order, deduped.
        self.assertEqual(rows["啊"][1], "a3 aa3")
        self.assertEqual(rows["你"][1], "nei5")
        self.assertEqual(stats.characters, 3)
        self.assertEqual(stats.readings, 4)

    def test_supplementary_plane_handled_by_code_point(self):
        output = self.root / "tones.csv"
        stats = self.build(output)
        rows = self.data_rows(output)
        self.assertIn("𠮟", rows)
        self.assertEqual(rows["𠮟"][0], "U+20B9F")
        self.assertEqual(rows["𠮟"][1], "hot3")
        self.assertEqual(stats.supplementary, 1)

    def test_rejects_source_hash_mismatch(self):
        output = self.root / "tones.csv"
        with self.assertRaises(ValueError):
            self.build(output, source_hash="0" * 64)

    @unittest.skipUnless(
        PINNED_SOURCE.is_file() and SHIPPED_REFERENCE.is_file(),
        "pinned rime-cantonese snapshot or shipped reference not present",
    )
    def test_shipped_reference_rebuilds_byte_for_byte(self):
        # Rebuild from the pinned upstream and compare against the committed file.
        rebuilt = self.root / "shipped.csv"
        builder.build_reference(
            PINNED_SOURCE,
            rebuilt,
            source_url=SOURCE_URL,
            source_commit=COMMIT,
            source_date=SOURCE_DATE,
            source_sha256=hashlib.sha256(PINNED_SOURCE.read_bytes()).hexdigest(),
        )
        self.assertEqual(
            hashlib.sha256(rebuilt.read_bytes()).hexdigest(),
            hashlib.sha256(SHIPPED_REFERENCE.read_bytes()).hexdigest(),
            "shipped tonal reference is stale; rerun build_jyutping_tones.py",
        )


if __name__ == "__main__":
    unittest.main()
