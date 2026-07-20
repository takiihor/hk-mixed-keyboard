# Next Product Work Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix the cropped launcher icon, add lightweight first-use guidance and current store screenshots, establish an offline HK mixed-language ranking benchmark, use it to make an evidence-based bigram decision, and prototype optional Jyutping annotations.

**Architecture:** Keep all new behaviour offline and split the work into independently reviewable commits. Put state transitions and scoring in pure Kotlin classes with JVM tests; keep Android classes limited to rendering, DataStore persistence, and IME lifecycle wiring. The benchmark lands before any bigram production code, and annotations remain disabled unless the user explicitly enables them.

**Tech Stack:** Android/Kotlin, AndroidX DataStore, Room (existing only), JUnit 4 JVM tests, existing corpus loaders/decoder, ADB/emulator, ImageMagick for deterministic icon assets.

---

## Pre-flight rules

- Work from a clean feature branch or isolated global worktree based on `master`.
- Do not use `-PversionedBuild=true`; these tasks must not select a new public version.
- Do not add `INTERNET`, analytics, ads, telemetry, cloud sync, or third-party SDKs.
- Do not copy private chats into benchmark fixtures.
- Commit each numbered task separately and run its stated verification before continuing.
- Preserve the source artwork `hk-mixed-keyboard-profile.png`; generated Android resources are derivatives.

## Task 0: Repair the adaptive launcher icon

**Files:**

- Source only: `hk-mixed-keyboard-profile.png`
- Modify: `android/app/src/main/res/mipmap-mdpi/ic_launcher_foreground.png`
- Modify: `android/app/src/main/res/mipmap-hdpi/ic_launcher_foreground.png`
- Modify: `android/app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.png`
- Modify: `android/app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png`
- Modify: `android/app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png`
- Modify: `android/app/src/main/res/mipmap-mdpi/ic_launcher.png`
- Modify: `android/app/src/main/res/mipmap-hdpi/ic_launcher.png`
- Modify: `android/app/src/main/res/mipmap-xhdpi/ic_launcher.png`
- Modify: `android/app/src/main/res/mipmap-xxhdpi/ic_launcher.png`
- Modify: `android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`

**Step 1: Record the source checksum and dimensions**

Run:

```bash
sha256sum hk-mixed-keyboard-profile.png
identify hk-mixed-keyboard-profile.png
```

Expected: a square source image; keep the checksum for the post-generation integrity check.

**Step 2: Generate adaptive foregrounds with safe padding**

For each 108/162/216/324/432 px foreground canvas, resize the source to 61% of the canvas (66/99/132/198/264 px), centre it, and leave the remainder transparent. Use a high-quality Lanczos filter and do not overwrite the source.

Equivalent ImageMagick operation for mdpi:

```bash
convert hk-mixed-keyboard-profile.png \
  -filter Lanczos -resize 66x66 \
  -gravity center -background none -extent 108x108 \
  android/app/src/main/res/mipmap-mdpi/ic_launcher_foreground.png
```

Repeat with the proportional sizes above for the other densities.

**Step 3: Generate legacy icons conservatively**

For 48/72/96/144/192 px legacy canvases, use 76% content sizes (36/55/73/109/146 px), centre on the existing launcher background colour `#202124`, and write the five `ic_launcher.png` files.

**Step 4: Confirm source integrity and output geometry**

Run:

```bash
sha256sum hk-mixed-keyboard-profile.png
identify android/app/src/main/res/mipmap-*/ic_launcher*.png
cd android && ./gradlew assembleDebug
```

Expected: source checksum unchanged, all resource dimensions match their density, and build succeeds.

**Step 5: Verify launcher masks on emulator**

Install the debug APK on `hk_test`. Check the home screen and app drawer using the default rounded-square mask, then change the emulator launcher icon shape to a circular option if available. Confirm the full source frame is visible and centred in both cases.

**Step 6: Commit**

```bash
git add hk-mixed-keyboard-profile.png android/app/src/main/res/mipmap-*/ic_launcher*.png
git commit -m "fix: keep launcher artwork inside adaptive safe area"
```

## Task 1: Add pure first-use hint policy and persisted state

**Files:**

- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/FirstUseHintPolicy.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/settings/KeyboardSettings.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/FirstUseHintPolicyTest.kt`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardPrefsTest.kt`

**Step 1: Write failing policy tests**

Cover these cases:

