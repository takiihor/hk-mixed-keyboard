import csv
import sys
import tempfile
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

import hkscs_coverage as coverage


REPO_ROOT = Path(__file__).resolve().parents[3]
SOURCE = REPO_ROOT / "corpus/sources/upstream/hkscs2016-a28a3b74.json"
SUPPLEMENT = REPO_ROOT / "android/app/src/main/assets/corpus/hkscs_supplement.csv"
QUICK = REPO_ROOT / "android/app/src/main/assets/corpus/hk_core_chars.csv"
JYUTPING = REPO_ROOT / "android/app/src/main/assets/corpus/jyutping.csv"


class HkscsCoverageTest(unittest.TestCase):
    def test_shipped_supplement_is_deterministic_and_measurable(self):
        han = coverage.load_han(SOURCE, coverage.DEFAULT_SOURCE_SHA)
        quick = coverage._single_char_texts(QUICK, text_col=0)
        jyutping = coverage._single_char_texts(JYUTPING, text_col=1)
        supplement = coverage.load_supplement(SUPPLEMENT)

        quick.update(entry.char for entry in supplement if entry.quick_code)
        jyutping.update(entry.char for entry in supplement if entry.jyutping)
        measured = coverage.measure(han, quick, jyutping)

        # Measured against the corpora this branch actually ships. The
        # cultural-preservation branch reports different numbers because it also
        # regenerated hk_core_chars.csv and jyutping.csv; that corpus rebuild is a
        # separate concern and is not ported here.
        self.assertEqual(4599, measured.quick_covered)
        self.assertEqual(4588, measured.jyutping_covered)
        # No official Quick or Jyutping mapping exists for these seven. They stay
        # unreachable until the tap-only Unicode fallback route is ported; the
        # keyboard does not invent linguistic data for them.
        self.assertEqual(
            ["𠃊", "𠃋", "𠃍", "𠃑", "𠄌", "𠄎", "𡿨"],
            [entry.char for entry in measured.missing_both],
        )

        with tempfile.TemporaryDirectory() as tmp:
            generated = Path(tmp) / "supplement.csv"
            coverage.emit_supplement(
                han,
                coverage._single_char_texts(QUICK, text_col=0),
                coverage._single_char_texts(JYUTPING, text_col=1),
                generated,
            )
            self.assertEqual(SUPPLEMENT.read_bytes(), generated.read_bytes())


if __name__ == "__main__":
    unittest.main()
