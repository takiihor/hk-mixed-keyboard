# Shift, Caps Lock, And Low-Latency Haptics Design

## Scope

This iteration addresses three product decisions:

- implement complete Shift and Caps Lock behavior;
- keep Quick and English as the supported mixed-input experience, without
  presenting incomplete Cangjie support as a separate input mode;
- replace the delayed and overlapping typing haptics observed on an OPPO Find
  X6 Pro running Android 16.

## Shift State

Shift has three states:

- `OFF`: letters are entered in lowercase;
- `ONCE`: the next letter is entered in uppercase, then Shift returns to `OFF`;
- `LOCKED`: letters remain uppercase until Shift is pressed again.

A single Shift press changes `OFF` to `ONCE`. A second Shift press within the
double-tap window changes `ONCE` to `LOCKED`. Pressing Shift while `LOCKED`
returns it to `OFF`.

The state transition is implemented in a pure Kotlin controller so timing and
state behavior can be covered by JVM tests. `KeyboardView` receives the current
state and redraws letter labels and the Shift key accordingly.

Only letter keys consume one-shot Shift. Number, punctuation, backspace, emoji,
symbol, Space, and Enter do not consume it.

## Mixed Input And Letter Case

The visible composing buffer preserves the user's requested English case.
Decoder lookup and Chinese candidate generation use a lowercase lookup form so
Shift never breaks Quick input.

English literals and completions follow the typed case pattern:

- lowercase input produces lowercase candidates;
- an initial uppercase letter produces title-case candidates;
- Caps Lock input produces uppercase candidates.

Chinese candidates are unchanged. Candidate selection and literal commits
preserve the displayed English case rather than forcing lowercase.

## Cangjie Scope

The supported keyboard remains Quick plus English.

- Remove the separate Cangjie subtype from IME metadata because the current app
  does not provide a complete, independently selected Cangjie implementation.
- Keep Cangjie-derived corpus data used internally by Quick decoding.
- Keep the optional Cangjie root labels, but make them disabled by default.
- Do not delete decoder or corpus structures that Quick depends on.

This avoids misleading users without blocking a future complete Cangjie mode.

## Haptic Architecture

The existing `View.performHapticFeedback(KEYBOARD_TAP)` request is made on
`ACTION_DOWN`, but ColorOS can schedule or shape the actual pulse later. It also
does not give the app control over overlapping pulses. Unit tests currently
verify only the selected constant, not the runtime path.

Introduce a keyboard-owned haptic engine:

1. On every physical key `ACTION_DOWN`, request one typing pulse.
2. Cancel the previous keyboard-owned vibration immediately before starting the
   new pulse, preventing queued or overlapping effects during rapid typing.
3. Prefer `VibrationEffect.Composition.PRIMITIVE_TICK` when the device reports
   support.
4. Otherwise use predefined `VibrationEffect.EFFECT_TICK`.
5. If direct vibration is unavailable or rejected, fall back to
   `View.performHapticFeedback(KEYBOARD_PRESS)`.
6. Classify direct vibration as `VibrationAttributes.USAGE_TOUCH`.

The direct path requires the normal `android.permission.VIBRATE` manifest
permission. It does not bypass system interruption or touch-feedback policy.
Legacy custom one-shot and waveform effects are excluded because they can ring,
feel buzzy, and worsen overlap.

Candidate and panel selection feedback can remain View-based. The reported
problem concerns high-frequency physical typing, where cancellation and pulse
ownership matter.

## Diagnostics

Add debug-only timing around:

- receipt of `ACTION_DOWN`;
- completion of the vibration API call;
- key dispatch to the IME service;
- composing-text update.

These timestamps measure application scheduling, not the actuator's physical
start time. Physical confirmation still requires testing on the OPPO Find X6
Pro, but the logs will expose main-thread stalls and confirm that haptic
submission precedes key processing.

## Error Handling

- No vibrator or unsupported primitive: use the predefined effect or View
  fallback.
- Security or runtime failure from the direct vibrator path: fall back to View
  haptics without interrupting typing.
- Vibration disabled in keyboard settings: do not call either path.
- Input view hidden or service destroyed: cancel keyboard-owned vibration.

## Testing

JVM tests cover:

- all Shift state transitions;
- one-shot Shift consumption only by letters;
- Caps Lock persistence and unlock;
- lowercase, title-case, and uppercase candidate transformations;
- lowercase decoder lookup while preserving visible case;
- haptic strategy selection for primitive, predefined, and View fallback;
- cancellation before every new direct typing pulse.

Static and build verification cover:

- `VIBRATE` permission;
- Quick-only IME subtype metadata;
- Cangjie roots disabled by default;
- full unit-test suite;
- debug and release APK builds.

Final acceptance requires rapid typing on the OPPO Find X6 Pro running Android
16, specifically checking that pulses no longer trail visible key presses or
merge during repeated input.
