# Performance results — HKSCS supplement impact

Measured 2026-07-21 against commit `c12eab0` (branch `fix/jyutping-cantonese-ranking`).

## Question

The HKSCS supplement grew the shipped overlay from 0 to 4,606 rows, adding 4,599
Quick entries and 5,467 Jyutping readings to the indices. Does that regress cold
start or memory?

## Method

A/B on the `hk_api36` emulator (Android 16, 1080×2400), debug build with
`PERF_TRACING` on. The two arms differ only in `hkscs_supplement.csv`: the full
4,606-row overlay versus a header-only file. Each run starts from `pm clear`, so
the binary row cache is cold and the CSVs are parsed from scratch.

Cold start is the elapsed time between the `warm_quick_done` and
`corpus_warm_done` PerfTracer marks. Memory is `dumpsys meminfo` Java heap taken
after the keyboard is up and a `RUNNING_CRITICAL` trim has been requested.

## Cold corpus warm-up

| Arm | Run 1 | Run 2 | Run 3 | Median |
|---|---:|---:|---:|---:|
| Without supplement | 4613 ms | 4710 ms | 4534 ms | **4613 ms** |
| With supplement | 4488 ms | 4594 ms | 4626 ms | **4594 ms** |

No regression. The 19 ms median difference is smaller than the spread within
either arm (176 ms and 138 ms respectively), so it is not a measurable effect.

## Memory (Java heap)

| Arm | Run 1 | Run 2 | Run 3 | Median |
|---|---:|---:|---:|---:|
| Without supplement | 85.3 MB | 78.1 MB | 85.2 MB | **85.2 MB** |
| With supplement | 66.8 MB | 66.7 MB | 85.1 MB | **66.8 MB** |

**These numbers do not support a conclusion.** The arm *with* more data measured
*lower*, which cannot be causal; the readings are dominated by GC timing. Within
the "with" arm alone the spread is 18 MB, larger than any plausible cost of
10,066 extra rows. Treat the memory impact as unmeasured, not as zero.

Total PSS was even noisier (137–345 MB across runs) and is not reported.

## Isolated index-build cost (JVM)

Measured directly on the host JVM, median of 5 reps, to separate the data cost
from device noise:

| Work | Without | With | Delta |
|---|---:|---:|---:|
| Quick index build | 20.7 ms | 23.3 ms | +2.6 ms |
| Jyutping index build | 34.2 ms | 28.9 ms | within noise |
| Parse `hkscs_supplement.csv` | — | 6.5 ms | +6.5 ms |

Roughly +10 ms of one-off work on a fast x86 JVM. Not directly comparable to an
ARM device, but consistent with the cold-start result: too small to see.

Note that the supplement is only parsed when `loadChars`/`loadJyutping` miss the
binary cache, and `hkscsUnicodeFallbackIndex` is built lazily — it is touched
only when the user types a `u…` escape. Warm starts pay none of this.

## Not measured

- **Per-keystroke decode latency.** The checklist targets (candidate update p50
  ≤30 ms, p95 ≤75 ms) are unverified. The emulator's IME window would not render
  for automated capture, and the physical device was in use. This gate remains
  open.
- **Sustained-session memory** over 30 minutes.
- **API 26 minimum-supported device** — only API 36 was exercised.
- **Release build.** All numbers are from a debug build with tracing enabled.
