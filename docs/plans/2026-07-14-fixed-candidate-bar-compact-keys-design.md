# Fixed Candidate Bar and Compact Key Design

## Goal

Replace the 0.60.0 candidate-bar collapse behaviour with a stable, Gboard-inspired
layout and reduce the height of every keyboard key without changing key widths,
horizontal placement, input behaviour or theme geometry.

This design supersedes the collapsing-bar portion of
`2026-07-14-candidate-bar-height-design.md`.

## Candidate and Preview Bar

The candidate bar remains present at a fixed `42dp` in every content state:

- empty;
- candidates or composing preview;
- safe mode, dictionary loading, errors and other system messages.

An empty bar shows only its themed background. It is never changed to `GONE` or
`0dp`, and the IME root always reserves the same `42dp`. Content changes therefore
do not resize the keyboard or move the host application.

Candidate and system-message text uses `19sp` with a medium sans-serif weight.
Labels remain single-line, vertically centred and ellipsized when necessary.
Horizontal scrolling and candidate selection behaviour remain unchanged. Dark and
light themes change colours only; they do not change candidate-bar dimensions.

## Keyboard Geometry

Every row in the main keyboard uses a fixed `44dp` slot height:

- number row;
- QWERTY row;
- home row;
- Shift / ZXCV / Backspace row;
- bottom function row.

The existing number-row `0.85` height multiplier is removed so all five rows have
the same compact height. The key grid therefore changes from approximately
`271.6dp` (`56dp × 4.85`) to `220dp` (`44dp × 5`).

Key widths, width weights, row staggering, horizontal positions, corner radius and
horizontal gaps stay unchanged. The existing `2.5dp` visual inset remains on each
edge, producing a normal portrait character-key face of roughly `36 × 39dp` on a
411dp-wide device. Hit regions continue to cover their complete row slots and may
overlap gaps, preserving reliable typing despite the smaller visual keys.

Text and hint sizes remain legible within 44dp. They may use bounded scaling based
on the new row height, but Cangjie roots, Latin hints, function labels and icons
must not be clipped.

## Symbol Pages

Both symbol pages occupy the same `220dp` keyboard slot as the alphabet keyboard.
Their four symbol rows and bottom function row each receive one fifth of the fixed
height. Symbol matrices, function-key width weights, long-press alternatives,
page state and input routing remain unchanged.

## Emoji Panel

The Emoji panel uses the same compact panel slot as other keyboard modes. Its ABC,
Backspace and category-tab controls are vertically compacted to fit the new
geometry. Colour Emoji glyph grid cells retain their existing proportions, font
size, spacing and scroll behaviour; they are not treated as keyboard keycaps.

Theme changes continue to style only surrounding Emoji UI. Colour Emoji glyphs
must never receive tint, text colour or opacity. Selected category, scroll position,
recent Emoji data and other panel state remain intact.

## Insets and State

The total IME content height is stable: `42dp` candidate bar plus `220dp` keyboard
or alternative-panel slot, followed by the independently calculated system
navigation or gesture inset. No animation is added.

Changing candidate content, keyboard mode, symbol page or colour theme must not
rebuild the InputConnection, reset composing state, dismiss the IME or change its
height.

## Validation

Automated tests will verify:

- the candidate bar is always `42dp`, including the empty state;
- empty, composing and system-message transitions keep the same root height;
- candidate text is `19sp`, medium weight, single-line and vertically centred;
- every main-keyboard row is `44dp` and all existing widths are unchanged;
- the keyboard slot is `220dp` in alphabet and symbol modes;
- both colour themes use identical geometry;
- Emoji category/function controls are compact while glyph cells remain unchanged
  and untinted;
- candidate, composing, keyboard mode, symbol page and Emoji state survive content
  and theme updates.

Emulator validation will compare dark and light screenshots, measure the fixed
candidate bar and compact key rows, and exercise alphabet, symbol and Emoji modes.
