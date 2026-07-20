# Keyboard Switch-Return Latency Design

## Problem

After selecting another system keyboard and switching back, the HK Mixed
Keyboard view can remain completely absent for several seconds.

The IME settings collector currently applies the Pinyin fuzzy-input preference
by dereferencing `CorpusLoader.pinyinDecoder` on the main thread. That property
is lazy, but its first access parses or reads the 122,093-row Pinyin corpus and
builds `PinyinLexicon` indexes. Because the first settings emission can run
before Android creates the input view, this initialization blocks presentation
of the entire keyboard. It also defeats the existing background corpus warm-up.

## Design

Keep the Pinyin fuzzy-input preference in `CorpusLoader` as a lightweight,
thread-visible value. Applying settings updates only that value and must not
dereference the lazy Pinyin decoder.

Construct `PinyinDecoder` with a preference provider that reads the current
value. The decoder and lexicon remain lazy, so their first construction occurs
on the existing warm-up or decode thread. Later preference changes remain
visible without rebuilding the corpus or queueing a setter behind corpus work.

The activation flow becomes:

1. `KeyboardSettings.flow` emits preferences.
2. `HkImeService` stores the Pinyin fuzzy preference through `CorpusLoader`
   without loading corpus data.
3. Android can create and show the keyboard view immediately.
4. The existing warm-up thread builds the active scheme first, then the other
   corpus indexes in the background.
5. Pinyin decoding reads the latest fuzzy preference when it runs.

## Alternatives Considered

- Posting the existing decoder setter to a background queue would remove the
  main-thread block, but later preference updates could wait behind long corpus
  warm-up work.
- Sharing corpus indexes across service instances could reduce repeated work,
  but introduces process-lifetime memory retention and broader lifecycle
  changes that are unnecessary for restoring prompt keyboard presentation.

## Error Handling

Corpus parsing and decoder warm-up keep their current best-effort behavior. The
new preference update performs no I/O and cannot turn a recoverable corpus-load
failure into a keyboard-presentation failure.

## Testing

Add a JVM regression test that creates a `CorpusLoader`, applies the Pinyin
fuzzy preference, and asserts that the lazy Pinyin decoder remains
uninitialized. Then initialize a controlled decoder and verify it observes the
stored preference. Run the focused regression and Pinyin suites followed by the
complete Android JVM test suite and a debug build.
