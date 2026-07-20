# HK Mixed Keyboard — iOS Development Guide v1.2 Enhanced (Historical Two-Mode Design)

> This document describes an older iOS two-mode design and is retained for
> historical reference. It is not evidence for the current Android three-mode
> Quick / Jyutping / Mandarin Pinyin release candidate.

香港人專用 iOS 中英混合速成 / 粵拼鍵盤（倉頡字根提示）  
Native iOS Custom Keyboard Extension port of the shipped Android app.

**Status:** Planning → Agent-ready after Android-contract accuracy fixes and licensing clearance  
**Supersedes:** `iOS_Development_Guide_v1.1_Enhanced.md` and `iOS_Development_Guide_v1.0.md`  
**Date:** 2026-06-16  
**Primary goal:** reproduce the shipped Android product behaviour feature-for-feature, while respecting iOS Custom Keyboard Extension constraints, App Store Review expectations, memory limits, and corpus licensing obligations.

---

## 0. Read First — What Changed in v1.2

This v1.2 revision keeps the v1.1 architecture, but applies Android-contract accuracy fixes that matter to implementation. These fixes are high priority because coding agents tend to copy concrete Swift models, protocols, schemas, and file lists literally.

Accuracy fixes applied in v1.2:

1. **DecodeResult/DecodeCandidate now mirror Android exactly** — one `candidates` list plus `buffer`, `scheme`, `consumedLen`, `isExactCode`, `isPrefixOnly`, with the three parse states computed (including `cnPrefixParsed = isPrefixOnly || consumedLen < buffer.length`). Candidates carry `isHkCore`, and the `CandidateType`/`SourceSchema` enums match `decoder/DecoderContract.kt` value-for-value (`userMemory` is a source, not a type). No separate stored exact/phrase/prefix arrays, no trimmed enums, no dropped fields.
2. **CorpusLoading protocol expanded** — includes Quick prefix lookup, Jyutping prefix lookup, Jyutping single-character syllable set, and user/custom memory candidate lookup.
3. **Ranking/display pipeline split** — do not collapse Android `Classifier.rankCandidates(...)` and `CandidateDisplayPolicy.order(...)` into one generic ranker.
4. **Key Android files added** — `engine/CandidateDisplayPolicy.kt` and `engine/EnglishLexicon.kt` are load-bearing and must be inspected.
5. **Test requirement clarified** — mirror every file under `android/app/src/test/` 1:1; the named Swift test files are illustrative, not exhaustive.
6. **Live-key verification added** — verify `？！` is actually present in the current layout before porting its long-press alternate.
7. **Jyutping syllable-set derivation corrected** — derive syllables from codes with at least one single-character reading, matching Android `CorpusLoader.jyutpingSyllableSet`; do not rely on a `syllable_count == 1` column.

v1.1 improvements retained:

1. **Clearer execution model for coding agents** — explicit task order, stop conditions, implementation gates, and deliverables.
2. **Stronger iOS platform constraints** — mandatory next-keyboard/globe key, secure field behaviour, phone-pad fallback, drawing bounds, keyboard lifecycle, and Full Access implications.
3. **Corrected Full Access language** — core typing must work with Full Access OFF; haptics are optional and must be empirically verified instead of assumed.
4. **Final iOS bottom-row budgeting** — Android already uses the full 10-unit row, so iOS cannot simply add a globe key without rebalancing widths.
5. **More rigorous memory architecture** — SQLite lazy loader, query budget, cache budget, DB schema, and profiling targets.
6. **Licensing gate elevated to release blocker** — corpus license clearance blocks both Android and iOS store release.
7. **Expanded QA matrix** — Notes, Safari, WhatsApp, Telegram, email fields, URL fields, secure fields, phone fields, orientation, iPad, Full Access OFF/ON.
8. **Agent handoff prompt included** — a coding agent can use this document as a build brief without reinterpreting the product.

---

## 1. Executive Summary

HK Mixed Keyboard is not a new iOS product. It is an iOS port of the already-shipped Android keyboard. The Android app is the behavioural source of truth. The iOS project must therefore be treated as two separate layers:

```text
Product behaviour  = port exactly from Android
Platform mechanics = implement idiomatically for iOS
```

The product logic is platform-agnostic:

- decode contract
- classifier
- commit controller
- candidate ranking
- memory policy
- sensitive field policy
- corpus tables
- layout unit math

There is no native `librime` dependency in the shipped app. Decoding is plain table lookup over bundled corpus data. The iOS port is therefore mostly:

1. Port the pure logic from Kotlin to Swift.
2. Replace the Android in-memory corpus loader with an iOS-safe SQLite lazy loader.
3. Rebuild the keyboard UI as a `UIInputViewController` Custom Keyboard Extension.
4. Build a real container app for onboarding, settings, custom words, import/export, privacy, and licenses.
5. Resolve corpus licensing before store submission.

North star remains unchanged:

```text
用戶打英文唔誤食；
打香港字即刻識；
就算誤食，一個 Backspace 即刻還原。
```

---

## 2. Non-Negotiable Product Rules

The following are product decisions, not platform suggestions. Do not redesign them during the iOS port.

| Rule | Required iOS behaviour |
|---|---|
| Android live code wins | If this guide conflicts with Android code, re-read the Kotlin source and follow the shipped behaviour. |
| v1.5 product guide remains valid | Decode, commit, ranking, privacy, and memory rules remain platform-agnostic. |
| 2-letter Latin + Space | Must stay English literal with trailing space. Do not convert to Chinese. |
| 粵拼 Space behaviour | Space commits the top candidate with **no trailing space**. |
| Conservative commit | Avoid accidental Chinese commit when English intent is plausible. |
| Backspace recovery | A mis-commit must be recoverable by one Backspace where Android supports it. |
| Phrase-first prediction | Preserve phrase-first and post-commit next-character prediction. |
| Sensitive/safe mode | No learning, no personalization, no cross-field leakage. |
| Memory policy | Local only. No network sync. No analytics. |
| Candidate ranking | Preserve Android ranking formula and acceptance thresholds. |

Acceptance targets are unchanged:

```text
Space mis-commit rate       <= 2%
English literal preservation >= 99%
Sensitive learning violation = 0
Core typing with Full Access OFF = required
Network usage                = 0
Analytics                    = 0
```

---

## 3. Sources of Truth

Use this priority order whenever there is disagreement.

### 3.1 Priority 1 — Live Android code

```text
android/app/src/main/kotlin/com/hkmixedkeyboard/
android/app/src/main/assets/corpus/
android/app/src/test/
```

