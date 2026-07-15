# Competitor Baseline

**Status:** OPEN — no comparison result has been collected.

Complete this document only with the locked benchmark and identical test
conditions. Do not infer competitor quality from store descriptions.

## Test protocol

- Benchmark hash:
- Test dates:
- Test lead:
- Native reviewers:
- Device model / Android version:
- Display, keyboard height and accessibility settings:
- Network state:
- Personalization reset method:
- Prompt randomization/blinding method:
- Timing and keystroke-counting method:

## Keyboard inventory

| Keyboard | Package | Version | Mode/language settings | Cold/learned | Evidence |
|---|---|---|---|---|---|
| HK Mixed Keyboard | `com.hkmixedkeyboard` | | | | |
| Gboard | | | | | |
| SwiftKey | | | | | |
| Quick specialist | | | | | |
| Jyutping specialist | | | | | |
| Pinyin specialist | | | | | |

## Results

| Mode/keyboard | Cases | Top-1 | Top-3 | Top-5 | MRR | KPC | Corrections/case | Wrong auto-commit | p95 latency | Preference |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Quick / HK Mixed | | | | | | | | | | |
| Quick / best competitor | | | | | | | | | | |
| Jyutping / HK Mixed | | | | | | | | | | |
| Jyutping / best competitor | | | | | | | | | | |
| Pinyin / HK Mixed | | | | | | | | | | |
| Pinyin / best competitor | | | | | | | | | | |

## Required conclusion

- [ ] Every app version and setting is recorded.
- [ ] Cold-state and learned-state results are separated.
- [ ] All keyboards used identical locked prompts and devices.
- [ ] Paired differences include 95% confidence intervals.
- [ ] HK Mixed matches/exceeds the best competitor's Top-3 for every mode.
- [ ] HK Mixed beats Top-1 by at least two percentage points or median KPC by at
  least 5% for every mode.
- [ ] No meaningful correction, latency or task-completion regression exists.
- [ ] At least 60% of blinded native testers prefer HK Mixed for the tested mode.
