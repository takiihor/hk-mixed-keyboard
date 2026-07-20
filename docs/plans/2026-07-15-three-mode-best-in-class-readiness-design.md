# Three-Mode Best-in-Class Readiness Design

**Date:** 2026-07-15

**Status:** Approved

**Decision owner:** Product owner

## Goal

Make 速成、粵拼 and 普通話拼音 demonstrably best-in-class for Hong Kong Chinese input quality and everyday usability before a public release.

## Agreed definition of “best-in-class”

Best-in-class means the keyboard is more accurate, predictable and efficient than the strongest current comparison keyboard on representative Hong Kong input tasks. It does not require unrelated online features such as GIF search, cloud translation, voice input or handwriting.

The claim must be supported by both:

1. Absolute quality gates for correctness, coverage, latency, stability, accessibility and safety.
2. A blinded, reproducible comparison against current mainstream and specialist keyboards using the same prompts, devices and scoring rules.

Passing unit tests alone is not sufficient.

## Scope

Included:

- 速成、粵拼 and普通話拼音 decoding, ranking and commit behaviour
- Hong Kong Traditional Chinese and HKSCS coverage
- Mixed Chinese/English input and local personalization
- Setup, mode switching, candidate selection and correction flows
- Accessibility, performance, reliability, privacy and release packaging
- Store claims, corpus provenance and release evidence

Excluded from the launch gate unless separately approved:

- Network-backed AI features
- GIF/sticker search
- Cloud translation
- Voice input and handwriting
- Cross-device cloud synchronization

The exclusions preserve the product's offline/privacy differentiation and do not lower the core input-quality bar.

## Gate structure

The operational checklist is organized into:

1. Integration and reproducibility gates
2. Shared input-contract gates
3. Separate 速成、粵拼 and普通話拼音 quality gates
4. Shared ranking, personalization and mixed-input gates
5. Performance, stability, accessibility and usability gates
6. Privacy, legal, store and signed-release gates
7. Independent comparison and final go/no-go approval

Every blocking item records an acceptance criterion and evidence. A public release is prohibited while any P0 or P1 item is unchecked, waived without written rationale, or supported only by self-referential corpus tests.

## Evidence model

- Benchmarks are created or reviewed by at least three independent native users and contain a locked holdout set.
- Test inputs are not generated solely from the same corpus used by the decoder.
- Results include Top-1/3/5 accuracy, keystrokes per committed Chinese character, correction rate, miscommit rate, out-of-vocabulary rate and latency percentiles.
- Competitor versions, device models, settings, test date and raw results are retained.
- Automated gates run in CI from a clean checkout.
- Manual gates retain screenshots, screen recordings, accessibility results and signed tester approval.

## Release decision

All three modes must pass independently. A strong Pinyin result cannot compensate for a weak Jyutping or Quick result. The final production artifact must be built from the exact reviewed commit and reproduce the recorded test results.
