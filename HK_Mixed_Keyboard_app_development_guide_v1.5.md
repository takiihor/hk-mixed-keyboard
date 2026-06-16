# HK Mixed Keyboard — App Development Guide v1.5

香港人專用 Android 中英混合速成 / 倉頡鍵盤
Stage 1 Technical Feasibility + Stage 2 Product Build Guide
版本：v1.5
狀態：可開始 Stage 1 development，不可直接跳到完整 Product Build

---

## 0. Executive Decision

本項目可以開始開發，但第一階段不是完整 keyboard app，而是 **Stage 1 Technical Feasibility Spike**。

開發順序必須是：

```text id="q79tpz"
Prototype 0 — Decode Contract Harness
Prototype 1 — Commit Rule Harness
Prototype 2 — Android IME Thin UI
```

只有當三個 Gate 全部通過，才進入完整 App Build。

```text id="hihsrl"
Gate 1：librime wrapper 能穩定提供三態 parse
Gate 2：commit rule 在測試集 mis-commit rate ≤ 2%
Gate 3：Safe Keyboard Mode 零 learning / 零 leakage
```

禁止一開始就做：

```text id="mb3esq"
完整 keyboard app
UI 美化
emoji 完整分類
主題系統
大型詞庫整合
雲端同步
STT
AI 改寫
iOS 版
上架準備
```

北極星：

```text id="kkekdf"
用戶打英文唔誤食；
打香港字即刻識；
就算誤食，一個 Backspace 即刻還原。
```

---

## 1. Product Definition

HK Mixed Keyboard 是一個香港人專用 Android 中英混合速成 / 倉頡鍵盤。

產品核心：

```text id="luunwd"
一個主鍵盤同時處理：
- 英文
- 速成
- 倉頡
- 香港口語詞
- 中文組詞
- 中英 mixed phrase
- 數字
- 常用標點
- emoji
- 符號頁
```

用戶不需要手動切換中英文。
Engine 內部同時處理 English / Quick / Cangjie interpretation，再由 parse-validity、ranking、commit rule、phrase-first、user memory 決定候選和 commit 行為。

目標輸入體驗：

```text id="crj7el"
我今日meeting完再call你
send個file俾我
confirm咗再話我知
我嘅意思係咁
你哋今晚幾點到
唔該幫我check下
MTR Exit B等
FPS過數俾你
```

---

## 2. Target Users

主要用戶：

```text id="p8oy04"
香港 Android 用戶
速成用戶
倉頡用戶
經常中英混合打字的人
經常 WhatsApp / Telegram / Signal / Instagram DM 的人
工作中常用中文 + 英文 terms 的人
不滿 Google Keyboard 速成體驗的人
不滿 Mixed Chinese Keyboard 字庫不足的人
```

核心痛點：

```text id="n2xrmz"
1. Google keyboard 打速成不好用
2. Mixed Chinese Keyboard 方向好，但字庫和香港口語不足
3. 現有 keyboard 中英切換麻煩
4. 香港口語字例如 嘅 / 啲 / 咗 / 喺 / 唔 / 冇 很難排前
5. 英文 autocomplete 和中文組詞不能自然共存
6. 數字和常用標點常常要切頁
```

---

## 3. Core Product Principles

### 3.1 No Manual Chinese / English Switching

用戶不應該需要按「中 / 英」切換鍵。

但這不代表 engine 沒有內部狀態。
正確設計是：

```text id="itkeee"
用戶層：無需手動切中英
Engine 層：同時保留 English / Quick / Cangjie interpretations
Commit 層：用 parse-validity + ranking + memory + conservative fallback 決定
```

---

### 3.2 Parse-validity Over Length

不要用簡單規則：

```text id="g9zjv0"
1–2 letters = Chinese
3+ letters = English
```

這是錯的。

正確原則：

```text id="c29gmn"
主信號 = parse-validity
length 只在 collision tie-break 時作最後參考
```

原因：

```text id="i2w4jk"
hap 可以是 English prefix
hap 也可以是 倉頡 sequence

send 可以是 English word
send 也可以是 shape code

Quick phrase 可能是 4 keystrokes
Cangjie character 也可能是 4–5 keystrokes
```

---

### 3.3 Conservative Auto-commit

Auto-commit 必須保守。

```text id="ep3jcd"
如果不確定，保留 EN literal
不要自作聰明猜中文
不要靜靜消滅用戶手打英文
```

Mis-commit 比少打一個候選更傷害信任。

---

### 3.4 Phrase-first

速成單字重碼很多，所以 v1 必須把 phrase-first 當核心，不是 nice-to-have。

```text id="rn2848"
唔 → 唔該 / 唔係 / 唔好 / 唔使
我 → 我哋 / 我嘅 / 我想 / 我會
send → send返 / send俾我 / send個file
confirm → confirm咗 / confirm返 / confirm一下
```

---

### 3.5 Privacy-first

鍵盤是高度敏感產品。

v1 必須主打：

```text id="ifq30d"
No account
No cloud sync
No keystroke upload
Personal memory stored locally
Sensitive fields disable learning and prediction
One-tap clear all personal data
```

---

## 4. Development Scope

### 4.1 Stage 1 — Technical Feasibility Spike

Stage 1 只證明技術可行性。

必須完成：

```text id="hv3o1q"
Prototype 0：Decode Contract Harness
Prototype 1：Commit Rule Harness
Prototype 2：Android IME Thin UI
```

Stage 1 不追求：

```text id="x2tf4c"
漂亮 UI
大型詞庫
完整 emoji
完整設定頁
商業上架
高完成度產品體驗
```

---

### 4.2 Stage 2 — Product Build

只有 Stage 1 三個 Gate 過關後，才進入 Stage 2。

Stage 2 才做：

```text id="qcjr4t"
完整 Android keyboard app
正式 UI polish
正式 corpus integration
自訂詞管理頁
完整設定頁
完整 emoji panel
Play Store privacy wording
Beta testing
```

---

