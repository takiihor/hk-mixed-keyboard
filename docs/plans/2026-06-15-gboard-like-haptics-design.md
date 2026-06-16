# Gboard-Like Haptics Design

## Scope

Replace the current long, maximum-amplitude keyboard vibration with a short,
device-tuned tick that remains distinct during rapid typing.

## Haptic Behavior

Each valid physical key press requests one haptic on `ACTION_DOWN`, before key
dispatch. The existing vibration preference continues to disable all
keyboard-owned haptics.

The direct haptic backend selects the lightest supported system effect:

1. `VibrationEffect.Composition.PRIMITIVE_TICK` when the device supports it.
2. `VibrationEffect.EFFECT_TICK` on devices with predefined-effect support.
3. `View.performHapticFeedback(KEYBOARD_PRESS)` when direct tick effects are
   unavailable or fail.

The custom `40 ms` one-shot at maximum amplitude is removed. System effects are
short and tuned for the device actuator, avoiding the buzz and overlap caused by
the fixed-duration pulse. The engine does not issue a synchronous
`Vibrator.cancel()` before each tick because that binder call can add latency;
cancel remains limited to keyboard teardown.

## Architecture

`HapticCapabilityPolicy` remains a pure Kotlin strategy selector. It receives
the vibrator and tick-effect capabilities and chooses primitive, predefined, or
View fallback behavior.

`TypingHapticEngine` executes the selected strategy and preserves the current
failure fallback and submission logging. `AndroidTypingHapticBackend` owns API
level checks and vibrator calls.

No touch, key dispatch, candidate, repeat, or long-press behavior changes.

## Error Handling

- Disabled vibration performs no work.
- Capability-query failure uses View feedback.
- Direct vibration failure uses View feedback without interrupting typing.
- Keyboard teardown cancels any keyboard-owned effect.

## Testing

JVM tests cover:

- primitive tick selection when supported;
- predefined tick selection when primitive support is absent;
- View fallback without a direct tick path;
- no custom one-shot strategy;
- repeated presses submit short ticks without per-key cancellation;
- direct vibration failure falls back without escaping.

Verification includes focused haptic tests, the complete unit-test suite, and a
debug APK build. Final actuator feel still requires device testing.
