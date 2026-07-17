# Process-Scoped Corpus Lifecycle Design

## Problem

Physical-device testing confirmed that ordinary keyboard switch-backs are fast
after removing main-thread Pinyin initialization, but rapid IME rebinds can grow
the process to 565 MB until Android kills it.

Each `HkImeService` instance currently creates its own `CorpusLoader` and starts
a full background warm-up. When Android switches to another IME,
`warmThread.quitSafely()` allows the current warm-up runnable to finish. If the
user switches back before it finishes, the new service creates another loader
and starts another warm-up in the same process. Multiple immutable corpus index
graphs then coexist until the old services and threads become collectible.

## Design

Own one lazy `CorpusLoader` in `HkApplication`, whose lifetime already matches
the Android process. `HkImeService` obtains that shared loader instead of
constructing a service-scoped loader.

The corpus data and indexes are immutable after their lazy construction. Kotlin
lazy initialization is synchronized, so overlapping service warm-up threads
will wait for or reuse the same index rather than building duplicate graphs.
When the process is killed normally, the application and corpus are released
together; a later cold process starts with a fresh loader.

Keep `CorpusBackedDecoder`, custom-word indexes, composition state, settings
collectors, haptics, and all UI references service-scoped. The shared loader
contains no service or activity context: it receives only the application
context. The existing volatile Pinyin fuzzy preference remains safe across
service instances, and the newest settings emission updates it.

## Alternatives Considered

- Replacing `quitSafely()` with immediate looper shutdown cannot interrupt a
  corpus index already being built and can still leave multiple large builds
  running concurrently.
- Checking a cancellation flag between warm-up stages reduces later work but
  cannot cancel the current large index construction.
- Warming only the active scheme lowers startup work but makes the first
  in-keyboard mode switch slower and still permits duplicate active indexes.

## Error Handling

If the application instance is unavailable or has an unexpected type, the
service falls back to a loader created from `applicationContext`, preserving the
current ability to start in unusual test or platform environments. Corpus cache
failures retain their existing best-effort CSV fallback.

## Testing

Add a JVM test around a small process-corpus owner to prove that repeated service
acquisitions invoke the loader factory once and return the same instance. Keep
the existing preference-loading regression to prove settings do not initialize
Pinyin on the main thread.

Run focused tests, all JVM tests, and the debug build. Install the APK on the
connected phone and repeat ordinary and forced-cold switch timing. Then run a
rapid switch burst and confirm that the HK process remains alive, corpus warm-up
memory stays bounded near the single-loader baseline, and exit history records
no system memory kill, crash, or ANR.