### 4.3 v1 不做

```text id="tsehqa"
iOS
雲端同步
AI 改寫
STT
Sticker
GIF
帳戶系統
首碼簡碼 phrase
Quick+Cangjie MIXED 預設
```

---

### 4.4 Phase 1.5 / Phase 2

Phase 1.5：

```text id="le9s3u"
privateImeOptions / app-specific no-learning hints
Quick+Cangjie MIXED scheme evaluation
small beta testing
```

Phase 2：

```text id="ucr071"
完整倉頡優化
首碼簡碼 phrase
更大型 HK 詞庫
缺字回報系統
可選 STT
雲端備份自訂詞
主題
單手模式
平板 layout
iOS research
```

---

## 5. Recommended Technology Stack

### 5.1 Android

```text id="w4mndt"
Language：Kotlin
IME：InputMethodService
UI：Native Android Views or Jetpack Compose inside IME view
Storage：Room / SQLite
Preferences：DataStore
Native bridge：JNI / CMake
Testing：JUnit + Android Instrumentation Tests
Performance：Macrobenchmark / custom latency logging
```

### 5.2 Decoder

```text id="dqrrfo"
Core：librime
License：BSD-3-Clause
Usage：native decoder core via JNI
```

Important constraints:

```text id="pimdcj"
Do not fork Trime for closed-source commercial base.
Do not include GPL legacy modules.
Schema / dictionary / font license must be audited separately.
```

### 5.3 Corpus

```text id="3cjzv9"
Base character data：HKSCS official data
Quick / Cangjie maps：licensed / audited source only
HK core words：curated internal list for Stage 1
HK frequency：must be HK-chat weighted, not generic Traditional Chinese only
```

---

## 6. Suggested Repository Structure

```text id="jqeo8y"
hk-mixed-keyboard/
├── android/
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── kotlin/com/hkmixedkeyboard/
│   │   │   │   ├── ime/
│   │   │   │   │   ├── HkImeService.kt
│   │   │   │   │   ├── ImeLifecycleController.kt
│   │   │   │   │   └── InputConnectionAdapter.kt
│   │   │   │   ├── ui/
│   │   │   │   │   ├── KeyboardView.kt
│   │   │   │   │   ├── CandidateBarView.kt
│   │   │   │   │   ├── SymbolPageView.kt
│   │   │   │   │   └── EmojiPanelView.kt
│   │   │   │   ├── engine/
│   │   │   │   │   ├── Composer.kt
│   │   │   │   │   ├── Classifier.kt
│   │   │   │   │   ├── CandidateGenerator.kt
│   │   │   │   │   ├── CandidateRanker.kt
│   │   │   │   │   ├── CommitController.kt
│   │   │   │   │   └── StateMachine.kt
│   │   │   │   ├── decoder/
│   │   │   │   │   ├── DecoderContract.kt
│   │   │   │   │   ├── RimeDecoder.kt
│   │   │   │   │   ├── MockDecoder.kt
│   │   │   │   │   └── DecodeResult.kt
│   │   │   │   ├── dictionary/
│   │   │   │   │   ├── HkCoreDictionary.kt
│   │   │   │   │   ├── EnglishLexicon.kt
│   │   │   │   │   ├── MixedPhraseDictionary.kt
│   │   │   │   │   └── PhraseCodePolicy.kt
│   │   │   │   ├── memory/
│   │   │   │   │   ├── UserMemoryRepository.kt
│   │   │   │   │   ├── UserMemoryDao.kt
│   │   │   │   │   └── PersonalizationScorer.kt
│   │   │   │   ├── privacy/
│   │   │   │   │   ├── SensitiveFieldDetector.kt
│   │   │   │   │   └── SafeKeyboardMode.kt
│   │   │   │   ├── settings/
│   │   │   │   └── performance/
│   │   │   │       └── LatencyLogger.kt
│   │   │   └── cpp/
│   │   │       ├── rime_bridge.cpp
│   │   │       └── CMakeLists.txt
│   │   └── build.gradle.kts
│   └── settings.gradle.kts
├── corpus/
│   ├── internal_stage1/
│   │   ├── hk_core_chars.csv
│   │   ├── hk_core_phrases.csv
│   │   ├── mixed_phrases.csv
│   │   └── whitelist_en.csv
│   ├── sources/
│   │   └── corpus_license_register.csv
│   └── README.md
├── docs/
│   ├── HK_Mixed_Keyboard_v1_5.md
│   ├── Corpus_Spec.md
│   ├── Decoder_Contract.md
│   ├── Commit_Rule_Test_Spec.md
│   └── Privacy_Spec.md
├── tests/
│   ├── decode_contract_cases.json
│   ├── commit_rule_cases.json
│   ├── acceptance_cases_cold.json
│   ├── acceptance_cases_warm.json
│   └── privacy_cases.json
└── README.md
```

---

## 7. Decoder Contract

### 7.1 Purpose

`DecoderContract` is the foundation of the whole product.
If this contract cannot be implemented reliably, do not proceed to full keyboard app.

### 7.2 Required Input

```text id="j4btnb"
buffer: String
scheme: Scheme
```

```text id="uxmk3o"
enum Scheme {
    QUICK,
    CANGJIE,
    MIXED_EXPERIMENTAL
}
```

### 7.3 Required Output

```text id="q78jq6"
DecodeResult {
    buffer: String
    scheme: Scheme
    consumedLen: Int
    isExactCode: Boolean
    isPrefixOnly: Boolean
    candidates: List<DecodeCandidate>
}
```

```text id="ml2yi3"
DecodeCandidate {
    text: String
    code: String
    sourceSchema: SourceSchema
    type: CandidateType
    frequency: Double
    isHkCore: Boolean
}
```

```text id="vi5nrm"
enum SourceSchema {
    QUICK,
    CANGJIE,
    MIXED_PHRASE,
    ENGLISH,
    USER_MEMORY
}

enum CandidateType {
    CHAR,
    PHRASE,
    MIXED_PHRASE,
    EN_LITERAL,
    EN_AUTOCOMPLETE
}
```