Before porting any component, the coding agent must re-read the relevant Kotlin file. Do not trust prose summaries if the code has evolved.

Key files to inspect before implementation:

```text
engine/Classifier.kt
engine/CandidateDisplayPolicy.kt
engine/EnglishLexicon.kt
decoder/DecodeResult.kt
decoder/CorpusBackedDecoder.kt
decoder/JyutpingSegmenter.kt
commit/CommitController.kt
privacy/SensitiveFieldDetector.kt
memory/RoomUserMemory.kt
ui/KeyboardLayout.kt
ui/SymbolPageView.kt
ui/EmojiPanelView.kt
```

Do not treat this as a closed list. Re-read every Kotlin file touched by the component being ported. In particular:

- `Classifier.rankCandidates(...)` and `CandidateDisplayPolicy.order(...)` are two different stages. Do not collapse them into a single generic ranking function.
- `EnglishLexicon.kt` holds whitelist/canonical-case rules that affect English literal preservation and the deliberate 2-letter Latin behaviour.

### 3.2 Priority 2 — Product guide v1.5

```text
HK_Mixed_Keyboard_app_development_guide_v1.5.md
```

This defines the product contract: decode, commit, ranking, privacy, memory, corpus generation, and acceptance gates.

### 3.3 Priority 3 — This iOS guide

This document governs iOS architecture, constraints, build plan, and App Store readiness. It does not override the product behaviour defined by Android.

---

## 4. iOS Platform Facts the Agent Must Respect

These are iOS platform constraints. They are not optional.

### 4.1 Custom keyboard structure

An iOS keyboard ships as two targets:

```text
HKMixedKeyboard.app             ← container app shown in App Store
└── HKKeyboardExtension.appex   ← actual keyboard extension
```

The container app is not optional. It must provide real user value:

- onboarding
- settings
- custom words
- dictionary import/export
- privacy controls
- license/attribution screen
- troubleshooting

### 4.2 Mandatory next-keyboard/globe key

Every iOS custom keyboard must provide a way to switch to another keyboard. The user expects a globe/next-keyboard key near the system keyboard's usual location.

Implementation requirement:

```swift
advanceToNextInputMode()
```

Recommended behaviour:

- Tap globe key → call `advanceToNextInputMode()`.
- Long press / drag on globe key → support `handleInputModeList(from:with:)` when using a `UIButton`, so the system keyboard list can appear where supported.
- Show the globe key only when appropriate via `needsInputModeSwitchKey`, but budget layout width for it because users commonly enable multiple keyboards.

### 4.3 Secure fields and phone fields

Do not try to outsmart iOS:

- Secure text entry fields normally show the system keyboard instead of third-party keyboards.
- Phone pad / name phone pad fields may also replace the custom keyboard with a system keyboard.
- Banking or high-security apps may reject all custom keyboards.

The keyboard must treat these cases as normal platform behaviour, not bugs.

### 4.4 Drawing bounds

A custom keyboard can draw only inside the primary view of its `UIInputViewController`. It cannot show key artwork above the top edge the way the Apple system keyboard does.

Therefore:

- Do not implement Android-style popups that draw outside keyboard bounds.
- Candidate grid must stay inside the keyboard view or be presented as an in-keyboard overlay.
- Long-press hints must be contained within the keyboard height.

### 4.5 No direct app launching from the keyboard

The keyboard extension must not interrupt the user or launch unrelated flows from inside the keyboard. Complex management should happen in the container app, not in the keyboard surface.

Allowed:

- simple in-keyboard custom-word quick management if required
- opening system Settings only where iOS permits and where user intent is explicit

Not allowed:

- network login from keyboard
- forced permission prompts while typing
- analytics SDKs
- advertising SDKs
- background upload

---

## 5. Full Access Decision

### 5.1 Product position

The strongest privacy position is:

```text
HK Mixed Keyboard works fully with Full Access OFF.
```

Core typing must not require Full Access:

| Capability | Full Access OFF support | Notes |
|---|---:|---|
| Type keys | Yes | Required |
| Decode candidates | Yes | Required |
| Commit Chinese / English | Yes | Required |
| Read bundled corpus DB | Yes | Use extension bundle / own container |
| Extension-local personal memory | Yes | Small local SQLite file in extension container |
| Network | No | Never needed |
| Analytics | No | Never used |
| Shared App Group with container app | No | Requires open access; treat as optional |
| Haptics | Verify on device | Default OFF; degrade silently if unavailable |
| Audio click | Verify on device | Standard input clicks via `UIInputViewAudioFeedback` / `playInputClick()` may work without Full Access; keep optional and test before enabling. |

### 5.2 Corrected haptics rule

Do **not** hard-code the claim that haptics always require Full Access. Instead:

```text
Haptics are optional. They must be tested on real devices under Full Access OFF and ON.
If haptics are unavailable or unreliable with Full Access OFF, disable them silently.
Typing correctness must never depend on haptics.
```

Recommended UX:

- Default haptics OFF.
- Settings text: “Haptic feedback is optional. If unavailable on your iOS version or access setting, the keyboard will continue to work normally.”
- Do not ask the user to enable Full Access just for haptics in v1 unless testing proves a clear benefit and no review risk.

### 5.3 Storage strategy with Full Access OFF

Because Full Access OFF cannot rely on App Group sharing, use this design:

```text
Keyboard extension local container
├── corpus.sqlite             # read-only, bundled or copied read-only
├── memory.sqlite             # small writable personal memory DB
└── settings.json             # extension-local keyboard settings

Container app sandbox
├── onboarding/settings UI
├── import/export UI
└── optional exported dictionary files
```

v1 recommendation:

- Keep essential settings editable inside the keyboard via lightweight controls.
- Keep full settings in the container app.
- If App Group is unavailable, the container app should show instructions and export/import flows rather than assuming direct sync.
- Do not block core typing if settings sync is unavailable.

Optional v1.1+ enhancement:

- App Group sync only when Full Access is enabled.
- Clearly label it as optional.
- Never use network.

---

## 6. Architecture

### 6.1 Recommended stack

```text
Language:        Swift 5.9+
Minimum iOS:     iOS 16.0 recommended; confirm market target before implementation
Core logic:      Swift Package: HKKeyboardCore, no UIKit
Keyboard UI:     UIKit, manual layout, custom views / CALayer / UIButton
Container UI:    SwiftUI
Storage:         SQLite via GRDB.swift or SQLite.swift
Testing:         SwiftPM `swift test` for core; xcodebuild/XCTest for app + extension integration
Profiling:       Instruments, Memory Graph, Time Profiler
Distribution:    TestFlight before App Store
```

