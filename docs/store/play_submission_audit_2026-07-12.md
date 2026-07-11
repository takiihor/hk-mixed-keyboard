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

### 1. 簽名 keystore（硬性阻塞）
呢部機**冇** `keystore.properties` 亦冇 `HKKBD_*` 環境變數，所以而家個 AAB 係**未簽名**，Play 唔收。
之前簽過 v0.46 AAB，即係 keystore 應該喺你手上：
- 搵返個 keystore 檔案，放喺 `android/app/keystore.properties`（參考 `.sample`）或設定 `HKKBD_STORE_FILE` 等環境變數
- 再跑 `./gradlew :app:bundleRelease -PversionedBuild=true`
- ⚠️ 用 Play App Signing：呢個 key 係 upload key，第一次上載時 Play 會問你登記

### 2. 私隱政策公開 URL（Play Console 必填欄位）
- 已生成可直接 host 嘅 `docs/store/privacy_policy.html`
- 最簡單：push 個 repo 上 GitHub → Settings → Pages → 開 `docs/` folder → URL 就係 `https://<user>.github.io/hk-mixed-keyboard/store/privacy_policy.html（視乎 Pages 設定）`

### 3. Play Console 商店資料
- **512×512 icon**：已生成 `docs/store/assets/play_store_icon_512.png`
- **Feature graphic 1024×500**：已生成 `docs/store/assets/feature_graphic_1024x500.png`（想靚啲可以自己再整）
- **手機截圖 ≥2 張**：要喺真機／模擬器影（建議：打字中 + 候選欄 + 設定頁）
- 應用名稱、簡介（80字）、詳細描述

### 4. 真機 AAB 測試
用 bundletool 或 Play internal testing track 裝一次簽名版，行一次完整打字流程（速成、粵拼、簡體輸出、自訂詞、匯出匯入）。

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
