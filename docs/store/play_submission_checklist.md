# Google Play submission checklist

Use this with the release AAB from `android/app/build/outputs/bundle/release/`.

## App identity

- App name: HK Mixed Keyboard
- Package name: `com.hkmixedkeyboard`
- Current release version: `0.55.0`
- Current version code: `70`
- Category: Tools / Keyboard / Input method

## Privacy policy

Google Play requires a public privacy-policy URL in Play Console. Host
`docs/store/privacy_policy.html` as a public web page and use that URL in:

- Play Console > Policy and programs > App content > Privacy Policy
- Store listing privacy-policy field, if shown

The same policy content is available in the app from Settings > 私隱政策.

## Data Safety

Based on the current manifest and source:

- Data collection: No user data collected off device.
- Data sharing: No data shared.
- Network access: No `INTERNET` permission.
- Ads: No ads.
- Analytics: No analytics SDK.
- Account creation: No.
- User data deletion: In-app local deletion is available from Settings >
  清除所有個人詞庫. No server-side account/data deletion URL is needed because the app
  has no accounts and no server-side storage.
- Security practices: Data is processed on device only. Cloud backup and device
  transfer are disabled by manifest backup rules.

Important: if a future build adds network, analytics, crash reporting, ads, or
third-party SDKs, update the Data Safety form before uploading.

## Permissions

- `android.permission.VIBRATE`: optional key-press haptic feedback.
- `android.permission.BIND_INPUT_METHOD`: required by Android for the IME service.
  This is bound by the system and is not a normal user-granted permission.

## Open-source notices

The app includes open-source notices and full license texts at:

- `android/app/src/main/assets/licenses/NOTICE.txt`
- `android/app/src/main/assets/licenses/GPL-3.0.txt`
- `android/app/src/main/assets/licenses/CC-BY-SA-4.0.txt`
- `android/app/src/main/assets/licenses/CC-BY-4.0.txt`
- `android/app/src/main/assets/licenses/ODbL-1.0.txt`

The in-app Settings > 開源授權與資料來源 screen displays these notices.

## Upload artifacts

- Play upload AAB: `android/app/build/outputs/bundle/release/app-release.aab`
- Tester APK: `android/app/build/outputs/apk/release/app-release-0.55.0.apk`

Before uploading, verify that both artifacts report the release version/version code
listed above and contain every feature advertised by the store listing. For a Pinyin
release, both commands below must print a `pinyin.csv` entry:

```bash
unzip -l android/app/build/outputs/bundle/release/app-release.aab | grep 'base/assets/corpus/pinyin.csv'
unzip -l android/app/build/outputs/apk/release/app-release-*.apk | grep 'assets/corpus/pinyin.csv'
```

The upload keystore is outside the repo at:

- Environment file: `~/.local/share/hk-mixed-keyboard/upload-key.env`
- Keystore file path is recorded inside that env file.

Source the env file before building release artifacts:

```bash
set -a
. ~/.local/share/hk-mixed-keyboard/upload-key.env
set +a
cd android
./gradlew bundleRelease assembleRelease
```

To intentionally bump the public release version before a new store upload:

```bash
./gradlew bundleRelease assembleRelease -PversionedBuild=true
```

Use `-PversionedBuild=true` exactly once for a release. Subsequent verification
rebuilds should omit it so they do not select another public version.
