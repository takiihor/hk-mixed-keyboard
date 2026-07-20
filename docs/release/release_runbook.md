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
- Set `HKKBD_EXPECTED_CERT_SHA256` to the approved 64-hex signing-certificate
  digest. Verification fails closed if it or the keystore inputs are absent.
- Pin an official bundletool release and set `BUNDLETOOL_JAR` to its verified jar.
- Intentionally update `android/app/version.properties`; builds never mutate it.
- Supply a controlled `docs/release/evidence/` directory (or set
  `HKKBD_RELEASE_EVIDENCE_DIR`) only after external owners create all six
  `APPROVED` JSON records. Each record has `status`, `date`, `commit` and
  `aab_sha256`, plus `reviewer` for `native_review`/`accessibility_review` or
  `owner` for `device_beta_matrix`, `legal_signoff`, `signed_aab` and
  `beta_result`. Every hash must match the candidate passed to the verifier.

## Clean verification and build

```bash
cd android
./gradlew clean test lintRelease connectedDebugAndroidTest bundleRelease
cd ..
PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest -q corpus/tools/tests scripts/tests
python3 corpus/tools/verify_corpus_manifest.py
python3 corpus/tools/verify_benchmark_templates.py
sha256sum --check docs/release/legal_assets.sha256
scripts/verify_release.sh android/app/build/outputs/bundle/release/app-release.aab \
  | tee docs/release/final_artifact_metadata.txt
```

`verify_release.sh` creates and signature/policy-checks a universal APK from that
exact AAB. It first rejects an incomplete or mismatched external-evidence bundle,
before signature or APK work. Install it plus device-specific splits on the supported-device matrix,
then repeat the three-mode smoke, migration, privacy, accessibility and
supplementary-HKSCS checks.
Do not substitute a separately built APK.

## Evidence and submission

Record the tag, Git commit, version, AAB SHA-256, certificate SHA-256, corpus
manifest SHA-256, CI run, device results, Data Safety answers and every required
owner signature in `three_mode_release_evidence.md`. Upload only that verified AAB.
If any P0/P1, native-language, accessibility, legal, device, beta or competitor
gate is open, the decision remains NO-GO.
