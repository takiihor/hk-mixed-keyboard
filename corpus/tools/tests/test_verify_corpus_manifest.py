import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = REPO_ROOT / "corpus/tools/verify_corpus_manifest.py"
SPEC = importlib.util.spec_from_file_location("verify_corpus_manifest", MODULE_PATH)
assert SPEC and SPEC.loader
verifier = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verifier)


class CorpusManifestVerifierTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.asset_dir = self.root / "android/app/src/main/assets/corpus"
        self.t2s_dir = self.root / "android/app/src/main/assets/t2s"
        self.asset_dir.mkdir(parents=True)
        self.t2s_dir.mkdir(parents=True)
        self.asset = self.asset_dir / "sample.csv"
        self.asset.write_text("# comment\nkey,value\na,甲\nb,乙\n", encoding="utf-8")

    def tearDown(self):
        self.temp_dir.cleanup()

    def manifest(self, **asset_changes):
        asset = {
            "path": "android/app/src/main/assets/corpus/sample.csv",
            "format": "csv",
            "header": True,
            "data_rows": 2,
            "bytes": self.asset.stat().st_size,
            "sha256": hashlib.sha256(self.asset.read_bytes()).hexdigest(),
            "provenance": {
                "status": "reviewed-curated",
                "licenses": ["Project-Curated"],
                "note": "test fixture",
            },
        }
        asset.update(asset_changes)
        manifest_path = self.root / "manifest.json"
        manifest_path.write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "corpus_content_version": 5,
                    "assets": [asset],
                }
            ),
            encoding="utf-8",
        )
        return manifest_path

    def test_accepts_matching_manifest(self):
        self.assertEqual([], verifier.verify_manifest(self.root, self.manifest()))

    def test_reports_hash_size_and_row_drift(self):
        errors = verifier.verify_manifest(
            self.root,
            self.manifest(bytes=1, sha256="0" * 64, data_rows=1),
        )
        self.assertTrue(any("byte size mismatch" in error for error in errors))
        self.assertTrue(any("SHA-256 mismatch" in error for error in errors))
        self.assertTrue(any("row count mismatch" in error for error in errors))

    def test_reports_unmanifested_shipped_asset(self):
        (self.t2s_dir / "extra.tsv").write_text("繁\t简\n", encoding="utf-8")
        (self.asset_dir / "hkscs_supplement.csv").write_text(
            "chinese,code_point,quick_code,jyutping\n𠀾,U+2003E,mi,bui\n",
            encoding="utf-8",
        )
        (self.asset_dir / "chinese_assist.csv").write_text(
            "chinese,english,freq\n巴士,bus,0.99\n",
            encoding="utf-8",
        )
        errors = verifier.verify_manifest(self.root, self.manifest())
        self.assertIn(
            "unmanifested shipped asset: android/app/src/main/assets/t2s/extra.tsv",
            errors,
        )
        self.assertIn(
            "unmanifested shipped asset: "
            "android/app/src/main/assets/corpus/hkscs_supplement.csv",
            errors,
        )
        self.assertIn(
            "unmanifested shipped asset: "
            "android/app/src/main/assets/corpus/chinese_assist.csv",
            errors,
        )

    def test_checks_reproducible_source_and_generator(self):
        errors = verifier.verify_manifest(
            self.root,
            self.manifest(
                provenance={
                    "status": "reproducible",
                    "licenses": ["CC-BY-SA-4.0"],
                    "generator": "missing.py",
                    "source": {
                        "path": "missing.gz",
                        "url": "https://example.invalid/source",
                        "date": "2026-07-13T00:00:00Z",
                        "sha256": "0" * 64,
                    },
                }
            ),
        )
        self.assertTrue(any("generator does not exist" in error for error in errors))
        self.assertTrue(any("source does not exist" in error for error in errors))

    def test_repository_manifest_is_valid(self):
        manifest = REPO_ROOT / "corpus/sources/corpus_manifest.json"
        self.assertEqual([], verifier.verify_manifest(REPO_ROOT, manifest))

    def test_repository_declares_hkscs_and_curated_chinese_assist_provenance(self):
        manifest = json.loads(
            (REPO_ROOT / "corpus/sources/corpus_manifest.json").read_text(encoding="utf-8")
        )
        assets = {asset["path"]: asset for asset in manifest["assets"]}

        hkscs = assets["android/app/src/main/assets/corpus/hkscs_supplement.csv"]
        self.assertEqual(4606, hkscs["data_rows"])
        self.assertEqual("reproducible", hkscs["provenance"]["status"])
        self.assertEqual("corpus/tools/hkscs_coverage.py", hkscs["provenance"]["generator"])
        self.assertIn("data.gov.hk", hkscs["provenance"]["source"]["url"])
        self.assertEqual(64, len(hkscs["provenance"]["source"]["sha256"]))
        self.assertEqual(2, len(hkscs["provenance"]["inputs"]))

        chinese_assist = assets["android/app/src/main/assets/corpus/chinese_assist.csv"]
        self.assertEqual(11, chinese_assist["data_rows"])
        self.assertEqual("reviewed-curated", chinese_assist["provenance"]["status"])
        self.assertIn("exact-match", chinese_assist["provenance"]["note"])

    def test_repository_declares_reproducible_english_assist_and_t2s_assets(self):
        manifest = json.loads(
            (REPO_ROOT / "corpus/sources/corpus_manifest.json").read_text(encoding="utf-8")
        )
        assets = {asset["path"]: asset for asset in manifest["assets"]}

        english = assets["android/app/src/main/assets/corpus/english_assist.csv"]
        self.assertEqual("reproducible", english["provenance"]["status"])
        self.assertEqual(
            "corpus/tools/build_english_assist_corpus.py",
            english["provenance"]["generator"],
        )
        self.assertEqual(
            {"cedict", "frequency-vocabulary", "ranking-frequency"},
            {source["role"] for source in english["provenance"]["sources"]},
        )

        t2s = assets["android/app/src/main/assets/t2s/t2s_map.tsv"]
        self.assertEqual("reproducible", t2s["provenance"]["status"])
        self.assertEqual("corpus/tools/build_t2s_map.py", t2s["provenance"]["generator"])
        self.assertEqual("opencc", t2s["provenance"]["sources"][0]["role"])


if __name__ == "__main__":
    unittest.main()
