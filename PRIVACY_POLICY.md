# 私隱政策 / Privacy Policy — HK Mixed Keyboard

更新日期 / Last updated: 2026-07-15

HK Mixed Keyboard 是一款完全離線的 Android 輸入法。本 App 沒有
`INTERNET` 權限，不含廣告、分析、追蹤、雲端輸入或當機回報 SDK，亦不會向
開發者或第三方傳送輸入內容、詞庫、設定、使用統計或當機資料。

HK Mixed Keyboard is a fully offline Android input method. It has no `INTERNET`
permission and no advertising, analytics, tracking, cloud-input or crash-reporting
SDK. Typed content, dictionaries, settings, usage statistics and crash data are
never sent to the developer or a third party.

## 本機處理及儲存 / On-device processing and storage

- 按鍵、目前組字和游標前少量文字只在記憶體中即時處理，以提供候選、標點及
  提交功能；目前組字不會作為逐字輸入紀錄保存。
- 您選用的候選及其輸入碼可保存於 App 私有 Room 資料庫
  `hk_user_memory.db`，用於本機排序學習。您手動新增的速成、粵拼或拼音詞條
  亦保存於同一資料庫。
- 模式、主題、震動、聲音、鍵盤高度、單手模式及簡體輸出等偏好保存在 App
  私有 DataStore。
- 以上資料保留至您在設定選擇「清除所有個人詞庫」、刪除個別自訂詞語、清除
  App 資料或解除安裝。學習資料沒有伺服器副本，雲端備份已停用。

Keystrokes, the active composition and limited preceding context are processed in
memory to produce candidates, punctuation and commits; the active composition is
not kept as a typed-text log. Selected candidates and their input codes may be
stored in the app-private Room database `hk_user_memory.db` for local ranking, and
manually added Quick, Jyutping or Pinyin entries are stored there too. Preferences
are stored in the app-private DataStore. Data remains until you clear the personal
dictionary, delete an entry, clear app data or uninstall. Backup is disabled.

## 敏感欄位 / Sensitive fields

密碼、數字密碼，以及付款、銀行、信用卡、安全碼和一次性密碼提示欄位會啟用
安全模式：不顯示預測或候選歷史、不讀取前文，亦不學習或保存該欄位輸入。

Password and numeric-password fields, plus fields identifying payment, banking,
card security or one-time-code input, activate safe mode: predictions, candidate
history, preceding-context inspection and learning are disabled.

## 匯入、匯出及刪除 / Import, export and deletion

匯出功能只在您選擇的位置建立包含自訂詞語、模式及輸入碼的 CSV；該檔案之後
由您控制。匯入由 Android 文件選擇器授權，並限制檔案大小、行數、行長、詞語
及輸入碼；必須為有效 UTF-8，任何錯誤會取消整批匯入。設定可刪除個別自訂
詞語或確認後清除所有學習及自訂詞庫。

Export creates a CSV of custom entries, schemes and codes only at a location you
choose. Import uses Android's document picker, is size/row/line/text/code bounded,
requires valid UTF-8 and is atomic. Settings can delete individual entries or,
after confirmation, all learned and custom dictionary data.

## 權限及分享 / Permissions and sharing

- `VIBRATE`：只用於可選的按鍵觸覺回饋。
- 無網絡權限、無資料分享、無銷售資料、無第三方分析或廣告。
- `allowBackup=false`；個人詞庫不會進入 Android 雲端備份。

## 兒童私隱 / Children's privacy

本 App 不會在知情下收集任何人的個人資料，包括兒童。The app does not
knowingly collect personal data from anyone, including children.

## 聯絡 / Contact

私隱查詢 / Privacy questions: mehlw2011@gmail.com