Why UIKit for the keyboard:

- lower memory overhead than complex SwiftUI view trees
- predictable touch handling
- easier custom hit areas
- easier candidate bar/grid performance tuning
- better lifecycle control inside `UIInputViewController`

SwiftUI is fine for the container app.

### 6.2 Target layout

```text
ios/
├── HKMixedKeyboard.xcodeproj
├── HKKeyboardCore/
│   ├── Package.swift
│   ├── Sources/HKKeyboardCore/
│   │   ├── Decoder/
│   │   │   ├── DecodeResult.swift
│   │   │   ├── CorpusBackedDecoder.swift
│   │   │   └── JyutpingSegmenter.swift
│   │   ├── Engine/
│   │   │   ├── Classifier.swift
│   │   │   ├── ClassifyResult.swift
│   │   │   ├── CandidateDisplayPolicy.swift
│   │   │   └── EnglishLexicon.swift
│   │   ├── Commit/
│   │   │   ├── CommitController.swift
│   │   │   ├── CommitEvent.swift
│   │   │   └── ImeTypes.swift
│   │   ├── Corpus/
│   │   │   ├── CorpusLoader.swift
│   │   │   ├── SQLiteCorpusLoader.swift
│   │   │   └── CorpusModels.swift
│   │   ├── Memory/
│   │   │   ├── UserMemory.swift
│   │   │   └── SQLiteUserMemory.swift
│   │   ├── Privacy/
│   │   │   └── SensitiveFieldPolicy.swift
│   │   ├── Ranking/
│   │   │   └── RankingNotes.md              # optional docs only; do not replace Android's two-stage ranking/display pipeline
│   │   └── Layout/
│   │       ├── KeyboardLayout.swift
│   │       ├── KeySpec.swift
│   │       └── LayoutMetrics.swift
│   └── Tests/HKKeyboardCoreTests/
│       ├── README.md                        # states: mirror android/app/src/test/ 1:1; names below are illustrative only
│       ├── CommitControllerTests.swift
│       ├── ClassifierTests.swift
│       ├── CandidateDisplayPolicyTests.swift
│       ├── EnglishLexiconTests.swift
│       ├── DecoderContractTests.swift
│       ├── JyutpingSegmenterTests.swift
│       ├── SensitiveFieldPolicyTests.swift
│       ├── KeyboardLayoutTests.swift
│       └── Acceptance/
│           ├── commit_rule_cases.json
│           ├── acceptance_group_A.json
│           ├── acceptance_group_B.json
│           ├── acceptance_group_C.json
│           ├── acceptance_group_D.json
│           └── acceptance_group_E.json
├── Container/
│   ├── HKMixedKeyboardApp.swift
│   ├── Views/
│   │   ├── OnboardingView.swift
│   │   ├── SettingsView.swift
│   │   ├── CustomWordsView.swift
│   │   ├── DictionaryImportExportView.swift
│   │   ├── PrivacyView.swift
│   │   ├── LicensesView.swift
│   │   └── TroubleshootingView.swift
│   └── Resources/
├── Keyboard/
│   ├── KeyboardViewController.swift
│   ├── KeyboardRootView.swift
│   ├── KeyButton.swift
│   ├── CandidateBarView.swift
│   ├── CandidateGridOverlay.swift
│   ├── SymbolPageView.swift
│   ├── EmojiPanelView.swift
│   ├── GlobeKeyButton.swift
│   ├── HapticEngine.swift
│   └── KeyboardStateStore.swift
├── SharedResources/
│   ├── corpus.sqlite
│   └── corpus_manifest.json
├── Scripts/
│   ├── build_corpus_sqlite.py
│   ├── verify_corpus_parity.py
│   ├── export_android_test_cases.py
│   └── ci_xcodebuild.sh
└── docs/
    ├── privacy_policy.md
    ├── licenses.md
    ├── app_store_review_notes.md
    └── qa_matrix.md
```

---

## 7. Core Logic Porting Rules

### 7.1 Pure logic must not import UIKit

`HKKeyboardCore` must compile independently and run unit tests on macOS without an iOS simulator.

Forbidden in `HKKeyboardCore`:

```swift
import UIKit
import SwiftUI
import Combine  // unless strictly justified
```

Allowed:

```swift
import Foundation
import SQLite3 / GRDB
```

### 7.2 Port tests before UI

Phase A is test-first. The Swift port is only correct when it produces identical output to Android for shared buffers.

Required test sources:

```text
android/app/src/test/              # mirror every file 1:1; do not stop at illustrative names
commit_rule_cases.json
acceptance groups A-E
JyutpingSegmenterTest
JyutpingSegmentationCorpusTest
CandidateDisplayPolicy tests
EnglishLexicon tests
```

The current Android suite is large and evolves. At the time this guide was reviewed, it was approximately 29 files / 159 `@Test` methods. The exact count is not the contract; **the contract is to mirror every file under `android/app/src/test/` and keep parity as Android changes.**

Do not begin keyboard UI until the core tests pass.

### 7.3 Do not “improve” typing rules

Several behaviours look odd but are deliberate:

| Behaviour | Why it must stay |
|---|---|
| 2-letter Latin + Space remains English | Prevents accidental Chinese conversion during English typing. |
| 粵拼 Space commits candidate without trailing space | Cantonese phrase input expects direct commit, not English spacing. |
| Conservative punctuation commit | Reduces accidental conversion. |
| One Backspace recovery | Essential trust feature. |
| Safe mode disables memory | Privacy and review risk control. |

### 7.4 Swift modelling guidance

Mirror the Kotlin data contract. Do **not** redesign `DecodeResult` into separate stored exact/phrase/prefix arrays. Android uses one candidate list plus flags, and the three parse states are derived from candidate type and exact/prefix flags. Commit logic depends on that derivation.

Recommended Swift shape:

Mirror the Android enums exactly (`decoder/DecoderContract.kt`). Do not invent a
reduced set, and keep `userMemory` as a **source**, not a candidate type — Android
models custom words as `type = char`, `source = userMemory`.

