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

Each romanization is labelled (`粵` / `拼`) so the two are never confused, and so
a single enabled hint still says which system it is teaching. Tapping a candidate
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
