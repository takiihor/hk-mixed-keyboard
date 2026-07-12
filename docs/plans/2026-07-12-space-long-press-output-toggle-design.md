# Space Long-Press Output Toggle Design

## Goal

Restore an in-keyboard Traditional/Simplified output toggle without bringing back the easy-to-hit candidate-bar chip or mode-key shortcut.

## Interaction

- A stationary space-key hold of about 600 ms toggles the persisted Simplified-output preference.
- Successful toggles produce one selection haptic and a Toast: `簡體輸出：開` or `繁體輸出：開`.
- Releasing after a successful hold does not insert a space.
- Moving far enough to activate the existing space-bar cursor swipe cancels the pending long press. The gesture remains cursor movement only.
- A normal tap retains the existing space behavior.
- The `·簡` space-label suffix remains the visible indication that Simplified output is active.

## Implementation Shape

Keep gesture arbitration in `KeyboardView`: start a cancellable hold action when space goes down, cancel it when cursor-swipe movement crosses the existing step threshold, and consume release after a fired hold. Deliver the long-press event through the existing `KeyListener.onKeyLongPress` callback. `HkImeService` handles `KEY_SPACE` by toggling the existing DataStore preference and showing the Toast.

## Verification

Pure gesture/controller tests cover space tap, stationary hold, swipe cancellation, and release consumption. Existing settings, T2S, space-swipe, and full regression suites must continue to pass. Emulator verification must confirm tap-space, stationary long-press toggle, `·簡`, Toast, no inserted space, and swipe-without-toggle.

