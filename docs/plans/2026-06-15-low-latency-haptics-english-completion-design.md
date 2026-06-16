# Low-Latency Haptics And English Completion Design

## Scope

Improve two user-facing parts of the keyboard:

- make typing, visual feedback, and vibration feel synchronized without overlapping;
- provide broad English word completion while retaining Chinese suggestions.

## Input And Haptic Timing

The current letter path vibrates on `ACTION_DOWN` but emits the key on
`ACTION_UP`. During fast typing, the vibration for the next key can occur before
the previous key is committed, which feels delayed or doubled.

Normal keys will emit once on `ACTION_DOWN`, immediately after a short, light
haptic. `ACTION_UP` will only clear the pressed visual state. Sliding outside a
key after it has fired will not cancel the already emitted character.

Backspace keeps its existing immediate first delete and repeat timer. It vibrates
only once when initially pressed, not on every repeated delete. The combined
question/exclamation key remains release-based because it needs time to
distinguish tap from long press; a short tap emits `？`, while a long press emits
`！`. It receives one haptic on initial press and no second overlapping haptic.

Use `HapticFeedbackConstants.CLOCK_TICK` for a lighter system-managed pulse. The
existing setting still controls whether haptics are requested.

## English Completion

The bundled `english_assist.csv` already contains roughly fifty thousand rows
with English source words and Chinese meanings. Build a unique English word
index from this data rather than shipping a second dictionary.

For prefixes of at least two letters, return English source words that begin with
the typed prefix, excluding an exact match. Rank each word by its highest corpus
frequency and limit results before merging.

The composing candidate bar merges, in order:

1. personalized English and Chinese candidates;
2. corpus English completions;
3. decoded Chinese suggestions;
4. the literal typed buffer.

Stable text de-duplication prevents repeated English words. This ordering keeps
learned choices first, ensures English completion remains visible, and preserves
Chinese translation and romanization suggestions.

## Performance

Build the English index lazily on the existing decode thread during startup
warm-up. Prefix lookup uses a sorted word list plus binary search, avoiding a
full scan of fifty thousand rows on every key press. Candidate decoding remains
off the main thread.

## Testing

JVM tests cover:

- one-shot normal-key emission on press;
- no duplicate emission on release;
- backspace repeat and punctuation long press remain unchanged;
- `comm` returns `communication`;
- exact English words are excluded from completion;
- completion ranking and limits;
- Chinese suggestions remain present alongside English completion.

Run the full unit-test suite and build a debug APK after focused tests pass.
