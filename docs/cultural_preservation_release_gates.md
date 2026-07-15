# Cultural Preservation Release Gates

**Status: integration engineering is being requalified; external review gates remain open.**

This document separates facts that can be reproduced from the repository from
claims that require independent human or device evidence. It prevents an
automated corpus check from being represented as linguistic certification.

## Reproducible engineering evidence

- `corpus/sources/corpus_manifest.json` pins every shipped corpus asset's
  source, checksum, license and deterministic generator where a source is
  available. `python3 corpus/tools/verify_corpus_manifest.py` regenerates the
  generated HKSCS, English-assist and T2S assets byte-for-byte.
- `hkscs_supplement.csv` contains all 4,606 HKSCS-2016 Han records. Official
  Quick or Jyutping routes are used whenever supplied by the source. The two
  records with neither official route (`U+200CD`, `U+200D1`) expose a clearly
  technical, tap-only Unicode escape; no Cantonese or Cangjie mapping is
  invented.
- `corpus/benchmarks/three_mode_source_coverage_cases.csv` contains 540
  deterministic source-derived cases (180 per mode). Its runner verifies the
  generated top candidate against the generated asset ordering. Every case is
  marked `automated-source-derived`; it is a corpus-consistency check, **not**
  a measure of language quality or native-speaker review.

## Historical signed-emulator evidence (not current release evidence)

An older signed `0.58.0 (build 73)` APK was installed on the API 35 AOSP emulator
on 2026-07-14. It verified the production keyboard's Quick candidate tap,
Space and punctuation paths (`rryo → 唔該`, `唔該 ` and `唔該。`), Jyutping
candidate tap (`hou → 好`), Pinyin Space commit (`hao → 好`), expanded-grid
candidate selection, the tap-only `u200cd → U+200CD` HKSCS fallback,
Pinyin-mode `cat → 貓` English assist, and `唔該 → 中→英 thanks` post-commit
assist. It also verified that the Android 15 custom-word form no longer hides
its first field behind an app bar.

That APK predates the current three-mode integration candidate. It is retained as
historical diagnostic context only and cannot satisfy any current release gate.

Run the reproducible checks from the repository root:

```bash
python3 corpus/tools/verify_corpus_manifest.py
python3 corpus/tools/run_three_mode_coverage_benchmark.py
python3 corpus/tools/run_jyutping_benchmark.py --require-top1 1.0
```

## Required before a cultural-preservation release claim

1. **Independent language review.** Record at least 500 native-speaker or
   language-professional reviewed cases. Include at least 100 cases for each
   of Quick, Jyutping and Pinyin, plus at least 100 cases across bilingual
   assists, HKSCS supplementary-plane characters, OOV, punctuation and
   cold-start behaviour. Store the reviewer role, corpus version, expected
   rank, and reviewed outcome. Do not relabel generated cases as reviewed.
2. **Production-path measurement.** Report Top-1, Top-3, Top-5, MRR, KPC,
   Space/punctuation miscommit rate, OOV behaviour and supplementary-plane
   commit results using the Android production decoder, display policy and
   commit path—not a corpus-only helper.
3. **Device and font matrix.** Test the signed release on API 26 AOSP, an
   Android 13/API 33 Samsung One UI device, and an Android 15/API 35
   Pixel/AOSP device. Record device model, build, font fallback and results.
   In each environment verify that `U+2003E` (`mi` Quick, `bui` Jyutping) and
   `U+2ADFF` (`ec` Quick) remain visible or code-point-labelled, selectable,
   and commit their original code point.
4. **Rights and acknowledgement review.** The release owner must check the
   current source licenses and attribution obligations, particularly the
   DATA.GOV.HK HKSCS acknowledgement, CC-BY/CC-BY-SA notices and OpenCC's
   Apache-2.0 notice, against the intended distribution channel.
5. **Ongoing cultural governance.** Establish a public contribution channel,
   a two-person review policy for curated linguistic additions, versioned
   corpus archives and a changelog. Preserve variants and historical forms as
   selectable entries; ranking preference must not delete the source form.

## Claim policy

Until all gates above are recorded for the exact release artifact, this app may
be described as an offline three-mode Hong Kong Traditional Chinese keyboard
with reproducible corpus checks. It must not be described as linguist-certified,
exhaustive for all Cantonese vocabulary, universally font-tested, or
"cultural-preservation grade".
