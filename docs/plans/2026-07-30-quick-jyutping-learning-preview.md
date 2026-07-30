# Quick Jyutping Learning Preview Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Show accurate, toned Jyutping for Quick candidates while composing and briefly confirm the reading of the candidate actually committed.

**Architecture:** Generate a compact exact-text reading asset from the pinned rime-cantonese dictionaries, load it lazily through `CorpusLoader`, and isolate display eligibility/formatting in a pure Kotlin policy. Extend the candidate strip with a compact preview line, then wire live and post-commit updates through `HkImeService` without changing decode or commit rules.

**Tech Stack:** Python 3 corpus tooling and `unittest`, Kotlin/JVM, Android custom views, JUnit 4, Gradle.

---

### Task 1: Generate a pinned toned-Jyutping learning asset

**Files:**
- Create: `corpus/tools/build_jyutping_readings.py`
- Create: `corpus/tools/tests/fixtures/jyutping_chars_sample.dict.yaml`
- Create: `corpus/tools/tests/fixtures/jyutping_words_sample.dict.yaml`
- Create: `corpus/tools/tests/test_build_jyutping_readings.py`
- Create: `android/app/src/main/assets/corpus/jyutping_readings.csv`
- Modify: `corpus/sources/corpus_license_register.csv`
- Modify: `android/app/src/main/assets/licenses/NOTICE.txt`

**Step 1: Write the failing generator tests**

Cover these behaviours with small fixture dictionaries and Quick CSV fixtures
created in a temporary directory:

```python
class BuildJyutpingReadingsTest(unittest.TestCase):
    def test_preserves_tones_and_word_syllable_spaces(self):
        rows = build_readings(...)
        self.assertEqual("hoeng1 gong2", rows["香港"])

    def test_filters_out_text_not_reachable_from_quick(self):
        rows = build_readings(...)
        self.assertNotIn("詞庫以外", rows)

    def test_keeps_first_source_reading_for_polyphonic_text(self):
        rows = build_readings(...)
        self.assertEqual("nei5", rows["你"])
```

**Step 2: Run the tests to verify RED**

Run:

```bash
python3 -m unittest corpus.tools.tests.test_build_jyutping_readings -v
```

Expected: FAIL because `build_jyutping_readings` does not exist.

**Step 3: Implement the minimal deterministic generator**

The tool must:

- parse tab-separated Rime dictionary body lines after the YAML `...` marker;
- accept the pinned rime-cantonese checkout plus Quick character/phrase CSVs;
- collect only Quick-reachable Chinese text;
- preserve `[a-z]+[1-6]` syllables and spaces exactly;
- keep the first exact-text reading according to explicit input-file order;
- emit stable UTF-8 CSV sorted by Chinese text with header
  `text,jyutping`;
- reject readings without tone numbers rather than emitting incomplete teaching
  data.

Use rime-cantonese revision
`c99b16e44d2df77a5cb8fb0867dd2bab7a112cb0` and process dictionaries in this
order:

1. `jyut6ping3.words.dict.yaml`
2. `jyut6ping3.phrase.dict.yaml`
3. `jyut6ping3.chars.dict.yaml`

**Step 4: Run the tests to verify GREEN**

Run:

```bash
python3 -m unittest corpus.tools.tests.test_build_jyutping_readings -v
```

Expected: all generator tests PASS.

**Step 5: Generate and audit the production asset**

Clone/check out the pinned revision in a temporary directory, then run:

```bash
python3 corpus/tools/build_jyutping_readings.py \
  --rime-dir /tmp/<checkout> \
  --quick-chars android/app/src/main/assets/corpus/hk_core_chars.csv \
  --quick-phrases android/app/src/main/assets/corpus/hk_core_phrases.csv \
  --output android/app/src/main/assets/corpus/jyutping_readings.csv
```

Verify:

```bash
rg '^香港,hoeng1 gong2$|^你,nei5$|^我,ngo5$' \
  android/app/src/main/assets/corpus/jyutping_readings.csv
```

Expected: all three standard readings are present.

Record the pinned revision, derived asset, CC BY 4.0 / ODbL attribution, build
date, and normalization/filtering in the licence register and bundled notice.

**Step 6: Commit**

```bash
git add corpus/tools/build_jyutping_readings.py \
  corpus/tools/tests/fixtures/jyutping_chars_sample.dict.yaml \
  corpus/tools/tests/fixtures/jyutping_words_sample.dict.yaml \
  corpus/tools/tests/test_build_jyutping_readings.py \
  corpus/sources/corpus_license_register.csv \
  android/app/src/main/assets/corpus/jyutping_readings.csv \
  android/app/src/main/assets/licenses/NOTICE.txt
git commit -m "data: add toned Jyutping learning readings"
```

