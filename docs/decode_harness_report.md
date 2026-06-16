# Decode Contract Harness Report
## Prototype 0 — Gate 1

**Date:** 2026-06-13  
**Decoder under test:** MockDecoder (deterministic), RimeDecoderSpike (placeholder)  
**Spec:** HK_Mixed_Keyboard_app_development_guide_v1.5.md §7, §21

---

## Gate 1 Verdict: ✓ PASSED

All five Gate 1 criteria met using MockDecoder.

---

## Gate 1 Criteria Results

| Criterion | Result | Notes |
|-----------|--------|-------|
| cnExactParsed derivable deterministically | ✓ PASS | All 11 test buffers produce stable result |
| cnHasPhraseMatch derivable deterministically | ✓ PASS | 唔該, 我哋 both return PHRASE type |
| cnPrefixParsed derivable deterministically | ✓ PASS | hap, mtr correctly flagged as prefix-only |
| Candidate source / type / text extractable | ✓ PASS | All non-empty candidate lists have complete metadata |
| Quick scheme available | ✓ PASS | MockDecoder.isSchemeAvailable(QUICK) = true |
| Cangjie scheme loadable | ✓ PASS | MockDecoder returns CANGJIE sourceSchema for 我, 唔 |
| Prefix-only buffers not auto-committable | ✓ PASS | isExactCode=false for hap, mtr |

---

## Test Buffer Results (Quick Scheme, MockDecoder)

| Buffer | cnExactParsed | cnHasPhraseMatch | cnPrefixParsed | enIsWord | enStrongPrefix | Notes |
|--------|--------------|-----------------|----------------|----------|----------------|-------|
| hap    | false | false | true  | true  | true  | Prefix of "ha"; also prefix of "happy" → tieBreak needed |
| send   | false | false | false | true  | false | Pure English word; no Chinese interpretation |
| ok     | true  | false | false | true  | false | Collision: Quick char "仗" AND SHORT_WHITELIST → EN wins |
| go     | true  | false | false | true  | false | Collision: Quick char "在" AND English word → tieBreak |
| mtr    | false | false | true  | true  | false | SHORT_WHITELIST (MTR); prefix "mt" only in Chinese |
| 我     | true  | false | false | false | false | Direct char input; Quick code QO |
| 你     | true  | false | false | false | false | Direct char input; Quick code OI |
| 唔     | true  | false | false | false | false | HK core; isHkCore=true; Quick code RO |
| 嘅     | true  | false | false | false | false | HK core; isHkCore=true; Quick code RV |
| 唔該   | false | true  | false | false | false | HK core phrase; Quick code ROIO |
| 我哋   | false | true  | false | false | false | HK core phrase; Quick code QORP |

---

## Test Results Summary

**28 tests, 28 passed, 0 failed.**

Categories covered:
- Parse state determinism: 11 parameterized tests
- Candidate metadata completeness
- Quick scheme availability
- Cangjie scheme availability and sourceSchema labeling
- RimeDecoderSpike unavailability reporting
- HK core character flagging (唔, 嘅, 唔該, 我哋)
- Phrase detection (唔該, 我哋)
- English word classification (send, ok)
- SHORT_WHITELIST handling (ok, mtr → MTR canonical form)
- Collision detection (ok, go: both cnExactParsed AND enIsWord)
- strongEnglishPrefix (hap → happy)
- Prefix-only safety (hap, mtr: not auto-committable)

---

## Architecture Decisions

### consumedLen convention
`consumedLen = buffer.length` when the decoder processes the full buffer and finds no Chinese interpretation (e.g. "send"). This ensures `cnPrefixParsed = consumedLen < buffer.length = false`, correctly marking "send" as having no Chinese prefix either.

### Direct Chinese character input
Test buffers "我", "你", etc. represent direct Unicode character input. MockDecoder handles these via reverse-lookup, returning the character's Quick code, type=CHAR, and isHkCore flag. This tests the metadata extraction path without requiring a romanized code sequence.

### Collision cases
"ok" and "go" both have `cnExactParsed=true` AND `enIsWord=true`. These require `tieBreak()` in CommitController (Prototype 1). Gate 1 correctly surfaces this collision — it does not resolve it.

---

## Known Limitations

| Item | Status | Impact |
|------|--------|--------|
| librime native library | Not present | RimeDecoderSpike returns RIME_UNAVAILABLE for all inputs. Real decoder cannot be validated until librime.so is built for the target ABI. |
| Cangjie codes in MockDecoder | Manually curated | A few sample entries only (我, 唔). Full Cangjie validation requires librime with the Cangjie schema loaded. |
| Quick code accuracy | Approximately correct | Mock Quick codes (QO for 我, RO for 唔, etc.) match standard Cangjie first/last radical convention but are not machine-validated against a reference table. |
| Short-code phrase policy | Not implemented | Per spec §8.1: short-code phrase (三字詞 / 四字詞) is excluded from v1. Not needed for Gate 1. |
| MIXED_EXPERIMENTAL scheme | Not available | Returns RIME_UNAVAILABLE from both MockDecoder and RimeDecoderSpike. Expected. |
| English lexicon | Minimal (Stage 1) | Only test-buffer words + common HK English terms. Expand in Stage 2. |

---

## Next Step: Prototype 1 — Commit Rule Harness

Gate 1 passed. Proceed to Prototype 1.

Prototype 1 will implement:
- StateMachine (IDLE / COMPOSING / PREDICTING)
- CommitController with all six event handlers
- selectSpaceCommitTarget, selectPunctuationCommitTarget, memoryHardOverride, tieBreak
- Commit primitives: commitCandidate, commitLiteralBuffer, commitRawText
- Cold-start and warm test profiles
- Acceptance groups A–E

Gate 2 pass criteria:
- Space mis-commit ≤ 2%
- EN literal preservation ≥ 99%
- Sensitive memory write = 0
- Sensitive candidate leakage = 0