### 7.4 Derived Parse States

```text id="u2mqyr"
cnExactParsed =
    isExactCode && any(candidate.type == CHAR)

cnHasPhraseMatch =
    isExactCode && any(candidate.type == PHRASE)

cnPrefixParsed =
    isPrefixOnly || consumedLen < buffer.length
```

### 7.5 Fallback Tables

If librime wrapper cannot provide required metadata, add helper tables:

```text id="a6f0i9"
charCodeMap
phraseCodeMap
prefixCodeTrie
exactCodeSet
candidateTypeMap
candidateFrequencyMap
```

### 7.6 Gate 1 Pass Criteria

Prototype 0 passes only if the harness can show:

```text id="zjotiz"
1. cnExactParsed / cnHasPhraseMatch / cnPrefixParsed are derived deterministically
2. Candidate text/code/source/type/frequency can be extracted or supplemented
3. Quick scheme works on test buffers
4. Cangjie scheme can be loaded, even if not default
5. Prefix-only buffers never become auto-committable
```

Test buffers:

```text id="kjwjf6"
hap
send
ok
go
mtr
我
你
唔
嘅
唔該
我哋
```

Stop condition:

```text id="c2uqd6"
If Gate 1 fails, do not build Android IME UI.
Fix decoder contract first.
```

---

## 8. Phrase Code Policy

### 8.1 Quick Mode

v1 policy:

```text id="c8c8rf"
Single character:
    Quick code = first + last Cangjie code

Phrase:
    Quick phrase code = concatenate each character's full Quick code

Example:
    唔該 = Quick(唔) + Quick(該)
```

v1 does not support short-code phrase policy.

Do not implement:

```text id="lp3vy8"
三字詞 = 每字首碼 + 尾字尾碼
四字詞 = 前三字首碼 + 最後字尾碼
```

Reason:

```text id="cok4a4"
Short-code phrase will increase EN/CN collisions.
It conflicts with conservative commit.
It should only be added after acceptance data proves it is safe.
```

### 8.2 Cangjie Mode

v1 policy:

```text id="7vjy9z"
Single character:
    full Cangjie code

Phrase:
    use available Rime schema phrase dictionary
    add small curated phrase shortcuts only if license-safe
```

### 8.3 Mixed Phrase

Mixed phrases are triggered by English tokens.

Examples:

```text id="u2ab6q"
send → send返 / send俾我 / send個file
confirm → confirm咗 / confirm返 / confirm一下
check → check下 / check email / check返
call → call你 / call我 / call返你
```

Autocomplete must not auto-commit on Space.

---

## 9. Keyboard Layout

### 9.1 Main Layout

```text id="l33qjx"
┌──────────────────────────────────────┐
│ 我哋  我嘅  唔該  happy  send返  ▾   │
├──────────────────────────────────────┤
│ 1   2   3   4   5   6   7   8   9   0 │
│ Q   W   E   R   T   Y   U   I   O   P │
│ 手  田  水  口  廿  卜  山  戈  人  心 │
│ A   S   D   F   G   H   J   K   L   ⌫ │
│ 日  尸  木  火  土  竹  十  大  中      │
│ ⇧   Z   X   C   V   B   N   M     ↵    │
│         難  金  女  月  弓  一          │
│ 😊  符   ，        Space        。  ？！│
└──────────────────────────────────────┘
```

### 9.2 Keycap Design

Each key shows:

```text id="e1otco"
Q
手
```

Rules:

```text id="kc8tgj"
English letter larger
Cangjie root smaller and softer
Centered vertically
No overcrowding
Root visibility configurable
```

### 9.3 Cangjie Mapping

```text id="jxpvoo"
A 日
B 月
C 金
D 木
E 水
F 火
G 土
H 竹
I 戈
J 十
K 大
L 中
M 一
N 弓
O 人
P 心
Q 手
R 口
S 尸
T 廿
U 山
V 女
W 田
X 難
Y 卜
Z 保留
```

Z is reserved for:

```text id="dn8fdd"
custom shortcut
future duplicate-code action
advanced function
```

Do not assign a fake non-standard root to Z.

### 9.4 Bottom Row

Normal:

```text id="mdn66t"
😊  符  ，      Space      。  ？！
```

Compact:

```text id="kaj30v"
符  ，      Space      。  ？！
```

In compact mode, emoji moves to candidate bar left icon or long-press symbol key.

### 9.5 Candidate Bar

Candidate bar must support:

```text id="v9shha"
horizontal scrolling
6–8 visible candidates
expand button ▾
grid view for heavy duplicate-code candidates
tap candidate to commit
long-press candidate for details / delete user memory item
```

### 9.6 Symbol Page

Symbol page is a pure symbol layer.
It is not a language switch.

```text id="s0nw6a"
按「符」→ symbol page
按「ABC」→ return to main keyboard
```

Symbol page keeps:

```text id="dxjo1g"
Space
Backspace
Enter
，
。
？！
Emoji entry
```

### 9.7 Emoji Panel

v1:

```text id="gpbxuk"
recent
smileys
hands
people
food
transport
symbols
```

Stage 1 Thin UI can skip full emoji categorization.

---

## 10. State Machine

### 10.1 States

```text id="z6x1ph"
IDLE
    buffer empty
    no active composing

COMPOSING
    buffer non-empty
    composing candidates shown

PREDICTING
    buffer empty
    just committed text
    post-commit predictions shown
```

### 10.2 Transitions

```text id="g5ifpn"
IDLE → COMPOSING
    user presses letter

COMPOSING → PREDICTING
    user commits candidate / literal buffer

PREDICTING → COMPOSING
    user presses letter

PREDICTING → IDLE
    raw space or sentence-ending punctuation resets context

ANY → IDLE
    field change
    onFinishInput
    reset
    Safe Keyboard Mode reset
```

### 10.3 Global State

