# Privacy, Data and Corpus Sign-off

Status: engineering inventory and merged-debug-APK policy checks pass;
owner/legal signatures and current public-policy deployment are OPEN.

The final source manifest requests only `VIBRATE`, has no `INTERNET` permission,
disables backup, exports only the launcher settings activity and the permission-
protected IME service, and disables learning/suggestions in password, payment,
banking and one-time-code hinted fields. Custom dictionary import is SAF-scoped,
size/row/line/code/display bounded, strict UTF-8, formula-prefix guarded and atomic.

Every shipped corpus entry must remain represented in
`corpus/sources/corpus_manifest.json` and `corpus_license_register.csv` with source,
version/date, checksum, transformation, licence and notice. The engineering verifier
must reproduce/check all declared assets before signature.

Checkpoint 3 verifies the merged debug APK contains only `VIBRATE`, no `INTERNET`,
backup disabled and the approved exported-component set. The public privacy URL
returns HTTP 200 but still serves the policy dated 2026-06-16; redeploy and exact
content verification remain required.

| Approval | Owner | Date | Evidence/signature |
|---|---|---|---|
| GPL/LGPL and derived Quick data | OPEN | | |
| CC BY/CC BY-SA Jyutping/Pinyin/English data | OPEN | | |
| HKSCS/data.gov.hk terms | OPEN | | |
| OpenCC/Apache data | OPEN | | |
| Play Data Safety from final AAB | OPEN | | |
| Public privacy URL matches app copy | OPEN | | |

## Release-evidence record

Only the responsible legal/data owner may add
`docs/release/evidence/legal_signoff.json` after all table rows are approved:

```json
{
  "status": "APPROVED",
  "owner": "legal-owner-01",
  "date": "YYYY-MM-DD",
  "commit": "<40-character candidate commit hash>",
  "aab_sha256": "<64-character candidate AAB SHA-256>"
}
```

The release verifier treats a missing or non-approved record as an external
NO-GO; an engineering assertion cannot substitute for this signature.
