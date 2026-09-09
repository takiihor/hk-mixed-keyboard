# Reading Hints Design (粵拼 / 拼音)

Supersedes `2026-07-30-quick-jyutping-learning-preview-design.md`.

## Goal

Let a learner see the toned pronunciation of the word they are about to commit,
in whichever romanization they are trying to learn, and let them turn each one on
or off independently.

## Interaction

Two switches under a new **學習提示** section in Settings:

- 顯示粵拼（粵語注音） — default **on**
- 顯示拼音（普通話注音） — default **off**

While a composition has Chinese candidates, the strip shows one compact line
above them, following the leading ranked candidate:

```
香港 · 粵 hoeng1 gong2 · 拼 xiang1 gang3
```

Each romanization is labelled (`粵` / `耶` / `拼`) so they are never confused, and
so a single enabled hint still says which system it is teaching.

## Notation (added 2026-09-09)

Hong Kong schools teach no Cantonese romanisation, so Jyutping's letter values
read wrong to someone with English or Pinyin instincts — `j` is a *y* sound, `c`
is *ts*, `oe`/`eo` are a vowel English does not write. A **粵語注音格式** choice
under the 粵拼 switch offers:

- **粵拼（標準，詞典通用）** — default. The LSHK standard the corpus is built on,
  and what dictionaries use; tone digits are its official form.
- **耶魯拼音（較易讀）** — Yale. Spellings closer to English intuition and tone by
  accent, as adult Cantonese courses teach it.

Yale is *derived* from Jyutping at display time (`YaleRomanization`), so it needs
no second corpus: both transcribe the same phonology. A reading Yale cannot spell
shows nothing rather than a Jyutping form mislabelled as Yale.

Pinyin is likewise stored numbered but **shown with diacritics**
(`PinyinDiacritics`): schools teach `xiāng gǎng`, not `xiang1 gang3`, and neutral
tone unmarked — 912 bundled readings carried a tone `5` that does not exist in
taught Pinyin. The asymmetry is deliberate: Jyutping has no official diacritic
form, so it correctly stays numeric. Tapping a candidate
briefly confirms the reading of the word actually committed, then the ordinary
next-character predictions continue underneath.

Two deliberate departures from the superseded design:

- **Every scheme, not 速成 only.** The 粵拼 input dictionary is toneless, so a
  toned hint still teaches tones to a 粵拼 typist; a 拼音 hint teaches Mandarin to
  a 速成 or 粵拼 typist. Restricting by scheme removed the case the hint is most
  useful in.
- **The strip height follows the preference, not the build.** 42dp with no hint
  enabled, 58dp with one or both. The height is still fixed across every display
  state, so it changes only on a settings change and never while typing.

The hint is suppressed for English literals, sensitive fields, loading/error
messages, and any entry without an exact reading.

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
digraph as `ü`. Where a headword has several readings the first in source order
wins, except that a surname / used-in-names / variant / cross-reference reading
is kept only when the headword has no ordinary one — without that rule 綠 leads
with `[lu4] /used in names/` and would teach `lu4` instead of `lü4`.

Known limitation: a headword whose readings are *all* ordinary (行 hang2/xing2,
重 chong2/zhong4) still resolves by source order, because CC-CEDICT ranks
nothing. Multi-character text is unaffected — every phrase reading comes from an
exact headword rather than from joining possibly polyphonic characters.

## Architecture

`CorpusLoader` lazily loads both assets into `ReadingLookup`, a plain exact-match
map. A pure `ReadingHintPolicy` decides eligibility and formats the line from a
`ReadingHints(jyutping, pinyin)` value, so all display rules are unit-testable
without Android views. `HkImeService` holds the current `ReadingHints`, mirrors
it from the settings flow, and resizes the strip only when the enabled/disabled
state actually flips.

Reading data is best-effort throughout: a missing or malformed asset yields
`ReadingLookup.empty()`, which shows no hint and never affects decoding,
ranking, or committing.

## Verification

- Generator tests cover tone preservation, syllable spacing, Quick filtering,
  `ü` spelling, and the name/variant preference.
- `ReadingHintPolicyTest` covers each toggle combination, the labelled one-line
  format, one romanization missing a reading, English suppression, and the
  committed-candidate confirmation.
- `BundledReadingAssetTest` reads the two shipped CSVs so a regenerated or
  truncated asset fails the build rather than silently disabling the hints.
- `CandidateBarLayoutPolicyTest` / `KeyboardLayoutTest` cover both strip heights
  and prove neither varies with display state.