```text id="cw84yq"
buffer: String
lastAutoCommit: { text, originalBuffer }?
prevCommitted: String?
ctx.scheme
ctx.isSensitiveField
ctx.enterPolicy
ctx.spaceMode
```

---

## 11. Commit Primitive Contract

All handlers must call one of these primitives.
Do not manually call `commitText()` elsewhere.

### 11.1 commitCandidate(target)

Use when user selected or smart-commit selected a candidate.

```text id="wgu1hj"
commitCandidate(target):
    commitText(canonicalText(target))
    buffer.clear()
    state = PREDICTING

    if sensitive:
        prevCommitted = null
        clearBar()
        return

    userMemory.record(target)
    prevCommitted = target.text
    refreshBar(buildPostCommitPredictions(prevCommitted))
```

### 11.2 commitLiteralBuffer(buffer, learn)

Use when committing raw buffer exactly.

```text id="xv3oc7"
commitLiteralBuffer(buffer, learn):
    text = buffer
    commitText(text)
    buffer.clear()
    state = PREDICTING

    if sensitive:
        prevCommitted = null
        clearBar()
        return

    if learn:
        userMemory.record(Candidate(text, EN_LITERAL))

    prevCommitted = text
    refreshBar(buildPostCommitPredictions(prevCommitted))
```

### 11.3 commitRawText(text, resetContext)

Use for space and punctuation.

```text id="r9ed2p"
commitRawText(text, resetContext):
    commitText(text)

    if resetContext or isSentenceTerminator(text):
        prevCommitted = null
        clearBar()
        state = IDLE
    else:
        keep prevCommitted
```

Sentence terminators:

```text id="v4op9t"
。
？
！
?!
？！
.
```

Non-reset punctuation:

```text id="xslbx0"
，
、
:
；
```

---

## 12. Event Handlers

### 12.1 onKeyPress(letter)

```text id="qxydbv"
onKeyPress(letter):
    lastAutoCommit = null

    if len(buffer) >= MAX_BUFFER_LEN:
        commitLiteralBuffer(buffer, learn=false)
        buffer.clear()

    buffer += letter
    state = COMPOSING
    refreshBar(buildCandidateList(buffer, ctx))
```

### 12.2 onCandidateTap(cand)

```text id="0q86g8"
onCandidateTap(cand):
    lastAutoCommit = null
    commitCandidate(cand)
```

Tap candidate is always highest priority.

```text id="74sjpl"
Tap what → commit what
No disambiguation
No override
```

### 12.3 onBackspace()

```text id="hffh2r"
onBackspace():
    if buffer not empty:
        buffer.popLast()
        refreshBar(buildCandidateList(buffer, ctx))
        return

    if lastAutoCommit != null and cursorJustAfter(lastAutoCommit):
        deleteSurroundingText(len(lastAutoCommit.text))
        buffer = lastAutoCommit.originalBuffer
        lastAutoCommit = null
        state = COMPOSING
        refreshBar(buildCandidateList(buffer, ctx))
        return

    deleteCommittedCharBeforeCursor()
```

Important:

```text id="ejuf90"
Only Space / punctuation auto-commit may create lastAutoCommit.
Manual candidate tap must not create lastAutoCommit.
```

v1 simplification:

```text id="xexkyw"
Revert only works if cursor is immediately after last auto-committed text.
If user typed another character or moved cursor, fallback to normal backspace.
```

### 12.4 onSpace()

```text id="p7bbyn"
onSpace():
    if buffer empty:
        commitRawText(" ", resetContext=false)
        return

    if ctx.spaceMode == ALWAYS_SPACE:
        commitLiteralBuffer(buffer, learn=false)
        commitRawText(" ", resetContext=false)
        return

    c = classifyBuffer(buffer, ctx.scheme)
    target = selectSpaceCommitTarget(buffer, c, ctx)

    lastAutoCommit = { text: target.text, originalBuffer: buffer }
    commitCandidate(target)
```

Note:

```text id="43yjjo"
English autocomplete is never committed by Space.
Space commits literal English buffer, not guessed autocomplete.
```

### 12.5 onPunctuation(p)

```text id="c7rruu"
onPunctuation(p):
    if buffer not empty:
        c = classifyBuffer(buffer, ctx.scheme)
        target = selectPunctuationCommitTarget(buffer, c, ctx)
        lastAutoCommit = { text: target.text, originalBuffer: buffer }
        commitCandidate(target)

    commitRawText(p, resetContext = isSentenceTerminator(p))
```

Punctuation is more conservative than Space.

### 12.6 onEnter()

```text id="fgqkcl"
onEnter():
    if ctx.enterPolicy == ALWAYS_PASS_THROUGH:
        passThroughEnter()
        return

    if buffer not empty:
        commitLiteralBuffer(buffer, learn=false)

        if ctx.enterPolicy == COMMIT_THEN_SWALLOW:
            return SWALLOW

    passThroughEnter()
```

Enter must never auto-select Chinese candidate.

---

## 13. Classify Buffer

### 13.1 classifyBuffer(buffer, scheme)

```text id="dp6pnv"
classifyBuffer(buffer, scheme):
    cn = engineDecode(buffer, scheme)

    return ClassifyResult {
        cnExactParsed    = cn.isExactCode && any(c.type == CHAR)
        cnHasPhraseMatch = cn.isExactCode && any(c.type == PHRASE)
        cnPrefixParsed   = cn.isPrefixOnly || cn.consumedLen < buffer.length

        cnCandidates     = rank(cn.candidates)

        enLiteral        = buffer
        enAutocomplete   = englishLexicon.autocomplete(buffer)
        enIsWord         = englishLexicon.isWord(buffer)
                           || SHORT_WHITELIST.containsIgnoreCase(buffer)
        enStrongPrefix   = strongEnglishPrefix(buffer, cn)
    }
```

### 13.2 strongEnglishPrefix

```text id="t0g130"
strongEnglishPrefix(buffer, cn):
    return len(buffer) >= 3
        && !cn.isExactCode
        && englishLexicon.hasHighFreqCompletion(buffer)
```