```kotlin
@Test fun `sensitive field never shows a hint`()
@Test fun `active composition never gets replaced by a hint`()
@Test fun `mode hint is shown before space hint`()
@Test fun `dismissed mode hint advances to space hint on a later keyboard show`()
@Test fun `both completed hints produce no hint`()
```

Use a pure model:

```kotlin
enum class FirstUseHint { MODE_CYCLE, SPACE_GESTURES }

data class FirstUseHintState(
    val modeHintSeen: Boolean,
    val spaceHintSeen: Boolean
)

object FirstUseHintPolicy {
    fun next(
        state: FirstUseHintState,
        sensitive: Boolean,
        hasComposition: Boolean
    ): FirstUseHint?
}
```

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests '*FirstUseHintPolicyTest' --tests '*KeyboardPrefsTest'
```

Expected: tests fail because the policy and preferences do not exist.

**Step 2: Add DataStore fields**

Add boolean keys `mode_hint_seen` and `space_hint_seen`; expose them as `modeHintSeen` and `spaceHintSeen` on `KeyboardPrefs`, defaulting to `false`. Add explicit setters and a `resetFirstUseHints()` edit that sets both to `false` in one transaction.

Do not add a version or timestamp. Default `false` intentionally lets existing users discover the new behaviour once after upgrade.

**Step 3: Implement the minimal policy**

Return no hint for sensitive fields or active composition. Otherwise return `MODE_CYCLE`, then `SPACE_GESTURES`, then `null` in that order.

**Step 4: Run focused and full tests**

```bash
./gradlew testDebugUnitTest --tests '*FirstUseHintPolicyTest' --tests '*KeyboardPrefsTest'
./gradlew test
```

Expected: all tests pass.

**Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/hkmixedkeyboard/ime/FirstUseHintPolicy.kt \
        android/app/src/main/kotlin/com/hkmixedkeyboard/settings/KeyboardSettings.kt \
        android/app/src/test/kotlin/com/hkmixedkeyboard/FirstUseHintPolicyTest.kt \
        android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardPrefsTest.kt
git commit -m "feat: persist first-use keyboard hints"
```

## Task 2: Add guided setup and hint reset in Settings

**Files:**

- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/settings/SettingsActivity.kt`
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/settings/OnboardingCopy.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/OnboardingCopyTest.kt`
- Modify: `android/app/src/main/res/values/strings.xml`

**Step 1: Write failing copy/ordering tests**

Keep the copy in a pure model so the cheaper implementation model does not bury product rules in `Activity` code:

```kotlin
data class SetupStep(val number: Int, val title: String, val detail: String)

object OnboardingCopy {
    val steps: List<SetupStep>
}
```

Assert exactly three ordered steps: enable keyboard, select keyboard, try the three modes/Space gestures. Assert nonblank Traditional-Chinese title and detail for each.

**Step 2: Add the lightweight Settings UI**

Directly below the Settings header:

- show three compact numbered text rows;
- retain the existing `Settings.ACTION_INPUT_METHOD_SETTINGS` button;
- add a button labelled `重新顯示鍵盤提示` that calls `resetFirstUseHints()` and shows a confirmation Toast;
- do not add a new Activity, pager, animation, or dependency.

**Step 3: Verify Settings manually**

Build and launch:

```bash
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug-*.apk
adb shell am start -n com.hkmixedkeyboard/.settings.SettingsActivity
```

Expected: all three steps fit in the existing scroll layout, enable button still opens Android IME settings, and reset shows confirmation.

**Step 4: Run tests and commit**

```bash
./gradlew test
git add android/app/src/main/kotlin/com/hkmixedkeyboard/settings \
        android/app/src/main/res/values/strings.xml \
        android/app/src/test/kotlin/com/hkmixedkeyboard/OnboardingCopyTest.kt
git commit -m "feat: add lightweight keyboard setup guide"
```

## Task 3: Render dismissible one-time hints in the candidate bar

**Files:**

- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/HintRenderSnapshot.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/HintRenderSnapshotTest.kt`
- Modify: `android/app/src/main/res/values/strings.xml`

**Step 1: Write failing rendering-state tests**

Test a pure snapshot with `message`, `dismissLabel`, and hint identity. Assert identical hints are render no-ops and different hint identities force a render.

**Step 2: Add `CandidateBarView.showHint`**

Add:

```kotlin
fun showHint(message: String, onDismiss: () -> Unit)
```

Render one horizontally fitting message plus `知道了`. Tapping either the dismiss label or the hint records dismissal. Do not invoke `CandidateListener`, selection learning, candidate commit, or expanded-grid behaviour.

Use copy:

- mode: `按「速／粵／拼」切換輸入法`
- Space: `空白鍵：左右滑移游標，長按切換繁／簡`

**Step 3: Wire service lifecycle safely**

On `onWindowShown`, evaluate `FirstUseHintPolicy` only after current preferences are available. Show at most one hint when:

- the field is not sensitive;
- composition buffer is empty;
- candidate/prediction list is empty;
- no loading or safe-mode message is active.

Persist the matching `*_hint_seen` flag only when dismissed, not merely when rendered. Candidate updates always take priority and may replace the hint.

**Step 4: Test sensitive and composition guards**

Add service-independent coordinator tests if lifecycle wiring grows beyond a few branches. At minimum prove password fields and nonempty composition never request a hint.

**Step 5: Emulator acceptance**

Clear app data, enable the IME, and verify:

1. first normal field shows mode hint;
2. typing immediately replaces the hint and does not lose a key;
3. dismissing persists across IME restart;
4. next clean keyboard session shows Space hint;
5. password fields show safe mode, never hints;
6. Settings reset makes the sequence available again.

**Step 6: Run and commit**

```bash
cd android && ./gradlew test assembleDebug
git add android/app/src/main/kotlin/com/hkmixedkeyboard/ime \
        android/app/src/main/kotlin/com/hkmixedkeyboard/ui \
        android/app/src/main/res/values/strings.xml \
        android/app/src/test/kotlin/com/hkmixedkeyboard
git commit -m "feat: show dismissible first-use keyboard hints"
```

## Task 4: Capture current Pinyin and offline-privacy screenshots

**Files:**

- Create: `docs/store/assets/screenshots/6_pinyin.png`
- Create: `docs/store/assets/screenshots/7_offline_privacy.png`
- Modify: `docs/store/play_submission_checklist.md`
- Modify: `docs/store/play_submission_audit_2026-07-12.md` only by adding a clearly dated follow-up section; do not rewrite the historical v0.55 audit.

**Step 1: Build a signed release APK without bumping**

```bash
set -a
. ~/.local/share/hk-mixed-keyboard/upload-key.env
set +a
cd android
./gradlew assembleRelease
```

Expected: release APK retains the currently selected version; `version.properties` is unchanged.

**Step 2: Prepare deterministic emulator state**

- use AVD `hk_test` at 1080×2400;
- disable notification overlays and clear unrelated recent apps;
- install the signed release APK;
- select HK Mixed Keyboard;
- use an empty test document with no personal content;
- keep output Traditional.

**Step 3: Capture Pinyin**

Switch to `拼`, type `xianggang`, and wait for the Traditional candidate `香港`. Capture while the mode key, input buffer, and candidates are all visible. Save the raw 1080×2400 PNG as `6_pinyin.png`; do not crop, composite, or mock the keyboard.

**Step 4: Capture offline privacy**

Open the in-app Privacy Policy screen at the section stating there is no network permission/data upload. Capture genuine app UI at 1080×2400 as `7_offline_privacy.png`.

**Step 5: Validate assets**

```bash
identify docs/store/assets/screenshots/6_pinyin.png \
         docs/store/assets/screenshots/7_offline_privacy.png
```

Expected: both exactly 1080×2400; visually inspect for status-bar personal data, transient Toasts, loading labels, stale mode labels, and Simplified characters.

**Step 6: Update checklist and commit**

Document the device/API level, APK version, sample input, and filenames.

```bash
git add docs/store/assets/screenshots docs/store/play_submission_checklist.md \
        docs/store/play_submission_audit_2026-07-12.md
git commit -m "docs: add Pinyin and offline privacy screenshots"
```

## Task 5: Define the licensed HK mixed-language benchmark dataset

**Files:**

- Create: `android/app/src/test/resources/ranking/hk_mixed_cases.tsv`
- Create: `android/app/src/test/resources/ranking/README.md`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/RankingBenchmarkCase.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/RankingBenchmarkDatasetTest.kt`

**Step 1: Define the schema in a failing parser test**

Use tab-separated UTF-8 fields:

```text
id  scheme  buffer  previous_token  expected_candidates  max_rank  note
```

`expected_candidates` is a `|`-separated set; passing means any expected candidate appears at or above `max_rank`. Reject duplicate IDs, unknown schemes, blank buffers, invalid ranks, malformed columns, and duplicate expected candidates.

