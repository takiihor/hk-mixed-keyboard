# HK Mixed Keyboard 三模式完整度、字庫及文化保育審核報告

原始審核日期：2026-07-13  
更新日期：2026-07-14  
更新範圍：`feat/cultural-preservation`，提交
`142ce78 feat: complete cultural preservation remediation`。  
歷史基線：原版報告審核的是 `master` 的 `0aae998`；其中 44% HKSCS
覆蓋、Quick／Jyutping literal Space 提交、缺少 reproducible corpora、缺少
中→英 assist 等結論，不能再代表目前 feature branch。

## 1. 更新後的結論

**工程 remediation 已完成；「文化保育級」release claim 尚未完成。**

目前可如實描述為：離線三模式香港繁體中文鍵盤，具可重現 corpus、HKSCS
輸入補充、候選／提交保護及明確 scope 的雙向 assist。不可描述為已由語言
專業人員認證、已窮盡所有粵語詞彙、已在所有字體環境驗證，或已達文化保育級。

最新 release claim 的限制及仍需外部證據的 gate，以
[`cultural_preservation_release_gates.md`](cultural_preservation_release_gates.md)
為準。

## 2. 可重現工程證據

以下命令於 2026-07-14 在 feature worktree 重跑並成功：

```bash
python3 corpus/tools/verify_corpus_manifest.py
python3 corpus/tools/run_three_mode_coverage_benchmark.py
python3 corpus/tools/run_jyutping_benchmark.py --require-top1 1.0
python3 corpus/tools/hkscs_coverage.py
cd android && ./gradlew test lintDebug assembleDebug assembleRelease
```

結果：

| 項目 | 可重現結果 | 判定範圍 |
|---|---:|---|
| Corpus manifest | 通過 | shipped asset 的 hash、大小、列數、來源、授權與 generator metadata |
| 三模式 coverage | 540/540 Top-1、Top-3 | source-derived asset-consistency cases，不是人工語言審閱 |
| Jyutping ranking | 30/30 Top-1、Top-3 | reviewed exact-key regression set，不是母體準確率 |
| HKSCS Han runtime 可達 | 4,606/4,606 | 官方 route 或明確 tap-only Unicode fallback |
| Quick 官方 route | 4,599/4,606 | 其餘由 Jyutping route 或 fallback 處理 |
| Jyutping 官方 route | 4,593/4,606 | 其餘由 Quick route 或 fallback 處理 |
| Android gate | 116 tasks，`BUILD SUCCESSFUL` | JVM tests、lint、debug/release APK build |

HKSCS-2016 有 1,716 個輔助平面漢字。`U+200CD`（𠃍）與
`U+200D1`（𠃑）沒有官方倉頡或粵拼 route；鍵盤不會捏造語言資料，而是提供
`u200cd`／`u200d1` technical、tap-only Unicode fallback。這使它們可選取和提交，
但不把 fallback 表述成官方粵拼或速成碼。

## 3. 原始缺口的完成狀態

| 原始審核問題 | 現況 | 證據／限制 |
|---|---|---|
| Quick／Jyutping Space、標點提交 literal Latin buffer | 已修正 | 三 mode 共用 resolved-candidate intent；僅 fresh、exact、可提交中文候選會在 Space／全形標點前提交。取消、stale、prefix、assist、null 及 direct-Latin 保持 literal。 |
| Auto-commit Backspace 無法還原 composition | 已修正 | 第一次 Backspace 還原 raw composition，第二次才編輯 buffer；包括 simplified output 的實際 UTF-16 長度。 |
| supplementary-plane 以 UTF-16 `Char`／`length` 處理 | 已修正 | CorpusLoader、decoder、IME、T2S 及測試改用 Unicode code point；輔助平面字可進入 route、display 與 commit path。 |
| 缺 glyph 時候選被靜默隱藏 | 已修正 | glyph 缺失時顯示 Unicode code-point label，保留原 text 作候選 tap／commit payload。接收 app 的字形仍取決於其字體。 |
| HKSCS 覆蓋約 44% | 已修正為完整 runtime baseline | pinned 官方 HKSCS source 生成 4,606 行 overlay；所有記錄有可達 route 或兩個明確 fallback。 |
| Quick／Jyutping／English-assist 無 pinned source 與 generator | 已修正 | corpus manifest、source archive／checksum、license register、deterministic generator 及 byte-for-byte rebuild test 已加入。 |
| Jyutping 常用香港詞排序與粗俗詞 Top-1 | 已修正 | reviewed overrides、直接詞組與 30-case exact-key ranking benchmark；`hai` 不以粗俗詞為 Top-1。 |
| Pinyin bundled rows 有不可達／非漢字輸出 | 已修正 | generator 在輸出前限制 composition reachability，拒絕 runtime 不可用候選；Traditional output 與 supplementary-plane mapping 有回歸測試。 |
| 英→中 assist 只限 Quick，香港本地化不足 | 已修正 | cross-mode、tap-only assist；pinned CC-CEDICT、FrequencyWords、rime-essay 及 reviewed Hong Kong overlay 生成。 |
| 中→英 assist 不存在 | 已修正，刻意限 scope | committed Chinese prefix 的 reviewed、標識為 `中→英`、tap-only candidate；不把 raw dictionary gloss 反轉成未審閱翻譯。 |
| `mixed_phrases.csv` 是 dead feature data | 已修正 | composing／post-commit suggestion path 已消費它；仍只作 curated mixed-language completion，不作語言學完整性聲稱。 |
| 沒有 current build E2E | 部分修正 | 0.58.0 build 73 已在 API 35 AOSP signed-emulator smoke 驗證 Quick、Jyutping、Pinyin、HKSCS fallback、bilingual assist 與 expanded grid。 |

