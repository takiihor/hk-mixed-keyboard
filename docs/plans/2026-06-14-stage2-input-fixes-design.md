# Stage 2 Input Fixes Design

## Scope

Fix four user-visible issues in `0.5.0-stage2`:

- backspace repeats while held;
- English and Chinese choices are learned automatically and influence suggestions;
- key vibration follows the existing setting and is enabled by default;
- the combined `？！` key types `？` on tap and `！` on hold.

## Suggestions And Learning

The existing Room-backed memory remains local-only and disabled in sensitive
fields. Memory changes from one row per input buffer to one row per
buffer/candidate pair so competing English and Chinese choices retain separate
usage counts.

Space and punctuation commits record the chosen word automatically. Candidate
taps continue to learn. `ALWAYS_SPACE` also learns the literal buffer before
inserting the space. Password and other sensitive fields neither read nor write
personalized candidates.

While composing, the candidate bar merges:

1. learned candidates whose original buffer starts with the current input;
2. decoded Chinese candidates;
3. built-in English completions such as `happy` and `happen`;
4. the literal input.

Learned candidates rank by use count, then built-in frequency. Duplicate display
text is removed. This makes repeated English words and Chinese selections rise
without replacing the bundled dictionaries.

## Key Interaction

`KeyboardView` owns hold timing because it receives raw touch down/up events.
Backspace fires once on touch down, waits for an initial delay, then repeats at a
fixed interval until release or cancellation.

The combined punctuation key schedules a long-press action. Releasing before the
threshold emits `？`; reaching the threshold emits `！` and suppresses the tap.

Each emitted key action requests Android keyboard-tap haptic feedback when the
vibration preference is enabled. Preference changes are observed by the IME and
applied to the live view.

## Testing

JVM tests cover automatic learning, multiple learned candidates, English and
Chinese prefix lookup/ranking, built-in English completions, and the hold-action
state machine. Existing commit, decoder, safe-mode, and layout tests remain the
regression suite. A debug APK build verifies Android integration.