Examples:

```text id="qcey3e"
hap → happy
mee → meeting
con → confirm
rep → reply
```

---

## 14. Commit Target Selection

### 14.1 selectSpaceCommitTarget

```text id="ahljqt"
selectSpaceCommitTarget(buffer, c, ctx):
    m = memoryHardOverride(buffer, ctx)
    if m != null:
        return m

    cnCommittable = c.cnExactParsed || c.cnHasPhraseMatch
    enStrong      = c.enIsWord || c.enStrongPrefix

    if cnCommittable && !enStrong:
        return topCn(c)

    if enStrong && !cnCommittable:
        return Candidate(c.enLiteral, EN_LITERAL)

    if cnCommittable && enStrong:
        return tieBreak(buffer, c, ctx)

    return Candidate(c.enLiteral, EN_LITERAL)
```

### 14.2 selectPunctuationCommitTarget

```text id="tjfhsu"
selectPunctuationCommitTarget(buffer, c, ctx):
    if c.enIsWord
       || c.enStrongPrefix
       || SHORT_WHITELIST.containsIgnoreCase(buffer):
        return Candidate(c.enLiteral, EN_LITERAL)

    if c.cnExactParsed || c.cnHasPhraseMatch:
        return topCn(c)

    return Candidate(buffer, EN_LITERAL)
```

### 14.3 memoryHardOverride

```text id="df0r8u"
memoryHardOverride(buffer, ctx):
    if ctx.isSensitiveField:
        return null

    m = userMemory.exactMatch(buffer)

    if m != null
       && m.count >= USER_MEM_OVERRIDE_MIN_COUNT
       && m.confidence >= USER_MEM_OVERRIDE_MIN_CONF:
        return m.candidate

    return null
```

### 14.4 tieBreak

```text id="vvu2ij"
tieBreak(buffer, c, ctx):
    if !ctx.isSensitiveField:
        r = userMemory.cnRatio(buffer)

        if r >= CN_RATIO_THRESHOLD:
            return topCn(c)

        if r <= EN_RATIO_THRESHOLD:
            return Candidate(buffer, EN_LITERAL)

    top = topCn(c)

    if len(buffer) <= 2 && top != null && top.isHkCore:
        return top

    return Candidate(buffer, EN_LITERAL)
```

### 14.5 Constants

```text id="sy3dce"
MAX_BUFFER_LEN = 20
USER_MEM_OVERRIDE_MIN_COUNT = 3
USER_MEM_OVERRIDE_MIN_CONF = 0.80
CN_RATIO_THRESHOLD = 0.65
EN_RATIO_THRESHOLD = 0.35
```

Short English whitelist:

```text id="x4dcu4"
ok
no
go
hi
pm
am
ai
it
hr
cv
id
ot
dm
ig
fb
tg
qr
kpi
pdf
doc
ppt
tax
mtr
fps
mpf
hk
hkd
usd
```

Commit canonical casing:

```text id="z04se6"
mtr → MTR
fps → FPS
mpf → MPF
hkd → HKD
usd → USD
pdf → PDF
qr → QR
```

---

## 15. Candidate Generation and Ranking

### 15.1 Candidate Sources

```text id="u1u0w2"
EN_LITERAL
EN_AUTOCOMPLETE
CN_CHAR
CN_PHRASE
MIXED_PHRASE
USER_MEMORY
POST_COMMIT_PREDICTION
```

### 15.2 Candidate Ranking Formula

```text id="a0zj2o"
score =
    base_frequency
  + hk_cantonese_boost
  + exact_match_boost
  + prefix_match_boost
  + phrase_boost
  + user_frequency_boost
  + recency_boost
  + context_boost
  + mixed_language_boost
  - rare_word_penalty
```

### 15.3 Ranking Rules

```text id="xy73t4"
1. Tap candidate always overrides ranking.
2. Ranking controls display order only.
3. Commit rule controls Space / punctuation behavior.
4. Prefix-only Chinese candidates may appear but must not auto-commit.
5. English autocomplete may appear but must not auto-commit on Space.
6. User memory may influence ranking.
7. User memory hard override requires count >= 3 and confidence >= 0.8.
```

### 15.4 Post-commit Prediction

Triggered after:

```text id="adnwpt"
commitCandidate()
commitLiteralBuffer()
```

Not triggered after:

```text id="pzfnta"
sentence-ending punctuation
field reset
Safe Keyboard Mode
```

Examples:

```text id="miywe2"
我 → 我哋 / 我嘅 / 我想 / 我會 / 我今日
你 → 你哋 / 你嘅 / 你係咪 / 你可以
唔 → 唔該 / 唔係 / 唔好 / 唔使 / 唔知
send → send返 / send俾我 / send個file
confirm → confirm咗 / confirm返 / confirm一下
```

---

## 16. Privacy and Safe Keyboard Mode

### 16.1 Sensitive Detection

On every `onStartInput(editorInfo)`:

```text id="bpyeyw"
resetCompositionState()
ctx.isSensitiveField = isSensitive(editorInfo)
if sensitive:
    enterSafeKeyboardMode()
```

Sensitive types include:

```text id="gtcclu"
password
visible password
web password
number password
TYPE_TEXT_FLAG_NO_SUGGESTIONS
```

Phase 1.5:

```text id="bp8m4b"
Investigate EditorInfo.privateImeOptions
Investigate app-specific no-learning hints
```

### 16.2 Safe Keyboard Mode Rules

When sensitive:

```text id="s8slc0"
No personalization
No post-commit prediction
No mixed phrase prediction
No user memory write
No missing-word report
No composing history
No candidate leakage from previous field
No prevCommitted retention
No lastAutoCommit retention
```

Allowed:

```text id="kpn5el"
basic key input
basic delete
basic enter
basic punctuation
non-personal static keyboard rendering
```

### 16.3 Lifecycle Reset

