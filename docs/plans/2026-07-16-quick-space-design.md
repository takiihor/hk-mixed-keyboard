# Quick mode preview and Space behavior design

## Goal

Treat 速成 (Quick) as independent from 粵拼 and 普通話拼音 for candidate preview
ordering and Space-key behavior.

## Behavior

- A Quick composition keeps Chinese candidates ahead of the raw English literal in
  the preview bar, including two-letter codes.
- Space never selects a Quick candidate, regardless of code length or candidate
  source.
- With an active Quick buffer, Space commits the raw code without adding a
  trailing space. The cursor remains immediately after that committed code, so
  the next input continues at that position.
- 粵拼 and 普通話拼音 retain their existing candidate-first Space selection
  behavior.

## Design

Candidate ordering will no longer have a Quick-only literal-first override. The
existing Chinese-first decision for a short Quick code or an exact Quick phrase
therefore remains the only input to candidate display ordering.

The IME policy will report no Space candidate for Quick. The commit controller
will use an explicit Quick branch to finalize a nonempty composition as its raw
literal without calling the ordinary Space path, which appends whitespace. Other
schemes keep the existing selected-candidate and literal-plus-space paths.

## Tests

Unit tests will prove that a two-letter Quick code keeps Chinese candidates at
the front, that Quick never resolves a Space candidate, and that its Space
commit produces only the raw buffer. Existing Jyutping and Pinyin tests cover
their unchanged candidate selection behavior.
