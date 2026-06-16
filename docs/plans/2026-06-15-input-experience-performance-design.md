# Input Experience And Performance Design

## Goals

Improve the keyboard's perceived responsiveness and candidate usability:

- make haptics clearer and consistent on keys and candidate selections;
- rank two-letter Quick input as Chinese-first;
- rank input longer than two letters as English-first while retaining Chinese;
- remove avoidable work from the key-to-candidate path;
- reduce stale candidate updates and candidate-bar churn.

## Candidate Language Policy

Candidate ordering depends on composing-buffer length:

- **one or two letters:** personalized Chinese, decoded Chinese, personalized
  English, literal input, then English completions;
- **three or more letters:** personalized English, English completions, literal
  input, personalized Chinese, then decoded Chinese.

The policy changes display order only. It does not remove Chinese suggestions,
change sensitive-field behavior, or make Space automatically accept an English
completion. Stable text de-duplication keeps the first occurrence.

This is deliberately a strong heuristic rather than a claim that all inputs over
two letters are English. Jyutping and longer Quick phrase candidates remain
available after the English group.

## Haptics

Use one centralized haptic helper for:

- physical keyboard keys;
- compact candidate-bar choices;
- expanded candidate-grid choices;
- emoji and symbol choices.

Normal typing uses Android's keyboard-tap haptic because it is more distinct than
the current clock tick on typical devices. Candidate selection uses virtual-key
feedback. Each physical action requests haptic feedback once; backspace repeat
does not vibrate for every deletion.

The existing vibration preference controls all keyboard-owned haptics. The design
uses system haptic primitives rather than custom long vibration effects, avoiding
overlap and device-specific buzzing.

## Key Path Performance

The current key path classifies synchronously in `CommitController.onKeyPress`,
then classifies the same buffer again on the decode thread. Key presses and
backspace while composing will become state-only operations; classification is
performed once by the candidate pipeline.

Space, punctuation, and candidate commit still classify synchronously when their
decision requires current candidates.

Only one candidate decode request may remain queued. Before scheduling a new
buffer, remove the previous callback. A generation token also rejects stale work
that has already begun. This prevents fast typing from waiting behind obsolete
prefixes.

## Candidate Rendering

`CandidateBarView` currently removes and recreates every child on every update.
It will retain a snapshot of candidate identity and skip identical renders. For
changed data, it will reuse existing `TextView` slots where practical and update
their content/listeners rather than always allocating a full new row.

The bar resets horizontal scroll only when candidate content changes. Candidate
click haptic occurs immediately before dispatch.

## Additional Findings

The review found these lower-priority issues:

- the debug build shows and updates a debug panel on every key, so release-like
  performance should be judged with a release APK;
- personalized prefix lookup scans all memory entries, acceptable now but should
  gain a prefix index if dictionaries grow substantially;
- Quick prefix lookup still scans all Quick keys for non-exact prefixes;
- `EnglishSuggestions` duplicates a small hard-coded list now superseded by the
  corpus index;
- custom-word entries are managed in settings but are not integrated into live
  candidate generation;
- sound settings exist but typing sound is not implemented;
- symbol and emoji panels use separate click behavior and no shared preference
  handling.

This iteration addresses the user-visible latency, ordering, and haptic paths.
The remaining items should be handled separately to avoid an unsafe broad
refactor.

## Testing

JVM tests cover:

- two-letter Chinese-first ordering;
- three-plus-letter English-first ordering with Chinese retained;
- learned candidates split by language;
- key presses and composing backspace do not classify synchronously;
- stale candidate request generation;
- candidate render identity comparisons;
- haptic action selection.

Run all unit tests, debug build, and release build. The release APK is the primary
artifact for physical-device responsiveness testing.
