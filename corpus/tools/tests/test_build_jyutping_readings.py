import tempfile
import unittest
from pathlib import Path

from corpus.tools.build_jyutping_readings import build_readings


FIXTURES = Path(__file__).parent / "fixtures"


class BuildJyutpingReadingsTest(unittest.TestCase):
    def quick_corpora(self, root: Path) -> tuple[Path, Path]:
        chars = root / "chars.csv"
        chars.write_text(
            "char,quick_code,cangjie_code,freq,hk_core\n"
            "你,on,ofb,1.0,1\n"
            "我,hqi,hqi,1.0,1\n",
            encoding="utf-8",
        )
        phrases = root / "phrases.csv"
        phrases.write_text(
            "phrase,quick_code,freq,hk_core\n"
            "香港,theng,1.0,1\n"
            "無調,mz,1.0,1\n",
            encoding="utf-8",
        )
        return chars, phrases

    def build(self) -> dict[str, str]:
        with tempfile.TemporaryDirectory() as tmp:
            chars, phrases = self.quick_corpora(Path(tmp))
            return build_readings(
                [
                    FIXTURES / "jyutping_words_sample.dict.yaml",
                    FIXTURES / "jyutping_chars_sample.dict.yaml",
                ],
                chars,
                phrases,
            )

    def test_preserves_tones_and_word_syllable_spaces(self):
        self.assertEqual("hoeng1 gong2", self.build()["香港"])

    def test_filters_out_text_not_reachable_from_quick(self):
        self.assertNotIn("詞庫以外", self.build())

    def test_keeps_first_source_reading_for_polyphonic_text(self):
        self.assertEqual("nei5", self.build()["你"])

    def test_rejects_readings_without_tone_numbers(self):
        self.assertNotIn("無調", self.build())


if __name__ == "__main__":
    unittest.main()
