# Reading Hints Design (粵拼 / 耶魯 / 拼音)

Supersedes `2026-07-30-quick-jyutping-learning-preview-design.md`.

## Goal

Let a learner see the toned pronunciation of the word they are about to commit,
in a romanization they can actually read, and let them turn each one on or off
independently.

## Interaction

A **學習提示** section in Settings:

- 顯示粵拼（粵語注音） — default **on**
- 粵語注音格式 — 粵拼 (default) or 耶魯拼音
- 顯示拼音（普通話注音） — default **off**

While a composition has Chinese candidates, the strip shows one compact line
above them, following the leading ranked candidate:

```
香港 · 粵 hoeng1 gong2 · 拼 xiāng gǎng
香港 · 耶 hēung góng · 拼 xiāng gǎng
```

Each romanization is labelled (`粵` / `耶` / `拼`) so they are never confused, and
so a single enabled hint still says which system it is teaching. Tapping a
candidate briefly confirms the reading of the word actually committed, then the
ordinary next-character predictions continue underneath.

Three deliberate departures from the superseded design:

- **Every scheme, not 速成 only.** The 粵拼 input dictionary is toneless, so a
  toned hint still teaches tones to a 粵拼 typist; a 拼音 hint teaches Mandarin to
  a 速成 or 粵拼 typist. Restricting by scheme removed the case the hint is most
  useful in.
- **The strip height follows the preference, not the build.** 42dp with no hint
  enabled, 58dp with one or both. The height is still fixed across every display
  state, so it changes only on a settings change and never while typing.
- **Notation is the learner's choice.** See below.

The hint is suppressed for English literals, sensitive fields, loading/error
messages, and any entry without an exact reading.

## Notation

Hong Kong schools teach no Cantonese romanisation — Chinese literacy goes
straight to characters — so Jyutping's letter values read wrong to someone with
English or Pinyin instincts: `j` is a *y* sound, `c` is *ts*, and `oe`/`eo` are a
vowel English does not write. Meanwhile Putonghua class has taught Hanyu Pinyin,
with diacritics, since the late 1990s. So the two hints need opposite treatment.

**Cantonese keeps Jyutping as the default.** It is the LSHK standard, it is what
this corpus and every serious Cantonese dictionary are built on, and a learner
who sticks with it ends up literate in the notation those dictionaries use. Tone
digits are its official form — Jyutping has no diacritic variant — so it
correctly stays numeric.

**Yale is offered alongside it** for readers who cannot get past those letter
values. It is *derived* from Jyutping at display time (`YaleRomanization`), not
sourced separately: both transcribe the same phonology, so no second corpus is
needed. The conversion covers the initials (`j`→`y`, `z`→`j`, `c`→`ch`), the
rounded front vowel (`oe`/`eo`→`eu`), the `jyu`→`yu` collapse, and Yale's tone
`h` — which belongs to the nucleus, so it sits after the vowel and before any
final consonant: 月 is `yuht`, 十 is `sahp`. A syllabic nasal (唔, 五) keeps the
`h` unaccented rather than stacking a combining mark on a consonant, which few
fonts render legibly. A reading Yale cannot spell shows nothing rather than a
Jyutping form mislabelled as Yale.

**Pinyin is shown with diacritics** (`PinyinDiacritics`). The asset stores
`xiang1 gang3`, the CC-CEDICT and input-method convention, but schools teach
`xiāng gǎng` with neutral tone unmarked — 912 bundled readings carried a tone `5`
that does not exist in taught Pinyin at all. Conversion happens at display time
so the asset stays in the canonical numeric shape the corpus tools and the
toneless input dictionary share.

## Pronunciation data

Two assets of the same `text,reading` shape, both filtered to text reachable
through the Quick character and phrase corpora, both exact-match only:

| Asset | Source | Rows |
| --- | --- | --- |
| `jyutping_readings.csv` | rime-cantonese `c99b16e4`, pinned | 34,246 |
| `pinyin_readings.csv` | vendored CC-CEDICT snapshot `2026-07-12T16:23:25Z` | 34,018 |

`corpus/tools/build_pinyin_readings.py` derives the Mandarin asset
deterministically from the snapshot already vendored for `pinyin.csv`. It keeps
tone digits and syllable spaces, lowercases, and spells CC-CEDICT's ASCII `u:`
digraph as `ü`.

Reading selection, in order:

1. A name / used-in-names / variant / cross-reference reading is kept only when
   the headword has no ordinary one — without this 綠 leads with
   `[lu4] /used in names/` and teaches `lu4` instead of `lü4`.
2. For a **polyphonic single character**, the words containing it vote: 行 reads
   `xing2` in 行為, 進行 and 旅行 but `hang2` only in 銀行, so `xing2` wins.
   CC-CEDICT writes one syllable per character, so a multi-character headword
   aligns position by position; entries whose counts disagree are skipped.
3. Ties fall back to CC-CEDICT source order.

Multi-character text always takes its own exact headword reading, so 銀行 stays
`yin2 hang2` and 睡覺 stays `shui4 jiao4` — the vote never leaks back into the
phrases that cast it.

The 粵拼 asset carries no equivalent vote and cannot be regenerated here:
rime-cantonese is pinned by revision but, unlike the CC-CEDICT snapshot, is not
vendored. Fixing its polyphonic characters needs that checkout.

## Architecture

`CorpusLoader` lazily loads both assets into `ReadingLookup`, a plain exact-match
map. A pure `ReadingHintPolicy` decides eligibility and formats the line from a
`ReadingHints(jyutping, pinyin, cantonese)` value, so all display rules — and
both notation conversions — are unit-testable without Android views.
`HkImeService` holds the current `ReadingHints`, mirrors it from the settings
flow, and resizes the strip only when the enabled/disabled state actually flips.

Reading data is best-effort throughout: a missing or malformed asset yields
`ReadingLookup.empty()`, which shows no hint and never affects decoding,
ranking, or committing.

## Verification

- Generator tests cover tone preservation, syllable spacing, Quick filtering,
  `ü` spelling, the name/variant preference, and the polyphonic vote.
- `RomanizationNotationTest` covers Pinyin mark placement (`a` first, then `e`,
  then the `o` of `ou`, else the last vowel), unmarked neutral tone, and the Yale
  initials, `eu` vowel, `jyu` collapse, and tone-`h` placement.
- `ReadingHintPolicyTest` covers each toggle combination, both notations, the
  labelled one-line format, one romanization missing a reading, English
  suppression, and the committed-candidate confirmation.
- `BundledReadingAssetTest` reads the two shipped CSVs so a regenerated or
  truncated asset fails the build rather than silently disabling the hints.
- `CandidateBarLayoutPolicyTest` / `KeyboardLayoutTest` cover both strip heights
  and prove neither varies with display state.
