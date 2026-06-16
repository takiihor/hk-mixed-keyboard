package com.hkmixedkeyboard.ime

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import com.hkmixedkeyboard.BuildConfig
import com.hkmixedkeyboard.commit.*
import com.hkmixedkeyboard.decoder.CorpusBackedDecoder
import com.hkmixedkeyboard.decoder.CorpusLoader
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.engine.CandidateDisplayPolicy
import com.hkmixedkeyboard.engine.Classifier
import com.hkmixedkeyboard.memory.CustomWordDao
import com.hkmixedkeyboard.memory.IUserMemory
import com.hkmixedkeyboard.memory.RoomUserMemory
import com.hkmixedkeyboard.memory.UserMemoryDatabase
import com.hkmixedkeyboard.performance.LatencyLogger
import com.hkmixedkeyboard.performance.PerfTracer
import com.hkmixedkeyboard.privacy.SensitiveFieldDetector
import com.hkmixedkeyboard.settings.KeyboardSettings
import com.hkmixedkeyboard.ui.CandidateBarView
import com.hkmixedkeyboard.ui.CandidateGridView
import com.hkmixedkeyboard.ui.EmojiPanelView
import com.hkmixedkeyboard.ui.AndroidTypingHapticBackend
import com.hkmixedkeyboard.ui.KeyboardLayout
import com.hkmixedkeyboard.ui.KeyboardView
import com.hkmixedkeyboard.ui.ShiftState
import com.hkmixedkeyboard.ui.ShiftStateController
import com.hkmixedkeyboard.ui.SymbolPageView
import com.hkmixedkeyboard.ui.TypingHapticEngine
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HkImeService : InputMethodService() {

    companion object {
        private const val TAG = "HkImeService"
        private const val LATENCY_LOG_TAG = "HkIme.Latency"
    }

    // A failure inside a launched coroutine would otherwise reach the thread's
    // default uncaught handler and crash the whole process (killing the IME).
    // Log and swallow instead — background persistence is best-effort.
    private val coroutineErrorHandler = CoroutineExceptionHandler { _, e ->
        android.util.Log.e(TAG, "Background coroutine failed", e)
    }
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + coroutineErrorHandler)

    private lateinit var corpus: CorpusLoader
    private lateinit var decoder: CorpusBackedDecoder
    private lateinit var classifier: Classifier
    private lateinit var commitController: CommitController
    private var memory: RoomUserMemory? = null
    private var customWordDao: CustomWordDao? = null
    private var soundEnabled = false
    private val audioManager by lazy {
        getSystemService(AUDIO_SERVICE) as? android.media.AudioManager
    }

    private val mainThread = Handler(Looper.getMainLooper())
    private val decodeThread = HandlerThread("HkDecode").also { it.start() }
    private val decodeHandler = Handler(decodeThread.looper)
    private val candidateRequestGate = CandidateRequestGate()
    // Decode coalescing + debounce. Kept at 0 ms for lowest candidate latency:
    // the in-flight/pending coalescing below already collapses bursts of fast
    // keystrokes into at most one in-flight + one pending decode, so no artificial
    // delay is needed to avoid redundant work.
    private val DECODE_DEBOUNCE_MS = 0L
    private var decodeInFlight = false
    private var decodeScheduled: Runnable? = null
    private var decodePendingBuffer: String? = null
    private var decodePendingGeneration: Long = 0L
    private val candidateDisplayPolicy = CandidateDisplayPolicy()

    private var imeState = ImeStateData()
    private var imeCtx = ImeContext()
    private var vibrationEnabled = true
    private var showCangjieRoots = true
    // null until the first prefs emission is observed, so we don't treat a persisted
    // token as a fresh clear and wipe the cache that init() just loaded.
    private var memoryClearToken: Long? = null
    private val shiftController = ShiftStateController()

    // Single low-latency haptic engine shared by every keyboard surface (main keys,
    // candidate bar/grid, symbol and emoji panels). One backend = one worker thread
    // and one capability probe, and one source for the haptic-submitted latency log.
    private var typingHaptics: TypingHapticEngine? = null

    private lateinit var candidateBar: CandidateBarView
    private lateinit var keyboardView: KeyboardView
    private lateinit var inputRoot: LinearLayout
    private var debugPanel: View? = null
    private var candidateGrid: CandidateGridView? = null
    // Prediction coalescing + debounce. 0 ms — next-character predictions should
    // appear immediately after a commit (see DECODE_DEBOUNCE_MS).
    private val PREDICT_DEBOUNCE_MS = 0L
    private var predictScheduled: Runnable? = null
    private var lastCandidates: List<DecodeCandidate> = emptyList()

    // Run of Chinese characters just committed, used for next-character prediction
    // (逐字組詞): after committing 我 the bar offers 們/哋/…; tapping one extends it.
    private var committedPrefix: String = ""
    private var altPanel: View? = null  // symbol page or emoji panel

    // In-memory, non-persistent fallback used if the Room database fails to open,
    // so typing and in-session memory keep working instead of crashing the IME.
    private val fallbackMemory = com.hkmixedkeyboard.memory.UserMemory()

    private val ctrl: CommitController get() = commitController

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
            typingHaptics = TypingHapticEngine(AndroidTypingHapticBackend(this)) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.d(
                        LATENCY_LOG_TAG,
                        "haptic-submitted ns=${android.os.SystemClock.elapsedRealtimeNanos()}"
                    )
                }
                // Perf trace source point for haptic submission
                PerfTracer.mark("haptic_submitted")
            }
        // Core typing pipeline — must always initialize.
        corpus = CorpusLoader(applicationContext)
        decoder = CorpusBackedDecoder(corpus)
        classifier = Classifier(decoder)

        // Persistence is best-effort. If Room can't open, fall back to in-memory
        // so the keyboard still works.
        try {
            val db = UserMemoryDatabase.get(applicationContext)
            val mem = RoomUserMemory(db.dao(), serviceScope)
            memory = mem
            customWordDao = db.customWordDao()
            serviceScope.launch { mem.init() }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Room init failed; using in-memory fallback", e)
            memory = null
            customWordDao = null
        }
        rebuildCommitController()
        reloadCustomWords()

        // Observe settings changes and update imeCtx on main thread
        serviceScope.launch {
            KeyboardSettings.flow(applicationContext)
                .catch { e -> android.util.Log.e(TAG, "Settings flow failed", e) }
                .collectLatest { prefs ->
                    val newSpaceMode = if (prefs.smartSpace) SpaceMode.SMART_COMMIT else SpaceMode.ALWAYS_SPACE
                    val newScheme = if (prefs.jyutpingPrimary) com.hkmixedkeyboard.decoder.Scheme.JYUTPING else com.hkmixedkeyboard.decoder.Scheme.QUICK
                    mainThread.post {
                        val schemeChanged = imeCtx.scheme != newScheme
                        imeCtx = imeCtx.copy(spaceMode = newSpaceMode, scheme = newScheme, jyutpingPrimary = prefs.jyutpingPrimary)
                        rebuildCommitController()
                        // A half-typed code is meaningless under the new scheme
                        // (Quick codes ≠ Jyutping), so clear it on every switch.
                        if (schemeChanged) resetCompositionState()
                        vibrationEnabled = prefs.vibration
                        soundEnabled = prefs.sound
                        showCangjieRoots = prefs.showRoots
                        // The user cleared their dictionary: flush live caches now so
                        // suggestions stop appearing without waiting for a restart.
                        // Skip the very first emission (just record the persisted token).
                        if (memoryClearToken == null) {
                            memoryClearToken = prefs.memoryClearToken
                        } else if (prefs.memoryClearToken != memoryClearToken) {
                            memoryClearToken = prefs.memoryClearToken
                            memory?.clearCache()
                            fallbackMemory.clear()
                            reloadCustomWords()
                            if (::candidateBar.isInitialized) candidateBar.clear()
                            lastCandidates = emptyList()
                        }
                        if (::keyboardView.isInitialized) {
                            keyboardView.vibrationEnabled = vibrationEnabled
                            // Cangjie roots only make sense for Quick; hide them in 粵拼.
                            keyboardView.showCangjieRoots = showCangjieRoots && newScheme != Scheme.JYUTPING
                            keyboardView.spaceLabel = schemeName(newScheme)
                            keyboardView.modeLabel = schemeShort(newScheme)
                            keyboardView.invalidate()
                        }
                        if (schemeChanged && ::candidateBar.isInitialized) candidateBar.clear()
                        if (::candidateBar.isInitialized) {
                            candidateBar.vibrationEnabled = vibrationEnabled
                        }
                        candidateGrid?.vibrationEnabled = vibrationEnabled
                    }
                }
        }

        // Warm the corpus indices on the decode thread so first keypress is fast
        decodeHandler.post {
            corpus.quickIndex  // triggers lazy load
            corpus.quickPrefixCandidateIndex
            corpus.nextCharIndex
            corpus.englishCompletionIndex
            corpus.englishAssistPrefixIndex
            corpus.jyutpingPrefixIndex
            // Probe vibrator capabilities now so the first keystroke's haptic isn't
            // delayed by the one-time binder query.
            typingHaptics?.warmUp()
            PerfTracer.mark("decode_thread_warm")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelCandidateDecode()
        serviceJob.cancel()
        decodeThread.quitSafely()
        typingHaptics?.release()
        typingHaptics = null
    }

    override fun onCreateInputView(): View {
        return try {
            buildInputView()
        } catch (e: Exception) {
            // Never let a view-build failure leave the IME with no input view
            // (that manifests as "keyboard won't open"). Log and show a minimal
            // fallback keyboard so the user can still type.
            android.util.Log.e(TAG, "onCreateInputView failed; using fallback", e)
            buildFallbackInputView()
        }
    }

    private fun buildInputView(): View {
        val root = LinearLayout(this).also { inputRoot = it }.apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(androidx.core.content.ContextCompat.getColor(
                this@HkImeService, com.hkmixedkeyboard.R.color.keyboard_bg))
        }

        // Candidate bar height in density-independent pixels (≈ 48dp).
        val candidateBarHeight = (56 * resources.displayMetrics.density).toInt()

        candidateBar = CandidateBarView(this).apply {
            vibrationEnabled = this@HkImeService.vibrationEnabled
            haptics = typingHaptics
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, candidateBarHeight
            )
            candidateListener = object : CandidateBarView.CandidateListener {
                override fun onCandidateTap(candidate: DecodeCandidate) = handleCandidateTap(candidate)
                override fun onExpandTap() = handleExpandTap()
            }
        }

        // Use an explicit pixel height. A raw View with WRAP_CONTENT ignores
        // minimumHeight under AT_MOST/EXACTLY measure specs, which can make the
        // keyboard render at the wrong height.
        val keyboardHeight = keyboardHeightPx()
        keyboardView = KeyboardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, keyboardHeight
            )
            minimumHeight = keyboardHeight
            vibrationEnabled = this@HkImeService.vibrationEnabled
            haptics = typingHaptics
            showCangjieRoots = this@HkImeService.showCangjieRoots && imeCtx.scheme != Scheme.JYUTPING
            spaceLabel = schemeName(imeCtx.scheme)
            modeLabel = schemeShort(imeCtx.scheme)
            keyListener = object : KeyboardView.KeyListener {
                override fun onKey(label: String) = handleKey(label)
                override fun onKeyLongPress(label: String) = handleKeyLongPress(label)
                override fun onSpaceSwipe(delta: Int) = handleSpaceSwipe(delta)
            }
        }

        root.addView(candidateBar)
        root.addView(keyboardView)

        if (BuildConfig.SHOW_DEBUG_PANEL) {
            debugPanel = buildDebugPanel()
            root.addView(debugPanel)
        }

        applyNavBarInset(root)
        return root
    }

    // targetSdk 35 forces edge-to-edge, so the IME window draws under the system
    // navigation bar — the bottom key row overlaps the 3-button nav and causes
    // mis-touches. Pad the bottom by the navigation-bar inset so the keyboard sits
    // entirely above it. The padded strip shows keyboard_bg behind the nav bar.
    private fun applyNavBarInset(root: View) {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.navigationBars()
            )
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bars.bottom)
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)
    }

    private fun buildFallbackInputView(): View {
        val root = LinearLayout(this).also { inputRoot = it }.apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(androidx.core.content.ContextCompat.getColor(
                this@HkImeService, com.hkmixedkeyboard.R.color.keyboard_bg))
        }
        val keyboardHeight = keyboardHeightPx()
        candidateBar = CandidateBarView(this).apply {
            vibrationEnabled = this@HkImeService.vibrationEnabled
            haptics = typingHaptics
        }
        root.addView(candidateBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (56 * resources.displayMetrics.density).toInt()))
        keyboardView = KeyboardView(this).apply {
            minimumHeight = keyboardHeight
            vibrationEnabled = this@HkImeService.vibrationEnabled
            haptics = typingHaptics
            showCangjieRoots = this@HkImeService.showCangjieRoots && imeCtx.scheme != Scheme.JYUTPING
            spaceLabel = schemeName(imeCtx.scheme)
            modeLabel = schemeShort(imeCtx.scheme)
            keyListener = object : KeyboardView.KeyListener {
                override fun onKey(label: String) = handleKey(label)
                override fun onKeyLongPress(label: String) = handleKeyLongPress(label)
                override fun onSpaceSwipe(delta: Int) = handleSpaceSwipe(delta)
            }
        }
        root.addView(keyboardView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, keyboardHeight))
        applyNavBarInset(root)
        return root
    }

    // Never take over the whole screen. Without this, Android puts the IME into
    // fullscreen "extract" mode (e.g. landscape, or editors that request it),
    // which stretches the input view to fill the display — the keys become
    // enormous and the keyboard is unusable. Modern keyboards stay docked at the
    // bottom at all times.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        closeAltPanel()
        val sensitive = SensitiveFieldDetector.isSensitive(attribute)
        imeCtx = imeCtx.copy(isSensitiveField = sensitive)
        rebuildCommitController()
        resetCompositionState()
        shiftController.reset()
        refreshShiftVisual()
        reloadCustomWords()
        if (::candidateBar.isInitialized) {
            if (sensitive) candidateBar.showSafeMode() else candidateBar.clear()
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        resetCompositionState()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        if (::candidateBar.isInitialized) {
            candidateGrid?.dismiss()
        }
        resetCompositionState()
    }

    // ── Key dispatch ──────────────────────────────────────────────────────────

    private fun handleKey(label: String) {
        LatencyLogger.keyDown()
        if (soundEnabled) {
            audioManager?.playSoundEffect(android.media.AudioManager.FX_KEYPRESS_STANDARD)
        }
        val out = when (label) {
            KeyboardView.KEY_BACKSPACE -> ctrl.onBackspace(imeState,
                cursorJustAfterAutoCommit = isAtAutoCommitPosition())
            KeyboardView.KEY_SPACE    -> ctrl.onSpace(imeState)
            KeyboardView.KEY_ENTER    -> ctrl.onEnter(imeState)
            KeyboardView.KEY_EMOJI    -> { showEmojiPanel(); return }
            KeyboardView.KEY_SYMBOL   -> { showSymbolPage(); return }
            KeyboardView.KEY_MODE     -> { toggleScheme(); return }
            KeyboardView.KEY_SHIFT    -> {
                shiftController.press(android.os.SystemClock.uptimeMillis())
                refreshShiftVisual()
                return
            }
            // The combined ？！ key commits ？ on tap; KeyboardView emits ！ on hold.
            KeyboardView.KEY_QUESTION -> ctrl.onPunctuation("？", imeState, precedingContext())
            KeyboardView.KEY_COMMA,
            KeyboardView.KEY_PERIOD,
            KeyboardView.KEY_EXCLAIM  -> ctrl.onPunctuation(label, imeState, precedingContext())
            else                      -> {
                val r = ctrl.onKeyPress(applyShiftCase(label), imeState)
                if (isAsciiLetter(label)) { shiftController.consumeLetter(); refreshShiftVisual() }
                r
            }
        }
        // Start a next-char chain only when Space auto-commits a Chinese character;
        // any other key (typing a code, punctuation, English, backspace) ends it.
        committedPrefix =
            if (label == KeyboardView.KEY_SPACE && isCjk(out.committedText)) out.committedText!!
            else ""
        applyOutput(out)
        LatencyLogger.visualFeedback()
    }

    private fun handleKeyLongPress(label: String) {
        when (label) {
            KeyboardView.KEY_SYMBOL -> showEmojiPanel()
        }
    }

    private fun handleSpaceSwipe(delta: Int) {
        val ic = currentInputConnection ?: return
        val keyCode = if (delta < 0) android.view.KeyEvent.KEYCODE_DPAD_LEFT
                      else android.view.KeyEvent.KEYCODE_DPAD_RIGHT
        ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
        ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
    }

    private fun isCjk(s: String?): Boolean =
        !s.isNullOrEmpty() && s.all { isCjkChar(it) }

    private fun isCjkChar(ch: Char): Boolean =
        ch.code in 0x3400..0x9FFF || ch.code in 0xF900..0xFAFF

    // Inspect the character just before the cursor to pick punctuation width when
    // there is no active composition (e.g. punctuation right after a committed
    // word). CJK → full-width 。，？！; ASCII letter/digit → half-width .,?!;
    // anything else (space, start of text, other punctuation) → NEUTRAL, which the
    // controller resolves to full-width by default.
    private fun precedingContext(): PrecedingContext {
        val ic = currentInputConnection ?: return PrecedingContext.NEUTRAL
        val ch = ic.getTextBeforeCursor(1, 0)?.lastOrNull() ?: return PrecedingContext.NEUTRAL
        return when {
            isCjkChar(ch) -> PrecedingContext.CJK
            ch.code < 0x80 && ch.isLetterOrDigit() -> PrecedingContext.LATIN
            else -> PrecedingContext.NEUTRAL
        }
    }

    private fun keyboardHeightPx(): Int =
        (56f * KeyboardLayout.totalHeightWeight * resources.displayMetrics.density).toInt()

    // Flip the input scheme (速成 ↔ 粵拼) from the on-keyboard mode key. Persisting
    // the flag is the single source of truth: the settings flow collector picks the
    // change up and updates imeCtx, the commit controller, and the key labels — the
    // same path the Settings toggle uses, so the two stay in sync.
    private fun toggleScheme() {
        val next = !imeCtx.jyutpingPrimary
        serviceScope.launch { KeyboardSettings.setJyutpingPrimary(applicationContext, next) }
    }

    private fun schemeName(scheme: Scheme): String =
        if (scheme == Scheme.JYUTPING) "粵拼" else "速成"

    private fun schemeShort(scheme: Scheme): String =
        if (scheme == Scheme.JYUTPING) "粵" else "速"

    private fun isAsciiLetter(label: String): Boolean =
        label.length == 1 && label[0] in 'A'..'Z'

    // Letters are emitted uppercase while Shift is engaged; everything else (and
    // letters with Shift off) is lowercased, which is also the form the decoder
    // looks up. Quick/Jyutping codes are case-insensitive; case only affects how an
    // English literal commits.
    private fun applyShiftCase(label: String): String =
        if (isAsciiLetter(label) && shiftController.state != ShiftState.OFF) label.uppercase()
        else label.lowercase()

    // Load user custom words (自訂詞庫) from the DB and hand them to the decoder so
    // typing their Quick code surfaces and commits them. Called at startup and each
    // time input begins, so words added in the settings screen take effect on reopen.
    private fun reloadCustomWords() {
        val dao = customWordDao ?: return
        if (!::decoder.isInitialized) return
        serviceScope.launch {
            val rows = runCatching { dao.loadAll() }.getOrNull() ?: return@launch
            val byCode = rows
                .filter { it.quickCode.isNotBlank() && it.display.isNotBlank() }
                .groupBy { it.quickCode }
                .mapValues { (code, list) ->
                    list.map { e ->
                        DecodeCandidate(
                            e.display, code,
                            com.hkmixedkeyboard.decoder.SourceSchema.USER_MEMORY,
                            com.hkmixedkeyboard.decoder.CandidateType.CHAR,
                            0.95, true
                        )
                    }
                }
            decoder.setCustomWords(byCode)
        }
    }

    private fun refreshShiftVisual() {
        if (!::keyboardView.isInitialized) return
        keyboardView.shiftActive = shiftController.state != ShiftState.OFF
        keyboardView.shiftLocked = shiftController.state == ShiftState.LOCKED
        keyboardView.invalidate()
    }

    private fun handleCandidateTap(candidate: DecodeCandidate) {
        candidateGrid?.dismiss()
        // No active composition → this is a next-character prediction tap; commit
        // it directly and extend the chain so the bar offers the following char.
        if (imeState.buffer.isEmpty()) {
            commitPredictionChar(candidate.text)
            return
        }
        val out = ctrl.onCandidateTap(candidate, imeState)
        committedPrefix = if (isCjk(out.committedText)) out.committedText!! else ""
        applyOutput(out)
    }

    private fun commitPredictionChar(text: String) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        ic.finishComposingText()
        ic.commitText(text, 1)
        ic.endBatchEdit()
        committedPrefix += text
        imeState = ImeStateData()
        showNextCharPredictions()
    }

    // Show the characters that commonly follow the committed prefix (我 → 們/哋…).
    private fun showNextCharPredictions() {
        if (!::candidateBar.isInitialized) return
        if (imeCtx.isSensitiveField) { candidateBar.showSafeMode(); return }
        val prefix = committedPrefix
        if (prefix.isEmpty()) { lastCandidates = emptyList(); candidateBar.clear(); return }
        schedulePredictions(prefix)
    }

    private fun handleExpandTap() {
        val grid = candidateGrid ?: CandidateGridView(this).also {
            it.vibrationEnabled = vibrationEnabled
            it.haptics = typingHaptics
            candidateGrid = it
        }
        if (grid.isShowing()) { grid.dismiss(); return }
        if (lastCandidates.isNotEmpty()) {
            grid.show(candidateBar, lastCandidates) { cand ->
                handleCandidateTap(cand)
            }
        }
    }

    // ── Output application ────────────────────────────────────────────────────

    private fun applyOutput(out: CommitOutput) {
        val ic = currentInputConnection ?: return

        ic.beginBatchEdit()

        // 1. Revert an earlier auto-commit (backspace): drop any composing region,
        //    then delete the already-committed characters before the cursor.
        if (out.deletedBefore > 0) {
            ic.finishComposingText()
            ic.deleteSurroundingText(out.deletedBefore, 0)
        }

        // 2. Commit finalized text. commitText() replaces the active composing
        //    region (the in-progress code/word) with the committed string.
        out.committedText?.let { text ->
            if (text == "\n" && !out.swallowEnter) {
                ic.finishComposingText()
                // A real ENTER key event inserts a newline in multi-line fields
                // (notes, chat) and triggers the action in single-line ones —
                // unlike sendDefaultEditorAction, which does nothing in notes.
                sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_ENTER)
            } else if (text.isNotEmpty()) {
                ic.commitText(text, 1)
            }
        }

        // 3. Reflect the still-uncommitted buffer as inline composing text, so the
        //    user sees what they are typing (underlined) instead of a blank field
        //    until commit. Empty buffer with no commit means clear any leftover
        //    composition (e.g. backspaced to empty).
        val buf = out.newState.buffer
        if (buf.isNotEmpty()) {
            ic.setComposingText(buf, 1)
        } else if (out.committedText == null) {
            ic.setComposingText("", 1)
            ic.finishComposingText()
        }

        ic.endBatchEdit()

        imeState = out.newState
        updateCandidateBar(out)
        updateDebugPanel()
    }

    private fun updateCandidateBar(@Suppress("UNUSED_PARAMETER") out: CommitOutput) {
        if (!::candidateBar.isInitialized) return
        if (imeCtx.isSensitiveField) {
            cancelCandidateDecode()
            candidateBar.showSafeMode()
            return
        }
        val bufSnapshot = imeState.buffer
        if (bufSnapshot.isEmpty()) {
            cancelCandidateDecode()
            // Nothing composing → show next-character predictions for what was just
            // committed (empty prefix simply clears the bar).
            showNextCharPredictions()
            LatencyLogger.firstCandidateRender()
            return
        }
        scheduleDecode(bufSnapshot)
    }

    private fun scheduleDecode(buffer: String) {
        val gen = candidateRequestGate.next()
        if (decodeInFlight) {
            decodePendingBuffer = buffer
            decodePendingGeneration = gen
            return
        }
        decodeScheduled?.let { decodeHandler.removeCallbacks(it) }
        val runnable = Runnable { runDecode(buffer, gen) }
        decodeScheduled = runnable
        decodeHandler.postDelayed(runnable, DECODE_DEBOUNCE_MS)
    }

    // Runs one decode on the decode thread and publishes its candidates. If a newer
    // buffer arrived while this was in flight, it chains straight into decoding that
    // one — so bursts collapse into at most one in-flight + one pending decode.
    private fun runDecode(buffer: String, gen: Long) {
        if (!candidateRequestGate.isCurrent(gen)) return
        decodeInFlight = true
        PerfTracer.mark("decode_start") { "gen=$gen buf_len=${buffer.length}" }
        LatencyLogger.decodeStart()
        val cr = PerfTracer.time("classify") { classifier.classify(buffer, imeCtx.scheme) }
        LatencyLogger.decodeEnd(buffer.length, cr.cnCandidates.size, imeCtx.scheme.name)
        val display = PerfTracer.time("build_display") {
            buildComposingDisplay(buffer, cr.cnCandidates, cr.cnHasPhraseMatch)
        }
        mainThread.post {
            if (candidateRequestGate.isCurrent(gen) && imeState.buffer == buffer) {
                lastCandidates = display
                candidateBar.setCandidates(display)
                LatencyLogger.firstCandidateRender()
                PerfTracer.mark("first_candidate_render") { "gen=$gen size=${display.size}" }
            }
        }
        decodeInFlight = false
        val pending = decodePendingBuffer ?: return
        val pgen = decodePendingGeneration
        decodePendingBuffer = null
        decodePendingGeneration = 0L
        decodeHandler.post { runDecode(pending, pgen) }
    }

    private fun schedulePredictions(prefix: String) {
        predictScheduled?.let { decodeHandler.removeCallbacks(it) }
        val runnable = Runnable {
            val preds = corpus.nextCharIndex[prefix].orEmpty()
            mainThread.post {
                if (committedPrefix == prefix && imeState.buffer.isEmpty()) {
                    lastCandidates = preds
                    if (preds.isEmpty()) candidateBar.clear() else candidateBar.setCandidates(preds)
                }
            }
        }
        predictScheduled = runnable
        decodeHandler.postDelayed(runnable, PREDICT_DEBOUNCE_MS)
    }

    // Merge personal choices, decoded Chinese, built-in English completions, and
    // the literal buffer. Stable de-duplication keeps the highest-ranked source.
    private fun buildComposingDisplay(
        buffer: String,
        decoded: List<DecodeCandidate>,
        phraseExact: Boolean = false
    ): List<DecodeCandidate> {
        val literal = DecodeCandidate(
            classifier.canonicalForm(buffer), "",
            com.hkmixedkeyboard.decoder.SourceSchema.ENGLISH,
            com.hkmixedkeyboard.decoder.CandidateType.EN_LITERAL, 0.0, false
        )
        val learned = (memory ?: fallbackMemory)
            .suggestions(buffer, imeCtx.isSensitiveField)
        val romanization = imeCtx.scheme == Scheme.JYUTPING
        return candidateDisplayPolicy.order(
            buffer = buffer,
            learned = learned,
            // English-word completions only make sense for the Latin/Quick path;
            // in 粵拼 they would crowd out the Cantonese candidates.
            english = if (romanization) emptyList()
                      else corpus.englishCompletionIndex.forPrefix(buffer, limit = 5),
            decoded = decoded,
            literal = literal,
            chineseFirst = romanization || phraseExact
        )
    }

    // ── Composition state ────────────────────────────────────────────────────

    private fun resetCompositionState() {
        cancelCandidateDecode()
        imeState = ImeStateData()
        committedPrefix = ""
        currentInputConnection?.finishComposingText()
    }

    private fun isAtAutoCommitPosition(): Boolean {
        val lac = imeState.lastAutoCommit ?: return false
        val ic = currentInputConnection ?: return false
        val beforeCursor = ic.getTextBeforeCursor(lac.text.length, 0)
        return beforeCursor?.toString() == lac.text
    }

    // ── Symbol / Emoji panels ─────────────────────────────────────────────────

    private fun showSymbolPage() {
        closeAltPanel()
        val panel = SymbolPageView(this).apply {
            vibrationEnabled = this@HkImeService.vibrationEnabled
            haptics = typingHaptics
            // Stay open so several symbols can be tapped in a row; the function row
            // below provides space / delete / Enter / return without leaving.
            onSymbolTap = { sym -> insertStandaloneText(sym) }
            onSpace = { insertStandaloneText(" ") }
            onBackspace = {
                // Finalize any in-progress composing buffer first, then delete a
                // committed character — same handling as the emoji panel.
                flushComposingBuffer()
                currentInputConnection?.deleteSurroundingText(1, 0)
            }
            onEnter = {
                flushComposingBuffer()
                sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_ENTER)
            }
            onClose = { closeAltPanel() }
        }
        swapToAltPanel(panel)
    }

    private fun showEmojiPanel() {
        closeAltPanel()
        val panel = EmojiPanelView(this).apply {
            vibrationEnabled = this@HkImeService.vibrationEnabled
            haptics = typingHaptics
            // Stay open so several emoji can be tapped in a row (Telegram-style).
            onEmojiTap = { emoji -> insertStandaloneText(emoji) }
            onBackspace = {
                // Finalize any in-progress composing buffer first, then delete a
                // committed character — otherwise the delete targets text around
                // the composing region and leaves the half-typed code stranded.
                flushComposingBuffer()
                currentInputConnection?.deleteSurroundingText(1, 0)
            }
            onClose = { closeAltPanel() }
        }
        swapToAltPanel(panel)
    }

    // Insert a symbol or emoji as standalone committed text. If an English word or
    // a Quick/Jyutping code is still composing (shown underlined via
    // setComposingText), commitText() would REPLACE that composing region with the
    // symbol — eating the typed text. So finalize the composing buffer first, then
    // commit the symbol so it appends after it (e.g. "hello" + "@" → "hello@").
    private fun insertStandaloneText(text: String) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        if (imeState.buffer.isNotEmpty()) {
            ic.finishComposingText()
        }
        ic.commitText(text, 1)
        ic.endBatchEdit()
        clearCompositionAfterStandaloneInsert()
    }

    // Finalize the active composing buffer (if any) as committed text without
    // inserting anything else. Used before an emoji-panel backspace.
    private fun flushComposingBuffer() {
        if (imeState.buffer.isEmpty()) return
        currentInputConnection?.finishComposingText()
        clearCompositionAfterStandaloneInsert()
    }

    private fun clearCompositionAfterStandaloneInsert() {
        cancelCandidateDecode()
        imeState = ImeStateData()
        committedPrefix = ""
        lastCandidates = emptyList()
        if (::candidateBar.isInitialized) {
            if (imeCtx.isSensitiveField) candidateBar.showSafeMode() else candidateBar.clear()
        }
    }

    // The panel and keyboard occupy the same slot — index 1, right after the
    // candidate bar — and MUST use the keyboard's fixed pixel height. Re-adding the
    // keyboard with WRAP_CONTENT (the old bug) let it expand to fill the whole
    // window after returning from the emoji/symbol panel.
    private fun swapToAltPanel(panel: View) {
        altPanel = panel
        inputRoot.removeView(keyboardView)
        inputRoot.addView(panel, 1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            keyboardView.minimumHeight
        ))
    }

    private fun closeAltPanel() {
        val p = altPanel ?: return
        inputRoot.removeView(p)
        if (keyboardView.parent == null) {
            inputRoot.addView(keyboardView, 1, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                keyboardView.minimumHeight
            ))
        }
        altPanel = null
    }

    // ── Debug panel (internal builds only) ──────────────────────────────────

    private fun buildDebugPanel(): View {
        return android.widget.TextView(this).apply {
            text = "[Debug Panel — ${BuildConfig.VERSION_NAME}]"
            textSize = 10f
            setPadding(8, 4, 8, 4)
        }
    }

    private fun updateDebugPanel() {
        if (!BuildConfig.SHOW_DEBUG_PANEL) return
        (debugPanel as? android.widget.TextView)?.text =
            "buf=\"${imeState.buffer}\" " +
            "state=${imeState.imeState} " +
            "sensitive=${imeCtx.isSensitiveField} " +
            "prevCommit=${imeState.prevCommitted}"
    }

    private fun rebuildCommitController() {
        if (!::classifier.isInitialized) return
        commitController = CommitController(
            memory ?: fallbackMemory,
            { buffer -> classifier.classify(buffer, imeCtx.scheme) },
            imeCtx
        )
    }

    private fun cancelCandidateDecode() {
        // Cancel any scheduled decodes/predictions and invalidate the generation so in-flight ignores results
        decodeScheduled?.let { decodeHandler.removeCallbacks(it) }
        decodeScheduled = null
        predictScheduled?.let { decodeHandler.removeCallbacks(it) }
        predictScheduled = null
        decodePendingBuffer = null
        decodeInFlight = false
        candidateRequestGate.invalidate()
    }
}
