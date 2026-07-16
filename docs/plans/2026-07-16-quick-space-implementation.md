# Quick mode Space behavior implementation plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make 速成 keep Chinese candidates first and have Space finalize its raw code without selecting a candidate or adding whitespace.

**Architecture:** Candidate display no longer accepts a Quick-specific literal-first override. Space eligibility becomes mode-specific: Quick has no candidate selection path, while Jyutping and Pinyin retain their exact-candidate behavior. `CommitController` owns the final Quick raw-commit rule so every caller is safe even if it supplies a stale candidate.

**Tech Stack:** Kotlin, Android IME APIs, JUnit 4, Gradle Android unit tests.

---

### Task 1: Specify the mode-specific contracts with failing tests

**Files:**

- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/CandidateDisplayPolicyTest.kt:17-47`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/ThreeModeCandidateSelectionTest.kt:15-58`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/ThreeModeCommitContractTest.kt:20-54`

**Step 1: Write the failing preview-order test**

Replace the two Quick literal-first tests with one test that keeps Chinese candidates in front:

```kotlin
@Test
fun `two Quick letters keep Chinese candidates ahead of the literal`() {
    val result = policy.order(
        buffer = "eg",
        learned = listOf(MemorySuggestion(cnChar("唔", "eg"), 5)),
        english = emptyList(),
        decoded = listOf(cnChar("額", "eg")),
        literal = enLiteralCand("eg")
    )

    assertEquals(listOf("唔", "額"), result.take(2).map { it.text })
}
```

**Step 2: Write the failing Quick Space-selection tests**

Replace the tests that expect a Quick candidate or leading literal to be selected with:

```kotlin
@Test
fun `Quick never resolves a Space candidate`() {
    val candidate = candidate("唔", "rr", SourceSchema.QUICK, CandidateType.CHAR)

    assertNull(PinyinImePolicy.spaceCandidate(Scheme.QUICK, "rr", listOf(candidate)))
}
```

Keep the existing Jyutping assertion unchanged to protect its independent behavior.

**Step 3: Write the failing raw-commit test**

Replace both Quick candidate-commit tests with one test that passes an exact Chinese candidate but expects the raw Quick code, no whitespace, and no auto-commit record:

```kotlin
@Test
fun `Space in Quick commits the raw buffer even when a candidate is supplied`() {
    val out = makeCtrl(memory = UserMemory(), ctx = ImeContext(scheme = Scheme.QUICK)).onSpace(
        ImeStateData(buffer = "rr", imeState = ImeState.COMPOSING),
        candidate("唔", "rr", SourceSchema.QUICK, CandidateType.CHAR)
    )

    assertEquals("rr", out.committedText)
    assertEquals("", out.newState.buffer)
    assertEquals("rr", out.newState.prevCommitted)
    assertNull(out.newState.lastAutoCommit)
}
```

Add the required `assertNull` import. Keep Pinyin/Jyutping exact Space tests unchanged.

**Step 4: Run test to verify it fails**

Run:

```bash
cd android && ./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.CandidateDisplayPolicyTest --tests com.hkmixedkeyboard.ThreeModeCandidateSelectionTest --tests com.hkmixedkeyboard.ThreeModeCommitContractTest
```

Expected: failures show the literal still ranks first, Quick still resolves a candidate, and Space commits the candidate rather than `rr`.

**Step 5: Commit the test specification**

```bash
git add app/src/test/kotlin/com/hkmixedkeyboard/CandidateDisplayPolicyTest.kt \
  app/src/test/kotlin/com/hkmixedkeyboard/ThreeModeCandidateSelectionTest.kt \
  app/src/test/kotlin/com/hkmixedkeyboard/ThreeModeCommitContractTest.kt
git commit -m "test: specify Quick literal Space commits"
```

### Task 2: Remove Quick literal-first ordering and candidate selection

**Files:**

- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/engine/CandidateDisplayPolicy.kt:17-100`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/PinyinImePolicy.kt:20-77`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/CandidateCommitPolicy.kt:16-64`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt:917-920,1488-1501`

**Step 1: Simplify display ordering**

Remove `literalFirst` from `CandidateDisplayPolicy.order`, delete the two-ASCII-letter helper and the literal-first `when` branch, and restore the existing Chinese-first/short-buffer branch:

```kotlin
val ordered = if (chineseFirst || buffer.length <= 2) {
    custom + learnedChinese + mixed + decodedChinese + learnedEnglish +
        decodedEnglish + literal + english
} else {
    // Existing long-Latin ordering unchanged.
}
```

Remove the corresponding `literalFirst = ...` argument from `HkImeService.buildComposingDisplay`.

**Step 2: Make Quick ineligible for Space selection**

Delete `shouldPrioritizeQuickLiteral` and its literal-first block in `PinyinImePolicy.spaceCandidate`. Return `null` at the start of that function for `Scheme.QUICK`, then remove Quick-only normalization/imports made unreachable by the guard.

Split `CandidateCommitPolicy.isEligibleForSpace` from punctuation eligibility so Quick returns false while its existing punctuation eligibility remains unchanged:

```kotlin
fun isEligibleForSpace(candidate: DecodeCandidate?, scheme: Scheme, buffer: String): Boolean =
    scheme != Scheme.QUICK && isEligibleForPunctuation(candidate, scheme, buffer)
