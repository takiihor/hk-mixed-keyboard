# Firefox URI Composition Design

**Date:** 2026-07-20

## Problem

Firefox reports its address bar as `TYPE_TEXT_VARIATION_URI` (`inputType=0x11`).
The IME currently treats every URI field as direct-entry: key presses commit raw
Latin text and skip the Quick, Jyutping, and Pinyin candidate pipeline. In the
Firefox address bar, tapping the `q`/手 key therefore types `q` and triggers URL
suggestions instead of producing Chinese candidates. Firefox web-page text
inputs already compose correctly.

## Decision

Keep the URI keyboard surface, including URL-oriented keys and the host-provided
Go action, but decouple it from direct-Latin commit. URI fields will use the
normal composition pipeline.

Direct entry remains unchanged for numeric, signed-decimal, phone, email,
password, terminal, and `TYPE_NULL` fields. The change applies by editor type,
not by Firefox package name, so other browsers with URI address fields receive
the same correct behavior.

## Data Flow

`EditorInfo.inputType` continues to select `KeyboardSurface.URI` for URI
fields. `DirectInputPolicy.shouldUseDirectLatinCommit` will no longer classify
that surface as direct entry. The existing key handler will consequently send
letter keys into the existing Quick/Jyutping/Pinyin composition and candidate
flow, while URI punctuation continues through its existing key handling.

## Verification

Add a unit test proving URI fields retain the URI surface but do not enable
direct Latin commit; preserve tests for the existing direct-entry field types.
Build and install the debug APK. With ADB, focus Firefox's address bar and tap
the `q`/手 key: the candidate strip must appear rather than committing raw text.
Recheck Firefox's in-page text field as a control and run the focused and full
unit test suites.
