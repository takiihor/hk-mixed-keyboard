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

        self.assertEqual(4606, len(supplement))
        # Seven HKSCS records lack an official input mapping, but five already
        # have a bundled legacy Jyutping route. Only the two absent from both
        # runtime corpora need a technical Unicode fallback.
        self.assertEqual(7, sum(not entry.cangjie and not entry.cantonese for entry in han))

        quick.update(entry.char for entry in supplement if entry.quick_code)
        jyutping.update(entry.char for entry in supplement if entry.jyutping)
        measured = coverage.measure(han, quick, jyutping)

        self.assertEqual(4599, measured.quick_covered)
        self.assertEqual(4593, measured.jyutping_covered)
        self.assertEqual(["𠃍", "𠃑"], [entry.char for entry in measured.missing_both])
        self.assertEqual(["u200cd", "u200d1"], [
            coverage.unicode_fallback_code(entry) for entry in measured.unicode_fallbacks
        ])
        self.assertEqual(4606, measured.runtime_reachable)

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
