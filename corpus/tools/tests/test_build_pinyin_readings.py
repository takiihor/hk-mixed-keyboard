import tempfile
import unittest
from pathlib import Path

from corpus.tools.build_pinyin_readings import (
    build_readings,
    character_votes,
    is_secondary,
    normalize_reading,
)


CEDICT_SAMPLE = """#! format=ts
#! charset=UTF-8
你 你 [ni3] /you (informal, as opposed to courteous 您[nin2])/
我 我 [wo3] /I/me/my/
王 王 [Wang2] /surname Wang/
王 王 [wang2] /king or monarch/
綠 绿 [lu4] /used in names/
綠 绿 [lu:4] /green/
香港 香港 [Xiang1 gang3] /Hong Kong/
無調 无调 [wu tiao] /toneless nonsense/
詞庫以外 词库以外 [ci2 ku4 yi3 wai4] /outside the corpus/
行 行 [hang2] /row; line/
行 行 [xing2] /to walk; to go/
銀行 银行 [yin2 hang2] /bank/
行為 行为 [xing2 wei2] /behaviour/
進行 进行 [jin4 xing2] /to carry out/
旅行 旅行 [lu:3 xing2] /to travel/
"""


class BuildPinyinReadingsTest(unittest.TestCase):
    def build(self) -> dict[str, str]:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            cedict = root / "cedict.u8"
            cedict.write_text(CEDICT_SAMPLE, encoding="utf-8")
            chars = root / "chars.csv"
            chars.write_text(
                "char,quick_code,cangjie_code,freq,hk_core\n"
                "你,on,ofb,1.0,1\n"
                "我,hqi,hqi,1.0,1\n"
                "王,mg,mg,1.0,1\n"
                "行,hon,honi,1.0,1\n"
                "綠,vfi,vfmcm,1.0,1\n",
                encoding="utf-8",
            )
            phrases = root / "phrases.csv"
            phrases.write_text(
                "phrase,quick_code,freq,hk_core\n"
                "香港,theng,1.0,1\n"
                "銀行,hvhon,1.0,1\n"
                "行為,honif,1.0,1\n"
                "無調,or,1.0,1\n",
                encoding="utf-8",
            )
            return build_readings(cedict, chars, phrases)

    def test_preserves_tones_and_word_syllable_spaces(self):
        self.assertEqual("xiang1 gang3", self.build()["香港"])

    def test_filters_out_text_not_reachable_from_quick(self):
        self.assertNotIn("詞庫以外", self.build())

    def test_rejects_readings_without_tone_numbers(self):
        self.assertNotIn("無調", self.build())

    def test_keeps_first_source_reading_for_polyphonic_text(self):
        self.assertEqual("ni3", self.build()["你"])

    def test_prefers_common_reading_over_a_surname_reading(self):
        self.assertEqual("wang2", self.build()["王"])

    def test_prefers_an_ordinary_gloss_over_a_used_in_names_reading(self):
        self.assertEqual("lü4", self.build()["綠"])

    def test_is_secondary_flags_names_variants_and_cross_references(self):
        self.assertTrue(is_secondary("Wang2", "surname Wang"))
        self.assertTrue(is_secondary("lu4", "used in names"))
        self.assertTrue(is_secondary("ru3", "old variant of 汝[ru3]"))
        self.assertFalse(is_secondary("wang2", "king or monarch"))

    def test_a_polyphonic_character_takes_the_reading_its_words_use(self):
        # 行 reads xing2 in 行為, 進行 and 旅行 but hang2 only in 銀行, so the
        # majority wins instead of whichever CC-CEDICT happened to list first.
        self.assertEqual("xing2", self.build()["行"])

    def test_a_multi_character_word_keeps_its_own_exact_reading(self):
        # Voting must not leak into phrases: 銀行 is still hang2, not xing2.
        self.assertEqual("yin2 hang2", self.build()["銀行"])

    def test_votes_align_syllables_to_characters(self):
        votes = character_votes([("銀行", "yin2 hang2", "bank")])

        self.assertEqual(1, votes["銀"]["yin2"])
        self.assertEqual(1, votes["行"]["hang2"])

    def test_votes_skip_entries_whose_syllables_do_not_align(self):
        votes = character_votes([("11區", "11 Qu1", "Area 11")])

        self.assertEqual({}, dict(votes))

    def test_normalize_lowercases_and_restores_u_umlaut(self):
        self.assertEqual("xiang1 gang3", normalize_reading("Xiang1 gang3"))
        self.assertEqual("nü3", normalize_reading("nu:3"))


if __name__ == "__main__":
    unittest.main()
