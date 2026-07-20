import csv
import sys
import tempfile
import unittest
from pathlib import Path


TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

import build_three_mode_coverage_cases as builder
import run_three_mode_coverage_benchmark as runner


REPO_ROOT = Path(__file__).resolve().parents[3]
CASES = REPO_ROOT / "corpus/benchmarks/three_mode_source_coverage_cases.csv"


class ThreeModeCoverageBenchmarkTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.chars = self._write(
            "chars.csv",
            "char,quick_code,cangjie_code,freq,hk_core\n甲,aa,a,0.2,0\n乙,bb,b,0.9,0\n",
        )
        self.phrases = self._write(
            "phrases.csv",
            "phrase,quick_code,freq,hk_core\n甲乙,aa,0.8,0\n",
        )
        self.jyutping = self._write(
            "jyutping.csv", "jyutping,chinese,freq\ngaa,甲,0.2\njik,乙,0.9\n"
        )
        self.overrides = self._write("overrides.csv", "jyutping,chinese,freq\n")
        self.hkscs = self._write("hkscs.csv", "chinese,code_point,quick_code,jyutping\n")
        self.pinyin = self._write(
            "pinyin.csv", "pinyin,chinese,freq\njia,甲,0.2\nyi,乙,0.9\n"
        )

    def tearDown(self):
        self.temp.cleanup()

    def test_builds_even_source_derived_cases_for_each_mode(self):
        cases = builder.build_cases(
            chars=self.chars,
            phrases=self.phrases,
            jyutping=self.jyutping,
            overrides=self.overrides,
            hkscs=self.hkscs,
            pinyin=self.pinyin,
            per_scheme=2,
        )

        self.assertEqual(6, len(cases))
        self.assertEqual({"quick", "jyutping", "pinyin"}, {case.scheme for case in cases})
        self.assertEqual({"automated-source-derived"}, {case.review_status for case in cases})

    def test_runner_reports_all_generated_fixture_cases_as_top1(self):
        cases = builder.build_cases(
            chars=self.chars,
            phrases=self.phrases,
            jyutping=self.jyutping,
            overrides=self.overrides,
            hkscs=self.hkscs,
            pinyin=self.pinyin,
            per_scheme=2,
        )
        report = runner.evaluate(
            cases,
            chars=self.chars,
            phrases=self.phrases,
            jyutping=self.jyutping,
            overrides=self.overrides,
            hkscs=self.hkscs,
            pinyin=self.pinyin,
        )

        self.assertEqual(6, report.total)
        self.assertEqual(6, report.top1)

    @unittest.skipUnless(CASES.is_file(), "generated benchmark cases unavailable")
    def test_shipped_automated_coverage_has_at_least_500_cases(self):
        cases = runner.read_cases(CASES)
        report = runner.evaluate(cases)

        self.assertGreaterEqual(report.total, 500)
        self.assertEqual({"automated-source-derived"}, {case.review_status for case in cases})
        self.assertEqual(report.total, report.top1)

    def _write(self, name: str, content: str) -> Path:
        path = self.root / name
        path.write_text(content, encoding="utf-8")
        return path


if __name__ == "__main__":
    unittest.main()