```text id="6w2cm1"
onStartInput:
    resetCompositionState()

onFinishInput:
    resetCompositionState()

onWindowHidden:
    resetCompositionState()

resetCompositionState:
    buffer.clear()
    prevCommitted = null
    lastAutoCommit = null
    clearCandidateBar()
    engineResetComposition()
    state = IDLE
```

---

## 17. Personalization

### 17.1 Stored Data

Store only candidate-level data:

```text id="v1wwgp"
candidate text
candidate source
usage count
last used timestamp
buffer exact match stats
cnRatio / enRatio
custom words
custom phrases
```

### 17.2 Not Stored

Never store:

```text id="ga8q8f"
full sentences
full chat messages
passwords
banking data
credit card data
private conversation context
```

### 17.3 Hard Override vs Influence

Hard override:

```text id="pu11vw"
count >= 3
confidence >= 0.8
not sensitive
exact buffer match
```

Influence:

```text id="s1z7ml"
Used inside ranking and tieBreak
Does not automatically override conservative default
```

### 17.4 Memory Reset

Settings must support:

```text id="e5xt2b"
clear all memory
clear custom words
reset ranking history
export personal dictionary
import personal dictionary
```

Stage 1 does not need import/export UI, but data model should not block it.

---

## 18. Dictionary and Corpus

### 18.1 Corpus Layers

```text id="ebxqym"
1. Basic Traditional Chinese characters
2. HKSCS / Hong Kong characters
3. HK Cantonese core characters
4. HK daily-life phrases
5. Mixed English-Chinese phrases
6. English autocomplete lexicon
7. User custom dictionary
```

### 18.2 HK Core Character List

Stage 1 curated list must include at least:

```text id="s6m27i"
嘅
啲
咗
喺
唔
冇
佢
哋
俾
畀
嚟
嘢
咩
呢
嗰
噉
咁
啱
睇
搵
諗
攞
嬲
攰
晒
```

These must have `isHkCore = true`.

### 18.3 Stage 1 Small Corpus

Stage 1 should use small curated corpus only:

```text id="y8ijs9"
HK core chars: 50–100
HK core phrases: 200–500
Mixed phrases: 100–200
English whitelist: current list
```

Do not integrate large corpus until commit rule passes.

### 18.4 Corpus Spec Required Fields

Every corpus source must have:

```text id="o27sqj"
source_name
url
license
commercial_use
modification
attribution
share_alike
redistribution
format
fields_used
production_allowed
notes
frequency_source
frequency_method
frequency_scope
last_updated
normalization_method
```

### 18.5 License Hard Gate

```text id="z4tznd"
Any schema / dictionary / font / corpus without license audit may be used only in internal prototype.
It must not be shipped in production APK.
```

### 18.6 Frequency Rule

Do not use generic Traditional Chinese frequency alone.

```text id="ydlkm6"
嘅 / 咗 / 啲 / 唔 / 冇
```

are high-frequency in HK chat but may not rank high in formal Traditional Chinese corpus.

Required:

```text id="vcftn3"
HK chat weighted frequency
curated HK core boost
user memory adjustment
```

---

## 19. Settings

### 19.1 Input Mode

```text id="wxklpc"
速成 Quick — default
倉頡 Cangjie — selectable
Quick + Cangjie — experimental
```

### 19.2 Display

```text id="ddmlf9"
show Cangjie roots: on/off
root size: small/medium/large
number row: on/off
theme: light/dark/system
compact mode: auto/manual
candidate grid expansion: on/off
```

### 19.3 Input Experience

```text id="vhpp4r"
vibration: on/off
vibration strength: light/medium/strong
key sound: on/off
Space behavior: Smart commit / Always space
Enter behavior: Commit-then-swallow / Commit-and-send / Pass-through
```

Defaults:

```text id="azwwq2"
vibration: on
vibration strength: light
key sound: off
Space behavior: Smart commit
Enter behavior: Commit-then-swallow
input mode: Quick
```

### 19.4 Dictionary

```text id="sgtpd1"
my common words
add custom word
delete custom word
import dictionary
export dictionary
reset memory
```

### 19.5 Privacy

```text id="x4sf1y"
local memory explanation
clear all personal data
anonymous missing-word report: off by default for Stage 1
```

---

## 20. Performance Budget

### 20.1 Latency Targets

```text id="xqbiln"
Key visual feedback: < 16 ms
Haptic feedback: < 16 ms
Composing text update: < 30 ms
Candidate bar first result: p95 < 50 ms
Candidate bar full rerank: p95 < 80 ms
```

### 20.2 Rules

```text id="t0rgbk"
Do not block UI thread with librime decode.
Run decode off main thread.
Post candidate result back to main.
Coalesce rapid keystrokes.
Debounce full rerank where needed.
Keep helper maps in memory.
Quick default runs one scheme only.
Quick+Cangjie mixed mode is experimental because decode cost doubles.
```

### 20.3 Instrumentation

Log in debug builds:

```text id="e5sdxg"
key_down_time
visual_feedback_time
decode_start_time
decode_end_time
first_candidate_render_time
full_rerank_time
scheme
buffer_length
candidate_count
```

Do not log actual full user sentences.

---

## 21. Build Staging

## Prototype 0 — Decode Contract Harness

### Goal

Validate decoder contract without Android IME UI.

Input:

```text id="pwtqdk"
buffer
scheme
```

Output:

```text id="uxzohb"
DecodeResult
ClassifyResult
candidate list
```

### Required Commands / Test Entry

```text id="08dmp7"
runDecodeHarness("hap", QUICK)
runDecodeHarness("send", QUICK)
runDecodeHarness("ok", QUICK)
runDecodeHarness("go", QUICK)
runDecodeHarness("mtr", QUICK)
runDecodeHarness("我", QUICK)
runDecodeHarness("你", QUICK)
runDecodeHarness("唔", QUICK)
runDecodeHarness("嘅", QUICK)
runDecodeHarness("唔該", QUICK)
runDecodeHarness("我哋", QUICK)
```

### Deliverables