### Task 2: Add an exact-reading lookup and preview policy

**Files:**
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/JyutpingReadingLookup.kt`
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/JyutpingLearningPreview.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/JyutpingReadingLookupTest.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/JyutpingLearningPreviewTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/CorpusLoader.kt`

**Step 1: Write failing lookup tests**

Test real parsing through a `Reader`, including CSV quoting:

```kotlin
@Test
fun `loads exact word readings with tones and spaces`() {
    val lookup = JyutpingReadingLookup.from(
        "text,jyutping\n香港,hoeng1 gong2\n你,nei5\n".reader()
    )
    assertEquals("hoeng1 gong2", lookup.readingFor("香港"))
    assertNull(lookup.readingFor("香"))
}
```

**Step 2: Run the lookup test to verify RED**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.JyutpingReadingLookupTest
```

Expected: compilation FAIL because the lookup class is missing.

**Step 3: Implement the minimal lookup**

`JyutpingReadingLookup` owns an immutable `Map<String, String>`, exposes
`readingFor(text)`, and has a `from(Reader)` factory that skips comments/header,
uses the existing `Csv.split`, rejects blank/malformed rows, and keeps the first
reading for duplicate text.

Expose a lazy instance from `CorpusLoader`:

```kotlin
val jyutpingReadingLookup: JyutpingReadingLookup by lazy {
    runCatching {
        ctx.assets.open("corpus/jyutping_readings.csv").bufferedReader().use {
            JyutpingReadingLookup.from(it)
        }
    }.getOrElse {
        Log.e("CorpusLoader", "Failed to load Jyutping learning readings", it)
        JyutpingReadingLookup.empty()
    }
}
```

Warm it after the Quick candidate indexes in `warmQuick()`.

**Step 4: Run the lookup test to verify GREEN**

Run the Task 2 lookup test again. Expected: PASS.

**Step 5: Write failing preview-policy tests**

Cover:

- first eligible Chinese candidate is used, even if an English literal leads;
- labels are formatted exactly as `香港 · hoeng1 gong2`;
- only `Scheme.QUICK` produces a live preview;
- English candidates and missing exact readings produce no label;
- a tapped Quick candidate formats independently of the previously leading
  candidate.

Desired API:

```kotlin
val policy = JyutpingLearningPreview(lookup)
assertEquals(
    "香港 · hoeng1 gong2",
    policy.liveLabel(Scheme.QUICK, listOf(enLiteralCand("hk"), hkCandidate))
)
assertEquals("你 · nei5", policy.committedLabel(Scheme.QUICK, youCandidate))
```

**Step 6: Run the policy tests to verify RED**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.JyutpingLearningPreviewTest
```

Expected: compilation FAIL because the policy is missing.

**Step 7: Implement the minimal policy**

Keep eligibility strict: candidate text must have an exact reading, contain CJK
text, and not be `EN_LITERAL`. Do not join per-character readings for a missing
phrase.

**Step 8: Run Task 2 tests to verify GREEN**

Run both new test classes. Expected: PASS.

**Step 9: Commit**

```bash
git add android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/JyutpingReadingLookup.kt \
  android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/CorpusLoader.kt \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ui/JyutpingLearningPreview.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/JyutpingReadingLookupTest.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/JyutpingLearningPreviewTest.kt
git commit -m "feat: resolve Quick candidates to toned Jyutping"
```

### Task 3: Add the preview line to the candidate strip

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarLayoutPolicy.kt`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/CandidateBarLayoutPolicyTest.kt`

**Step 1: Write the failing layout-policy test**

Specify a fixed preview-capable bar height and compact preview typography:

```kotlin
@Test
fun `candidate strip reserves a compact Jyutping learning line`() {
    assertEquals(58f, CandidateBarLayoutPolicy.VISIBLE_HEIGHT_DP)
    assertEquals(13f, CandidateBarLayoutPolicy.PREVIEW_TEXT_SIZE_SP)
}
```

Keep the existing invariant that all candidate-bar display states have the same
height.

