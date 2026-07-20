# Market Readiness Remediation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Produce a release candidate that fixes all repository-remediable market-readiness defects and fails closed on remaining external qualification gates.

**Architecture:** Make text deletion intent explicit, route all host-editor deletion through one Unicode-safe applier, and use small pure policies for editor layouts and release evidence. Keep the existing three-mode decoder and candidate pipeline intact; extend only boundary layers, Settings semantics and release-gate tooling.

**Tech Stack:** Kotlin, Android \`InputMethodService\`/\`InputConnection\`, AndroidX AppCompat, JVM and instrumentation tests, Bash, Python \`unittest\`/\`pytest\`.

---

### Task 1: Make host-editor deletion Unicode safe

**Files:**

- Modify: \`android/app/src/main/kotlin/com/hkmixedkeyboard/commit/ImeTypes.kt\`
- Modify: \`android/app/src/main/kotlin/com/hkmixedkeyboard/commit/CommitController.kt\`
- Create: \`android/app/src/main/kotlin/com/hkmixedkeyboard/ime/SurroundingTextDeletionPolicy.kt\`
- Modify: \`android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt\`
- Create: \`android/app/src/test/kotlin/com/hkmixedkeyboard/SurroundingTextDeletionPolicyTest.kt\`
- Modify: \`android/app/src/test/kotlin/com/hkmixedkeyboard/ThreeModeCommitContractTest.kt\`

**Step 1: Write failing tests**

Define a deletion unit contract and assert that ordinary backspace emits one code point while auto-commit restoration keeps the exact UTF-16 length. Add pure fallback tests for BMP input (\`A\` → 1), emoji (\`😀\` → 2), supplementary HKSCS input (for example \`𠬠\` → 2), malformed/null context (→ 1), and a cursor after a BMP character following an emoji (→ 1).

**Step 2: Run test to verify it fails**

Run: \`cd android && ./gradlew testDebugUnitTest --tests '*SurroundingTextDeletionPolicyTest' --tests '*ThreeModeCommitContractTest' --rerun-tasks\`

Expected: compilation or assertions fail because deletion still has an ambiguous \`deletedBefore\` count and no policy exists.

**Step 3: Implement the minimal code**

Add a deletion request whose unit is either \`CODE_POINTS\` or \`UTF16_UNITS\`. \`CommitController.onBackspace\` produces \`CODE_POINTS(1)\` for ordinary deletion and \`UTF16_UNITS(lac.text.length)\` for auto-commit restoration. In \`HkImeService\`, apply requests through one method:

~~~kotlin
if (request.unit == CODE_POINTS &&
    connection.deleteSurroundingTextInCodePoints(request.count, 0)) return
connection.deleteSurroundingText(
    policy.utf16UnitsForFallback(request, connection.getTextBeforeCursor(2, 0)), 0
)
~~~

Use that method from normal Backspace and the emoji panel. Do not change decoder, candidate ranking or auto-commit restore semantics.

**Step 4: Run focused tests**

Run the Step 2 command again. Expected: all focused tests pass.

**Step 5: Commit**

~~~bash
git add android/app/src/main/kotlin/com/hkmixedkeyboard/commit \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ime \
  android/app/src/test/kotlin/com/hkmixedkeyboard
git commit -m "fix: delete Unicode code points safely"
~~~

### Task 2: Correct Settings contrast and semantics

**Files:**

- Modify: \`android/app/src/main/kotlin/com/hkmixedkeyboard/settings/SettingsActivity.kt\`
- Modify: \`android/app/src/androidTest/kotlin/com/hkmixedkeyboard/SettingsReleaseRegressionTest.kt\`
- Create or modify: \`android/app/src/test/kotlin/com/hkmixedkeyboard/SettingsAccessibilityPolicyTest.kt\`

**Step 1: Write failing tests**

Assert that theme-preview description colour comes from the Settings surface, not \`KeyboardThemeColors.label\`; every switch has an ID and accessible label; and tapping a setting row changes exactly its associated switch.

**Step 2: Run test to verify it fails**

Run: \`cd android && ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hkmixedkeyboard.SettingsReleaseRegressionTest\`

Expected: the current independent label/switch views fail the row-action and semantic assertions.

**Step 3: Implement the minimal code**

Create the label and switch as one labelled control relationship, assign the switch a generated ID/content description, and route row activation through \`performClick()\` on the switch. Use an appropriate Settings foreground/secondary text colour for the preview description. Avoid changing persisted preference keys or settings ordering.

**Step 4: Run focused tests**

Run the Step 2 command and the corresponding JVM policy test. Expected: Settings rows are operable and theme descriptions remain readable.

**Step 5: Commit**

~~~bash
git add android/app/src/main/kotlin/com/hkmixedkeyboard/settings \
  android/app/src/androidTest/kotlin/com/hkmixedkeyboard \
  android/app/src/test/kotlin/com/hkmixedkeyboard
git commit -m "fix: make settings controls accessible"
~~~

### Task 3: Add editor-aware layouts and IME actions

**Files:**

- Create: \`android/app/src/main/kotlin/com/hkmixedkeyboard/ime/EditorLayoutPolicy.kt\`
- Modify: \`android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt\`
- Modify: \`android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt\`
- Modify: \`android/app/src/main/kotlin/com/hkmixedkeyboard/ui/SymbolKeyboardSpec.kt\`
- Create: \`android/app/src/test/kotlin/com/hkmixedkeyboard/EditorLayoutPolicyTest.kt\`
- Modify: \`android/app/src/test/kotlin/com/hkmixedkeyboard/DirectInputPolicyTest.kt\`
- Modify: \`android/app/src/androidTest/kotlin/com/hkmixedkeyboard/KeyboardAccessibilityNodeProviderTest.kt\`

**Step 1: Write failing tests**

Specify mappings for number, signed/decimal number, phone, email and URI input types, as well as text and password fallback. Specify an IME-action policy that offers next-IME only if \`shouldOfferSwitchingToNextInputMethod()\` is true and labels settings/switch actions for accessibility.

**Step 2: Run test to verify it fails**

Run: \`cd android && ./gradlew testDebugUnitTest --tests '*EditorLayoutPolicyTest' --tests '*DirectInputPolicyTest' --rerun-tasks\`

Expected: the layout policy is absent and all editors resolve to the standard surface.

**Step 3: Implement the minimum surface policy**

Add the pure layout policy. Build only the key rows necessary for each editor class, reuse existing key rendering and touch/accessibility infrastructure, and fall back to text for unknown types. Add labelled settings and platform-aware next-IME actions without clearing composition. Preserve password, payment, banking and OTP safe-mode behavior.

**Step 4: Run focused tests**

Run the Step 2 command, then the keyboard accessibility instrumentation class. Expected: input-type mapping, action visibility and virtual key labels pass.

**Step 5: Commit**

~~~bash
git add android/app/src/main/kotlin/com/hkmixedkeyboard/ime \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ui \
  android/app/src/test/kotlin/com/hkmixedkeyboard \
  android/app/src/androidTest/kotlin/com/hkmixedkeyboard
git commit -m "feat: adapt keyboard to editor type"
~~~

### Task 4: Make mode quality evidence enforceable

**Files:**

- Modify: \`corpus/tools/verify_benchmark_templates.py\`
- Modify: \`corpus/tools/score_three_mode_benchmark.py\`
- Modify: \`corpus/tools/tests/test_verify_benchmark_templates.py\`
- Modify: \`corpus/tools/tests/test_score_three_mode_benchmark.py\`
- Modify: \`corpus/benchmarks/README.md\`
- Modify: \`docs/release/competitor_baseline.md\`

**Step 1: Write failing tests**

Add fixtures proving that header-only holdouts, missing reviewer identities, missing benchmark hashes, mismatched commit/AAB hashes and self-labelled native or competitor results are rejected. Preserve deterministic source-coverage mode as explicitly non-independent evidence.

**Step 2: Run test to verify it fails**

Run: \`PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest -q corpus/tools/tests/test_verify_benchmark_templates.py corpus/tools/tests/test_score_three_mode_benchmark.py\`

Expected: verification currently accepts templates that cannot substantiate a market comparison.

**Step 3: Implement strict evidence validation**

Require locked prompt hash, reviewer/lead, device/settings, input state, commit/AAB hashes, and raw-result provenance before a report can be marked native or competitor comparative. Continue to allow empty templates but report them as OPEN; do not generate fabricated benchmark rows.

**Step 4: Run focused tests**

Run the Step 2 command again. Expected: incomplete comparison evidence fails closed and source-only reports remain correctly labelled.

**Step 5: Commit**

~~~bash
git add corpus/tools corpus/benchmarks docs/release/competitor_baseline.md
git commit -m "test: fail closed on incomplete market evidence"
~~~

### Task 5: Enforce all external release gates against one candidate

**Files:**

- Create: \`scripts/verify_release_evidence.py\`
- Create: \`scripts/tests/test_verify_release_evidence.py\`
- Modify: \`scripts/verify_release.sh\`
- Modify: \`docs/release/go_no_go.md\`
- Modify: \`docs/release/device_beta_matrix.md\`
- Modify: \`docs/release/accessibility_report.md\`
- Modify: \`docs/release/legal_signoff.md\`
- Modify: \`docs/release/release_runbook.md\`

**Step 1: Write failing tests**

Construct a complete synthetic evidence directory and one missing each of native review, physical-device matrix, accessibility review, legal signature, signed AAB metadata and beta result. Assert the verifier rejects every missing or hash-mismatched record and accepts only a complete, consistent bundle.

**Step 2: Run test to verify it fails**

Run: \`PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest -q scripts/tests/test_verify_release_evidence.py\`

Expected: the verifier is absent.

**Step 3: Implement the evidence verifier**

Use a documented JSON/TSV schema that records status, reviewer/owner, date, commit hash and AAB hash. \`verify_release.sh\` invokes it before signature/APK checks. The default repository evidence remains OPEN and therefore causes production verification to fail; this is intentional.

**Step 4: Run focused tests**

Run the Step 2 command and \`scripts/verify_release.sh\` without credentials. Expected: tests pass; the release script exits non-zero with the first missing external gate rather than producing an AAB claim.

**Step 5: Commit**

~~~bash
git add scripts docs/release
git commit -m "build: require complete release evidence"
~~~

### Task 6: Run full qualification and capture limits

**Files:**

- Modify: \`docs/release/three_mode_release_evidence.md\`
- Modify: \`docs/release/checkpoint_3_verification.md\`

**Step 1: Run all automated checks**

~~~bash
cd android && ./gradlew test lintDebug
cd .. && PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest -q corpus/tools/tests scripts/tests
python3 corpus/tools/verify_corpus_manifest.py
python3 corpus/tools/verify_benchmark_templates.py
~~~

**Step 2: Run API emulator qualification**

Run the existing instrumentation suite and \`scripts/device_smoke.sh\` on API 26, 35 and 36 against the built candidate APK. Include the new emoji/supplementary HKSCS backspace and editor-layout scenarios in the evidence.

**Step 3: Record only observed results**

Update evidence documents with commands, commit, APK/AAB hashes, outcomes and limitations. Do not change NO-GO to GO unless the independently supplied external records also pass Task 5's verifier.

**Step 4: Commit**

~~~bash
git add docs/release
git commit -m "docs: record market-readiness remediation evidence"
~~~

### Task 7: Integrate only after the completion audit

**Files:**

- Review: \`docs/release/go_no_go.md\`
- Review: all Task 1–6 files and test outputs

**Step 1: Audit each blocker**

For every original finding, classify it as fixed and verified, externally OPEN with an executable gate, or failed. Verify the candidate worktree is clean and all evidence hashes match the tested artifact.

**Step 2: Decide integration**

Merge the candidate into \`master\` only if the user authorizes it and no unrelated local master changes are overwritten. Public release stays NO-GO while any external gate is OPEN.

**Step 3: Commit only audit documentation**

Do not make a public-release or market-superiority claim without complete, independent evidence.

