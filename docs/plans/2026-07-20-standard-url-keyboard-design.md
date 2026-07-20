# Standard URL Keyboard Design

**Date:** 2026-07-20

## Problem

Android reports browser address bars and other URL editors as
`TYPE_TEXT_VARIATION_URI`. The keyboard currently maps that variation to a
dedicated URI surface with a different bottom row. As a result, Firefox and
other URL fields do not show the same keyboard that users see in ordinary text
fields, even though URI letters now use the normal Chinese composition path.

## Decision

Treat every `TYPE_TEXT_VARIATION_URI` editor as a standard text surface. The
host application will continue to receive and own its URI input metadata and
IME action, but the keyboard rows, controls, composition behavior, and
candidate flow will match an ordinary text field.

This is an editor-type policy rather than an application exception, so it
applies consistently to URL fields in Firefox, other browsers, and other apps.
URL punctuation remains available through the standard symbol layout.

## Implementation

`EditorLayoutPolicy.surfaceFor` will no longer select a URI-specific surface.
URI input types will fall through to `KeyboardSurface.TEXT`, while number,
phone, and email mappings remain unchanged.

Because no editor will select the URI surface after this change, remove the
URI-only keyboard layout and the URI-only direct-symbol commit path. This keeps
the standard text layout as the single source of truth and prevents a dormant
special layout from diverging again.

## Verification

Use test-driven development to first change the URI layout expectation from
`KeyboardSurface.URI` to `KeyboardSurface.TEXT` and verify that it fails. Then
make the minimal policy and dead-code cleanup, run the focused layout and input
policy tests, and run the complete debug unit suite.

Build and install the debug APK. With ADB, focus Firefox's address bar and
confirm its `inputType` remains `0x11`, then verify visually that its keyboard
rows and bottom controls match the ordinary text keyboard and that Chinese
composition still produces candidates.