```text id="fqcdw0"
DecoderContract.kt
MockDecoder.kt
RimeDecoder.kt or RimeDecoderSpike.kt
decode_contract_cases.json
decode_harness_report.md
```

### Gate 1 Pass

```text id="h20688"
Can derive cnExactParsed
Can derive cnHasPhraseMatch
Can derive cnPrefixParsed
Can identify candidate source schema
Can identify candidate type CHAR / PHRASE
Can load Quick schema
Can load Cangjie schema
Can show failure reason if metadata unavailable
```

### Stop Condition

```text id="b2mwll"
If exact/prefix/phrase state cannot be derived, stop Stage 1.
Do not build UI.
```

---

## Prototype 1 — Commit Rule Harness

### Goal

Validate commit rule as pure logic.

No real Android IME.
No UI.
No librime dependency required if using mock decoder.

Input:

```text id="v4pbdv"
buffer
ClassifyResult
Context
UserMemoryState
Event
```

Output:

```text id="p3ig20"
CommitDecision
NewState
CandidateBarState
MemoryWriteDecision
```

### Deliverables

```text id="z741mq"
CommitController.kt
StateMachine.kt
CommitRuleTest.kt
commit_rule_cases.json
acceptance_cases_cold.json
acceptance_cases_warm.json
commit_rule_report.md
```

### Must Test

```text id="9no3gc"
Space smart commit
Space always-space mode
Enter policies
Backspace revert-last-commit
Punctuation conservative commit
Memory hard override
Memory influence
Sensitive field no-learning
Whitelist collision
Prefix-only Chinese candidates
English autocomplete never Space-commits
```

### Gate 2 Pass

```text id="gt07vw"
Space mis-commit ≤ 2%
EN literal preservation ≥ 99%
Backspace correction ≤ 5%
Hard override not triggered by one accidental choice
Sensitive memory write = 0
```

---

## Prototype 2 — Android IME Thin UI

### Goal

Build minimal Android IME to prove integration.

Must include:

```text id="mzpqwu"
InputMethodService
candidate bar
QWERTY + Cangjie root labels
number row
Space
Backspace
Enter
basic punctuation
basic symbol page
Safe Keyboard Mode
settings stub
debug report screen
```

Do not include:

```text id="psscrd"
UI polish
themes
full emoji categories
large corpus
cloud
STT
AI
Play Store packaging
```

### Gate 3 Pass

```text id="ffjse6"
Can enable IME in Android settings
Can type into normal text field
Can type into chat app text field
Can detect password/no-suggestions field
Safe Mode shows no personalized predictions
No memory write in sensitive field
Candidate bar clears on field change
Performance budget roughly met
```

---

## 22. Acceptance Gate

### 22.1 Test Profiles

Run every group in two profiles:

```text id="y8fwjo"
Cold-start:
    no user memory
    default settings

Warm:
    seed memory:
        我哋 often selected
        ok often literal
        MTR often literal
        send often literal / mixed phrase
```

### 22.2 Test Groups

A. HK Core Characters / Phrases

```text id="x9mhwu"
我嘅
你哋
佢哋
唔該
冇問題
有冇
咗未
喺邊
嗰個
啲嘢
```

B. Mixed Chinese-English

```text id="jnt4xi"
我今日meeting完再call你
send個file俾我
confirm咗再話我知
我lunch之後再reply你
check下email
```

C. Whitelist Collisions

```text id="q0ol2d"
ok
go
MTR
FPS
MPF
PDF
QR
HKD
OT
DM
```

D. Phrase-first

```text id="t6dgry"
唔該
唔係
唔好
你哋
我哋
冇問題
可唔可以
遲啲
等陣
```

E. Privacy Fields

```text id="ndnn5e"
password
visible password
web password
number password
no suggestions
```

### 22.3 Metrics

```text id="fk23rh"
Top-1 expected token hit rate ≥ 70%
Top-3 expected token hit rate ≥ 90%
Space mis-commit rate ≤ 2%
Backspace correction rate ≤ 5%
KPC for Quick ≤ 2.2
KPC for Cangjie: measured separately
EN literal preservation ≥ 99%
Sensitive learning violation = 0
Sensitive candidate leakage = 0
```

### 22.4 Mis-commit Definition

A mis-commit occurs when:

```text id="omd94r"
actual committed text != expected committed text
```

For privacy tests:

```text id="m82uo7"
Any memory write in sensitive field = fail
Any candidate from previous normal field shown in sensitive field = fail
Any post-commit prediction in sensitive field = fail
```

---

## 23. Debug and Developer Tools

Stage 1 debug screen should show:

```text id="gjbkt4"
current buffer
current scheme
current state
isSensitiveField
lastAutoCommit
prevCommitted
ClassifyResult
candidate list with source/type/score
selected commit target
memory write yes/no
latency metrics
```

Do not show this in production.

Debug logs must avoid full sentence logging.

Allowed logs:

```text id="qyvjdk"
buffer in test harness
candidate metadata in internal builds
latency values
scheme
state transitions
```

Not allowed in production logs:

```text id="ba5aom"
full user text
full chat sentence
password field content
banking field content
```

---

## 24. Coding Agent Rules

When assigning work to a coding agent, include these rules:

```text id="n8ikvy"
1. Do not build full keyboard before Prototype 0 and 1 pass.
2. Do not add UI polish unless explicitly requested.
3. Do not add cloud, account, STT, AI, analytics, ads, or telemetry.
4. Do not include unaudited corpus/font/schema in production path.
5. Do not fork Trime as product base.
6. Do not bypass commit primitives.
7. Do not call commitText directly outside commit primitives.
8. Do not store full user sentences.
9. Do not write user memory in sensitive fields.
10. Do not make Space commit English autocomplete.
11. Do not make prefix-only Chinese auto-committable.
12. Do not implement short-code phrase in v1.
13. Use Quick as default scheme.
14. Treat Quick+Cangjie MIXED as experimental only.
15. Produce test report after every prototype.
```