**Step 2: Add at least 60 reviewed cases**

Minimum coverage:

- 12 Quick exact/prefix cases;
- 12 Jyutping single/continuous phrase cases;
- 12 Pinyin single/continuous/polyphonic cases;
- 8 English completion cases;
- 8 English-to-Traditional meaning-assist cases;
- 8 realistic mixed-language collisions.

At least 20 cases must provide `previous_token` so the same dataset can later evaluate context. Baseline ranking intentionally ignores it.

**Step 3: Document provenance**

The README must state which entries are project-authored, which are derived from already licensed in-repo corpora, and why no private conversation data is present. Add any new source to `corpus/sources/corpus_license_register.csv` before using it.

**Step 4: Run parser tests and commit**

```bash
cd android
./gradlew testDebugUnitTest --tests '*RankingBenchmarkDatasetTest'
git add app/src/test/resources/ranking app/src/test/kotlin/com/hkmixedkeyboard/RankingBenchmark*
git commit -m "test: add HK mixed-language ranking dataset"
```

## Task 6: Build the context-free benchmark runner and baseline report

**Files:**

- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/RankingMetrics.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/RankingBenchmarkTest.kt`
- Create: `docs/benchmarks/ranking-baseline.md`

**Step 1: Write failing metric tests**

Implement and test:

```kotlin
data class RankingMetrics(
    val caseCount: Int,
    val top1: Double,
    val top3: Double,
    val top5: Double,
    val meanReciprocalRank: Double,
    val misses: List<String>
)
```

Use hand-calculated candidate ranks to verify all metrics and missing-candidate handling.

**Step 2: Run production decoding**

Use `buildFullCorpusDecoder("src/main/assets/corpus")`, the production `Classifier`, and `CandidateDisplayPolicy`. Do not duplicate ranking rules in the harness. Use empty learned memory for the reproducible baseline and record memory-enabled results separately if desired.

**Step 3: Add conservative regression thresholds**

After the first measured run, set thresholds slightly below the observed baseline so accidental ranking regressions fail CI. Do not invent target numbers before measuring. Print misses sorted by scheme and case ID.

**Step 4: Write the baseline report**

Record commit, dataset checksum, case count, top-1/top-3/top-5/MRR by scheme, worst misses, execution time, and the fact that `previous_token` was ignored.

**Step 5: Verify determinism**

Run the benchmark three times and require identical metrics and miss order.

```bash
./gradlew testDebugUnitTest --tests '*RankingMetrics*' --tests '*RankingBenchmarkTest'
```

**Step 6: Commit**

```bash
git add android/app/src/test/kotlin/com/hkmixedkeyboard/Ranking* docs/benchmarks/ranking-baseline.md
git commit -m "test: establish context-free ranking baseline"
```

## Task 7: Prototype bigram scoring behind an experiment-only interface

**Files:**

- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/ContextCandidateRanker.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/BigramExperimentRanker.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/BigramExperimentTest.kt`
- Create: `android/app/src/test/resources/ranking/experimental_bigrams.tsv`
- Create: `docs/benchmarks/bigram-decision.md`

Keep all prototype code under `src/test`; do not touch `HkImeService`, production assets, or APK dependencies until the decision gate passes.

**Step 1: Write failing pure ranker tests**

Interface:

```kotlin
fun interface ContextCandidateRanker {
    fun rerank(previousToken: String?, candidates: List<DecodeCandidate>): List<DecodeCandidate>
}
```

Prove:

- null/blank context preserves exact order;
- unknown bigram preserves exact order;
- known bigram promotes only matching candidates;
- stable ordering is retained for equal scores;
- duplicate candidates are not introduced;
- English candidates are not globally demoted merely because context is Chinese.

**Step 2: Add a small auditable experimental table**

Schema: `previous_token<TAB>candidate<TAB>score<TAB>source_note`. Use only project-authored examples or already licensed corpus derivations. Keep it small enough for line-by-line review.

**Step 3: Compare with the same benchmark**

Run baseline and experimental ranker over cases with `previous_token`. Report overall and per-scheme deltas for top-1/top-3/top-5/MRR, plus regressions by ID.

**Step 4: Apply the decision gate**

Recommend production work only when all are true:

- contextual subset top-1 or MRR improves by a predeclared meaningful margin;
- overall top-3 does not regress materially;
- mixed English cases do not regress;
- projected compressed table stays within the agreed APK budget;
- lookup design is bounded and does no disk I/O per key;
- sensitive fields pass `previousToken = null` and never query context.

