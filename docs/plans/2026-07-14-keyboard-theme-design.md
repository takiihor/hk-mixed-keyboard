# Keyboard Theme Design

## Goal

Keep the approved dark keyboard unchanged and add an explicit, persistent
`IOS_LIGHT` keyboard theme. The setting applies to every existing keyboard
surface without changing input state, keyboard geometry, key behaviour, or
system-theme behaviour.

## Architecture

`KeyboardTheme` is a small domain enum owned by settings. `KeyboardThemePreference`
parses and serializes the stable DataStore values `dark` and `ios_light`; missing
or invalid values resolve to `DARK`. `KeyboardSettings` remains the sole settings
repository and adds the selected theme to `KeyboardPrefs`.

`KeyboardThemeColors` is the sole palette object. It resolves the unchanged dark
palette and the new light palette, including normal/function/pressed key states,
candidates, symbols, popups, and all non-glyph Emoji panel surfaces. No IME view
reads DataStore directly. `HkImeService` observes `KeyboardSettings.flow`, resolves
the palette on its UI thread, and injects it into existing views. Each view redraws
in place so composing text, keyboard mode, symbol page, candidate data, and Emoji
panel state remain intact.

## UI

Settings has a `鍵盤主題` section with two whole-row selectable radio options:
`深色` and `淺色（iPhone 風格）`. Each includes a programmatic miniature preview
made from the same tokens. The selected state is exposed in the row content
description; no save button is introduced.

The light palette uses a cool grey `#D1D5DB` keyboard surface, white character
keys, `#AEB4BD` function keys, `#111214` labels/icons, and low-opacity shadow
separation. The dark palette stays resource-equivalent to the current approved
appearance. System appearance never chooses a keyboard theme.

## Emoji constraints

The Emoji panel receives palette tokens for panel background, category bar,
search UI, grid pressed state, selection indicator, dividers, empty state,
skin-tone popup, and function icons. Emoji text views remain native colour emoji:
the theme API intentionally has no glyph tint, text-colour, or alpha token.
The light Emoji panel stays grey rather than white.

Theme application must preserve category, grid scroll offset, query text, recent
usage, and skin-tone preference. Updating palette properties may redraw or
rebind existing non-glyph presentation, but must not reconstruct the panel or
its model state.

## Verification

Unit tests cover parsing/default/fallback/serialization, both token palettes,
and state-preserving theme application policies. Existing unit tests and the
debug build are run after the implementation. Device screenshots remain a manual
validation item because no emulator/screenshot harness is currently present in
the project.