```swift
// Android: enum class CandidateType { CHAR, PHRASE, MIXED_PHRASE, EN_LITERAL,
//                                      EN_AUTOCOMPLETE, ENGLISH_ASSIST, JYUTPING }
enum CandidateType: String, Equatable, Codable {
    case char
    case phrase
    case mixedPhrase
    case enLiteral
    case enAutocomplete
    case englishAssist
    case jyutping
}

// Android: enum class SourceSchema { QUICK, CANGJIE, MIXED_PHRASE, ENGLISH,
//                                     USER_MEMORY, ENGLISH_ASSIST, JYUTPING }
enum SourceSchema: String, Equatable, Codable {
    case quick
    case cangjie
    case mixedPhrase
    case english
    case userMemory
    case englishAssist
    case jyutping
}

struct DecodeCandidate: Equatable, Codable {
    let text: String
    let code: String
    let sourceSchema: SourceSchema
    let type: CandidateType
    let frequency: Double
    let isHkCore: Bool          // required: Stage-1 ranking orders by isHkCore then frequency
}

struct DecodeResult: Equatable {
    let buffer: String          // required: cnPrefixParsed compares consumedLen to buffer length
    let scheme: Scheme
    let consumedLen: Int
    let isExactCode: Bool
    let isPrefixOnly: Bool
    let candidates: [DecodeCandidate]

    var cnExactParsed: Bool {
        isExactCode && candidates.contains { $0.type == .char }
    }

    var cnHasPhraseMatch: Bool {
        isExactCode && candidates.contains { $0.type == .phrase }
    }

    // Mirrors Android exactly: isPrefixOnly || consumedLen < buffer.length
    var cnPrefixParsed: Bool {
        isPrefixOnly || consumedLen < buffer.count
    }
}
```

Why this matters:

- Jyutping segmentation can deliberately return `isExactCode == true` with a `.phrase` candidate, which must produce `cnHasPhraseMatch == true`.
- `cnPrefixParsed` needs `buffer` (or its length) on the result — without it you cannot reproduce `consumedLen < buffer.length`, and the parse state silently diverges.
- `isHkCore` must live on the candidate; the Stage-1 ranking in §7.5 sorts by it.
- CommitController rules inspect parse state, but parse state is derived from the same candidate list that is displayed and ranked.
- Splitting candidates into separate arrays, dropping fields, or trimming the enums risks losing Android parity and silently changing Space/Enter/punctuation behaviour.

Use protocols for replaceable storage, but make the protocol rich enough for the real decoder:

```swift
protocol CorpusLoading {
    // Quick / Cangjie-derived lookup
    func lookupQuick(code: String, limit: Int) throws -> [DecodeCandidate]
    func lookupQuickPrefix(prefix: String, limit: Int) throws -> [DecodeCandidate]

    // Phrase lookup
    func lookupPhrase(code: String, limit: Int) throws -> [DecodeCandidate]
    func lookupPhrasePrefix(prefix: String, limit: Int) throws -> [DecodeCandidate]

    // Jyutping lookup
    func lookupJyutping(code: String, limit: Int) throws -> [DecodeCandidate]
    func lookupJyutpingPrefix(prefix: String, limit: Int) throws -> [DecodeCandidate]

    // DP segmenter alphabet: codes that have at least one single-character reading
    func jyutpingSingleCharacterSyllableSet() throws -> Set<String>

    // English → Chinese assist
    func lookupEnglishAssist(_ input: String, limit: Int) throws -> [DecodeCandidate]

    // Post-commit prediction
    func lookupNextChar(prefix: String, limit: Int) throws -> [DecodeCandidate]

    // User/custom memory candidates; these must rank before normal corpus candidates where Android does so
    func lookupUserMemory(inputKey: String, limit: Int) throws -> [DecodeCandidate]
}
```

Keep personal memory as a separate writable DB internally, but expose it through the candidate pipeline so Android's `USER_MEMORY` priority can be reproduced.

Keep commit events explicit:

```swift
enum CommitAction: Equatable {
    case insertText(String)
    case deleteBackward(count: Int)
    case clearComposition
    case updateCandidates([DecodeCandidate])
    case restoreComposition(String)
    case none
}
```

### 7.5 Ranking and display ordering

Do not implement a single catch-all `CandidateRanker` that hides Android's two-stage behaviour. Port the two stages explicitly:

```text
Stage 1: Classifier.rankCandidates(...)
         Applies base candidate scoring, including HK-core and frequency ordering.

Stage 2: CandidateDisplayPolicy.order(...)
         Applies display-order rules such as Chinese-first for romanization/phrase-exact paths,
         and English-first where long Latin input indicates English intent.
```

The UI must display the output of the display policy, not merely raw corpus frequency order. This is load-bearing product behaviour for English literal preservation and mixed Chinese/English typing.

---

## 8. Corpus and Memory Architecture

### 8.1 Why the Android loader cannot be copied

The Android implementation loads large CSVs into memory. That is unsafe for an iOS keyboard extension. iOS may kill extensions with high memory footprints; the exact ceiling is device- and OS-dependent and should be treated as a low budget.

Do not port this approach:

```text
CSV → full Swift Dictionary / HashMap → always resident
```

Required iOS approach:

```text
CSV source files → build-time corpus.sqlite → indexed lazy queries per keystroke
```

### 8.2 Corpus DB schema

Prebuild `corpus.sqlite` from the same CSV files used by Android.

```sql
CREATE TABLE metadata (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE quick (
    code TEXT NOT NULL,
    text TEXT NOT NULL,
    freq REAL NOT NULL DEFAULT 0,
    hk_core INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_quick_code ON quick(code);
CREATE INDEX idx_quick_text ON quick(text);

CREATE TABLE phrase (
    code TEXT NOT NULL,
    text TEXT NOT NULL,
    freq REAL NOT NULL DEFAULT 0
);
CREATE INDEX idx_phrase_code ON phrase(code);

CREATE TABLE jyutping (
    code TEXT NOT NULL,
    text TEXT NOT NULL,
    freq REAL NOT NULL DEFAULT 0
);
CREATE INDEX idx_jyutping_code ON jyutping(code);
CREATE INDEX idx_jyutping_text ON jyutping(text);

-- Derived exactly like Android CorpusLoader.jyutpingSyllableSet:
-- every Jyutping code with at least one single-character reading.
CREATE TABLE jyutping_syllable (
    code TEXT PRIMARY KEY
);

CREATE TABLE english_assist (
    en TEXT NOT NULL,
    zh TEXT NOT NULL,
    freq REAL NOT NULL DEFAULT 0
);
CREATE INDEX idx_english_assist_en ON english_assist(en);

CREATE TABLE next_char (
    prefix TEXT NOT NULL,
    ch TEXT NOT NULL,
    freq REAL NOT NULL DEFAULT 0
);
CREATE INDEX idx_next_char_prefix ON next_char(prefix);
```

Build-script rule for `jyutping_syllable`:

```text
INSERT DISTINCT code
FROM jyutping
WHERE text is exactly one user-visible Chinese character / grapheme cluster.
```

