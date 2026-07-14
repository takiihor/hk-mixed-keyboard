# Candidate Bar Height Design

## Goal

Remove the empty strip above the number row while keeping candidate, composing and
system-message text readable, vertically centred and independent of the selected
keyboard colour theme.

## Current Causes

- `KeyboardLayout.candidateBarHeightPx()` returns the keyboard base-row height
  (`56dp`).
- `HkImeService` assigns that fixed height to `CandidateBarView` in both normal
  and fallback input-view builders.
- The root IME `LinearLayout` also has a minimum height that permanently adds
  the candidate-bar height, so clearing the view cannot reduce the IME.
- `CandidateBarView.clear()` removes its children but leaves its parent height.

## Design

Define a view-owned display state with exactly three values:

- `Hidden`: no candidates, composing/preview text or system message. Set the
  candidate bar to `GONE` and remove the root container’s candidate-height
  minimum reservation.
- `CandidatesOrComposing`: candidates or composing/preview content is present.
  The bar is visible at a fixed `42dp` height.
- `SystemMessage`: safety, loading and future error/status messages. It uses the
  same fixed `42dp` height to avoid a resize when loading turns into candidates.

The service owns only the root minimum-height reservation; `CandidateBarView`
notifies it whenever its display state changes. The view retains existing
candidates during a theme rebind and only reapplies colours, so theme changes do
not change state or height.

Candidate and system-message labels use `17sp`, vertical-centre gravity,
`5dp` vertical padding and single-line ellipsis. Candidate rows use the same
height in both dark and iPhone-style light themes. The keyboard view keeps its
explicit existing height; changing the bar only changes total IME height. The
existing root navigation-inset padding stays independent at the bottom.

## Validation

- Pure layout policy tests cover hidden and visible heights, root reservations,
  theme invariance and the constant keyboard height.
- Candidate-bar state tests verify `clear()` hides only when no system message
  is active, and loading-to-candidates stays visible at the same height.
- Unit tests verify system labels are single-line/ellipsized and candidate labels
  remain readable at the compact text size.
- Emulator screenshots capture dark and light candidate states; measured bounds
  document the before (`56dp`) and after (`42dp`/`0dp`) heights.
