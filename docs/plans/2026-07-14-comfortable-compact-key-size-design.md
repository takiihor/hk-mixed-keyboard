# Comfortable Compact Key Size Design

## Context

The first compact-key checkpoint used five `44dp` rows and `19sp` medium
candidate text. Device testing found that the visible key face was too short for
the centred `17sp` Cangjie root and top-aligned `13sp` Latin hint, while candidate
text was smaller than desired.

## Approved Geometry

Every main-keyboard row increases from `44dp` to `48dp`. All five rows keep equal
`1f` height weights, so the shared keyboard or alternative-panel slot becomes
`240dp`. The fixed `42dp` candidate bar makes the total IME content height `282dp`
before the independently calculated navigation inset.

The existing `2.5dp` key margin leaves a roughly `43dp`-high visible key face.
Key widths, horizontal positions, row staggering, hit inflation, nearest-key
selection and corner radii remain unchanged. The existing `17sp` main labels,
`13sp` Latin hints, `13sp` space label and `21sp` popup labels also remain
unchanged; the extra vertical room addresses the overlap without changing the
keyboard's visual hierarchy.

The shared slot grows consistently for alphabet, symbol and Emoji modes so mode
switches cannot change the IME root height. No mode-specific height is added.

## Candidate Typography

Candidate and system-message text increases from `19sp` to `20sp`, retaining the
medium sans-serif typeface, single-line layout, vertical centring and end
ellipsis. The candidate bar stays fixed at `42dp` in empty, composing/candidate
and system-message states, with its current `4dp` vertical padding.

Candidate content, ordering, colours, selection callbacks, horizontal scrolling
and state transitions remain unchanged.

## Validation

TDD coverage will first require:

- `20sp` candidate text with the unchanged `42dp` bar;
- five equal `48dp` main-keyboard rows;
- a `240dp` keyboard slot and `282dp` total IME content height;
- unchanged key widths and horizontal positions;
- unchanged key-touch and period-long-press behaviour.

Focused candidate, keyboard-layout and interaction tests will be run after each
change. No assemble, bundle or install task will run during implementation:
`0.61.0` has already been packaged, and another package task would increment the
APK to `0.62.0`.
