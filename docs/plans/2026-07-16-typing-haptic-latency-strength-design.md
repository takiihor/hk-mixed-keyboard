# Typing Haptic Latency and Strength Design

## Problem

Device-tuned haptics improved the keyboard's response, but rapid typing can still feel delayed and the calibrated `TICK` effect is too light. The current engine calls `Vibrator.cancel()` before every direct haptic submission, adding a second binder operation to every key press and interrupting the actuator before the next effect is submitted.

## Approved Approach

- Stop cancelling a previous haptic before each key press by default. Continue to cancel explicitly when the keyboard detaches or the engine is released.
- Keep the existing synchronous `ACTION_DOWN` submission path and device-calibrated vibration APIs.
- Use `VibrationEffect.Composition.PRIMITIVE_CLICK` where supported, falling back to `VibrationEffect.EFFECT_CLICK` on Android 10+ and the existing view haptic path otherwise.
- Keep the debug one-shot and heavy waveform modes disabled. They can be stronger, but may reintroduce the delayed feel this change is intended to remove.

## Alternatives Considered

1. Increase the scale of `PRIMITIVE_TICK`. The current test build already uses the maximum configured scale, and device implementations may still render a tick too lightly.
2. Use a fixed-amplitude one-shot or waveform. This is less device-aware and previously felt delayed, so it is not appropriate for normal typing.

## Error Handling and Lifecycle

Capability-query and direct-vibration failures continue to use the view fallback without escaping into the input path. Explicit lifecycle cancellation remains guarded so vibrator service failures cannot crash keyboard teardown.

## Verification

Use red-green unit-test cycles to prove that repeated default submissions do not call `cancel()` and that both direct capability paths select click effects. Then run the complete unit-test and debug lint suites before bumping the test APK to `0.64.4`.

## Non-goals

- Do not change key processing, candidate generation, row height, sound, or release-gate behavior.
- Do not merge into `master` without explicit approval.
