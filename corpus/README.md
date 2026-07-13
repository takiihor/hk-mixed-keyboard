# HK Mixed Keyboard — Corpus

This directory contains the linguistic data that drives the keyboard's Chinese decoding layer.

## Structure

```
corpus/
├── tools/
│   └── build_pinyin_corpus.py     # Deterministic offline CC-CEDICT converter
├── sources/
│   ├── corpus_license_register.csv   # License & provenance record for every source
│   └── upstream/                     # Pinned upstream source snapshots
├── internal_stage1/
│   ├── hk_core_chars.csv             # ~60 HK Cantonese core characters
│   ├── hk_core_phrases.csv           # ~65 common HK phrases
│   ├── mixed_phrases.csv             # ~60 Chinese–English mixed collocations
│   └── whitelist_en.csv             # ~35 English words that must never become Chinese
└── README.md                         # This file
```

## File Formats

### hk_core_chars.csv

| Column | Type | Description |
|--------|------|-------------|
| `char` | string | The Chinese character |
| `quick_code` | 2-letter | First + last Cangjie radical key (Quick/速成 code) |
| `cangjie_code` | 2–5 letter | Full Cangjie radical sequence |
| `freq` | float [0–1] | HK-chat-weighted frequency |
| `hk_core` | 0 or 1 | 1 = HK-specific character not found in standard Mandarin input |

Characters with `hk_core=1` are highlighted blue in the candidate bar and receive tieBreak preference when the buffer length ≤ 2.

### hk_core_phrases.csv

| Column | Type | Description |
|--------|------|-------------|
| `phrase` | string | Multi-character phrase |
| `quick_code` | string | Concatenated Quick codes of constituent characters |
| `freq` | float [0–1] | HK-chat-weighted phrase frequency |
| `hk_core` | 0 or 1 | 1 = HK-specific collocation |

Phrases rank above single characters in the candidate bar when the Quick code matches exactly.

### mixed_phrases.csv

| Column | Type | Description |
|--------|------|-------------|
| `trigger_en` | string | The English word that triggers this suggestion (e.g. `send`) |
| `phrase` | string | The mixed Chinese–English phrase shown as a completion (e.g. `send返`) |
| `freq` | float [0–1] | Estimated HK-chat frequency |

These appear in the post-commit prediction bar immediately after the user commits the English trigger word.

### whitelist_en.csv

| Column | Type | Description |
|--------|------|-------------|
| `word` | string | English word that must always be committed as English |
| `canonical` | string | Canonical casing override (empty = use as-is). E.g. `mtr` → `MTR` |

Whitelist entries always win over any Chinese decode that shares the same Quick code (e.g. `ok` = 仗 is suppressed).

### pinyin.csv

`android/app/src/main/assets/corpus/pinyin.csv` is generated offline from the
pinned CC-CEDICT snapshot. Its fields are `pinyin`, `chinese` (the Traditional
headword), and `freq`. Keys are lowercase and toneless; whitespace,
apostrophes, hyphens, and other separators are removed, while `ü` and `u:`
become `v`; the resulting key contains only ASCII `a-z`.

An exact Traditional-text match in `hk_core_chars.csv` or
`hk_core_phrases.csv` supplies the frequency only when that row has
`hk_core=0`; Cantonese-specific `hk_core=1` rows never boost Pinyin candidates.
Unmatched entries use a fixed, deterministic length/type heuristic: `0.3000`
for a single character, `0.2500` for a two-character word, and `0.2000` for a
longer phrase. The canonical product example `mao` ranks `貓` first without
depending on file order: its persisted generated score is the documented
override `1.0000`, strictly above every competing `mao` candidate.

The generator validates the compressed source SHA-256 before reading it. It
requires and validates CC-CEDICT `entries`, `version`, `subversion`, `format`,
`charset`, and `date` metadata, accepts only the pinned `version=1`,
`subversion=0`, `format=ts`, `charset=UTF-8` format, rejects malformed records
and verifies the parsed count. Generation also fails if the corpus exceeds
130,000 rows, 4 MiB, or 256 candidates for one key.

After normalization, a canonical Mandarin syllable inventory must fully cover
the continuous key. This keeps valid spellings (including `lv`/`nve`,
`ju`/`qu`/`xu`/`yu`, erhua `r`, and rare `fiao`/`sei`/`tei`) while excluding
placeholder readings such as `xx` and acronym-only readings. The Android
decoder repeats this validation before building its runtime indexes.

Before toneless normalization, each raw reading is validated against the
pinned source syntax: ASCII-letter Pinyin or acronym tokens, tone digits
`1`–`5` only at token ends, `u:` notation, spaces, commas, and middle-dot
separators. Apostrophe and hyphen syllable separators accepted by the input
normalizer are also validated. Standalone numeric literal tokens present in
CC-CEDICT are allowed; mixed digit/letter tokens and unexpected characters are
rejected. Syntactically valid acronym readings may still be excluded by the
canonical Mandarin-syllable validation above.

Rebuild it without network access from the repository root:

```bash
python3 corpus/tools/build_pinyin_corpus.py \
  --cedict corpus/sources/upstream/cc-cedict-2026-07-12T162325Z.u8.gz \
  --source-url 'https://cc-cedict.org/editor/editor_export_cedict.php?c=gz' \
  --source-date '2026-07-12T16:23:25Z' \
  --source-sha256 '90e2881776366f606171a977a1f54786196f428ba604c728a279d4aaff7223a8'
```

Run the generator tests with
`python3 -m unittest discover -s corpus/tools/tests -v`.

## Corpus Design Principles

1. **HK-first**: Characters and phrases reflect Hong Kong Cantonese usage, not Mandarin written Chinese.
2. **Privacy-safe**: No user sentences, chat logs, or identifiable data are stored here. Corpus is derived from internal frequency estimation.
3. **Static at runtime**: The corpus is bundled as read-only assets. User personalisation lives in the Room database (`user_memory` and `custom_words` tables), not here.
4. **Quick code accuracy**: All Quick codes are verified against the standard 速成 (Quick) table — first character key + last character key of the full Cangjie sequence.

## Adding New Words

To add words to the bundled corpus, edit the CSV files under `internal_stage1/` and rebuild the app. For user-specific words, use the **管理自訂詞語** screen in Settings — those are stored in the on-device Room database and are not affected by app updates.

## License

Internal corpus data remains proprietary. CC-CEDICT-derived data is distributed
under CC BY-SA 4.0. See `sources/corpus_license_register.csv` for provenance.