## 4. 三模式現況

### Quick

- 有 exact、prefix、custom word、fresh resolved-candidate Space／標點 commit、
  two-stage Backspace restore 及 production-path E2E。
- 官方 HKSCS Quick route 覆蓋 4,599/4,606；其餘由其他正式 route 或 technical
  fallback 保留可達性。
- Quick corpus 由 pinned `rime-cangjie`／`rime-essay` 及 reviewed HK input
  生成；legacy aliases 僅作相容性 route，不冒充官方碼。

判定：**工程功能達標；語言排名認證未完成。**

### Jyutping

- 有 exact、prefix、segmented phrase、fresh resolved-candidate Space／標點 commit、
  tone-preserving reproducible reference 及 reviewed ranking overrides。
- 30 個 reviewed exact-key cases 全部 Top-1；這只證明該 regression set，不代表
  全部粵語輸入的 population accuracy。
- 官方 HKSCS Jyutping route 覆蓋 4,593/4,606；缺 route 的項目由其他正式 route
  或 technical fallback 保留可達性。

判定：**工程功能達標；完整詞彙／語言學排名認證未完成。**

### Pinyin

- 有 exact、prefix、continuous segmentation、Traditional output、fresh Space／
  punctuation commit 與 Backspace restore。
- pinned CC-CEDICT source 可 byte-for-byte rebuild；generator 會排除 composition
  不可達及 runtime 不可用的候選。
- 540-case automated coverage 包含 Pinyin source-derived cases，但不等同母語者
  審閱的普通話排名 benchmark。

判定：**工程功能達標；獨立排名認證未完成。**

## 5. 仍未完成的文化保育 release gates

以下不是可由 repo 自動補假的工作，因此本報告不把它們標為已完成：

1. **獨立語言審閱。** 至少 500 個 native-speaker 或語言專業人員 reviewed cases；
   每個 mode 至少 100，另至少 100 覆蓋 bilingual assist、HKSCS supplementary
   plane、OOV、標點與 cold-start。需記錄 reviewer role、corpus version、期望 rank
   與結果。
2. **Production-path 指標。** 在 Android production decoder、display policy 及
   commit path 上報告 Top-1、Top-3、Top-5、MRR、KPC、Space／標點誤提交、OOV 與
   supplementary-plane commit 結果。540 個 source-derived cases 不能替代此量測。
3. **裝置／字體矩陣。** 尚缺 API 26 AOSP、Android 13 Samsung One UI 與 Android 15
   Pixel/AOSP 實機／字體驗證。每個環境應確認 `U+2003E`（Quick `mi`、Jyutping
   `bui`）及 `U+2ADFF`（Quick `ec`）在 glyph 有／無時仍可見或有碼位標籤、可選取、
   可提交原 code point。
4. **權利與 acknowledgement 人工覆核。** release owner 仍須針對實際 distribution
   channel 複核 DATA.GOV.HK HKSCS acknowledgement、CC-BY／CC-BY-SA、LGPL／GPL 及
   OpenCC Apache-2.0 obligations。
5. **長期治理。** 需要建立公開 contribution channel、兩人審訂 policy、版本 archive
   及 curator change log；排序偏好不得刪除異體、俗字或歷史字的可選取來源形式。

## 6. 目前可作與不可作的發布聲稱

可以：

- 「離線三模式香港繁體中文鍵盤」；
- 「可重現 corpus、source checksum 與 generator」；
- 「HKSCS-aware；官方 route 存在時提供 route，缺 glyph 時保留可選取的碼位標籤」；
- 「中英 assist 為明確標識、tap-only 的輔助候選」。

不可以：

- 「所有廣東話詞彙完整」；
- 「已由語言學家／母語者大規模認證」；
- 「所有 Android 字體／裝置均驗證」；
- 「文化保育級」或等同官方認證的聲稱。

## 7. 審核判定

| 層級 | 狀態 |
|---|---|
| 原始工程 remediation checklist | **完成** |
| 可重現 corpus／HKSCS runtime baseline | **完成** |
| signed API 35 emulator smoke | **完成，但只是一個環境** |
| 500-case independent language review | **未完成** |
| production quality metrics | **未完成** |
| multi-device/font matrix | **未完成** |
| release rights review 與文化治理 | **未完成** |
| 文化保育級 release claim | **不可作出** |

這份文件保持為未追蹤的 audit evidence，不取代 source license、manifest、test output
或 release owner 的法律與產品責任。
