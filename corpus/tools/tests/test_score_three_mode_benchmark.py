import csv
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


REPO_ROOT = Path(__file__).resolve().parents[3]
SCRIPT = REPO_ROOT / "corpus/tools/score_three_mode_benchmark.py"


class ThreeModeBenchmarkScorerTest(unittest.TestCase):
    def run_scorer(self, cases, results, *extra_args, with_markdown=False, evidence=None):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            cases_path = tmp_path / "cases.tsv"
            results_path = tmp_path / "results.tsv"
            json_path = tmp_path / "report.json"
            markdown_path = tmp_path / "report.md"

            self.write_tsv(cases_path, cases)
            self.write_tsv(results_path, results)

            evidence_args = []
            if evidence is not None:
                evidence_path = tmp_path / "evidence.json"
                evidence_path.write_text(json.dumps(evidence), encoding="utf-8")
                evidence_args = ["--evidence", str(evidence_path)]

            command = [
                    sys.executable,
                    str(SCRIPT),
                    "--cases",
                    str(cases_path),
                    "--results",
                    str(results_path),
                    "--json-output",
                    str(json_path),
                    *evidence_args,
                    *extra_args,
                ]
            if with_markdown:
                command.extend(("--markdown-output", str(markdown_path)))
            completed = subprocess.run(
                command,
                cwd=REPO_ROOT,
                capture_output=True,
                text=True,
                check=False,
            )
            report = json.loads(json_path.read_text()) if json_path.exists() else None
            if with_markdown:
                markdown = markdown_path.read_text() if markdown_path.exists() else None
                return completed, report, markdown
            return completed, report

    @staticmethod
    def write_tsv(path, rows):
        with path.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=rows[0].keys(), delimiter="\t")
            writer.writeheader()
            writer.writerows(rows)

    def test_scores_a_perfect_candidate_result(self):
        cases = [
            {
                "case_id": "jyutping-001",
                "mode": "jyutping",
                "category": "common_phrase",
                "input": "neihou",
                "context": "",
                "acceptable_outputs": '["你好"]',
                "split": "development",
                "provenance": "native_reviewer",
            }
        ]
        results = [
            {
                "case_id": "jyutping-001",
                "candidates": '["你好", "你號"]',
                "committed_text": "你好",
                "keystrokes": "7",
                "corrections": "0",
                "wrong_auto_commits": "0",
                "elapsed_ms": "22.5",
            }
        ]

        completed, report = self.run_scorer(cases, results)

        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertEqual(report["total"], 1)
        self.assertEqual(report["top1_rate"], 1.0)
        self.assertEqual(report["top3_rate"], 1.0)
        self.assertEqual(report["top5_rate"], 1.0)
        self.assertEqual(report["mean_reciprocal_rank"], 1.0)
        self.assertEqual(report["oov_rate"], 0.0)

    def test_reports_input_efficiency_corrections_and_latency(self):
        cases = [
            {
                "case_id": "quick-001",
                "mode": "quick",
                "category": "common_character",
                "input": "ai",
                "context": "",
                "acceptable_outputs": '["時"]',
                "split": "development",
                "provenance": "native_reviewer",
            },
            {
                "case_id": "quick-002",
                "mode": "quick",
                "category": "common_character",
                "input": "rr",
                "context": "",
                "acceptable_outputs": '["唔"]',
                "split": "development",
                "provenance": "native_reviewer",
            },
        ]
        results = [
            {
                "case_id": "quick-001",
                "candidates": '["日", "時"]',
                "committed_text": "時",
                "keystrokes": "2",
                "corrections": "1",
                "wrong_auto_commits": "0",
                "elapsed_ms": "20",
            },
            {
                "case_id": "quick-002",
                "candidates": '["不"]',
                "committed_text": "不",
                "keystrokes": "3",
                "corrections": "2",
                "wrong_auto_commits": "1",
                "elapsed_ms": "40",
            },
        ]

        completed, report = self.run_scorer(cases, results)

        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertEqual(report["top1_rate"], 0.0)
        self.assertEqual(report["top3_rate"], 0.5)
        self.assertEqual(report["mean_reciprocal_rank"], 0.25)
        self.assertEqual(report["committed_accuracy"], 0.5)
        self.assertEqual(report["keystrokes_per_correct_character"], 2.0)
        self.assertEqual(report["corrections_per_case"], 1.5)
        self.assertEqual(report["wrong_auto_commit_rate"], 0.5)
        self.assertEqual(report["latency_ms"]["p50"], 30.0)
        self.assertEqual(report["latency_ms"]["p95"], 39.0)
        self.assertEqual(report["latency_ms"]["p99"], 39.8)

    def test_breaks_ranking_results_down_by_benchmark_dimensions(self):
        cases = [
            {
                "case_id": "quick-001",
                "mode": "quick",
                "category": "common_character",
                "frequency_band": "high",
                "phrase_length": "1",
                "hkscs_status": "core",
                "input": "ai",
                "context": "",
                "acceptable_outputs": '["時"]',
                "split": "development",
                "provenance": "native_reviewer",
            },
            {
                "case_id": "jyutping-001",
                "mode": "jyutping",
                "category": "common_phrase",
                "frequency_band": "high",
                "phrase_length": "2",
                "hkscs_status": "core",
                "input": "neihou",
                "context": "",
                "acceptable_outputs": '["你好"]',
                "split": "development",
                "provenance": "native_reviewer",
            },
        ]
        results = [
            {
                "case_id": "quick-001",
                "candidates": '["時"]',
                "committed_text": "時",
                "keystrokes": "2",
                "corrections": "0",
                "wrong_auto_commits": "0",
                "elapsed_ms": "10",
            },
            {
                "case_id": "jyutping-001",
                "candidates": '["你號", "你好"]',
                "committed_text": "你好",
                "keystrokes": "7",
                "corrections": "1",
                "wrong_auto_commits": "0",
                "elapsed_ms": "20",
            },
        ]

        completed, report = self.run_scorer(cases, results)

        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertEqual(report["breakdowns"]["mode"]["quick"]["top1_rate"], 1.0)
        self.assertEqual(
            report["breakdowns"]["mode"]["jyutping"]["top1_rate"], 0.0
        )
        self.assertEqual(
            report["breakdowns"]["category"]["common_phrase"]["top3_rate"], 1.0
        )
        self.assertEqual(report["breakdowns"]["phrase_length"]["2"]["total"], 1)

    def test_rejects_duplicate_case_ids(self):
        case = {
            "case_id": "duplicate",
            "mode": "pinyin",
            "category": "common_phrase",
            "input": "nihao",
            "context": "",
            "acceptable_outputs": '["你好"]',
            "split": "development",
            "provenance": "native_reviewer",
        }
        result = {
            "case_id": "duplicate",
            "candidates": '["你好"]',
            "committed_text": "你好",
            "keystrokes": "6",
            "corrections": "0",
            "wrong_auto_commits": "0",
            "elapsed_ms": "10",
        }

        completed, _ = self.run_scorer([case, case], [result])

        self.assertNotEqual(completed.returncode, 0)
        self.assertIn("duplicate case_id", completed.stderr)

    def test_locked_holdout_rejects_production_corpus_provenance(self):
        case = {
            "case_id": "leaked-001",
            "mode": "jyutping",
            "category": "common_phrase",
            "input": "neihou",
            "context": "",
            "acceptable_outputs": '["你好"]',
            "split": "locked_holdout",
            "provenance": "production_corpus",
        }
        result = {
            "case_id": "leaked-001",
            "candidates": '["你好"]',
            "committed_text": "你好",
            "keystrokes": "7",
            "corrections": "0",
            "wrong_auto_commits": "0",
            "elapsed_ms": "10",
        }

        completed, _ = self.run_scorer(
            [case], [result], "--locked-holdout", "--minimum-cases", "1"
        )

        self.assertNotEqual(completed.returncode, 0)
        self.assertIn("production_corpus provenance", completed.stderr)

    def test_locked_holdout_requires_one_thousand_cases_by_default(self):
        case = {
            "case_id": "holdout-001",
            "mode": "jyutping",
            "category": "common_phrase",
            "input": "neihou",
            "context": "",
            "acceptable_outputs": '["你好"]',
            "split": "locked_holdout",
            "provenance": "native_reviewer",
        }
        result = {
            "case_id": "holdout-001",
            "candidates": '["你好"]',
            "committed_text": "你好",
            "keystrokes": "7",
            "corrections": "0",
            "wrong_auto_commits": "0",
            "elapsed_ms": "10",
        }

        completed, _ = self.run_scorer([case], [result], "--locked-holdout")

        self.assertNotEqual(completed.returncode, 0)
        self.assertIn("requires at least 1000 cases; found 1", completed.stderr)

    def test_rejects_missing_results(self):
        case = {
            "case_id": "missing-result",
            "mode": "quick",
            "category": "common_character",
            "input": "ai",
            "context": "",
            "acceptable_outputs": '["時"]',
            "split": "development",
            "provenance": "native_reviewer",
        }
        unrelated_result = {
            "case_id": "another-case",
            "candidates": '["時"]',
            "committed_text": "時",
            "keystrokes": "2",
            "corrections": "0",
            "wrong_auto_commits": "0",
            "elapsed_ms": "10",
        }

        completed, _ = self.run_scorer([case], [unrelated_result])

        self.assertNotEqual(completed.returncode, 0)
        self.assertIn("missing result for case_id: missing-result", completed.stderr)

    def test_writes_a_human_readable_markdown_report(self):
        case = {
            "case_id": "pinyin-001",
            "mode": "pinyin",
            "category": "common_phrase",
            "input": "nihao",
            "context": "",
            "acceptable_outputs": '["你好"]',
            "split": "development",
            "provenance": "native_reviewer",
        }
        result = {
            "case_id": "pinyin-001",
            "candidates": '["你好"]',
            "committed_text": "你好",
            "keystrokes": "6",
            "corrections": "0",
            "wrong_auto_commits": "0",
            "elapsed_ms": "10",
        }

        completed, _, markdown = self.run_scorer(
            [case], [result], with_markdown=True
        )

        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertIn("# Three-Mode Benchmark Report", markdown)
        self.assertIn("| Top-1 | 100.00% |", markdown)
        self.assertIn("| Wrong automatic commit rate | 0.00% |", markdown)

    def test_source_coverage_report_is_labelled_non_independent(self):
        case = {
            "case_id": "source-coverage-001",
            "mode": "pinyin",
            "category": "common_phrase",
            "input": "nihao",
            "context": "",
            "acceptable_outputs": '["你好"]',
            "split": "development",
            "provenance": "source_coverage",
        }
        result = {
            "case_id": "source-coverage-001",
            "candidates": '["你好"]',
            "committed_text": "你好",
            "keystrokes": "6",
            "corrections": "0",
            "wrong_auto_commits": "0",
            "elapsed_ms": "10",
        }

        completed, report = self.run_scorer(
            [case],
            [result],
            evidence={"classification": "source_coverage"},
        )

        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertEqual(report["evidence"]["classification"], "SOURCE_COVERAGE")
        self.assertFalse(report["evidence"]["independent"])

    def test_rejects_an_empty_acceptable_output_list(self):
        case = {
            "case_id": "invalid-expected",
            "mode": "pinyin",
            "category": "common_phrase",
            "input": "nihao",
            "context": "",
            "acceptable_outputs": "[]",
            "split": "development",
            "provenance": "native_reviewer",
        }
        result = {
            "case_id": "invalid-expected",
            "candidates": '["你好"]',
            "committed_text": "你好",
            "keystrokes": "6",
            "corrections": "0",
            "wrong_auto_commits": "0",
            "elapsed_ms": "10",
        }

        completed, _ = self.run_scorer([case], [result])

        self.assertNotEqual(completed.returncode, 0)
        self.assertIn("acceptable_outputs must be a non-empty JSON string list", completed.stderr)


if __name__ == "__main__":
    unittest.main()
