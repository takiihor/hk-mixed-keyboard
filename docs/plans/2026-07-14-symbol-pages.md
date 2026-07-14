# Two-Page Symbol Keyboard Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the single symbol panel with an accessible, data-driven two-page symbol keyboard that matches the approved keyboard-only reference and retains existing IME behaviour.

**Architecture:** Content, interaction semantics, geometry, keyboard-mode state, and theme tokens live outside the renderer. `SymbolPageView` consumes a page plus Enter specification, calculates visual/non-overlapping touch cells, renders keys/popups/vectors, and emits a selected `SymbolKeySpec`; `HkImeService` alone routes the event to existing IME handlers.

**Tech Stack:** Kotlin 2.0, Android custom `View`, Android vector drawables, Android `InputMethodService`, JUnit 4, Gradle.

---

### Task 1: Define and prove the symbol data contract

**Files:**

- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/SymbolKeyboardSpec.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/SymbolKeyboardSpecTest.kt`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/SymbolPageLayoutTest.kt`

**Step 1: Write the failing test**

Assert the exact four-by-ten Unicode matrices, page labels/dots, required long-press mappings (including literal `\\`), Chinese accessibility labels, and function-key weights.

**Step 2: Run it to verify it fails**

Run: `cd android && ./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.SymbolKeyboardSpecTest`

Expected: compilation fails because `SymbolKeyboardSpec` does not exist.

**Step 3: Implement the smallest immutable data model**

Add `SymbolPage`, `SymbolKeyRole`, `SymbolKeyIcon`, `SymbolKeySpec`, and `SymbolKeyboardSpec`. Define the specified Page 1/Page 2 rows, semantic labels, alternatives, bottom labels, and indicator state as UTF-8 literals. Keep all content out of `SymbolPageView`.

**Step 4: Run it to verify it passes**

Run the same command and expect the contract tests to pass.

### Task 2: Add pure mode, Enter action, and geometry policies

**Files:**

- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/SymbolKeyboardState.kt`
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/SymbolKeyboardGeometry.kt`
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/SymbolEnterAction.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/SymbolKeyboardStateTest.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/SymbolKeyboardGeometryTest.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/SymbolEnterActionTest.kt`

**Step 1: Write the failing tests**

Test `ALPHABET → SYMBOL/COMMON`, page toggling without text payload, and `ABC → ALPHABET` without altering independent language or Shift/Caps values. Test `EditorInfo` Search/Send/Next/Done/Go/Return resolution. At the narrowest width, test ten contiguous, non-overlapping hit cells per row and visual gaps that contract before requested text sizing.

**Step 2: Run them to verify they fail**

Run: `cd android && ./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.SymbolKeyboardStateTest --tests com.hkmixedkeyboard.SymbolKeyboardGeometryTest --tests com.hkmixedkeyboard.SymbolEnterActionTest`

Expected: compilation fails because the policy classes do not exist.

**Step 3: Implement the pure policies**

Add immutable transition functions, a geometry rect model, and an Enter-action resolver. Keep page selection independent of alphabet scheme and Shift state. Use full contiguous cell slots as hit targets, inset only visible rects, and never inflate touch rects into neighbours.

**Step 4: Run them to verify they pass**

Run the same command and expect all policy tests to pass.

### Task 3: Centralize keyboard theme tokens and add icon resources

**Files:**

- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardThemeColors.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt`
- Modify: `android/app/src/main/res/values/colors.xml`
- Create: `android/app/src/main/res/drawable/ic_symbol_backspace.xml`
- Create: `android/app/src/main/res/drawable/ic_symbol_return.xml`
- Create: `android/app/src/main/res/drawable/ic_symbol_search.xml`
- Create: `android/app/src/main/res/drawable/ic_symbol_send.xml`
- Create: `android/app/src/main/res/drawable/ic_symbol_next.xml`
- Create: `android/app/src/main/res/drawable/ic_symbol_done.xml`
- Create: `android/app/src/main/res/drawable/ic_symbol_go.xml`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardThemeColorsTest.kt`

**Step 1: Write the failing test**

Assert the symbol palette is supplied only by the shared immutable token object and rebinding visual theme input does not alter `SymbolKeyboardState`.

**Step 2: Run it to verify it fails**

Run: `cd android && ./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.KeyboardThemeColorsTest`

Expected: compilation fails because `KeyboardThemeColors` does not exist.

**Step 3: Implement tokens and resources**

Expose resource-backed keyboard, symbol-key, function-key, pressed, label, indicator, and popup colours through `KeyboardThemeColors`; migrate `KeyboardView` to it. Define dark symbol tokens in resources and vector paths for Backspace plus every Enter action.

**Step 4: Verify test and resources**

Run: `cd android && ./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.KeyboardThemeColorsTest assembleDebug`

Expected: tests pass and Android resource processing succeeds.

### Task 4: Replace the symbol renderer with a data-driven custom view

**Files:**

- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/SymbolPageView.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/SymbolLongPressTest.kt`

