# Commit Rule Harness Report
## Prototype 1 — Gate 2

**Date:** 2026-06-13  
**Spec:** HK_Mixed_Keyboard_app_development_guide_v1.5.md §12–§17, §22

---

## Gate 2 Verdict: ✓ PASSED

All Gate 2 metrics met.

---

## Gate 2 Metrics

| Metric | Result | Limit | Status |
|--------|--------|-------|--------|
| Space mis-commit rate | 0.0% | ≤ 2% | ✓ PASS |
| EN literal preservation | 100% | ≥ 99% | ✓ PASS |
| Sensitive memory write | 0 | = 0 | ✓ PASS |
| Sensitive candidate leakage | 0 | = 0 | ✓ PASS |
| Accidental single-choice override | None | Never | ✓ PASS |

---

## Test Results: 49 tests, 49 passed, 0 failed

### Acceptance Groups

| Group | Profile | Cases | Passed | Mis-commit |
|-------|---------|-------|--------|------------|
| A — HK Core Characters/Phrases | cold | 10 | 10 | 0.0% |
| A — HK Core Characters/Phrases | warm | 10 | 10 | 0.0% |
| B — Mixed English tokens | cold | 8 | 8 | 0.0% |
| B — Mixed English tokens | warm | 8 | 8 | 0.0% |
| C — Whitelist Collisions | cold | 8 | 8 | 0.0% |
| C — Whitelist Collisions | warm | 8 | 8 | 0.0% |
| D — Phrase-first | cold | 9 | 9 | 0.0% |
| E — Privacy / Sensitive Fields | — | 6 | 6 | 0 violations |

---

## Commit Rules Verified

### Space Smart Commit
- Clear Chinese → commits top CN candidate
- Clear English → commits EN literal
- Collision (CN + enIsWord): tieBreak applied
  - Cold-start, len≤2, isHkCore → Chinese (e.g. 唔, 嘅)
  - Cold-start, len≤2, NOT isHkCore → English (e.g. ok=仗 → "ok")
  - Warm, cnRatio≥0.65 → Chinese
  - Warm, enRatio≤0.35 → English
- Prefix-only CN: never auto-committable

### Space Always-Space Mode
- Commits buffer as literal regardless of CN match

### English Autocomplete Never Space-Commits
- "hap" → committed "hap", not "happy"

### Enter Policies
- COMMIT_THEN_SWALLOW: commits buffer literal, swallows Enter event
- ALWAYS_PASS_THROUGH: passes Enter even with non-empty buffer
- Enter never auto-selects Chinese candidate

### Backspace Revert
- Non-empty buffer: pops last letter
- Empty buffer + lastAutoCommit: restores original buffer, deletes committed text
- Empty buffer + no lastAutoCommit: deletes char before cursor (delegated)

### Punctuation Conservative Commit
- SHORT_WHITELIST → EN literal even if CN match
- enIsWord → EN literal
- Pure CN → commits Chinese
- Sentence terminators reset context; non-reset punctuation preserves prevCommitted

### Memory Hard Override
- count≥3 AND confidence≥0.8 → override
- count=1 accidental tap → no override
- Triggered correctly for seeded warm entries (我哋 at count=5)

### Sensitive Field (Group E)
- 0 memory writes across all event types (Tap, Space, Enter)
- Candidate bar empty after commit
- prevCommitted null after commit
- hardOverride returns null in sensitive field

---

## Canonical Casing (spec §14.5)
Applied inside `doCommitCandidate()`:

| Buffer | Committed |
|--------|-----------|
| mtr | MTR |
| fps | FPS |
| mpf | MPF |
| hkd | HKD |
| usd | USD |
| pdf | PDF |
| qr | QR |

---

## Architecture Notes

### Pure JVM — No Android Dependency
CommitController, StateMachine, UserMemory, and Classifier are all pure Kotlin/JVM.
Integrate into Android IME Service in Prototype 2 by injecting classify function and wiring event handlers to InputMethodService callbacks.

### classify() as Injected Function
CommitController takes `classify: (String) -> ClassifyResult` rather than a concrete Classifier. This allows tests to inject hand-crafted ClassifyResults directly, and lets Prototype 2 swap in the real decoder.

### MemoryEntry.confidence
`confidence = max(cnCount, enCount) / count`
Represents fraction of times the dominant choice was made.
At count=3 with consistent CN selection: confidence = 1.0 ≥ 0.8 threshold.
Prevents single accidental pick from triggering override (count=1 → never hard-overrides).

---

## Known Limitations

| Item | Status | Impact |
|------|--------|--------|
| Post-commit prediction bar | Stub (returns empty list) | Predictions after commit not tested. Extend in Stage 2 with phrase continuations. |
| User memory persistence | In-memory only | Data is lost on restart. Prototype 2 wires Room/SQLite. |
| Backspace after non-auto-commit | Delegates to host | Real implementation requires `deleteSurroundingText()` in IME. |
| EnglishLexicon | Minimal (Stage 1) | Only test-buffer vocabulary. Expand before production. |
| MIXED_EXPERIMENTAL scheme | Not implemented | Excluded from v1 per spec. |

---

## Next Step: Prototype 2 — Android IME Thin UI

Both Gate 1 (Prototype 0) and Gate 2 (Prototype 1) have passed.

Prototype 2 wires CommitController + ExtendedMockDecoder (or RimeDecoder) into a minimal Android InputMethodService:
- Main keyboard layout with QWERTY + Cangjie root labels
- Candidate bar
- Safe Keyboard Mode detection
- Field transition reset
- Basic settings stub
- Debug panel
