# Quick Jyutping Learning Preview Design

## Goal

Help a 速成 user learn accurate 粵拼 during ordinary typing without changing
candidate selection or adding network access.

## Learning interaction

While a Quick composition has Chinese candidates, the candidate strip shows one
compact line above the candidates:

`香港 · hoeng1 gong2`

The line follows the leading ranked candidate. When the user taps any Chinese
candidate, the line briefly confirms the word that was actually committed and
its reading, while the normal next-character predictions continue underneath.
Starting another composition immediately replaces the confirmation with the new
live preview.

The preview is shown only in 速成 mode. It is hidden for English literals,
post-commit predictions after the confirmation expires, sensitive fields,
loading/error messages, and entries without a reliable reading.

## Pronunciation data

The current runtime Jyutping input corpus is intentionally toneless, so it must
not be used as the learning label. A deterministic corpus tool will derive a
small `text -> toned Jyutping` asset from a pinned rime-cantonese revision. The
source provides standard Jyutping with tone numbers and syllable boundaries,
including exact word-level readings such as `香港 -> hoeng1 gong2`.

The generated asset is filtered to Chinese text reachable through the app's
Quick character and phrase corpora. Exact word readings take precedence; the
app does not fabricate phrase readings by joining possibly polyphonic
characters. When several source readings exist, the deterministic source order
selects the primary reading. Source revision, attribution, licence register,
and bundled notice are updated with the derived asset.

## Architecture and data flow

`CorpusLoader` lazily loads the compact reading map and exposes exact lookups.
A pure `JyutpingLearningPreview` policy decides whether a candidate is eligible
and formats `text · reading`. It accepts the active scheme and candidate so its
behaviour can be unit-tested without Android views.

After a Quick decode, `HkImeService` asks the policy for the leading candidate's
label and passes it to `CandidateBarView`. On a composed candidate tap, the
service formats the tapped candidate before committing it and retains that
label briefly. A generation/token check ensures an old delayed clear cannot
erase a newer preview.

`CandidateBarView` becomes a vertical container with a small, single-line
learning label and an inner horizontally scrolling candidate row. The candidate
bar keeps a fixed, slightly taller height across empty, loading, and candidate
states, preventing the IME window from jumping while typing. Existing candidate
tap, expand, theme, safe-mode, and horizontal-scrolling behaviour remains
unchanged.

## Error handling

Reading data is best-effort. A missing or malformed asset produces no preview
and never affects decoding or committing text. Delayed confirmation callbacks
are cancelled by new input, scheme changes, sensitive-field transitions, and
service destruction.

## Verification

- Corpus-tool tests prove tones and syllable spaces are preserved, source
  ordering is deterministic, and output is filtered to Quick-reachable text.
- JVM policy tests cover Quick-only display, exact readings, English and missing
  reading suppression, and tapped-candidate confirmation state.
- Layout policy tests cover the fixed preview-capable height.
- Existing Android JVM tests and lint/build checks protect candidate and commit
  behaviour.
