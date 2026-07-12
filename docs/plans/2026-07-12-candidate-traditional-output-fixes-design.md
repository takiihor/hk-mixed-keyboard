# Candidate and Traditional Output Fixes Design

## Goal

Make `cat` surface the Traditional Chinese candidate `貓`, keep Simplified output available only as an explicit Settings choice, and render all expanded candidates within the keyboard width.

## Decisions

- Traditional output remains the default (`simplified_output=false`).
- Remove the candidate-bar output toggle and the mode-key long-press output toggle. The Settings switch remains the only way to enable Simplified output.
- Keep the `·簡` suffix on the space bar while Simplified output is enabled so the active state is visible.
- Treat an exact English-assist match as Chinese intent for display ranking. Exact `cat → 貓` candidates should precede English prefix completions such as `category`.
- Bump the corpus content cache version so local and same-build installs cannot retain an older `english_assist.csv` without `cat`.
- Constrain the expanded grid to the popup width. Weighted cells use a zero base width, and the grid is attached to the vertical `ScrollView` with `MATCH_PARENT` width.

## Data Flow

Typing `cat` still passes through the classifier and corpus-backed decoder. The decoder returns exact English-assist candidates and exposes that exact-assist state to display ordering, which places `貓` before English completions. Candidate taps continue through `outputText`: Traditional mode passes `貓` unchanged; an explicitly enabled Settings preference converts it to `猫`.

## Safety and Compatibility

Existing users who explicitly enabled Simplified output retain that persisted preference. New and unset installations remain Traditional. Quick (`bw`) and Jyutping (`maau`) dictionary behavior is unchanged. The corpus cache bump invalidates only derived cache files; source assets and user memory are untouched.

## Verification

- Unit tests cover exact English-assist display priority, default Traditional preference, cache content version, and the pure expanded-grid sizing policy.
- The complete debug unit-test suite must pass.
- Emulator verification must show `貓` in the visible candidate bar for `cat`, commit `貓` in default mode, and display readable text in every expanded grid row.

