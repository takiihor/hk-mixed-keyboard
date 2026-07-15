# Privacy, Data and Corpus Sign-off

Status: engineering inventory complete; owner/legal signatures and public-policy verification are OPEN.

The final source manifest requests only `VIBRATE`, has no `INTERNET` permission,
disables backup, exports only the launcher settings activity and the permission-
protected IME service, and disables learning/suggestions in password, payment,
banking and one-time-code hinted fields. Custom dictionary import is SAF-scoped,
size/row/line/code/display bounded, strict UTF-8, formula-prefix guarded and atomic.

Every shipped corpus entry must remain represented in
`corpus/sources/corpus_manifest.json` and `corpus_license_register.csv` with source,
version/date, checksum, transformation, licence and notice. The engineering verifier
must reproduce/check all declared assets before signature.

| Approval | Owner | Date | Evidence/signature |
|---|---|---|---|
| GPL/LGPL and derived Quick data | OPEN | | |
| CC BY/CC BY-SA Jyutping/Pinyin/English data | OPEN | | |
| HKSCS/data.gov.hk terms | OPEN | | |
| OpenCC/Apache data | OPEN | | |
| Play Data Safety from final AAB | OPEN | | |
| Public privacy URL matches app copy | OPEN | | |