```

Ensure `selectForAutoCommit` checks `isEligibleForSpace`, not punctuation eligibility.

**Step 3: Bypass async candidate resolution only for Quick Space**

Keep Quick candidate resolution for punctuation, but have `requestCandidateCommitIfComposing` return false for a Quick `CandidateCommitIntent.Space`:

```kotlin
if (intent == CandidateCommitIntent.Space && imeCtx.scheme == Scheme.QUICK) return false
if (!isCandidateCommitScheme(imeCtx.scheme) || imeState.buffer.isEmpty()) return false
```

This makes `handleKey(KEY_SPACE)` fall through to `ctrl.onSpace(imeState)` immediately, while Jyutping/Pinyin preserve their asynchronous exact-candidate flow.

**Step 4: Run tests to verify it passes**

Run:

```bash
cd android && ./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.CandidateDisplayPolicyTest --tests com.hkmixedkeyboard.ThreeModeCandidateSelectionTest
```

Expected: PASS. The commit-contract test remains red until Task 3.

**Step 5: Commit the policy change**

```bash
git add app/src/main/kotlin/com/hkmixedkeyboard/engine/CandidateDisplayPolicy.kt \
  app/src/main/kotlin/com/hkmixedkeyboard/ime/PinyinImePolicy.kt \
  app/src/main/kotlin/com/hkmixedkeyboard/ime/CandidateCommitPolicy.kt \
  app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt
git commit -m "fix: separate Quick candidate selection"
```

### Task 3: Commit a Quick buffer as raw text on Space

**Files:**

- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/commit/CommitController.kt:80-102`
- Test: `android/app/src/test/kotlin/com/hkmixedkeyboard/ThreeModeCommitContractTest.kt:20-54`

**Step 1: Add the smallest Quick-specific branch**

At the start of `onSpace`, before inspecting `autoCommitCandidate`, finalize a nonempty Quick buffer as a literal:

```kotlin
if (ctx.scheme == Scheme.QUICK && state.buffer.isNotEmpty()) {
    return commitLiteralBuffer(
        buffer = state.buffer,
        learn = true,
        state = state.copy(lastAutoCommit = null)
    )
}
```

Do not call `doCommitRaw(" ", ...)`; this keeps the cursor immediately after the raw code and leaves no trailing whitespace. Empty-buffer Quick Space continues through the ordinary raw-space path.

**Step 2: Run tests to verify it passes**

Run:

```bash
cd android && ./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.ThreeModeCommitContractTest --tests com.hkmixedkeyboard.PinyinImeIntegrationTest
```

Expected: the new Quick contract and the unchanged Pinyin tests pass; any remaining old Quick expectations identify the regression cases to update in Task 4.

**Step 3: Commit the controller change**

```bash
git add app/src/main/kotlin/com/hkmixedkeyboard/commit/CommitController.kt
git commit -m "fix: commit Quick Space buffers literally"
```

### Task 4: Update production-path expectations and verify all modes

**Files:**

- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/PinyinImeIntegrationTest.kt:226-236`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/ConservativeSpaceTest.kt:124-142`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/MixedPhraseSuggestionTest.kt:40-46`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/ProductionPathThreeModeE2ETest.kt:35-42,96-163`

**Step 1: Update direct Quick Space expectations**

Change existing Quick tests that expect `"rr "`, `"rryo "`, or `"send "` from `onSpace` to expect the raw buffer with no trailing space. Preserve their punctuation expectations because punctuation still has its separate candidate path.

**Step 2: Separate the production Quick assertion from romanization auto-commit**

In `ProductionPathThreeModeE2ETest`, give Quick its own test path:

```kotlin
val resolved = CandidateCommitPolicy.selectForAutoCommit(Scheme.QUICK, "rryo", candidates)
assertNull(resolved)
assertEquals("唔該", tapController.onCandidateTap(candidate, state).committedText)
assertEquals("rryo", spaceController.onSpace(state, candidate).committedText)
```

Keep the shared exact-candidate Space and Backspace-restoration helper for Jyutping and Pinyin only. In the custom-entry loop, assert that Quick candidates remain tappable but are not Space-committable; retain committability expectations for Jyutping/Pinyin.

**Step 3: Run focused regression tests**

Run:

```bash
cd android && ./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.PinyinImeIntegrationTest --tests com.hkmixedkeyboard.ConservativeSpaceTest --tests com.hkmixedkeyboard.MixedPhraseSuggestionTest --tests com.hkmixedkeyboard.ProductionPathThreeModeE2ETest
```

Expected: PASS, with Quick candidate taps and punctuation still working, raw Quick Space commits having no delimiter, and romanization Space still committing exact Chinese candidates.

**Step 4: Run the complete unit suite**

Run:

```bash
cd android && ./gradlew testDebugUnitTest --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

**Step 5: Commit the regression updates**

```bash
git add app/src/test/kotlin/com/hkmixedkeyboard/PinyinImeIntegrationTest.kt \
  app/src/test/kotlin/com/hkmixedkeyboard/ConservativeSpaceTest.kt \
  app/src/test/kotlin/com/hkmixedkeyboard/MixedPhraseSuggestionTest.kt \
  app/src/test/kotlin/com/hkmixedkeyboard/ProductionPathThreeModeE2ETest.kt
git commit -m "test: cover independent Quick Space behavior"
```

