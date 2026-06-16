# HK Mixed Keyboard

香港人專用 Android 中英混合速成 / 倉頡鍵盤

## Stage 1 Status

| Prototype | Gate | Status |
|-----------|------|--------|
| 0 — Decode Contract Harness | Gate 1: three parse states derivable | ✓ PASSED |
| 1 — Commit Rule Harness | Gate 2: mis-commit ≤ 2% | Not started |
| 2 — Android IME Thin UI | Gate 3: IME works end-to-end | Not started |

## Quick Start (Prototype 0)

```bash
cd proto0
./gradlew test     # run all 28 tests
./gradlew run      # run the decode harness and print Gate 1 verdict
```

Requires Java 21. Gradle wrapper downloads Kotlin and dependencies automatically.

## Spec

See `HK_Mixed_Keyboard_app_development_guide_v1.5.md` for the full development guide.

See `docs/decode_harness_report.md` for the Gate 1 report.
