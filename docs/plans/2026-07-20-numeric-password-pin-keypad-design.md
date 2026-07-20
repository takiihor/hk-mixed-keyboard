# Numeric Password PIN Keypad Design

**Date:** 2026-07-20

## Problem

Android identifies a numeric password or PIN editor as
`TYPE_CLASS_NUMBER | TYPE_NUMBER_VARIATION_PASSWORD`. The keyboard currently
maps every number-class editor to the same `KeyboardSurface.NUMBER` layout.
That layout has five full-height rows: three rows of oversized digits, a row
where `0` and Backspace each occupy half the screen, and a final Enter key that
occupies the entire width. This is visually unbalanced and does not resemble a
standard mobile PIN keypad.

Numeric password fields already enter the existing sensitive-field safe mode,
so candidates and learning are suppressed correctly. The defect is the editor
surface and its geometry, not the privacy policy.

## Decision

Add a dedicated numeric-password surface selected only when the number-class
variation is `TYPE_NUMBER_VARIATION_PASSWORD`. Keep ordinary number,
signed-decimal, phone, email, and text surfaces unchanged.

The PIN surface uses a centered four-row, three-column keypad:

```text
1  2  3
4  5  6
7  8  9
⋯  0  ⌫
```

Each row uses equal key widths inside balanced left and right gutters. The
bottom-left position contains the host editor action only when the editor
provides a meaningful Search, Send, Next, Done, or Go action. It remains an
empty, non-interactive gap when the action resolves to Return. The center is
`0`, and Backspace is on the right.

## Architecture

`EditorLayoutPolicy.surfaceFor` detects numeric-password variation before the
general number and signed/decimal branches and returns a new dedicated
`KeyboardSurface.NUMERIC_PASSWORD` value. `usesDirectEntry` includes this
surface so PIN digits continue committing directly.

`KeyboardLayout.rowsFor` receives the current `SymbolEnterAction` in addition
to the selected surface. It constructs the four-row PIN grid and omits the
bottom-left key definition entirely when the action is `RETURN`; no fake blank
key, touch target, or accessibility node is created.

`KeyboardView.enterAction` rebuilds cells when the PIN surface is active,
because changing between a meaningful editor action and Return changes the
bottom-row geometry. Existing icon drawing, action dispatch, haptics,
Backspace behavior, and accessibility descriptions are reused.

The keyboard retains its current overall docked height. Four equal rows
therefore receive more vertical space than five-row text surfaces, producing
larger, touch-friendly PIN targets without the previous extra action row.

## Privacy and Error Handling

The existing `SensitiveFieldDetector` remains the source of truth for safe
mode. Numeric-password editors continue to suppress candidate suggestions,
contextual ranking, and learning. Unknown or malformed input types retain the
existing text fallback.

If an editor supplies no recognized action or sets
`IME_FLAG_NO_ENTER_ACTION`, the PIN layout leaves the bottom-left position
empty. It does not synthesize a Return key in a single-line password field.

## Verification

Use test-driven development to prove:

- numeric-password input selects `NUMERIC_PASSWORD` while ordinary numbers,
  signed decimals, and phone inputs keep their existing surfaces;
- the PIN rows are exactly `1–3`, `4–6`, `7–9`, then action/`0`/Backspace;
- meaningful editor actions occupy the bottom-left slot, while Return leaves
  that slot empty and non-interactive;
- all PIN keys use balanced centered geometry and retain existing action and
  accessibility behavior.

Run focused policy/layout tests, the complete debug unit suite, and a debug APK
build. Install the APK and use ADB to inspect a real numeric-password editor:
its input type must include `TYPE_NUMBER_VARIATION_PASSWORD`, its keyboard must
show the centered four-row PIN grid, and candidate or learning content must not
appear.
