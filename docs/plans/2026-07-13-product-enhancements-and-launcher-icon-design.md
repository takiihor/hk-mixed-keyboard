# Product Enhancements and Launcher Icon Design

## Goals

1. Make the existing Android launcher artwork fully visible under adaptive-icon masks.
2. Define the next five product-investment tracks precisely enough for a lower-cost model to implement them safely in separate, reviewable increments.

## Launcher icon

The existing adaptive foreground fills almost the entire 108 dp canvas. Android launchers may apply circular, rounded-square, or squircle masks and only guarantee the central safe region, so content near all four edges is cropped.

Use the user-edited `hk-mixed-keyboard-profile.png` as the source artwork without modifying that source file. Generate density-specific foreground PNGs with the complete source image scaled into the central adaptive-icon safe region and transparent padding around it. Retain the existing `#202124` adaptive background and update legacy launcher PNGs from the same padded composition. Do not change the Play Store 512 px icon in this fix.

Verify the result under at least circular and rounded-square masks. The visible artwork must remain complete, centred, and recognisable, with no source-image overwrite.

## 1. Lightweight onboarding and first-use hints

Keep onboarding inside the existing Settings activity and keyboard surface. Add a short enable/select/try setup guide, plus two dismissible one-time keyboard hints: one for cycling `速／粵／拼`, and one for Space gestures. Persist only completion flags locally in DataStore. Do not add analytics, accounts, or network access.

Hints must never appear in password or other sensitive fields, must not block typing, and must be available again through Settings. Existing users should receive the feature-discovery hints once after upgrading.

## 2. Pinyin and offline-privacy screenshots

Capture the signed release build on the existing 1080×2400 emulator profile. Add one screenshot showing the `拼` mode, continuous Pinyin input, Traditional candidates, and the absence of network-dependent UI. Add or revise one screenshot to communicate that the keyboard has no network permission and processes input locally.

Store screenshots must contain only genuine app UI, use stable sample text, contain no personal data, and remain consistent with the current store descriptions.

## 3. HK mixed-language ranking benchmark

Create a deterministic, offline benchmark dataset covering Quick, Jyutping, Pinyin, English completion, English-to-Chinese assist, and realistic mixed-language inputs. Each case records the active scheme, input buffer, optional preceding committed token, expected candidates, and acceptable rank thresholds.

Build a JVM test harness that runs the production decoder and candidate display policy, reports top-1/top-3/top-5 accuracy and mean reciprocal rank, and writes no user data. The first benchmark establishes the context-free baseline; it must not alter production ranking.

Dataset entries must be reviewed for licensing and must not be copied wholesale from private chats or restricted corpora.

## 4. Bigram decision gate

Do not implement production bigram ranking until the benchmark exposes a measurable context-related deficit. Prototype previous-token scoring behind a pure ranking interface using a small licensed or project-authored table. Compare it against the baseline using the same benchmark.

Adopt bigram ranking only if it improves predefined accuracy thresholds without materially harming English mixing, memory use, cold-start time, sensitive-field behaviour, or APK size. Otherwise document the result and stop without adding dormant production machinery.

## 5. Optional Jyutping annotation prototype

Prototype annotations in the expanded candidate grid or an explicitly enabled learning mode, not in the compact default candidate bar. Reuse existing Jyutping corpus readings where they can be mapped reliably, and define deterministic handling for phrases, multiple pronunciations, and missing readings.

Annotations must remain legible at supported font scales, expose useful accessibility text, add no network dependency, and leave normal typing unchanged when disabled. Treat the prototype as a usability experiment before committing to the final UI.

## Delivery boundaries

Each track is a separate commit and verification unit. Onboarding comes first; screenshots follow only after the onboarding UI is stable. The benchmark must land before any bigram production decision. Jyutping annotations remain opt-in throughout the prototype.

The launcher-icon fix is independent of the five product tracks and may ship first.
