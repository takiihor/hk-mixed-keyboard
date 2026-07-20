# Performance Qualification

Status: API 26/35/36 emulator warm-decode ceilings pass; required physical-device
measurements are OPEN.

Checkpoint 3 ran 300 warm full-corpus decodes per emulator execution: 100 each
for Quick `rryo`, Jyutping `nei5hou2` and Pinyin `ni3hao3`. That is 900 decodes
across API 26, 35 and 36. Every emulator passed the instrumentation p95 ceiling
of 150 ms. These assertions are regression guards, not the reference-device
market performance gate.

## Required devices and targets

| Metric | Reference mid-range target | API 26 target | Recorded result |
|---|---:|---:|---|
| Key visual/haptic acknowledgement p95 | ≤50 ms | ≤50 ms | OPEN |
| Candidate update p50 / p95 / p99 | ≤30 / 75 / 150 ms | p95 ≤120 ms | OPEN |
| Warm / cold keyboard presentation p95 | ≤300 / 700 ms | same | OPEN |
| Longest ordinary-typing frame | ≤32 ms | ≤32 ms | OPEN |
| 30-minute memory growth after close | near baseline | near baseline | OPEN |

Use a cold process and unwarmed corpus for cold runs. Capture at least 2,000 key
samples per mode with the full corpora and personalization loaded, preserve raw
`LatencyLogger` snapshots, Perfetto/gfxinfo traces and meminfo output, and record
device model, build fingerprint, thermal state and exact Git commit. Benchmark-only
shortcuts are prohibited. No target may be checked without attached raw evidence.
