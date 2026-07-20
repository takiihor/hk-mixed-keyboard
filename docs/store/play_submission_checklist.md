# Google Play Submission Checklist

Status: **NO-GO** until `docs/release/go_no_go.md` changes against a signed final AAB.

## App identity

- Store name: HK Quick Jyutping Pinyin / HK 混合鍵盤：速成／粵拼／普通話拼音
- Package: `com.hkmixedkeyboard`
- Planned engineering candidate: `0.64.0` / version code `79`
- minSdk 26; targetSdk/compileSdk 36
- Category: Tools / keyboard/input method

## Privacy and Data Safety

- Source manifest permits `VIBRATE`, has no `INTERNET`, and disables backup.
- Final AAB permissions and dependency tree must be checked by
  `scripts/verify_release.sh` and signed in the evidence index.
- Public privacy URL: https://takiihor.github.io/hk-mixed-keyboard/store/privacy_policy.html
- Redeploy `docs/store/privacy_policy.html`, then verify the public response exactly
  matches the final in-app/store policy. On 2026-07-15 the URL returned HTTP 200,
  but its SHA-256 differed and it still displayed `Last updated: 2026-06-16`;
  current-content deployment remains OPEN.
- Complete Play Data Safety from `docs/release/play_data_safety.md` only after the
  final AAB check.

## Listing and artefacts

- Text sources: `name_*`, `short_*`, `long_*` in this directory.
- Icon and feature graphic exist, but all phone/tablet screenshots must be recaptured
  from APKs generated from the final signed AAB and must show all three modes.
- Do not publish or advertise “best-in-class” while any locked benchmark,
  competitor-relative, reviewer, device, accessibility, legal or beta gate is open.

## Build and verify

Update `android/app/version.properties` once in a reviewed source commit. Gradle is
read-only and never auto-increments it. In a clean tagged checkout with external
signing environment variables:

```bash
cd android
./gradlew clean test lintRelease connectedDebugAndroidTest bundleRelease
cd ..
PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest -q corpus/tools/tests scripts/tests
scripts/verify_release.sh android/app/build/outputs/bundle/release/app-release.aab
```

Generate tester/device APKs from that AAB with the pinned bundletool. Never use a
separately built debug or release APK as evidence for the uploaded bundle.

## Required approvals

Complete the three-mode locked/competitor results, physical device matrix,
performance targets, TalkBack/Switch Access review, corpus/legal approval, signed
artifact/certificate record and 100-person two-week closed beta. Link every result
to the exact Git commit and AAB SHA-256.