---

## 25. Suggested Coding Agent Prompt — Prototype 0

```text id="0zymlo"
You are building Stage 1 Prototype 0 for HK Mixed Keyboard.

Do not build Android IME UI.
Do not build full keyboard.
Do not build settings UI.
Do not add cloud, analytics, STT, AI, or production corpus.

Goal:
Build a Decode Contract Harness for HK Mixed Keyboard.

Implement:
- DecoderContract
- DecodeResult
- DecodeCandidate
- ClassifyResult
- Scheme enum
- CandidateType enum
- SourceSchema enum
- MockDecoder for deterministic tests
- RimeDecoderSpike placeholder or actual librime bridge if available
- classifyBuffer(buffer, scheme)
- decode_contract_cases.json
- decode_harness_report.md

Required output for each buffer:
- consumedLen
- isExactCode
- isPrefixOnly
- cnExactParsed
- cnHasPhraseMatch
- cnPrefixParsed
- candidates with text/code/sourceSchema/type/frequency/isHkCore

Test buffers:
hap, send, ok, go, mtr, 我, 你, 唔, 嘅, 唔該, 我哋

Pass Gate 1 only if:
- three parse states can be derived deterministically
- candidate source/type can be identified or supplemented
- Quick schema works
- Cangjie schema can be loaded or mocked with clear limitation
- failure cases are explicitly reported

Return:
- files changed
- how to run tests
- test output
- Gate 1 pass/fail verdict
- known limitations
```

---

## 26. Suggested Coding Agent Prompt — Prototype 1

```text id="t641vx"
You are building Stage 1 Prototype 1 for HK Mixed Keyboard.

Do not build Android IME UI.
Do not build full keyboard.
Do not polish UI.

Goal:
Build Commit Rule Harness as pure Kotlin/JVM logic.

Implement:
- StateMachine
- CommitController
- CandidateRanker stub
- UserMemory stub
- SensitiveField stub
- selectSpaceCommitTarget
- selectPunctuationCommitTarget
- memoryHardOverride
- tieBreak
- commit primitives:
  commitCandidate
  commitLiteralBuffer
  commitRawText
- event handlers:
  onKeyPress
  onCandidateTap
  onBackspace
  onSpace
  onPunctuation
  onEnter

Test:
- Cold-start profile
- Warm profile
- A–E acceptance groups
- Space smart commit
- Always space
- Enter policies
- Backspace revert-last-commit
- Sensitive field no-learning
- Whitelist collision
- Prefix-only Chinese candidate display but no auto-commit
- English autocomplete never Space-commits

Pass Gate 2 only if:
- Space mis-commit ≤ 2%
- EN literal preservation ≥ 99%
- Sensitive learning violation = 0
- Sensitive candidate leakage = 0
- Accidental single memory choice does not hard override

Return:
- files changed
- tests added
- test result table
- Gate 2 pass/fail verdict
- remaining risks
```

---

## 27. Suggested Coding Agent Prompt — Prototype 2

```text id="79r5gy"
You are building Stage 1 Prototype 2 for HK Mixed Keyboard.

Build minimal Android IME Thin UI only.
Do not build full product UI.
Do not add cloud, analytics, STT, AI, ads, or production corpus.

Implement:
- InputMethodService
- main keyboard layout
- QWERTY + Cangjie root dual labels
- number row
- candidate bar with horizontal scroll and expand placeholder
- Space / Backspace / Enter
- punctuation keys
- basic symbol page
- Safe Keyboard Mode detection
- field transition reset
- simple debug panel for internal build
- basic settings stub:
  input mode Quick default
  Space behavior
  Enter behavior
  vibration
  sound

Integrate:
- CommitController from Prototype 1
- DecoderContract from Prototype 0
- MockDecoder or RimeDecoderSpike

Do not:
- add theme polish
- add complete emoji category UI
- add full corpus
- add Play Store packaging
- collect telemetry
- store full user text

Pass Gate 3 only if:
- Android IME can be enabled and used
- normal text field typing works
- password/no-suggestions field enters Safe Mode
- no memory write in Safe Mode
- candidate bar clears on field change
- latency logs are available
- basic acceptance cases can be run manually

Return:
- files changed
- install/run steps
- screenshots optional
- Gate 3 pass/fail verdict
- known limitations
```

---

## 28. Product Build Entry Criteria

Do not start Stage 2 Product Build until:

```text id="r4db1n"
Gate 1 passed
Gate 2 passed
Gate 3 passed
Corpus license register exists
Small curated Stage 1 corpus works
Sensitive field tests pass with 0 violation
Performance budget is not obviously broken
```

Stage 2 begins only after written approval.

---

## 29. Stage 2 Product Build Scope

After Stage 1 passes, Stage 2 may implement:

```text id="h07274"
production-grade UI
full settings pages
user custom word management
full emoji panel
candidate grid
corpus expansion
HK frequency tuning
export/import personal dictionary
beta distribution
Play Store privacy policy
crash reporting without keystroke content
```

Stage 2 still excludes:

```text id="cjgx2s"
STT
AI rewrite
cloud sync
ads
account
iOS
```

unless explicitly approved.

---

## 30. Final Development Instruction

Start development now with this exact instruction:

```text id="rbwe6o"
Start Stage 1 Technical Feasibility Spike for HK Mixed Keyboard.

Begin with Prototype 0 — Decode Contract Harness only.

Do not build full keyboard app.
Do not build UI.
Do not integrate large corpus.
Do not add cloud, account, analytics, STT, AI, ads, or Play Store preparation.

Your first deliverable is a runnable decode/classify harness proving whether librime + wrapper can provide the required three parse states:
- cnExactParsed
- cnHasPhraseMatch
- cnPrefixParsed

If Gate 1 fails, stop and report why.
```

---

## 31. Final Status

```text id="fdd7vp"
v1.5 status:
Ready for Stage 1 development.

Not ready for full product build.

Next action:
Prototype 0 — Decode Contract Harness.
```
