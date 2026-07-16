# Market Readiness Remediation Design

**Date:** 2026-07-16  
**Branch:** \`feat/three-mode-market-readiness\`  
**Status:** Approved for implementation

## Purpose

Close every repository-remediable finding from the market-readiness review without relabelling automated evidence as human validation. The target is a clean, testable release candidate ready to enter external qualification; a public-market GO remains impossible until owners attach real evidence to the same commit and AAB.

## Scope

### Repository-remediable issues

1. Backspace must delete one Unicode code point, not one UTF-16 code unit, for ordinary and emoji-panel deletion. Reverting an auto-commit must continue to delete the exact committed UTF-16 text span.
2. Settings controls must meet the app's accessibility contract: readable contrast, a labelled switch, and a full-row activation target.
3. The IME must recognize numeric, phone, email and URL editors and expose a purpose-appropriate keyboard surface while retaining password/privacy protections.
4. Keyboard settings and switching must be discoverable from the IME itself.
5. Quick, Jyutping and Pinyin quality gates must reject empty or self-attested competitor evidence and make their locked-review requirements executable.
6. Release qualification must remain fail-closed until its required signed AAB, reviews, physical-device evidence and beta evidence are present.

### External gates

The following cannot be created by source-code changes and must remain OPEN until real evidence is attached: native language ranking review; competitor measurements; physical Samsung/Pixel/AOSP and adaptive-layout qualification; TalkBack/Switch Access specialist review; legal/data owner signatures; signing key ownership and final AAB verification; and the 100-person two-week beta. The software will provide checklists, data formats and failure guards for these gates, but will not claim that they have passed.

## Architecture

### Unicode deletion contract

\`CommitOutput\` will describe deletion intent rather than treating every count as the same unit. Ordinary backspace emits one code point; auto-commit restoration emits the exact UTF-16 unit count of the committed string. A single IME-side applier handles both paths and is also used by the emoji panel. It first uses Android's code-point API and falls back to a bounded preceding-text inspection only if an editor declines that request.

This prevents divergent deletion logic and preserves the existing restore contract for composed output.

### Editor and keyboard surface policy

A small, pure \`EditorLayoutPolicy\` maps \`EditorInfo.inputType\` to the existing text keyboard or dedicated number, phone, email and URI surfaces. Sensitive and \`TYPE_NULL\` fields retain the existing direct/safe policy. Unknown variations intentionally fall back to text so a new Android variation cannot remove the user's normal keyboard.

The IME toolbar exposes settings and the Android next-input-method action only when the platform offers a successor. Both actions have accessibility labels; they do not alter candidate selection or composition state.

### Settings semantics

Every setting switch receives a stable generated ID, an accessible name, and a label relationship. Its parent row delegates activation to the switch, making the complete row a usable target without duplicate accessibility focus. Preview descriptions use the Settings screen's secondary text colour, rather than a keyboard preview foreground token.

### Evidence discipline

Benchmark and release tooling will validate record completeness and provenance. It may produce deterministic source-coverage reports, but cannot mark a native review, competitor comparison, legal signature, physical-device run or beta as passed. The release gate reads those records and fails closed when a required record is absent or is bound to a different commit/AAB hash.

## Error handling

- If code-point deletion is rejected by a host editor, inspect at most two UTF-16 units before the cursor and delete two only for a valid surrogate pair; otherwise delete one unit.
- If settings cannot launch, retain the current IME and show a short failure toast; never crash or clear composition.
- If Android does not offer another IME, hide or disable the next-IME action.
- If an editor type is unsupported, choose the standard text layout.
- If required external evidence is missing, release verification fails with an actionable list instead of converting the status to GO.

## Verification strategy

Every code change follows red-green-refactor. Focused JVM tests prove policy and controller semantics, while instrumentation tests exercise real Settings views, IME-facing input connections, accessibility nodes and editor selection. The full Android debug/release unit suite, lint, corpus Python tests, manifest and release guards run after each coherent batch. API 26, 35 and 36 emulator runs remain required. Physical-device, reviewer, signature and beta gates are separately recorded as external evidence and are never inferred from emulator success.

## Release decision

After local remediation passes, the candidate may progress to controlled qualification. \`docs/release/go_no_go.md\` remains NO-GO until every external gate is evidenced against one clean commit and signed AAB hash.

