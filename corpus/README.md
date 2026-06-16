# HK Mixed Keyboard — Corpus

This directory contains the linguistic data that drives the keyboard's Chinese decoding layer.

## Structure

```
corpus/
├── sources/
│   └── corpus_license_register.csv   # License & provenance record for every source
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

## Corpus Design Principles

1. **HK-first**: Characters and phrases reflect Hong Kong Cantonese usage, not Mandarin written Chinese.
2. **Privacy-safe**: No user sentences, chat logs, or identifiable data are stored here. Corpus is derived from internal frequency estimation.
3. **Static at runtime**: The corpus is bundled as read-only assets. User personalisation lives in the Room database (`user_memory` and `custom_words` tables), not here.
4. **Quick code accuracy**: All Quick codes are verified against the standard 速成 (Quick) table — first character key + last character key of the full Cangjie sequence.

## Adding New Words

To add words to the bundled corpus, edit the CSV files under `internal_stage1/` and rebuild the app. For user-specific words, use the **管理自訂詞語** screen in Settings — those are stored in the on-device Room database and are not affected by app updates.

## License

All corpus data is internal/proprietary. See `sources/corpus_license_register.csv` for provenance records.
