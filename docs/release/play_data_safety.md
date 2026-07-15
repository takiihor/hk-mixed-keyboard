# Play Data Safety Working Copy

Status: engineering answers prepared; final Play Console submission against the signed AAB is OPEN.

- Data collected: none sent off device.
- Data shared: none.
- Security practices: data is not transferred; cloud backup is disabled; users can
  delete learned/custom data in-app or by clearing app data/uninstalling.
- Local-only data: selected candidate/code counts, custom dictionary entries and
  keyboard preferences, as described in the privacy policy.
- Permissions in source: `VIBRATE` only. The release verifier must confirm the same
  in the final AAB and absence of `INTERNET`.
- Ads, analytics, tracking, crash reporting and cloud processing: none.
- Account creation: none.

The release owner must compare every console answer with the final dependency tree,
merged manifest, public privacy URL and final AAB, then record date and signature in
the evidence index.
