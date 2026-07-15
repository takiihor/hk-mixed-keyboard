# Three-Mode Independent Benchmark

These files define the release benchmark for 速成, 粵拼 and 普通話拼音. They
are deliberately header-only until independent reviewers supply cases. Empty
files are not release evidence and the locked scorer rejects fewer than 1,000
cases per mode.

Do not populate these files from the production corpora, ranking overrides,
private chats or generated text presented as human-reviewed language.

## Case schema

All case files are UTF-8, tab-separated text.

| Field | Requirement |
|---|---|
| `case_id` | Stable unique ID, prefixed by mode |
| `mode` | `quick`, `jyutping`, `pinyin` or `mixed` |
| `category` | Reviewed category such as `common_phrase`, `name`, `colloquial`, `ambiguity`, `mixed_english`, `punctuation`, `hkscs` or `reputation_sensitive` |
| `frequency_band` | `high`, `medium`, `long_tail` or `rare` |
| `phrase_length` | Expected Chinese code-point count as an integer string |
| `hkscs_status` | `core`, `hkscs_bmp`, `hkscs_supplementary` or `not_applicable` |
| `input` | Exact keys entered before candidate selection |
| `context` | Optional preceding committed text, with private data prohibited |
| `acceptable_outputs` | Non-empty JSON string list of reviewer-approved variants |
| `split` | `development` while authoring; exactly `locked_holdout` when frozen |
| `provenance` | Reviewer/source class; `production_corpus` is forbidden in a locked holdout |
| `reviewer_ids` | Pseudonymous reviewer IDs; at least three reviewers approve the locked set |
| `notes` | Variant or ambiguity rationale without private information |

Keep answer-bearing holdout files access-controlled if the repository becomes
public. CI may receive a checksum and schema-only subset rather than the locked
answers.

## Result schema

Use `results_template.tsv` for this app and every comparison keyboard.

- `candidates` is a JSON string list in displayed order.
- `committed_text` is the final text for the prompt.
- `keystrokes` counts input keys plus candidate/correction actions under the
  documented protocol.
- `corrections` counts backspace, candidate replacement and undo actions.
- `wrong_auto_commits` is `1` only when the keyboard commits unintended text
  without explicit selection; otherwise `0`.
- `elapsed_ms` is measured from the first key event through the stable candidate
  result for ranking runs, or through correct commit for task-completion runs.
- Keyboard, version, device and run IDs are mandatory in collected evidence.

## Collection and lock procedure

1. Recruit at least three independent native reviewers. Cantonese/HK
   Traditional expertise is required for Quick and Jyutping; Mandarin
   Traditional-Chinese expertise is required for Pinyin.
2. Draft at least 1,250 cases per mode so at least 1,000 remain after duplicate,
   ambiguity and provenance review.
3. Include at least 200 ambiguity cases and 100 reputation-sensitive cases per
   mode. Cover characters, words, names, places, colloquial phrases, sentences,
   punctuation, correction, mixed English and HKSCS.
4. Record all acceptable regional/orthographic variants before any keyboard is
   scored.
5. Have reviewers approve cases independently; adjudicate disagreements without
   viewing this app's ranking.
6. Change the retained cases to `split=locked_holdout` and freeze them before
   tuning.
7. Record hashes:

   ```bash
   sha256sum corpus/benchmarks/*_holdout.tsv \
     > docs/release/benchmark_holdout.sha256
   ```

8. Reset personalization between cold runs. Use identical prompts, devices,
   settings and scoring rules for each keyboard.
9. Store anonymized results without typed personal content.

## Scoring

Development example:

```bash
python3 corpus/tools/score_three_mode_benchmark.py \
  --cases corpus/benchmarks/jyutping_holdout.tsv \
  --results /secure/results/hk_mixed_jyutping.tsv \
  --mode jyutping \
  --json-output docs/release/jyutping_benchmark.json \
  --markdown-output docs/release/jyutping_benchmark.md
```

Locked release example:

```bash
python3 corpus/tools/score_three_mode_benchmark.py \
  --cases corpus/benchmarks/jyutping_holdout.tsv \
  --results /secure/results/hk_mixed_jyutping.tsv \
  --mode jyutping \
  --locked-holdout \
  --json-output docs/release/jyutping_benchmark.json \
  --markdown-output docs/release/jyutping_benchmark.md
```

The scorer reports Top-1/3/5, mean reciprocal rank, OOV, committed accuracy,
keystrokes per correct character, corrections, wrong automatic commits, latency
percentiles and ranking breakdowns. A passing report still requires the
absolute and competitor-relative gates in the readiness plan.

## What this infrastructure does not prove

- Header-only files do not prove language quality.
- Source-derived corpus consistency tests are not independent benchmarks.
- A perfect development-set result is not a release result.
- Automated scores do not replace native review, blinded preference testing,
  physical-device testing or legal approval.
