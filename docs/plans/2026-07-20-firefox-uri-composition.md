# Firefox URI Composition Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Let URI fields such as Firefox's address bar use the existing Chinese composition and candidate pipeline while retaining their URL-oriented keyboard surface.

**Architecture:** `EditorLayoutPolicy` will continue to map `TYPE_TEXT_VARIATION_URI` to `KeyboardSurface.URI`, but its direct-entry predicate will only cover numeric, signed-decimal, phone, and email surfaces. `DirectInputPolicy` will therefore leave URI letters in the normal Quick/Jyutping/Pinyin composition path without introducing a Firefox-specific exception.

**Tech Stack:** Kotlin, Android `InputType`/`EditorInfo`, JUnit 4, Gradle, Android Debug Bridge.

---

### Task 1: Specify URI composition behavior

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/DirectInputPolicyTest.kt:76-102`
- Reference: `android/app/src/test/kotlin/com/hkmixedkeyboard/EditorLayoutPolicyTest.kt:35-49`

**Step 1: Write the failing test**

Replace the URI assertion in the existing direct-entry test with a focused expectation that keeps email and phone direct but makes URI composing:

```kotlin
@Test
fun `URI editors keep URL layout but compose letters`() {
    val uriInputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI

    assertFalse(
        DirectInputPolicy.shouldUseDirectLatinCommit(
            inputType = uriInputType,
            packageName = "org.mozilla.firefox",
            privateImeOptions = null
        )
    )
    assertFalse(DirectInputPolicy.shouldCommitKeyDirectly("q", directLatinCommit = false))
}
```

Keep the email and phone assertions in a test named `email and phone editors commit their direct-entry characters`.

**Step 2: Run the focused test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.DirectInputPolicyTest`

Expected: FAIL because `shouldUseDirectLatinCommit` still returns `true` for `TYPE_TEXT_VARIATION_URI`.

### Task 2: Decouple URI layout from direct commit

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/EditorLayoutPolicy.kt:39-40`
- Test: `android/app/src/test/kotlin/com/hkmixedkeyboard/DirectInputPolicyTest.kt:76-113`

**Step 1: Implement the minimal policy change**

Replace the broad surface comparison with an explicit set of truly direct-entry surfaces:

```kotlin
fun usesDirectEntry(inputType: Int): Boolean =
    surfaceFor(inputType) in setOf(
        KeyboardSurface.NUMBER,
        KeyboardSurface.SIGNED_DECIMAL_NUMBER,
        KeyboardSurface.PHONE,
        KeyboardSurface.EMAIL
    )
```

Do not alter `surfaceFor`; it must continue returning `KeyboardSurface.URI` for URI editors. Do not add a Firefox package condition.

**Step 2: Run the focused test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.DirectInputPolicyTest`

Expected: PASS, including the new URI-composition regression test.

**Step 3: Run the related layout tests**

Run: `./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.EditorLayoutPolicyTest`

Expected: PASS, confirming URI inputs retain the `KeyboardSurface.URI` layout.

**Step 4: Commit the implementation**

```bash
git add android/app/src/main/kotlin/com/hkmixedkeyboard/ime/EditorLayoutPolicy.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/DirectInputPolicyTest.kt
git commit -m "fix: compose Chinese in URI fields"
```

### Task 3: Build and verify Firefox on-device

**Files:**
- Verify only: `android/app/build/outputs/apk/debug/app-debug.apk`

**Step 1: Build the debug APK**

Run: `./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL` and a debug APK at `app/build/outputs/apk/debug/app-debug.apk`.

**Step 2: Install and activate the tested IME**

Run:

```bash
ADB=/home/europa/Android/Sdk/platform-tools/adb
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" shell ime enable com.hkmixedkeyboard.debug/com.hkmixedkeyboard.ime.HkImeService
"$ADB" shell ime set com.hkmixedkeyboard.debug/com.hkmixedkeyboard.ime.HkImeService
```

Expected: install succeeds and `settings get secure default_input_method` reports the debug IME service.

**Step 3: Reproduce the original Firefox field**

Launch Firefox, focus its address bar, and inspect the focused session:

```bash
"$ADB" shell monkey -p org.mozilla.firefox -c android.intent.category.LAUNCHER 1
"$ADB" shell dumpsys input_method
```

Expected: the latest Firefox `StartInput` reports `inputType=0x11` (URI). Tap the on-screen `q`/手 key and capture a screenshot with `"$ADB" exec-out screencap -p`; the candidate strip must show Quick candidates (for example 手), rather than Firefox URL suggestions from a raw committed `q`.

**Step 4: Recheck an in-page web text field**

Focus a normal Firefox web form, tap `q`/手, and capture another screenshot.

Expected: candidates remain visible and the control continues to use the existing composition behavior.

**Step 5: Run the full unit suite**

Run: `./gradlew testDebugUnitTest`

Expected: `BUILD SUCCESSFUL` with no test failures.
