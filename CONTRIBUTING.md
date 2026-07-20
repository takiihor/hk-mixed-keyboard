# Contributing to HK Mixed Keyboard

Contributions are welcome, especially corrections that improve Hong Kong
Traditional Chinese, Cantonese and HKSCS support. Please keep additions
traceable and reversible.

## Corpus changes

- Do not add private chat logs, personal data or unlicensed corpus material.
- Pin each third-party source to a URL/revision, date, SHA-256 and license in
  `corpus/sources/corpus_manifest.json` and
  `corpus/sources/corpus_license_register.csv`.
- Add a deterministic generator and a test when an asset can be regenerated.
  Run `python3 corpus/tools/verify_corpus_manifest.py` before proposing the
  change.
- Keep source variants, historical forms and Hong Kong forms selectable.
  Ranking may prefer a form but must not destructively normalize another away.
- For HKSCS, use official source data. Never invent a Cangjie or Jyutping route
  for a record that lacks one; a technical Unicode escape may be used only as
  an explicitly labelled fallback.

## Review labels

Use evidence labels precisely:

- `reproducible`: deterministic output from pinned, checksummed sources.
- `reviewed-curated`: project-maintained data with a documented human review.
- `automated-source-derived`: generated coverage evidence only; it is not a
  linguistic-quality review.

Native-speaker review records must include the corpus version, input, expected
result/rank, reviewer role, and outcome. Do not convert automated cases into
human-reviewed evidence without an actual independent review.

## Development checks

Run the relevant unit tests, then the complete local gates before requesting
review:

```bash
cd android
./gradlew testDebugUnitTest --rerun-tasks
./gradlew lintDebug assembleDebug
cd ..
python3 -m unittest discover -s corpus/tools/tests -v
python3 corpus/tools/verify_corpus_manifest.py
python3 corpus/tools/run_three_mode_coverage_benchmark.py
```

For UI or IME behaviour, test a signed build on an emulator or device and
record the Android version, device/font environment and exact input sequence.
The full external release requirements are in
[`docs/cultural_preservation_release_gates.md`](docs/cultural_preservation_release_gates.md).

## Curated linguistic additions

For an entry intended to be shown as Hong Kong-preferred, include a concise
source/review note and request two independent reviewers where possible. New
curated data should be reflected in `CHANGELOG.md`; breaking corpus changes
also require a corpus content-version bump and migration consideration.