**Step 1: Write the failing tests**

Test that a normal release emits the base once, a long press emits no base before selection, a chosen variant emits once, and cancel/outside emits nothing. Test that the view-facing model uses a supplied page and does not define a matrix or colours itself.

**Step 2: Run it to verify it fails**

Run: `cd android && ./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.SymbolLongPressTest`

Expected: compilation fails because the symbol long-press policy does not exist.

**Step 3: Implement rendering and touch behaviour**

Rewrite the panel as one custom `View` consuming `SymbolKeyboardSpec`, `SymbolKeyboardGeometry`, and `KeyboardThemeColors`. Draw normal/function keys, page dots, readable dark variant popup, pressed state, and vector icons. Use geometry hit cells for all touch selection. Publish virtual accessibility nodes from each key’s semantic label and announce the active page. Route long-press selection through one controller so cancel and slide-out never commit text.

**Step 4: Run it to verify it passes**

Run the same command and expect all long-press tests to pass.

### Task 5: Route symbol events through existing IME handlers

**Files:**

- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/SymbolKeyboardRoutingTest.kt`

**Step 1: Write the failing tests**

Test the pure routing policy for text, page switch, `ABC`, Space, Backspace, and Enter. Assert page switches have no text payload, symbols preserve exact Unicode output, and an alphabet language/Shift snapshot is unchanged by page switching and return. Test the `EditorInfo` action supplied to the view.

**Step 2: Run it to verify it fails**

Run: `cd android && ./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.SymbolKeyboardRoutingTest`

Expected: compilation fails because the routing policy does not exist.

**Step 3: Implement minimal service wiring**

Store independent keyboard-mode and symbol-page state in the IME. Opening Symbols selects `COMMON`; toggle updates the existing panel in place; `ABC` restores alphabet. Text calls `insertStandaloneText`; Space, Backspace, and Enter call `handleKey` using existing sentinel labels, retaining repeat, composition, real Enter-event, haptic, and input-connection handling. Supply the active `EditorInfo`-resolved icon/action to the view.

**Step 4: Run it to verify it passes**

Run the same command and expect routing tests to pass.

### Task 6: Verify integrated keyboard and capture evidence

**Files:**

- Modify: only files required by failures from Tasks 1–5.

**Step 1: Run full Android verification**

Run: `cd android && ./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`

Expected: tests, lint, debug APK, and release APK succeed.

**Step 2: Inspect the change**

Run: `git diff --check master...HEAD && git status --short`

Expected: no whitespace errors and only files needed for the symbol keyboard.

**Step 3: Capture both pages where an AVD/device is available**

Use an installed emulator/device to show Common and Extended and capture one screenshot each. If no bootable device exists, report that limitation rather than fabricating screenshots.

**Step 4: Commit the implementation**

Run: `git add <verified files> && git commit -m "feat: redesign symbol keyboard"`
