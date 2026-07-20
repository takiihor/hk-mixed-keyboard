Performance Investigation Notes

Pipeline
- Touch → KeyboardView.onTouchEvent
- Haptic submitted (TypingHapticEngine.perform on UI thread)
- Key dispatch to IME (KeyboardView.emitKey → HkImeService.handleKey)
- Commit/Controller updates state
- Decode candidate request posted to decodeHandler
- Classifier/decoder work on decode thread
- UI thread updates candidate bar

Instrumentation
- BuildConfig.PERF_TRACING gates hot-path tracing.
- PerfTracer.mark/time records events and section timings to HkIme.Perf.
- KeyboardView marks touch_received and key_dispatch with elapsedRealtimeNanos when tracing is enabled.
- HkImeService traces decode_start, classify duration, build_display duration, first_candidate_render.

Known bottlenecks (from code inspection)
1. Obsolete decode runnables queue on a single decode thread; gate prevents stale render, but work still runs and delays latest decode.
2. Haptic cancel+vibrate binder calls occur on UI thread for each press.
3. Debug logging on hot path (BuildConfig.DEBUG) can add overhead in debug builds; now gated by PERF_TRACING.

Experiments
1. With PERF_TRACING on, type rapidly and observe many decode_start/end for intermediate buffers. If classify for the latest buffer completes only after earlier ones, backlog confirmed.
2. Decode coalescing is always active; verify one in-flight plus one latest pending request under rapid typing.
3. Temporarily disable next-char prediction posting to decodeHandler to check contention with classification.

Next steps (potential fixes once validated)
- Keep decode coalescing enabled to ensure only latest generation runs.
- Consider a small debounce on decode requests (e.g. 8–12ms) to collapse bursts.
- Optionally move haptic binder work off UI thread or make cancelBeforeTick conditional in fast-repeat scenarios on problematic devices.
