# Gboard-style Keyboard Layout Design

## Goal

Change the main keyboard to a five-row layout with a dedicated number row,
standard staggered QWERTY letter positions, and the requested punctuation and
action-key placement.

## Layout

```text
1 2 3 4 5 6 7 8 9 0
Q W E R T Y U I O P
 A S D F G H J K L ?
Shift Z X C V B N M Backspace
Emoji Symbol Comma Space Exclaim Period Enter
```

- The QWERTY row uses a half-key left inset and the home row uses a one-key
  inset, so the center of `A` is exactly between the centers of `Q` and `W`.
- Letter keys retain one common key-width unit across the QWERTY, home, and
  bottom-letter rows. The question key also keeps a full key-width touch target.
- Backspace moves from the home row to the `Z` row.
- Enter moves to the bottom row.
- Exclamation moves next to the right side of the space bar.
- The space bar uses a weight of `3`, reduced from `4`.
- The number row is slightly shorter than the typing rows to limit total
  keyboard height.

## Implementation

Move the row definitions and geometry calculation into a pure Kotlin
`KeyboardLayout` object. `KeyboardView` will draw and hit-test the rectangles
produced by this object, ensuring visual placement and touch targets stay
identical. `HkImeService` will use the layout's height weights when assigning
the keyboard view height.

## Verification

Unit tests will verify row contents, punctuation/action-key positions, space
weight, and the exact `A` versus `Q/W` center alignment. The Android unit test
suite and debug APK build will then be run.

## Repository Note

The supplied workspace has no `.git` directory, so this design cannot be
committed from the current checkout.