If the gate fails, document `NO-GO`, commit the evidence, and stop. Do not leave unused production abstractions.

**Step 5: Commit the experiment**

```bash
git add android/app/src/test/kotlin/com/hkmixedkeyboard/*Bigram* \
        android/app/src/test/kotlin/com/hkmixedkeyboard/ContextCandidateRanker.kt \
        android/app/src/test/resources/ranking/experimental_bigrams.tsv \
        docs/benchmarks/bigram-decision.md
git commit -m "test: evaluate previous-token ranking"
```

If the result is GO, write a separate production design and implementation plan before changing app code.

## Task 8: Add a pure optional Jyutping annotation model

**Files:**

- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateAnnotationPolicy.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/CandidateAnnotationPolicyTest.kt`
- Modify only if needed: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/JyutpingSegmenter.kt`

The current corpus contains toneless Jyutping. The prototype must label annotations as toneless and must not invent tone numbers.

**Step 1: Write failing policy tests**

Model:

```kotlin
data class CandidatePresentation(
    val text: String,
    val annotation: String?,
    val accessibilityLabel: String
)
```

Cover exact Jyutping, fully segmentable continuous Jyutping, multi-character phrases, ambiguous segmentation, missing code, Quick/Pinyin/English candidates, and disabled state.

**Step 2: Implement minimal deterministic output**

- disabled: no annotation for any candidate;
- non-Jyutping source: no annotation;
- exact atomic Jyutping: show the toneless code;
- fully segmentable phrase: show syllables separated by spaces;
- ambiguous/unsegmentable input: show no annotation rather than a misleading one;
- accessibility label: Chinese text followed by `無聲調粵拼` and the spaced reading.

**Step 3: Run tests and commit**

```bash
cd android
./gradlew testDebugUnitTest --tests '*CandidateAnnotationPolicyTest'
git add app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateAnnotationPolicy.kt \
        app/src/test/kotlin/com/hkmixedkeyboard/CandidateAnnotationPolicyTest.kt
git commit -m "feat: add optional Jyutping annotation policy"
```

## Task 9: Add the annotation preference and expanded-grid prototype

**Files:**

- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/settings/KeyboardSettings.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/settings/SettingsActivity.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateGridView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardPrefsTest.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/CandidateAnnotationLayoutTest.kt`

**Step 1: Add a default-off preference**

Add `jyutping_annotations` defaulting to `false`, setter, and a Settings switch labelled `候選顯示無聲調粵拼（學習功能）`.

**Step 2: Extend grid rendering only**

Keep the compact `CandidateBarView` unchanged. In `CandidateGridView`, render annotated entries as a vertical cell with Chinese at the existing size and annotation below at a smaller size. Preserve tap target, haptic, colour priority, popup bounds, and scrolling.

Pass the preference and presentation function explicitly into `show`; do not make the grid read DataStore.

**Step 3: Add accessibility and font-scale tests**

Use the policy accessibility label as `contentDescription`. Verify unannotated candidates retain their Chinese label. Manually test Android font scales 1.0 and 1.3; annotations must not overlap or escape cells.

**Step 4: Emulator prototype acceptance**

Verify:

- default install looks identical to current release;
- enabling annotations affects expanded Jyutping candidates only;
- Quick and Pinyin grids remain unchanged;
- continuous `neihou` displays a spaced toneless reading only when segmentation is reliable;
- TalkBack reads Chinese and annotation once;
- disabling the switch restores the original grid.

**Step 5: Full verification and commit**

```bash
cd android
./gradlew test lintDebug assembleDebug assembleRelease
git add app/src/main app/src/test
git commit -m "feat: prototype optional Jyutping candidate annotations"
```

## Final handoff checklist

Run from the repository root:

```bash
git status --short
cd android
./gradlew test lintDebug assembleDebug assembleRelease
cd ..
python3 -m unittest discover -s corpus/tools/tests -v
```

Then verify:

- no `INTERNET` permission in the merged release manifest;
- `version.properties` did not change;
- all new settings are default-safe and local;
- password fields show neither onboarding hints nor contextual ranking;
- screenshots are genuine 1080×2400 captures;
- benchmark results are deterministic and provenance is documented;
- bigram production work exists only if the decision document says GO;
- annotations remain opt-in and out of the compact bar;
- each task has its own commit and no generated secrets/build outputs are staged.
