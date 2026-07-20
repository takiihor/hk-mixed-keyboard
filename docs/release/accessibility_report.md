# Accessibility Qualification

Status: virtual keyboard/symbol nodes and operable candidate controls are
implemented and the API 26/35/36 automated checks pass; assistive-technology
review is OPEN.

Checkpoint 3 passed virtual keyboard and symbol-node routing, screen bounds,
candidate position/reading/selection semantics, localized state announcements,
48dp target policy and theme contrast tests. These checks do not certify real
TalkBack/Switch Access traversal or focus behaviour.

Verify on the final candidate in light/dark, portrait/landscape and large font/display:

- TalkBack explore-by-touch and linear traversal reaches every key and candidate.
- Labels include Cangjie roots, mode, Shift/Caps state, candidate position,
  pronunciation annotation, first-choice state, and expand/collapse purpose.
- Double-tap activation, correction, paging and mode changes retain sensible focus.
- Switch Access completes setup, all three typing modes and correction.
- Every effective key/candidate target is at least 48dp and text/essential controls
  meet WCAG AA contrast.

Record device/build, screen recording, accessibility hierarchy dump, defects and
the names/signatures of two assistive-technology users or one qualified specialist.

## Release-evidence record

The completed review must add
`docs/release/evidence/accessibility_review.json`:

```json
{
  "status": "APPROVED",
  "reviewer": "accessibility-specialist-01",
  "date": "YYYY-MM-DD",
  "commit": "<40-character candidate commit hash>",
  "aab_sha256": "<64-character candidate AAB SHA-256>"
}
```

This record is an external gate. `OPEN`, missing reviewer/date, or an artifact
hash mismatch blocks release verification.