**Step 2: Run the layout test to verify RED**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.CandidateBarLayoutPolicyTest
```

Expected: FAIL with the old 42dp height / missing preview constant.

**Step 3: Implement the two-row candidate strip**

Change `CandidateBarView` into a vertical `LinearLayout` containing:

- a single-line `TextView` for the learning preview;
- an inner `HorizontalScrollView` containing the existing candidate row.

Add:

```kotlin
fun setLearningPreview(label: String?)
```

An empty label uses `View.GONE`. `showSystemMessage`, `showSafeMode`,
`showLoading`, and explicit clears hide the preview. Candidate rows retain their
current text size, tap listeners, haptics, expand control, accessibility labels,
horizontal scrolling, render snapshots, and theme colours.

Use the fixed 58dp policy height for every state so loading, decoding,
confirmation expiry, and clearing never resize the IME window.

**Step 4: Run layout and candidate presentation tests**

Run:

```bash
cd android
./gradlew testDebugUnitTest \
  --tests com.hkmixedkeyboard.CandidateBarLayoutPolicyTest \
  --tests com.hkmixedkeyboard.CandidateRenderSnapshotTest \
  --tests com.hkmixedkeyboard.CandidatePresentationTest
```

Expected: PASS.

**Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarView.kt \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarLayoutPolicy.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/CandidateBarLayoutPolicyTest.kt
git commit -m "feat: add Jyutping line to candidate strip"
```

### Task 4: Wire live preview and committed-candidate confirmation

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/JyutpingLearningPreviewTest.kt`

**Step 1: Add a failing confirmation-selection test**

Extend the pure policy test to prove the confirmed label comes from the tapped
candidate, not the leading live candidate:

```kotlin
assertEquals("我 · ngo5", policy.liveLabel(Scheme.QUICK, listOf(me, you)))
assertEquals("你 · nei5", policy.committedLabel(Scheme.QUICK, you))
```

Run the policy test and confirm RED if the committed API is not yet complete.

**Step 2: Wire live decode updates**

Create the policy lazily from `corpus.jyutpingReadingLookup`. After publishing a
Quick display list, set the live label from the first eligible Chinese candidate.
For Jyutping/Pinyin modes, loading/safe mode, new sessions, and no eligible
candidate, clear the learning line.

**Step 3: Wire brief post-commit confirmation**

Before committing a tapped composed Quick candidate, calculate its exact label.
After `applyOutput` updates the candidate row, show that label for 1400ms.

Store the delayed clear as a `Runnable`; always remove the previous callback
before scheduling another. New composing input, scheme changes, sensitive-field
changes, reset, and `onDestroy` cancel the runnable. Expiry clears only the
learning label, leaving next-character predictions untouched.

Prediction taps with no active composition do not create confirmations.

**Step 4: Run focused JVM tests**

Run:

```bash
cd android
./gradlew testDebugUnitTest \
  --tests com.hkmixedkeyboard.JyutpingLearningPreviewTest \
  --tests com.hkmixedkeyboard.CandidateDisplayPolicyTest \
  --tests com.hkmixedkeyboard.CommitRuleTest
```

Expected: PASS.

**Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/JyutpingLearningPreviewTest.kt
git commit -m "feat: preview Jyutping while typing Quick"
```

### Task 5: Full verification and visual smoke test

**Files:**
- Modify only if a verification failure is caused by this feature.

**Step 1: Run corpus tooling tests**

```bash
python3 -m unittest discover -s corpus/tools/tests -v
```

Expected: PASS.

**Step 2: Run all Android JVM tests**

```bash
cd android
./gradlew testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

**Step 3: Run lint and compile checks without versioned packaging**

```bash
cd android
./gradlew lintDebug compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. Avoid `assemble`, `bundle`, and `install` because
this repository intentionally mutates `version.properties` for those tasks.

**Step 4: Inspect the final diff**

```bash
git status --short
git diff --check
git diff HEAD~4 --stat
```

Confirm unrelated pre-existing files, especially `android/app/version.properties`
and untracked screenshots, remain untouched.

**Step 5: Manual device/emulator smoke test if available**

- In 速成, type the code for `香港`; verify the preview reads
  `香港 · hoeng1 gong2`.
- Tap a non-leading candidate; verify its own reading is shown for about 1.4s.
- Begin typing during confirmation; verify the new live preview wins immediately.
- Switch to 粵拼 or 拼音; verify no learning line appears.
- Open a password field; verify only the safe-mode message appears.
- Verify candidates still scroll, tap, expand, and commit normally in light and
  dark themes.

**Step 6: Commit any verification-only correction**

Commit only scoped corrections, with a message describing the corrected
behaviour. If no correction was needed, do not create an empty commit.
