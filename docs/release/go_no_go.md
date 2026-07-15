# Market Go/No-Go

Decision: **NO-GO** as of 2026-07-15.

Repository-automatable remediation for three-mode input, phrase-evidence and
tone-aware ranking, corpus provenance, bounded local learning/import, lifecycle
coordination, accessibility surfaces, setup controls and fail-closed CI/release
tooling is implemented. Checkpoint 3 records 882 JVM tests, 74 Python tests plus
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
- measured performance targets on reference devices;
- assistive-technology user/specialist review;
- legal/data owner approval, final Data Safety and deployment of the current
  privacy policy (the public URL is reachable but stale);
- a signed final AAB, certificate ownership/recovery, screenshots from that AAB;
- the 100-person, two-week closed beta and its stability/preference thresholds.

Change this document to GO only when every linked P0/P1 gate and every P2 gate used
for a market-superiority claim is signed against the same clean commit and AAB hash.