Do not derive the DP syllable alphabet from a `syllable_count == 1` column. The Android production rule is “codes that have at least one single-character reading.” These mostly coincide with one-syllable readings, but they are not the same contract.

### 8.3 Prefix query rule

For phrase prefix search, avoid unindexed `LIKE` where possible. Use a lexicographic range query:

```sql
SELECT text, freq
FROM phrase
WHERE code >= ? AND code < ?
ORDER BY freq DESC
LIMIT ?;
```

Where:

```text
lower = prefix
upper = prefix + U+10FFFF sentinel or another safe upper-bound strategy
```

If using the common `prefix || '￿'` approach, verify it works with the actual SQLite collation and corpus character set.

### 8.4 Hot memory sets

Allowed resident memory:

```text
English whitelist              small, loaded once
Mixed phrase overrides          small, loaded once
HK-core char flags              compact Set/String table only if needed
Jyutping single-character syllable set  derived from codes with at least one single-character reading; needed by DP segmenter
LRU query cache                 bounded
Current composition state        tiny
```

Do not keep all candidate tables resident.

### 8.5 Query cache budget

Recommended default:

```text
LRU query cache entries: 256–512
Max candidates per query: 50 raw, then rank/trim
Candidate display limit: 8–10 in bar, more in grid
Cache invalidation: on corpus version change
```

### 8.6 Performance targets

```text
First candidate p95 latency: < 50 ms on real device
First candidate p99 latency: < 100 ms on real device
Keyboard touch-to-visual feedback: immediate on touchesBegan
Extension memory target: stay comfortably below observed kill threshold
Cold DB open: acceptable only on first keyboard launch; avoid blocking first touch
```

### 8.7 Personal memory DB

Keep separate from read-only corpus:

```sql
CREATE TABLE user_memory (
    input_key TEXT NOT NULL,
    committed_text TEXT NOT NULL,
    count INTEGER NOT NULL DEFAULT 1,
    confidence REAL NOT NULL DEFAULT 0,
    last_used_at INTEGER NOT NULL,
    PRIMARY KEY (input_key, committed_text)
);

CREATE INDEX idx_user_memory_input ON user_memory(input_key);
CREATE INDEX idx_user_memory_last_used ON user_memory(last_used_at);
```

Sensitive/safe fields must not write to this DB.

Reproduce Android's learning/override thresholds, not just the schema. Personal
memory becomes a hard override only at **count ≥ 3 and confidence ≥ 0.8** (see v1.5
§17 and `memory/RoomUserMemory.kt` — re-read for the exact formula and the `cnRatio`
tie-break the commit controller uses). Do not invent your own thresholds.

---

## 9. Keyboard Lifecycle and State Management

### 9.1 Required lifecycle hooks

`KeyboardViewController` must reset or refresh state in these methods:

```swift
override func viewDidLoad()
override func viewWillAppear(_ animated: Bool)
override func viewDidAppear(_ animated: Bool)
override func viewWillDisappear(_ animated: Bool)
override func textWillChange(_ textInput: UITextInput?)
override func textDidChange(_ textInput: UITextInput?)
```

### 9.2 State reset rules

Reset composition state when:

- active text field changes
- app changes
- keyboard view disappears
- `textWillChange` indicates a new document context
- safe/sensitive field policy changes
- user manually switches input scheme
- Full Access status changes while keyboard is running

Clear:

```text
composition buffer
candidate list
candidate grid overlay
long-press timers
backspace repeat timers
post-commit prediction chain
pending restore state if field changed
```

### 9.3 Text proxy limitations

`UITextDocumentProxy` provides limited context. Do not assume full document access.

Use cautiously:

```swift
textDocumentProxy.documentContextBeforeInput
textDocumentProxy.documentContextAfterInput
textDocumentProxy.keyboardType
textDocumentProxy.returnKeyType
textDocumentProxy.autocapitalizationType
textDocumentProxy.autocorrectionType
```

Do not build features requiring full text history.

---

## 10. Sensitive Field and Safe Mode

### 10.1 iOS signals

Available signals are weaker than Android `EditorInfo`, but still useful:

```swift
textDocumentProxy.keyboardType
textDocumentProxy.autocorrectionType
textDocumentProxy.documentContextBeforeInput
```

Treat these keyboard types as no-learning contexts:

```text
.emailAddress
.URL
.numberPad
.phonePad
.namePhonePad
.asciiCapableNumberPad
.decimalPad
.twitter / webSearch if behaviour suggests non-natural typing
```

### 10.2 Safe mode behaviour

When safe mode is active:

```text
No personalization write
No next-character prediction
No cross-field restore
No memory learning
No export of context
Composition clears on field transition
Candidate generation still works from bundled corpus if appropriate
```

### 10.3 Secure text fields

Usually iOS replaces the custom keyboard with the system keyboard in secure fields. Still implement defensive policy in case some host app behaves unexpectedly.

---

## 11. UI Specification

### 11.1 Overall keyboard height

Use a fixed keyboard height constraint with device-specific tuning.

Initial targets:

```text
iPhone portrait: 260–300 pt depending on candidate row
iPhone landscape: compact mode required
iPad floating/split: verify separately
iPad full width: use wider candidate layout
```

Do not rely on Android pixel dimensions. Port the unit-grid math, not the raw pixels.

### 11.2 Input surfaces

Keyboard must support:

```text
速成 / Quick
倉頡 root hints / code display where applicable, not a separate selectable mode unless Android later adds one
粵拼 / Jyutping
English literal preservation
Symbols
Emoji panel
Candidate bar
Candidate grid
```

### 11.3 Main keyboard layout

Port `KeyboardLayout.kt` unit math into `HKKeyboardCore/Layout`.

Requirements:

- 10-unit grid baseline.
- Half-key stagger where Android uses it.
- Key hit areas must be at least as generous as Android.
- Visual labels can differ slightly if required by iOS typography.
- Touch should emit visual feedback on `touchesBegan`, not only `touchUpInside`.

### 11.4 iOS bottom row — final width budget required

The Android bottom row already totals 10 units:

```text
符(1.5) 😊(1.0) ⌨(1.0) [space](3.0) 。(1.0) ，(1.0) ↵(1.5) = 10.0
```

iOS must add a globe/next-keyboard affordance. Therefore, do **not** simply add it. Rebalance the row.

Recommended iOS v1 bottom row:

```text
🌐(0.8) 符(1.2) 😊(0.9) ⌨(0.9) [space](2.8) 。(0.8) ，(0.8) ↵(1.8)
Total = 10.0
```

