# Two-Page Symbol Keyboard Design

## Goal

Replace the single symbol panel with a reference-aligned two-page symbol
keyboard. The actual IME contains only the keyboard surfaces shown at the top
of the approved design board; headings, annotations, gesture examples, and
the design-notes footer are not keyboard content.

## Architecture

The symbol model lives outside the Android view:

- `SymbolKeySpec` describes the committed text, visible label, semantic
  accessibility label, and long-press alternatives.
- `SymbolPage` owns the `COMMON` and `EXTENDED` page identities and their
  four-by-ten matrices.
- `SymbolKeyboardSpec` owns common symbols, function-key semantics, page
  labels, and theme-independent layout weights.
- `SymbolPageView` is a renderer and touch surface only. It derives visual
  rects and non-overlapping hit rects from the available size, renders pressed
  states and popups, and emits typed callbacks. It has no literal symbol
  matrix, colour value, or language-specific accessibility table.
- `KeyboardThemeColors` is a shared theme-token holder. The existing custom
  keyboard renderer and the symbol renderer consume its token values, so a
  future light theme does not require copying colour decisions into either
  view.

`HkImeService` manages `keyboardMode` independently from the active
`symbolPage`: entering Symbols always selects `COMMON`, page toggling changes
only `symbolPage`, and `ABC` restores the existing alphabet keyboard without
altering its selected input scheme or Shift/Caps state.

## Input and Interactions

The IME remains the one owner of text entry. Symbol taps and selected
long-press alternatives use the existing standalone commit path; Space,
Backspace, and Enter use the existing IME handlers. Backspace keeps its repeat
behaviour. Enter continues to use `EditorInfo` action handling. A long press
does not first commit the base symbol; releasing after cancellation or outside
the popup commits nothing.

The bottom key uses dynamic weights: `ABC 1.25`, page `0.90`, space `2.60`,
Backspace `1.05`, and Enter `1.20`. The visible gap contracts before text
size on narrow widths. Every complete cell remains its own touch target; hit
regions never overlap.

## Visual and Accessibility Design

The symbol surface uses the existing dark keyboard visual language through
shared theme tokens: near-black background, charcoal keys, off-white labels,
subtle pressed brightening, and rounded 8--10dp corners. Page indicator dots
render beneath the page key label. Backspace and Enter are vector drawables,
not glyph or emoji substitutions. Each key has a Traditional Chinese semantic
description, and the selected page is announced.

## Validation

Unit tests define the exact Unicode matrix, escaping, long-press policy,
mode/page transitions, common function-key routing, layout geometry at narrow
widths, and token-driven theme stability. Android tests/builds confirm that
the IME compiles with the shared handlers intact. Emulator screenshots will
cover both symbol pages where an emulator is available.
