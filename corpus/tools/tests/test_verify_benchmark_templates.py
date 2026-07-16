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
    @staticmethod
    def complete_evidence():
        return {
            "classification": "native",
            "locked_prompt_sha256": "a" * 64,
            "test_lead": "lead-01",
            "reviewer_ids": ["reviewer-01", "reviewer-02", "reviewer-03"],
            "device": "Pixel 8 / Android 35",
            "settings": "default height; animations off",
            "input_state": "cold",
            "candidate_commit": "b" * 40,
            "result_commit": "b" * 40,
            "candidate_aab_sha256": "c" * 64,
            "result_aab_sha256": "c" * 64,
            "raw_result_provenance": "secure-results://run-001",
        }

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

    def test_empty_holdout_cannot_be_classified_as_native_evidence(self):
        with self.assertRaisesRegex(ValueError, "at least one locked case"):
            verifier.validate_comparative_evidence(self.complete_evidence(), case_count=0)

    def test_comparative_evidence_requires_reviewer_identities(self):
        evidence = self.complete_evidence()
        evidence["reviewer_ids"] = []

        with self.assertRaisesRegex(ValueError, "reviewer_ids"):
            verifier.validate_comparative_evidence(evidence, case_count=1000)

    def test_comparative_evidence_requires_locked_prompt_hash(self):
        evidence = self.complete_evidence()
        del evidence["locked_prompt_sha256"]

        with self.assertRaisesRegex(ValueError, "locked_prompt_sha256"):
            verifier.validate_comparative_evidence(evidence, case_count=1000)

    def test_comparative_evidence_rejects_mismatched_candidate_hashes(self):
        evidence = self.complete_evidence()
        evidence["result_commit"] = "d" * 40

        with self.assertRaisesRegex(ValueError, "commit hash"):
            verifier.validate_comparative_evidence(evidence, case_count=1000)

        evidence = self.complete_evidence()
        evidence["result_aab_sha256"] = "d" * 64

        with self.assertRaisesRegex(ValueError, "AAB hash"):
            verifier.validate_comparative_evidence(evidence, case_count=1000)

    def test_comparative_evidence_rejects_self_labelled_results(self):
        for classification in ("native", "competitor_comparative"):
            with self.subTest(classification=classification):
                evidence = self.complete_evidence()
                evidence["classification"] = classification
                evidence["reviewer_ids"] = ["self"]

                with self.assertRaisesRegex(ValueError, "self-labelled"):
                    verifier.validate_comparative_evidence(evidence, case_count=1000)

    def test_source_coverage_is_explicitly_non_independent(self):
        classification = verifier.validate_comparative_evidence(
            {"classification": "source_coverage"}, case_count=0
        )

        self.assertEqual(classification, "SOURCE_COVERAGE")


if __name__ == "__main__":
    unittest.main()
