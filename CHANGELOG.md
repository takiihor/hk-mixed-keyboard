# Changelog

All notable user-visible and corpus changes are recorded here. Dates use Hong
Kong local time.

## Unreleased — 2026-07-14

- Completed the official HKSCS-2016 Han overlay and made its source-backed
  Quick/Jyutping routes available without silently discarding unsupported-font
  candidates. The two records with no official linguistic or Cangjie route now
  have an explicit tap-only Unicode fallback.
- Kept every shipped Pinyin and Jyutping input key within a bounded composition
  limit and excluded non-Han Pinyin candidate outputs from generated assets.
- Extended tap-only English-to-Chinese assistance to all three input modes,
  including safe composing of supported hyphen and apostrophe keys. Native
  mode candidates retain priority and Space does not auto-commit an assist.
- Rebuilt English-assist and Traditional-to-Simplified data from pinned,
  checksummed FrequencyWords, CC-CEDICT, Rime Essay and OpenCC inputs.
- Added a 540-case automated three-mode corpus-consistency benchmark. It is
  explicitly not human language review; outstanding certification and device
  gates are documented separately.
- Fixed the custom-word screen on Android 15/API 35 so the app bar no longer
  covers the first editable field.

See [`docs/cultural_preservation_release_gates.md`](docs/cultural_preservation_release_gates.md)
for release claim boundaries and external evidence still required.
