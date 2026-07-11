# Google Play 上架檢查清單 — HK Mixed Keyboard

_更新：2026-07-12（corpus 清理 + 簡體輸出功能之後）_

## ✅ 已完成（本地驗證過）

| 項目 | 狀態 |
|---|---|
| targetSdk 36 / compileSdk 36 | ✅ 符合 2026-08-31 起嘅新 app 政策 |
| minSdk 26 | ✅ |
| 唯一權限：`VIBRATE` | ✅ 冇 INTERNET 權限 — 私隱賣點 |
| `allowBackup=false` + backup/data-extraction rules | ✅ |
| Adaptive icon（foreground/background/monochrome） | ✅ mipmap-anydpi |
| R8 minify release build | ✅ |
| Debug panel 喺 release 關閉 | ✅ SHOW_DEBUG_PANEL=false |
| Lint（release） | ✅ 0 errors（僅 legacy icon 警告，API 26+ 不使用） |
| 私隱政策 app 內版 | ✅ PrivacyPolicyActivity |
| 開源授權聲明 app 內版 | ✅ NOTICE + GPL/CC/ODbL/Apache 全文 |
| 敏感欄位安全模式 | ✅ 密碼欄唔顯示候選、唔學習 |
| 單元測試 | ✅ 189/189 |
| AAB build（`:app:bundleRelease`） | ✅ 產出成功 |

## ❌ 上架前必須完成（需要你行動）

### 1. ~~簽名 keystore~~ ✅ 已解決（2026-07-12）
Keystore 搵返喺 `~/.local/share/hk-mixed-keyboard/`（credentials 喺 `upload-key.env`）。
已簽名 AAB：`android/app/build/outputs/bundle/release/app-release.aab`（v0.55.0，`jarsigner` 驗證通過）。
上載時記得喺 Play Console 登記 Play App Signing。

### 2. ~~私隱政策公開 URL~~ ✅ 已解決（2026-07-12）
Repo 已公開發佈：https://github.com/takiihor/hk-mixed-keyboard （同時滿足 GPL 來源要求）
**Play Console 私隱政策 URL：https://takiihor.github.io/hk-mixed-keyboard/store/privacy_policy.html** （已驗證 200 OK）

### 3. Play Console 商店資料 — 素材已備齊 ✅
- **512×512 icon**：`docs/store/assets/play_store_icon_512.png`
- **Feature graphic 1024×500**：`docs/store/assets/feature_graphic_1024x500.png`
- **手機截圖 ×5（1080×2400，模擬器實拍）**：`docs/store/assets/screenshots/`
  （速成鍵盤＋繁▸簡chip、候選列、聯想預測、簡體輸出、粵拼分詞）
- 名稱／簡介／描述文案：`docs/store/name_*.txt`、`short_*.txt`、`long_*.md`
- 剩低：喺 Play Console 逐項貼上

### 4. 裝置測試 — 模擬器已通過 ✅（建議真機再過一次）
2026-07-12 喺 Android 15 模擬器（簽名 release v0.55.0）完整冒煙測試通過：
IME 註冊啟用、速成 ai→時（零簡體）、候選提交、逐字組詞預測、繁▸簡 chip 切換、
簡體輸出（時→时）、速成↔粵拼切換、粵拼連打 neihou→你好、長按⌨切換、設定跨scheme持久。
上架前建議喺真機（不同廠商 ROM）快速再過一次。

## 📋 Data Safety 表格答案（照抄）

| 問題 | 答案 |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | N/A（冇收集） |
| Do you provide a way for users to request that their data is deleted? | N/A（冇收集；本地詞庫可喺 app 內清除） |

理據：鍵盤輸入處理全部喺裝置內進行；個人詞庫只存本地 Room DB；app 冇 INTERNET 權限，物理上冇可能上傳。

## 📋 內容分級問卷
- 冇暴力／色情／賭博內容 → 預期評級：3+／PEGI 3
- 類別建議：Tools（工具）

## ⚠️ 注意事項（非阻塞，但要知）

1. **GPL 數據合規**：速成字表衍生自 rime-cangjie（GPL）。NOTICE 已聲明並附全文，但 GPL 對「衍生數據」嘅要求係要提供來源 — 最穩陣做法係**公開個 GitHub repo**（同時解決咗私隱政策 hosting）。CC-BY-SA 詞庫（CC-CEDICT）同理。
2. **IME 政策**：Google 對鍵盤 app 嘅重點審查係「有冇偷 keystroke」——本 app 冇 INTERNET 權限，係最強證明。喺商店描述度寫明「完全離線、零數據收集」有利審核同下載轉化。
3. **上載前記得 commit**：而家成個 working tree 都係未 commit 狀態（包括今次所有修復）。
