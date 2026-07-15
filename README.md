# HK Mixed Keyboard

香港人專用 Android 中英混合鍵盤，支援速成、粵拼及普通話拼音。

## Current features

- A mode key cycles Quick (速) → Jyutping (粵) → Pinyin (拼).
- Holding the mode key opens a direct picker; Settings also offers keyboard-height
  and left/right one-handed controls.
- Quick, exact/segmented Jyutping, and Pinyin candidates share the same
  resolved-candidate commit path for tap, Space, and punctuation.
- Jyutping accepts optional tone digits and boundaries and exposes pronunciation
  annotations. Pinyin accepts tone marks/digits, boundaries, optional strict-first
  fuzzy pairs and a tap-only nearby-key correction.
- Custom entries are validated per scheme and bounded, atomic UTF-8 CSV import is
  supported. Learning is local, capped, reversible and disabled in sensitive fields.
- Traditional Chinese is the default; Simplified output is an explicit output
  conversion setting and does not mutate the dictionary or prediction context.
- The keyboard has no `INTERNET` permission and performs candidate lookup
  locally.
- An official HKSCS-2016 overlay contains all 4,606 Han records. It uses
  source-backed Quick/Jyutping routes where official data exists; the only
  records with neither official Cangjie nor Cantonese data (`U+200CD`,
  `U+200D1`) remain selectable through an explicit tap-only Unicode escape,
  not an invented linguistic mapping.
- If a device font lacks a candidate glyph, the candidate remains tappable and
  is displayed as a Unicode code-point label such as `U+2003E`.
- Reviewed Hong Kong bilingual assists are explicit: Quick-only mixed phrases
  are tap-only, and a deliberately small exact Chinese→English post-commit
  overlay is visibly marked `中→英`.

## Build and verify

```bash
cd android
./gradlew testDebugUnitTest --rerun-tasks
./gradlew lintDebug
./gradlew assembleDebug
# With the configured upload-key environment:
./gradlew bundleRelease assembleRelease
# `apksigner` is supplied by Android SDK Build Tools; add it to PATH or invoke
# the binary in your installed build-tools directory. Replace <version> with
# the versioned filename emitted by the build.
apksigner verify --verbose app/build/outputs/apk/release/app-release-<version>.apk
jarsigner -verify app/build/outputs/bundle/release/app-release.aab
cd ..
python3 -m unittest discover -s corpus/tools/tests -v
python3 corpus/tools/verify_corpus_manifest.py
python3 corpus/tools/hkscs_coverage.py
```

Requires Java 17 or newer. Gradle wrapper downloads Kotlin and dependencies automatically.

## Release status

The engineering readiness work is tracked in `docs/release/three_mode_release_evidence.md`.
The app is currently **NO-GO** for a public market launch: independent locked
language benchmarks, competitor comparisons, physical-device/performance testing,
assistive-technology review, legal approval, a signed candidate and the closed beta
remain mandatory. The project does not claim “best-in-class” until those gates pass.

## Spec

See `HK_Mixed_Keyboard_app_development_guide_v1.5.md` for the full development guide.

See `docs/decode_harness_report.md` for the Gate 1 report.