Alternative if Enter feels too wide:

```text
🌐(0.8) 符(1.2) 😊(0.9) ⌨(0.9) [space](3.0) 。(0.8) ，(0.8) ↵(1.6)
Total = 10.0
```

Implementation notes:

- `🌐` must call next-keyboard behaviour.
- `⌨` remains the HK Mixed Keyboard internal scheme switch, e.g. 速成 ↔ 粵拼.
- `符` opens symbol page.
- Long press on `符` can open emoji, but the dedicated `😊` key remains.
- Punctuation keys remain direct commit keys.
- Test hit areas on iPhone mini/SE width if those devices are supported.
- The `🌐(0.8)` width above is a starting point, not a target. 0.8 units (~8% width)
  is a borderline touch target, and the row carries six non-space keys plus a 2.8-unit
  space. Treat globe width ≥ ~1.0 unit as the goal and verify every key meets the
  minimum hit area on the narrowest supported device before locking the budget.

### 11.5 Candidate bar

Equivalent of Android `CandidateBarView`:

```text
[ EN literal pill ] [ Candidate 1 ] [ Candidate 2 ] ... [ ▾ ]
```

Use `UICollectionView` horizontal layout or a custom lightweight scroll view.

Requirements:

- candidate updates must be fast and non-janky
- top candidate clear and tappable
- English literal option visible when relevant
- more/grid button opens in-keyboard overlay
- clear on field change
- safe mode disables post-commit prediction

### 11.6 Candidate grid overlay

Equivalent of Android `CandidateGridView`.

Requirements:

- stays inside keyboard bounds
- scrollable if many candidates
- dismissible by tapping close / selecting candidate / switching layout
- should not cover globe key permanently
- should not require a modal outside the extension

### 11.7 Symbol page

The current Android decision is:

```text
single page
40 symbols
4 rows x 10 columns
CJK punctuation first
bottom function row remains available
no scrolling
no #+= toggle
```

Port this exactly unless Android code changes.

Required bottom function row on symbol page:

```text
返回 / 空格 / ⌫ / ↵
```

Reason: users must be able to delete, add space, and press Enter without returning to the main keyboard.

### 11.8 Emoji panel

Equivalent of Android `EmojiPanelView`:

```text
Telegram-style 9-column grid
category tabs
ABC return key
⌫ delete key
```

Notes:

- iOS already has system emoji keyboard, but the dedicated `😊` key is part of the Android product and should be preserved.
- Keep memory footprint low. Do not load huge emoji metadata files unless needed.
- Avoid network emoji/GIF/sticker features.

### 11.9 Long press and repeat

Port Android `HoldActionController` behaviour, but first verify that each key is live in the current `KeyboardLayout.kt`:

```text
backspace hold → repeat delete timer
符 long press → emoji panel
？！ long press → alternate punctuation only if the key is actually present in the current rendered layout
```

Implementation warning: `KEY_QUESTION = "？！"` may exist in code paths while not being placed in any current row. Do not build UI for a vestigial key unless the live layout renders it.

Implementation requirements:

- cancel timers on touch end/cancel
- cancel timers on view disappear
- cancel timers on field change
- no stuck repeat bug

---

## 12. Container App Specification

The container app must be useful by itself. It is also the main place for explanations that should not interrupt typing.

### 12.1 Required screens

```text
OnboardingView
SettingsView
CustomWordsView
DictionaryImportExportView
PrivacyView
LicensesView
TroubleshootingView
AboutView
```

### 12.2 Onboarding

Include step-by-step setup:

```text
1. Open iPhone Settings
2. General
3. Keyboard
4. Keyboards
5. Add New Keyboard
6. Select HK Mixed Keyboard
7. Optional: Allow Full Access explanation
```

Important copy:

```text
Full Access is optional. HK Mixed Keyboard can type and show candidates without Full Access.
We do not use network, analytics, or cloud sync.
```

### 12.3 Settings

Mirror Android settings where applicable:

```text
Default input mode: 速成 / 粵拼
Space behaviour: smart / always literal where Android supports it
Show Cangjie roots / key hints: on/off
Candidate grid: compact / expanded
Theme: system / light / dark
Haptics: off by default, availability checked
Clear personal data
Export dictionary
Import dictionary
```

If settings cannot sync to extension without Full Access, explain clearly and provide a manual import/export path.

### 12.4 Custom words

Minimum v1:

```text
Add custom word
Delete custom word
Search custom words
Export custom words
Import custom words
Clear all custom words
```

Optional v1.1:

```text
Bulk import CSV
Conflict resolution
Usage count display
```

### 12.5 Privacy screen

Must state plainly:

```text
No network
No analytics
No account
No cloud sync
No keystroke upload
Personal learning stays on device
Full Access not required for core typing
Safe fields disable learning
```

### 12.6 Licenses screen

Mandatory before release.

Must include:

```text
source name
source URL
license
attribution text
derived file list
share-alike obligations
where derived data is published, if required
```

---

## 13. Licensing Gate — Release Blocker

This is the highest release risk.

The existing corpus appears to include third-party-derived data. Before any App Store or Play Store release, the project must verify and resolve all license obligations.

Known risk table:

| File | Declared / likely source | License risk | Required action |
|---|---|---|---|
| `hk_core_chars.csv` | RIME `cangjie5` | GPL conflict risk | Replace, obtain rights, or comply with GPL. |
| `hk_core_phrases.csv` | rime-essay + Cangjie5 lineage | GPL / derivative risk | Verify lineage and obligations. |
| `english_assist.csv` | CC-CEDICT | CC-BY-SA | Attribution + share-alike compliance. |
| `jyutping.csv` | rime-cantonese | CC-BY / ODbL risk | Attribution + ODbL/share-alike compliance if applicable. |

### 13.1 Required before store release

```text
[ ] Update corpus_license_register.csv to include every shipped corpus file.
[ ] Confirm exact license for each source.
[ ] Confirm whether generated code tables are derivative works.
[ ] Resolve GPL conflict for Cangjie/Quick data.
[ ] Add attribution screen to Android and iOS.
[ ] Publish derived data if required by share-alike terms.
[ ] Keep license texts in repository.
[ ] Add release checklist item blocking store submission until complete.
```

### 13.2 Engineering may proceed, but release may not

Allowed before licensing is solved:

```text
Core Swift port
XCTest parity tests
SQLite loader
Internal TestFlight with non-public/legal-review build
UI implementation
```

Not allowed before licensing is solved:

```text
Public App Store release
Public Play Store release
Marketing claim that product is legally clear
Removing attribution obligations silently
```

