# Light Priority Candidate and Period Hold Design

## Goal

Restore the blue visual distinction for learned and other priority candidates in the
iPhone-style light keyboard theme, and make a long press on the main keyboard’s
`。` key insert a half-width period (`.`) directly.

## Findings

- `CandidateBarView` already selects `candidatePriorityText` for any candidate
  accepted by `CandidateVisualPolicy.isPriority`.
- The dark palette uses `#8AB4F8`, but the light palette currently gives
  `candidatePriorityText` the same near-black value as normal candidate text.
- The main keyboard’s `。` key emits on touch-down because it is not a hold
  gesture key. `KeyboardView` already uses `HoldActionController` to defer
  short/long-press decisions for other main-keyboard controls.

## Design

Add a light-theme priority-candidate token with a contrast-safe blue value. Keep
the existing dark-theme blue unchanged. `CandidateBarView` remains the only view
that resolves candidate priority to a text colour.

Treat `。` as a main-keyboard hold gesture. A normal release inside the key emits
the existing `。` label. Once the existing long-press delay elapses, the view calls
the IME’s existing long-press callback; the service handles `。` by committing `.`
directly. This reuses the existing gesture cancellation path so the base and
alternative punctuation cannot both be entered.

## Scope and Non-goals

- The symbol-page `。` long-press alternatives remain unchanged.
- No keyboard geometry, candidate ranking, composing state, or theme-selection
  behavior changes.
- No popup selector is added for the main keyboard’s `。` key.

## Validation

- Theme-token tests assert light priority candidates use a blue distinct from
  normal candidate text and retain the dark palette value.
- Touch-policy tests assert `。` is deferred to the hold gesture rather than
  emitted on press.
- A pure long-press action test asserts short press resolves to `。` and long
  press resolves only to `.`.
