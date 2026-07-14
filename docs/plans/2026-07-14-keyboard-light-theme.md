# Keyboard Light Theme Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add an explicit persistent iPhone-style light keyboard colour theme while preserving the current dark theme and all input state.

**Architecture:** Extend the DataStore-backed `KeyboardSettings` with a typed `KeyboardTheme` preference. Resolve both palettes from one immutable `KeyboardThemeColors`; `HkImeService` observes settings, resolves tokens, and injects them on the UI thread.

**Tech Stack:** Kotlin, Android custom Views, AndroidX DataStore, coroutines, JUnit 4, Gradle.

---

### Task 1: Theme preference model and parsing

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/settings/KeyboardSettings.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardThemePreferenceTest.kt`

**Step 1: Write the failing test**

Cover the `DARK` default, `dark` and `ios_light` parsing, unknown-value fallback, and serialization.

**Step 2: Run test to verify it fails**

Run `./gradlew :app:testDebugUnitTest --tests com.hkmixedkeyboard.KeyboardThemePreferenceTest`; expect failure because the theme policy does not exist.

**Step 3: Write minimal implementation**

Add `KeyboardTheme`, DataStore key `keyboard_theme`, `KeyboardPrefs.theme`, pure parser/serializer, and `KeyboardSettings.setTheme`.

**Step 4: Run test to verify it passes**

Run the same test command; expect pass.

### Task 2: Central palette resolver

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardThemeColors.kt`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardThemeColorsTest.kt`

**Step 1: Write the failing test**

Assert both palettes, white light character keys versus grey function keys, near-black labels/icons, pressed colours, and Emoji UI tokens without glyph tint or alpha.

**Step 2: Run test to verify it fails**

Run `./gradlew :app:testDebugUnitTest --tests com.hkmixedkeyboard.KeyboardThemeColorsTest`; expect failure because resolver tokens do not exist.

**Step 3: Write minimal implementation**

Expand `KeyboardThemeColors` with candidate, divider/shadow, and all surrounding Emoji UI tokens. Resolve the resource-equivalent dark palette and specified light palette.

**Step 4: Run test to verify it passes**

Run the same test command; expect pass.

### Task 3: Live injection for core and symbol surfaces

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateGridView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/SymbolPageView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardThemeColorsTest.kt`

**Step 1: Write the failing test**

Assert palette presentation changes leave keyboard mode and symbol page state unchanged.

**Step 2: Run test to verify it fails**

Run `./gradlew :app:testDebugUnitTest --tests com.hkmixedkeyboard.KeyboardThemeColorsTest`; expect failure because live palette application does not exist.

**Step 3: Write minimal implementation**

Give existing Views redrawable palette properties. Have the existing service settings collector call idempotent UI-thread `applyTheme` without creating an input view or changing input state.

**Step 4: Run test to verify it passes**

Run the same test command; expect pass.

### Task 4: Emoji presentation and retention

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/EmojiPanelView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/EmojiThemePresentationTest.kt`

**Step 1: Write the failing test**

Test retained category, scroll, query, recents, and skin-tone selection under a palette change. Test that no glyph tint or alpha field exists.

**Step 2: Run test to verify it fails**

Run `./gradlew :app:testDebugUnitTest --tests com.hkmixedkeyboard.EmojiThemePresentationTest`; expect failure because the presentation state/policy does not exist.

**Step 3: Write minimal implementation**

Give `EmojiPanelView` an in-place theme setter. Apply all surrounding UI tokens, tint only functional drawables, retain native emoji glyph rendering, and never recreate the active panel.

**Step 4: Run test to verify it passes**

Run the same test command; expect pass.

### Task 5: Settings selector and previews

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/settings/SettingsActivity.kt`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardThemePreferenceTest.kt`

**Step 1: Write the failing test**

Test Chinese labels, selected accessibility state, and selection mapping to the typed preference.

**Step 2: Run test to verify it fails**

Run `./gradlew :app:testDebugUnitTest --tests com.hkmixedkeyboard.KeyboardThemePreferenceTest`; expect failure because the selector presentation policy does not exist.

**Step 3: Write minimal implementation**

Add a `鍵盤主題` selector with full-row previews that write via `KeyboardSettings.setTheme`, hydrate without duplicate persistence, and expose selected state accessibly.

**Step 4: Run test to verify it passes**

Run the same test command; expect pass.

### Task 6: Full verification and commit

**Files:**
- Modify only if verification identifies a scoped issue.

**Step 1:** Run `./gradlew :app:testDebugUnitTest`; expect zero failed tests.

**Step 2:** Run `./gradlew :app:assembleDebug`; expect `BUILD SUCCESSFUL`.

**Step 3:** Run `git diff --check && git status --short`; expect no whitespace errors and only feature-related files.

**Step 4:** Commit theme files and this plan with message `feat: add selectable keyboard colour themes`.
