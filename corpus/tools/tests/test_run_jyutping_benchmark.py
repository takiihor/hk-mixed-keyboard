import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = REPO_ROOT / "corpus/tools/run_jyutping_benchmark.py"
SPEC = importlib.util.spec_from_file_location("run_jyutping_benchmark", MODULE_PATH)
assert SPEC and SPEC.loader
runner = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = runner
SPEC.loader.exec_module(runner)

SHIPPED_BASE = REPO_ROOT / "android/app/src/main/assets/corpus/jyutping.csv"
SHIPPED_OVERRIDES = REPO_ROOT / "android/app/src/main/assets/corpus/jyutping_overrides.csv"
SHIPPED_CASES = REPO_ROOT / "corpus/benchmarks/jyutping_ranking_cases.csv"


class JyutpingBenchmarkTest(unittest.TestCase):
    def _write(self, name: str, content: str) -> Path:
        path = Path(self.temp.name) / name
        path.write_text(content, encoding="utf-8")
        return path

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()

    def tearDown(self):
        self.temp.cleanup()

    def test_override_wins_and_dedupes_by_frequency(self):
        base = self._write("base.csv", "jyutping,chinese,freq\nhai,閪,0.9\nhai,係,0.3\n")
        overrides = self._write("ov.csv", "jyutping,chinese,freq\nhai,喺,2.1\n")
        index = runner.build_index(base, overrides)
        # Override (2.1) ranks first; base rows follow in frequency order.
        self.assertEqual(index["hai"][0], "喺")
        self.assertIn("閪", index["hai"])

    def test_absent_expected_is_reported_as_miss(self):
        base = self._write("base.csv", "jyutping,chinese,freq\nhai,係,0.3\n")
        overrides = self._write("ov.csv", "jyutping,chinese,freq\n")
        cases = self._write("cases.csv", "key,expected,category\nhai,喺,char\n")
        report = runner.evaluate(base, overrides, cases)
        self.assertEqual(report.top1, 0)
        self.assertEqual(report.misses[0].rank, -1)

    @unittest.skipUnless(
        SHIPPED_BASE.is_file() and SHIPPED_OVERRIDES.is_file() and SHIPPED_CASES.is_file(),
        "shipped Jyutping corpus or cases not present",
    )
    def test_shipped_reviewed_cases_are_all_top1(self):
        report = runner.evaluate(SHIPPED_BASE, SHIPPED_OVERRIDES, SHIPPED_CASES)
        self.assertGreater(report.total, 0)
        self.assertEqual(
            report.top1,
            report.total,
            msg=f"reviewed cases regressed: {[m.key for m in report.misses]}",
        )


if __name__ == "__main__":
    unittest.main()