---

## 14. App Store Review Readiness

### 14.1 Keyboard-specific requirements

```text
[ ] Keyboard works with Full Access OFF.
[ ] Globe / next-keyboard key exists and works.
[ ] No network usage from keyboard.
[ ] No analytics SDK.
[ ] No keystroke collection off-device.
[ ] Secure fields and phone fields behave normally.
[ ] Container app has real functionality.
[ ] Privacy policy URL works.
[ ] License screen is complete.
[ ] Review notes explain Full Access is optional.
```

### 14.2 Privacy nutrition label

Target label if implementation follows this guide:

```text
Data Not Collected
```

This is only true if:

- no analytics SDK
- no crash SDK sending user data
- no network calls
- no account system
- no server-side logging
- no third-party ad SDK
- no telemetry from keyboard

If any crash reporting is added, reassess the privacy label.

### 14.3 App Review notes draft

Use this in App Store Connect review notes:

```text
HK Mixed Keyboard is a Cantonese/Hong Kong Chinese input keyboard.
The keyboard works without Allow Full Access. Full Access is optional and not required for typing, candidate generation, or Chinese/English input.
The keyboard does not use network access, analytics, advertising SDKs, accounts, or cloud sync.
All dictionary data is bundled on device. Personal learning, if enabled, remains local on device and is disabled in sensitive contexts.
The globe key is provided for switching to the next keyboard.
The container app provides onboarding, settings, custom word management, import/export, privacy information, and license attribution.
```

---

## 15. Implementation Plan

Do the work in phases. Do not skip gates.

### Phase 0 — Licensing and source audit

Owner: product/legal/engineering lead

Deliverables:

```text
corpus_license_register.csv updated
license risk decision written
replacement corpus plan if needed
attribution text drafted
```

Gate 0:

```text
[ ] All corpus files listed.
[ ] Unknown licenses marked BLOCKER.
[ ] Release decision documented.
```

Engineering can continue after Gate 0 risk is visible, but public release remains blocked until fully resolved.

### Phase A — Core logic port, no UI

Deliverables:

```text
HKKeyboardCore Swift Package
Ported Decoder
Ported Classifier
Ported CommitController
Ported JyutpingSegmenter
Ported ranking logic
Ported privacy policy logic
Ported KeyboardLayout unit math
Full XCTest suite mirroring Android tests
```

Gate A:

```text
[ ] All ported unit tests pass.
[ ] commit_rule_cases.json passes.
[ ] Acceptance groups A-E pass.
[ ] Mis-commit <= 2%.
[ ] EN literal preservation >= 99%.
[ ] Sensitive learning violation = 0.
[ ] No UIKit import in HKKeyboardCore.
```

### Phase B — SQLite corpus loader

Deliverables:

```text
build_corpus_sqlite.py
corpus.sqlite
SQLiteCorpusLoader.swift
Corpus parity tests
Latency benchmark tests
Memory profiling notes
```

Gate B:

```text
[ ] SQLite loader output matches Android/reference output.
[ ] First candidate p95 < 50 ms on real device.
[ ] Memory remains comfortably below observed kill threshold.
[ ] DB open does not block repeated typing.
[ ] Query cache bounded.
```

### Phase C — Keyboard extension UI

Deliverables:

```text
KeyboardViewController
KeyboardRootView
Main key grid
Candidate bar
Candidate grid overlay
Symbol page
Emoji panel
Globe key
Scheme switch key
Backspace repeat
Long press actions
Safe mode integration
```

Gate C:

```text
[ ] Keyboard can be enabled in iOS Settings.
[ ] Works in Notes.
[ ] Works in Safari search/address fields with appropriate safe behaviour.
[ ] Works in WhatsApp/Telegram.
[ ] Works with Full Access OFF.
[ ] Globe key switches keyboard.
[ ] Field change clears candidate/composition state.
[ ] Secure fields / phone fields fall back normally.
[ ] No overlay draws outside keyboard bounds.
[ ] No stuck timers.
```

### Phase D — Container app

Deliverables:

```text
Onboarding
Settings
Custom words
Import/export
Privacy
Licenses
Troubleshooting
App icon / screenshots
```

Gate D:

```text
[ ] Container app has meaningful user value.
[ ] Privacy explanations are clear.
[ ] Full Access explanation is accurate.
[ ] Licenses screen complete or marked blocker.
[ ] Clear personal data works.
```

### Phase E — Store readiness

Deliverables:

```text
Privacy policy URL
App Store screenshots
Review notes
TestFlight build
QA matrix results
License clearance proof
```

Gate E:

```text
[ ] License blocker resolved.
[ ] App privacy label accurate.
[ ] TestFlight real-device testing complete.
[ ] Review notes added.
[ ] No private API.
[ ] No network/analytics.
[ ] Submit to App Review.
```

---

## 16. QA Matrix

### 16.1 Device matrix

Minimum recommended:

```text
iPhone SE / narrow-width device
iPhone standard size
iPhone Pro Max / large screen
iPad portrait
iPad landscape
```

### 16.2 iOS version matrix

```text
iOS 16.x if minimum supported
iOS 17.x
iOS 18.x / current shipping version
iOS 26 beta/current only if project is actively targeting it
```

### 16.3 App matrix

```text
Apple Notes
Messages
Safari address/search field
Mail To field
Mail body
WhatsApp
Telegram
Signal if available
Google Docs / Microsoft Word if available
Password field in Safari
Phone number field
Banking app or app that rejects custom keyboards, if available
```

### 16.4 Behaviour matrix

```text
Full Access OFF
Full Access ON if supported
Light mode
Dark mode
Portrait
Landscape
External hardware keyboard connected
Switching between HK Mixed Keyboard and system keyboard
Switching between 速成 and 粵拼
Entering English sentence
Entering mixed English + Chinese
Entering punctuation-heavy Cantonese text
Backspace recovery after candidate commit
Long-press backspace repeat
Symbol page function row
Emoji panel delete and return
```

### 16.5 Regression phrases

Keep a regression list from Android QA. Include at least:

```text
2-letter English + Space
neihou → 你好
kaukei /求其 style Jyutping phrase behaviour
CJK punctuation: 。 ， 、 ！ ？ ； ： … 「 」
English literal preservation in URL/email fields
Post-commit next-character prediction
Backspace restore after accidental commit
```

---

## 17. CI and Build Commands

### 17.1 Core tests

```bash
cd ios/HKKeyboardCore
swift test
```

### 17.2 App and extension integration tests

Run core tests with `swift test` as above. Use `xcodebuild` for container-app and keyboard-extension integration tests, UI smoke tests, and simulator builds:

