# Input Experience And Performance Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Improve perceived typing latency, candidate haptics, and bilingual candidate ordering while preserving existing commit behavior.

**Architecture:** Extract candidate ordering and request-generation policies into pure Kotlin components with unit tests. Keep composing key events state-only, run one cancellable decode per latest buffer, and centralize system haptics across keyboard and candidate UI.

**Tech Stack:** Kotlin, Android InputMethodService/View APIs, JUnit 4, Gradle.

---

### Task 1: Bilingual Candidate Ordering

**Files:**
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/engine/CandidateDisplayPolicy.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Test: `android/app/src/test/kotlin/com/hkmixedkeyboard/CandidateDisplayPolicyTest.kt`

1. Write failing tests for two-letter Chinese-first and three-letter
   English-first ordering with both languages retained.
2. Run the focused test and confirm failure.
3. Implement language grouping, personalization grouping, stable de-duplication,
   and display limits.
4. Connect `HkImeService.buildComposingDisplay` to the policy.
5. Re-run focused tests.

### Task 2: Remove Duplicate Decode Work

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/commit/CommitController.kt`
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/CandidateRequestGate.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Test: `android/app/src/test/kotlin/com/hkmixedkeyboard/CompositionPerformanceTest.kt`
- Test: `android/app/src/test/kotlin/com/hkmixedkeyboard/CandidateRequestGateTest.kt`

1. Write failing tests proving letter presses and composing backspace do not call
   the classifier.
2. Write failing tests proving only the latest candidate generation is accepted.
3. Run focused tests and confirm failures.
4. Make composing key/backspace operations state-only.
5. Remove the previous decode callback before posting the latest request and use
   generation checks before rendering.
6. Re-run focused and commit-regression tests.

### Task 3: Unified Haptic Feedback

**Files:**
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/HapticFeedbackPolicy.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateGridView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/EmojiPanelView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/SymbolPageView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Test: `android/app/src/test/kotlin/com/hkmixedkeyboard/HapticFeedbackPolicyTest.kt`

1. Write failing tests for typing versus selection haptic constants.
2. Implement one-shot system haptic policy.
3. Propagate the vibration preference to candidate and alternate panels.
4. Trigger haptic before candidate, grid, emoji, and symbol callbacks.
5. Ensure backspace repeat does not produce repeated haptics.
6. Re-run focused touch and hold tests.

### Task 4: Candidate Bar Rendering

**Files:**
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateRenderSnapshot.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarView.kt`
- Test: `android/app/src/test/kotlin/com/hkmixedkeyboard/CandidateRenderSnapshotTest.kt`

1. Write failing tests for candidate identity snapshots and unchanged-render
   detection.
2. Skip no-op candidate renders.
3. Reuse candidate child views where possible and update content/listeners.
4. Re-run focused tests.

### Task 5: Verification And Release Artifact

**Files:**
- Modify only if verification exposes a defect.

1. Run `./gradlew testDebugUnitTest`.
2. Run `./gradlew assembleDebug`.
3. Run `./gradlew assembleRelease`.
4. Report test count and exact artifact paths.
5. Note that Git commits are unavailable because this workspace has no `.git`.
