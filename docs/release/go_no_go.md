# Market Go/No-Go

Decision: **NO-GO** as of 2026-07-16.

Repository-automatable remediation for three-mode input, phrase-evidence and
tone-aware ranking, corpus provenance, bounded local learning/import, lifecycle
coordination, accessibility surfaces, setup controls and fail-closed CI/release
tooling is implemented. Checkpoint 3 records 888 JVM tests, 74 Python tests plus
63 subtests, 42 instrumentation tests, 30,000 deterministic emulator events and
60 focus-recovery cycles across API 26/35/36. All final automated device runs have
zero app crash, ANR or actionable StrictMode markers. These are self-attested
engineering results, not human sign-off. “Best-in-class” is not an approved
product claim.

The release remains blocked by evidence that software changes cannot manufacture:

- locked independent native-review benchmarks and competitor-relative results for
  速成, 粵拼 and 普通話拼音;
- native Cantonese and Mandarin ranking sign-off;
- physical Samsung, Pixel/AOSP and adaptive-layout matrix results (API 26/35/36
  emulator automation passes but does not replace physical qualification);
- actual selected-system-IME process death/rebind in a host editor with no loss,
  duplication or stale commit of committed/composing text;
- measured performance targets on reference devices;
- assistive-technology user/specialist review;
- legal/data owner approval, final Data Safety and deployment of the current
  privacy policy (the public URL is reachable but stale);
- a signed final AAB, certificate ownership/recovery, screenshots from that AAB;
- the 100-person, two-week closed beta and its stability/preference thresholds.

Change this document to GO only when every linked P0/P1 gate and every P2 gate used
for a market-superiority claim is signed against the same clean commit and AAB hash.

## Machine-checked external evidence gate

`scripts/verify_release_evidence.py` requires six JSON records in
`docs/release/evidence/` (or `HKKBD_RELEASE_EVIDENCE_DIR`): `native_review`,
`device_beta_matrix`, `accessibility_review`, `legal_signoff`, `signed_aab`
and `beta_result`. Every record must be `APPROVED`, dated, attributable and
match the release commit and AAB SHA-256. The directory is intentionally absent
until external reviewers supply those records, so the production verifier fails
closed and this decision remains **NO-GO**.

## 2026-07-16 completion audit

| Original blocker | Classification | Audit evidence |
|---|---|---|
| Unicode-unsafe host deletion | FIXED and JVM-verified | `7ec3caf`; debug/release deletion-policy tests cover BMP, emoji and supplementary-HKSCS fallback. |
| Settings contrast and control semantics | FIXED; targeted API 35 regression verified, but full emulator qualification FAILED | `c340848`; semantic test passed after reboot, while API 35/36 full suites each lost Espresso window focus in three pre-existing UI interactions. |
| Editor-aware layouts and IME actions | FIXED and verified | `05d3088`; debug/release editor-layout policy tests and API 35 keyboard action/accessibility tests passed. |
| Native/competitor quality claims | EXTERNALLY OPEN with fail-closed gate | `f66829d`; header-only templates are `OPEN`; source coverage is non-independent; comparative labels require locked, reviewed, hash-matched evidence. |
| Release-owner, device, accessibility, legal, signed-AAB and beta gates | EXTERNALLY OPEN with fail-closed gate | `a1c727d`; the release verifier stops first at missing `native_review` and requires all six records to match one commit/AAB. |
| Emulator qualification | FAILED | Fresh API 26 instrumentation/smoke passed; API 35 full instrumentation and smoke failed; API 36 full instrumentation failed. Details and APK hash are in `three_mode_release_evidence.md`. |

The audited debug APK hash is
`1176bd9cb4dfc8e829a89fa2355eec3ad051eac9c55080ba54983cfd418f6e71`.
No signed final AAB exists, so no commit/AAB evidence bundle can match a release
artifact. This branch has not been merged into `master`, and merge is not
authorized while any failed or OPEN gate remains.