```bash
xcodebuild \
  -project ios/HKMixedKeyboard.xcodeproj \
  -scheme HKMixedKeyboard \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  test
```

### 17.3 Build corpus

```bash
python3 ios/Scripts/build_corpus_sqlite.py \
  --input android/app/src/main/assets/corpus \
  --output ios/SharedResources/corpus.sqlite \
  --manifest ios/SharedResources/corpus_manifest.json
```

### 17.4 Verify parity

```bash
python3 ios/Scripts/verify_corpus_parity.py \
  --android-fixtures android/app/src/test/resources \
  --sqlite ios/SharedResources/corpus.sqlite
```

### 17.5 Archive for TestFlight

```bash
xcodebuild \
  -project ios/HKMixedKeyboard.xcodeproj \
  -scheme HKMixedKeyboard \
  -configuration Release \
  -archivePath build/HKMixedKeyboard.xcarchive \
  archive
```

---

## 18. Risk Register

| Risk | Severity | Likelihood | Mitigation |
|---|---:|---:|---|
| Corpus license conflict blocks release | Critical | High | Run license clearance before public release; replace corpus if needed. |
| iOS memory kill | High | Medium | SQLite lazy loader, bounded cache, Instruments profiling. |
| Agent changes product rules | High | Medium | Test-first port, Android source of truth, acceptance gates. |
| Globe key omitted or too small | High | Low-Medium | Mandatory bottom-row budget and QA. |
| Full Access accidentally required | High | Medium | Test Full Access OFF in Gate C. |
| Candidate UI draws outside bounds | Medium | Medium | In-keyboard overlay only. |
| Haptics cause privacy/access confusion | Medium | Medium | Default off, verify on device, degrade silently. |
| Container app considered too thin | Medium | Low-Medium | Add onboarding/settings/custom words/import/export/privacy/licenses. |
| Safe mode leakage | High | Low-Medium | Unit tests + field-change reset tests. |
| Slow first candidate | Medium | Medium | Indexed queries, warm DB, LRU cache. |

---

## 19. Definition of Done

The iOS port is not done when it “looks like a keyboard.” It is done only when all of the following pass:

```text
[ ] Android behavioural parity tests pass.
[ ] Core typing works with Full Access OFF.
[ ] Globe key works.
[ ] SQLite corpus loader meets latency and memory targets.
[ ] Sensitive field safe mode has zero learning violations.
[ ] Symbol page retains function row.
[ ] Emoji panel works without network.
[ ] Container app provides real value.
[ ] Privacy policy is live.
[ ] License screen is complete.
[ ] Corpus licensing blocker is resolved.
[ ] TestFlight QA matrix is complete.
[ ] App Store review notes are prepared.
```

---

## 20. Coding Agent Handoff Prompt

Use this prompt when assigning the work to a coding agent.

```text
You are building the native iOS port of HK Mixed Keyboard.

Your job is not to redesign the product. Your job is to reproduce the shipped Android app behaviour feature-for-feature as a native iOS Custom Keyboard Extension.

Read these files first:
1. HK_Mixed_Keyboard_iOS_Development_Guide_v1.2_Enhanced.md (this document)
2. HK_Mixed_Keyboard_app_development_guide_v1.5.md
3. Android source under android/app/src/main/kotlin/com/hkmixedkeyboard/
4. Android corpus under android/app/src/main/assets/corpus/
5. Android tests under android/app/src/test/

Source of truth priority:
1. Live Android code
2. Product guide v1.5
3. iOS guide v1.2

Start with Phase A only:
- Create HKKeyboardCore Swift Package.
- Port the Android core logic to Swift.
- Port the full Android JUnit suite to XCTest.
- Do not build UI yet.
- Do not improve product rules.
- Do not use UIKit inside HKKeyboardCore.
- Make commit_rule_cases.json and acceptance groups A-E pass.

Hard constraints:
- Core typing must work with Full Access OFF.
- No network.
- No analytics.
- Do not port the Android HashMap corpus loader to iOS.
- Use SQLite lazy queries for corpus data.
- iOS keyboard must include a globe/next-keyboard key.
- Do not draw UI outside the keyboard primary view.
- Do not submit to App Store until corpus licensing is resolved.

Stop and ask only if:
- Android code and product guide conflict in a way you cannot resolve.
- A product rule is ambiguous in both documents.
- Licensing blocks the data source decision.

Deliverable for first task:
- A short implementation plan.
- HKKeyboardCore package skeleton.
- Ported tests list.
- First passing subset of XCTest.
- Clear TODO list for remaining Android parity tests.
```

---

## 21. Recommended First Three Tasks

### Task 1 — License audit branch

```text
Branch: ios/license-audit
Goal: identify whether current corpus can legally ship.
Output: corpus_license_register.csv + docs/licenses.md + release blocker decision.
```

### Task 2 — Core parity branch

```text
Branch: ios/core-port-phase-a
Goal: port pure logic and tests only.
Output: HKKeyboardCore + XCTest parity suite.
```

### Task 3 — Corpus SQLite branch

```text
Branch: ios/sqlite-corpus-phase-b
Goal: generate corpus.sqlite and implement lazy loader.
Output: build script + SQLiteCorpusLoader + parity and latency tests.
```

Do not start keyboard UI until Task 2 has a meaningful passing test base and Task 3 proves the memory strategy.

---

## 22. Apple Reference Notes for Developers

The implementation team should keep these Apple references open during development:

```text
App Store Review Guidelines
https://developer.apple.com/app-store/review/guidelines/

App Extension Programming Guide — Custom Keyboard
https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html

Creating a custom keyboard
https://developer.apple.com/documentation/uikit/creating-a-custom-keyboard

Configuring a custom keyboard interface
https://developer.apple.com/documentation/uikit/configuring-a-custom-keyboard-interface

Configuring open access for a custom keyboard
https://developer.apple.com/documentation/uikit/configuring-open-access-for-a-custom-keyboard

Handling text interactions in custom keyboards
https://developer.apple.com/documentation/uikit/handling-text-interactions-in-custom-keyboards
```

---

## 23. Final Recommendation

Proceed in this order:

```text
1. License audit
2. Core Swift port + XCTest
3. SQLite corpus loader
4. Keyboard UI
5. Container app
6. TestFlight QA
7. Store submission only after licensing is clear
```

The safest development strategy is to avoid UI-first development. A keyboard can look correct but still destroy user trust if commit behaviour differs from Android. Start with the core logic and tests, then build the iOS UI on top of a proven engine.
