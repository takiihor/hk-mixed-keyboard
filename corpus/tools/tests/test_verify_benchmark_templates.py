import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "verify_benchmark_templates.py"
SPEC = importlib.util.spec_from_file_location("verify_benchmark_templates", SCRIPT)
verifier = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(verifier)


class VerifyBenchmarkTemplatesTest(unittest.TestCase):
    def test_valid_header_only_template_passes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "cases.tsv"
            path.write_text("\t".join(verifier.CASE_FIELDS) + "\n", encoding="utf-8")
            verifier.verify_template(path, verifier.CASE_FIELDS, require_header_only=True)

    def test_answer_rows_are_rejected_from_public_holdout(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "cases.tsv"
            path.write_text(
                "\t".join(verifier.CASE_FIELDS) + "\n" + "\t".join(["x"] * 13) + "\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "header-only"):
                verifier.verify_template(path, verifier.CASE_FIELDS, require_header_only=True)

    def test_schema_drift_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "cases.tsv"
            path.write_text("case_id\tmode\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "schema changed"):
                verifier.verify_template(path, verifier.CASE_FIELDS, require_header_only=True)


if __name__ == "__main__":
    unittest.main()
