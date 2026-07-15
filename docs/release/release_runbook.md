# Release Runbook

Status: engineering workflow implemented; production credentials and approvals remain external gates.

## Preconditions

- Use a clean clone at an annotated release tag.
- Confirm the current Google Play target-API policy. As checked on 2026-07-15,
  new apps and updates require API 35 or newer; this project targets API 36.
  Source: https://developer.android.com/google/play/requirements/target-sdk
- Keep the upload keystore and recovery material outside Git. Set
  `HKKBD_STORE_FILE`, `HKKBD_STORE_PASSWORD`, `HKKBD_KEY_ALIAS`, and
  `HKKBD_KEY_PASSWORD` only in the controlled release environment.
- Pin an official bundletool release and set `BUNDLETOOL_JAR` to its verified jar.
- Intentionally update `android/app/version.properties`; builds never mutate it.

## Clean verification and build

```bash
cd android
./gradlew clean test lintRelease connectedDebugAndroidTest bundleRelease
cd ..
PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest -q corpus/tools/tests
python3 corpus/tools/verify_corpus_manifest.py
scripts/verify_release.sh android/app/build/outputs/bundle/release/app-release.aab \
  | tee docs/release/final_artifact_metadata.txt
```

Create installable APKs from that exact AAB with bundletool, install the universal
APK plus device-specific splits on the supported-device matrix, then repeat the
three-mode smoke, migration, privacy, accessibility and supplementary-HKSCS checks.
Do not substitute a separately built APK.

## Evidence and submission

Record the tag, Git commit, version, AAB SHA-256, certificate SHA-256, corpus
manifest SHA-256, CI run, device results, Data Safety answers and every required
owner signature in `three_mode_release_evidence.md`. Upload only that verified AAB.
If any P0/P1, native-language, accessibility, legal, device, beta or competitor
gate is open, the decision remains NO-GO.
