# Offline Mandarin Pinyin Mode Design

## Goal

Add a complete third input mode for offline, toneless Mandarin Pinyin while preserving the keyboard's Traditional-first output and privacy model.

## User Experience

- The mode key cycles `速成 → 粵拼 → 拼音 → 速成` and displays `速`, `粵`, or `拼`.
- Pinyin accepts lowercase/uppercase toneless continuous input such as `nihao`, `xianggang`, `putonghua`, and `mao`.
- Exact word and phrase candidates rank first, followed by common prefix candidates.
- Candidates are Traditional Chinese. The existing explicit Simplified-output mode converts only at the commit boundary.
- English completions are suppressed in Pinyin mode; Enter still commits the literal Latin buffer.
- `v` represents `ü`; common `j/q/x/y + u` spellings are normalized for lookup.
- No network permission or runtime network path is added.

## Corpus

Generate a pinned, reproducible `pinyin.csv` from CC-CEDICT Traditional headwords and Mandarin Pinyin readings. Strip tone numbers and separators to create continuous lookup keys. Rank entries using existing HK/Rime phrase frequencies when available, with deterministic length/type fallbacks for uncovered entries. Preserve multiple readings and de-duplicate identical key/text pairs.

The generated asset records source version, download URL, checksum, transformation rules, and CC BY-SA 4.0 attribution. Existing HK Traditional normalization remains authoritative for output variants.

## Architecture

- Add `Scheme.PINYIN`, `SourceSchema.PINYIN`, and `CandidateType.PINYIN`.
- Replace the persisted two-state `jyutping_primary` preference with a string/enum input-scheme preference. Migrate an existing true value to Jyutping and false/absent to Quick.
- Extend `CorpusLoader` with cached Pinyin rows, exact index, prefix index, valid syllables, and segmenter.
- Add a Pinyin decoder branch parallel to Jyutping. Exact dictionary phrases are committable; prefixes remain tap-only. For fully segmented strings not present as phrases, compose bounded candidates from per-syllable readings.
- Treat Pinyin like Jyutping in display ordering, corpus warm-up, root visibility, and space behavior.

## Verification

- Corpus-generation tests validate normalization, de-duplication, Traditional output, representative words, and source metadata.
- Unit tests cover preference migration, three-mode cycling, exact/prefix/segmented decoding, candidate ordering, and Traditional/Simplified commits.
- Full unit/lint/build verification runs before emulator checks.
- Emulator checks cycle all three modes and type representative inputs without network access.

