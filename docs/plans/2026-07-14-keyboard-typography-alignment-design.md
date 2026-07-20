# Keyboard Typography and Hint Alignment Design

## Context

The fixed candidate bar is readable but its single-line text is still slightly
too small. On Cangjie keys, the centred Chinese root and the Latin hint can look
crowded, especially on narrow keys.

## Approved Typography

Candidate and system-message text increases from `20sp` to `21sp`. The candidate
bar remains `42dp` high with its current single-line, vertically centred and end-
ellipsized behaviour.

The centred Chinese root decreases from `17sp` to `16sp`. The Latin hint remains
`13sp` and stays right-aligned in the key's top-right corner. Its inset decreases
from `5dp` to `3dp`, keeping the glyph inside the key while moving it farther away
from the centred Chinese root.

Space, popup and other key-label text sizes remain unchanged. Key dimensions,
positions, touch targets, colours and input behaviour also remain unchanged.

## Implementation

Keep candidate typography in `CandidateBarLayoutPolicy`. Add a small pure-Kotlin
main-key typography policy so the Chinese-root size and top-right hint anchor can
be covered by local unit tests. `KeyboardView` consumes the policy without
changing its drawing order or interaction logic.

## Validation

Use test-first coverage to require:

- `21sp` candidate text with the unchanged `42dp` bar;
- `16sp` centred Chinese roots;
- `13sp` Latin hints anchored `3dp` from the right and top edges;
- unchanged space and popup text sizes and unchanged keyboard geometry.

Run focused typography and candidate-bar tests, the related keyboard-layout
tests, the full Android unit-test suite, and `git diff --check`. Do not assemble,
bundle or install the app because packaging changes the version checkpoint.
