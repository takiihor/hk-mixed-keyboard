# HK Mixed Keyboard

香港人專用 Android 中英混合鍵盤，支援速成、粵拼及普通話拼音。

## Current features

- 鍵盤模式鍵依次切換速成（速）→ 粵拼（粵）→ 普通話拼音（拼）
- 普通話拼音免輸入聲調，支援常用繁體字詞及連續拼音
- 預設繁體輸出；可在設定或長按空白鍵切換簡體輸出
- 完全離線運作，沒有 `INTERNET` 權限或網絡候選

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
