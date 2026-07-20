# Text Password PIN Toggle Design

**Date:** 2026-07-20

## Problem

The numeric-password PIN surface works when an Android editor reports
`TYPE_CLASS_NUMBER | TYPE_NUMBER_VARIATION_PASSWORD` (`inputType=0x12`).
On-device ADB verification found that Firefox reports an HTML numeric password
field as `TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_PASSWORD` (`inputType=0x81`),
with no `hintText`, label, private IME option, or other `EditorInfo` metadata
that preserves the page's numeric input intent.

Automatically treating every Firefox text-password field as numeric would
break ordinary alphanumeric passwords. Package-specific guessing is therefore
unsafe. The keyboard needs an explicit, field-local way for the user to enter
the existing PIN surface.

## Decision

Add a text-password surface that retains the normal alphabet keyboard and
symbol access but replaces the input-scheme switch with a `123` key. Tapping
`123` switches only the active password editor to the existing four-row PIN
surface.

While manual PIN mode is active, the sensitive-field status strip exposes an
accessible `ABC` action that returns to the text-password surface. Keeping the
return action in the status strip leaves the PIN bottom row available for its
existing host-editor action, centered `0`, and Backspace.

Native numeric-password fields continue selecting the PIN surface
automatically and do not show the manual `ABC` return action. No Firefox
package-name exception is added.

## Architecture

`EditorLayoutPolicy` adds a distinct `TEXT_PASSWORD` surface for genuine text
password variations. It must classify password variations before the ordinary
text fallback. Direct-entry behavior remains owned by the existing
`DirectInputPolicy` password detection.

`KeyboardLayout` renders `TEXT_PASSWORD` with the existing alphabet rows and a
password bottom row. That row keeps symbol access and other existing text
controls, replacing only `KEY_MODE` with a new `KEY_PIN_MODE` (`123`) action.
The normal text surface remains unchanged.

`HkImeService` keeps two separate values:

- the host-selected `editorSurface`, which remains `TEXT_PASSWORD`; and
- a field-local `passwordPinMode` flag, which temporarily chooses
  `NUMERIC_PASSWORD` for the visible `KeyboardView`.

The `123` key enables the flag and reapplies the visible surface. The `ABC`
status-strip action clears the flag and reapplies `TEXT_PASSWORD`. The flag is
cleared on every `onStartInput` and `onFinishInput`, so it never leaks into
another editor or survives a field switch.

The candidate/status view receives only an optional auxiliary action label and
callback. In sensitive text-password PIN mode it displays `ABC`; otherwise it
retains its existing safe-mode presentation without an auxiliary action. The
action must be keyboard-accessible, expose an accessibility description, and
must not reveal or inspect editor contents.

## Interaction and Error Handling

- `123` is available only on the text-password surface.
- `ABC` is available only while that field is in manual PIN mode.
- Leaving or restarting the editor returns to text-password mode.
- Unknown text variations retain the existing text fallback.
- Native numeric-password fields remain automatic PIN surfaces.
- Host actions, PIN touch-gap protection, haptics, and accessibility-focus
  reset behavior remain unchanged.

If a sensitive editor changes while a key is held or accessibility focus is
active, the existing structural-rebuild cancellation and focus-reset path
continues to prevent stale activation.

## Privacy

Both the text-password and manual PIN surfaces remain in the existing
sensitive-field safe mode. Candidates, contextual ranking, and learning stay
disabled. The toggle stores no password data and no preference; it is an
in-memory flag scoped to the current editor session.

## Verification

Use test-driven development to prove:

- text-password variations select `TEXT_PASSWORD`, while numeric passwords
  still select `NUMERIC_PASSWORD`;
- the text-password bottom row contains `123` instead of the input-scheme key
  while preserving symbol access;
- `123` changes only the visible password surface to the existing PIN layout;
- the status strip exposes `ABC` only during manual PIN mode and returns to the
  alphabet surface;
- field restart/finish resets manual PIN mode;
- safe mode, native PIN layout, PIN touch gaps, host actions, accessibility,
  and all non-password surfaces remain unchanged.

Run focused unit and instrumentation tests, the complete debug unit suite, and
an APK build. Install the APK and use ADB on the Firefox test field: ADB may
continue reporting `inputType=0x81`, but the user must be able to tap `123`, see
the centered four-row PIN keypad, use `ABC` to return, and observe no candidates
or learning content.
