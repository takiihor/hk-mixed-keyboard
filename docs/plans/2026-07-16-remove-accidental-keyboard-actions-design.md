# Remove Accidental Keyboard Actions Design

**Date:** 2026-07-16  
**Status:** Approved by product direction

## Goal

Remove the bottom-left Settings gear and the right-side next-input-method key from every keyboard surface because they cause accidental touches.

## Root cause

\`KeyboardLayout\` reserves 0.75–2.0 grid units for \`KEY_SETTINGS\` and conditionally appends \`KEY_NEXT_IME\` when Android offers another IME. The action row then reduces the space-bar or Enter width to keep the row within ten units. Those edge actions are therefore reachable during ordinary typing.

## Design

Remove both keys from the row definitions for text, email, URI, number, signed-decimal and phone surfaces. Restore the released width to the existing typing control: text/email/URI surfaces expand Space by the former Settings width; compact numeric surfaces let Enter use the released space.

No candidate, language-mode, symbol, emoji, Shift, Backspace or input-type behavior changes. \`速成\`／\`粵拼\`／\`普拼\` switching remains on the mode key and its existing long-press picker. Settings remain available from the app launcher and Android's IME Settings; Android's normal system keyboard switcher remains the route for changing keyboards.

## Verification

Unit tests will prove every layout surface excludes both action labels, still fills exactly ten horizontal units, and preserves text-surface essential key widths. Accessibility instrumentation will prove no virtual node exposes either removed action. The focused layout/accessibility suites and full Android test/lint suite will run after the change.

