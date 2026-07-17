# Keyboard Switch-Return Latency Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Prevent the IME main thread from loading the full Pinyin corpus when the keyboard is selected again.

**Architecture:** Store the Pinyin fuzzy preference as a volatile value in `CorpusLoader` and inject a provider for that value into the lazy `PinyinDecoder`. The settings collector updates the value without dereferencing the decoder, preserving the existing background warm-up order.

**Tech Stack:** Kotlin, Android `InputMethodService`, lazy initialization, JUnit 4, Gradle

---

### Task 1: Reproduce Main-Thread Lazy Initialization

**Files:**
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/PinyinPreferenceLoadingTest.kt`

**Step 1: Write the failing tests**

Create a bare `CorpusLoader` with the test suite's existing `Unsafe` context
pattern. Inspect the `pinyinDecoder$delegate` and assert that applying the Pinyin
fuzzy preference leaves it uninitialized:

```kotlin
@Test
fun `applying Pinyin preference does not initialize its corpus`() {
    val loader = bareLoader()
    val decoder = loader.lazyDelegate("pinyinDecoder")

    assertFalse(decoder.isInitialized())
    loader.setPinyinFuzzyEnabled(true)

    assertFalse(decoder.isInitialized())
}
```

Add a second test that replaces `pinyinLexicon$delegate` with a tiny controlled
lexicon, enables fuzzy input before decoder initialization, and verifies that
decoding `zong` reaches a `zhong` entry:

```kotlin
@Test
fun `lazy Pinyin decoder observes the latest preference`() {
    val loader = bareLoader().apply {
        setLazy("pinyinLexicon", PinyinLexicon(listOf(PinyinEntry("zhong", "中", 1.0))))
        setPinyinFuzzyEnabled(true)
    }

    assertEquals("中", loader.pinyinDecoder.decode("zong").candidates.first().text)
}
```

**Step 2: Run the focused test to verify RED**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.PinyinPreferenceLoadingTest
```

Expected: Kotlin compilation fails because `setPinyinFuzzyEnabled` does not yet
exist. This establishes that the required non-loading settings path is absent.

### Task 2: Defer Pinyin Corpus Initialization

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/CorpusLoader.kt:95-103`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt:284-289`
- Test: `android/app/src/test/kotlin/com/hkmixedkeyboard/PinyinPreferenceLoadingTest.kt`

**Step 1: Store the preference without loading the decoder**

In `CorpusLoader`, add a volatile boolean and expose an O(1) setter:

```kotlin
@Volatile private var pinyinFuzzyEnabled = false

fun setPinyinFuzzyEnabled(enabled: Boolean) {
    pinyinFuzzyEnabled = enabled
}
```

Construct the existing lazy decoder with a provider rather than the default
constant:

```kotlin
val pinyinDecoder: PinyinDecoder by lazy {
    PinyinDecoder(pinyinLexicon) { pinyinFuzzyEnabled }
}
```

**Step 2: Remove the main-thread decoder dereference**

Change the settings collector from:

```kotlin
corpus.pinyinDecoder.setFuzzyEnabled(prefs.pinyinFuzzy)
```

to:

```kotlin
corpus.setPinyinFuzzyEnabled(prefs.pinyinFuzzy)
```

Do not change warm-up ordering, corpus cache behavior, or Pinyin ranking.

**Step 3: Run focused tests to verify GREEN**

Run:

```bash
cd android
./gradlew testDebugUnitTest \
  --tests com.hkmixedkeyboard.PinyinPreferenceLoadingTest \
  --tests com.hkmixedkeyboard.PinyinDecoderTest \
  --tests com.hkmixedkeyboard.PinyinImeIntegrationTest
```

Expected: all selected tests pass with zero failures.

**Step 4: Commit the regression and fix**

```bash
git add \
  android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/CorpusLoader.kt \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/PinyinPreferenceLoadingTest.kt
git commit -m "fix: avoid blocking keyboard activation on Pinyin load"
```

### Task 3: Verify the Complete Android Project

**Files:**
- Modify only if verification exposes a defect caused by this change.

**Step 1: Run all JVM unit tests**

```bash
cd android
./gradlew testDebugUnitTest
```

Expected: all JVM tests pass with zero failures.

**Step 2: Build the debug APK**

```bash
cd android
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL` and a debug APK under
`android/app/build/outputs/apk/debug/`.

**Step 3: Inspect the final diff and working tree**

Confirm that every implementation line traces to the switch-return delay and
that the pre-existing haptics and version changes remain untouched.
