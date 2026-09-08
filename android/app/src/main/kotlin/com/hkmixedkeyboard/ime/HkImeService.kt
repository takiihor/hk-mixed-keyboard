package com.hkmixedkeyboard.ime

import android.annotation.SuppressLint
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.Toast
import com.hkmixedkeyboard.BuildConfig
import com.hkmixedkeyboard.commit.*
import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.CorpusBackedDecoder
import com.hkmixedkeyboard.decoder.CorpusLoader
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.engine.CandidateDisplayPolicy
import com.hkmixedkeyboard.engine.Classifier
import com.hkmixedkeyboard.engine.T2SConverter
import com.hkmixedkeyboard.memory.CustomWordDao
import com.hkmixedkeyboard.memory.IUserMemory
import com.hkmixedkeyboard.memory.RoomUserMemory
import com.hkmixedkeyboard.memory.MemoryStalenessPolicy
import com.hkmixedkeyboard.memory.UserMemoryDatabase
import com.hkmixedkeyboard.performance.LatencyLogger
import com.hkmixedkeyboard.performance.PerfTracer
import com.hkmixedkeyboard.privacy.SensitiveFieldDetector
import com.hkmixedkeyboard.settings.ChangeTokenTracker
import com.hkmixedkeyboard.settings.DirectInputMode
import com.hkmixedkeyboard.settings.KeyboardTheme
import com.hkmixedkeyboard.settings.KeyboardSettings
import com.hkmixedkeyboard.settings.InputSchemePreference
import com.hkmixedkeyboard.settings.InputSchemeTransitionCoordinator
import com.hkmixedkeyboard.settings.InputSchemeWriteWorker
import com.hkmixedkeyboard.ui.CandidateBarView
import com.hkmixedkeyboard.ui.CandidateGridView
import com.hkmixedkeyboard.ui.EmojiPanelView
import com.hkmixedkeyboard.ui.HapticFeedbackPolicy
import com.hkmixedkeyboard.ui.AndroidTypingHapticBackend
import com.hkmixedkeyboard.ui.KeyboardLayout
import com.hkmixedkeyboard.ui.KeyboardThemeColors
import com.hkmixedkeyboard.ui.KeyboardView
import com.hkmixedkeyboard.ui.JyutpingLearningPreview
import com.hkmixedkeyboard.ui.MainKeyboardLongPressPolicy
import com.hkmixedkeyboard.ui.ShiftState
import com.hkmixedkeyboard.ui.ShiftStateController
import com.hkmixedkeyboard.ui.SymbolKeyboardRouting
import com.hkmixedkeyboard.ui.SymbolKeyboardState
import com.hkmixedkeyboard.ui.SymbolEnterAction
import com.hkmixedkeyboard.ui.SymbolPageView
import com.hkmixedkeyboard.ui.TypingHapticEngine
import com.hkmixedkeyboard.ui.toColors
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HkImeService : InputMethodService() {

    companion object {
        private const val TAG = "HkImeService"
        private const val LATENCY_LOG_TAG = "HkIme.Latency"

        // One-time purge of memory entries learned from the pre-v0.47 corpus,
        // which emitted these variant forms before HK normalization (爲→為 …).
        private const val VARIANT_PURGE_FLAG = "variant_purge_v47"
        private const val JYUTPING_CONFIRMATION_DURATION_MS = 1_400L
        private val STALE_VARIANT_CHARS =
            "僞喫嬀擡柺棱溼潙潨爲癡皁祕竈糉纔脣臺菸蔿衆覈踊鉢鍼".toSet()
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

    // 簡體輸出 mode: convert committed Chinese to Simplified at the output
    // boundary only — dictionaries, memory and prediction prefixes stay
    // traditional. Controlled from Settings or a space-bar hold and persisted
    // across IME restarts.
    private var simplifiedOutput = false
    private val t2s = T2SConverter()
    private val simplifiedOutputSettings by lazy {
        SimplifiedOutputSettingsCoordinator(
            loadConverter = { complete ->
                if (t2s.isLoaded) complete(true)
                else serviceScope.launch(Dispatchers.IO) { complete(ensureT2SLoaded()) }
            },
            apply = { enabled -> mainThread.post { applySimplifiedOutputSetting(enabled) } }
        )
    }
    private val simplifiedOutputToggle by lazy {
        SimplifiedOutputToggleController(
            isConverterReady = { t2s.isLoaded },
            apply = ::applySimplifiedOutputToggle,
            showLoading = {
                preloadT2S()
                Toast.makeText(this, "簡體轉換載入中，請稍後再試", Toast.LENGTH_SHORT).show()
            }
        )
    }
    private val audioManager by lazy {
        getSystemService(AUDIO_SERVICE) as? android.media.AudioManager
    }

    private val mainThread = Handler(Looper.getMainLooper())
    private val decodeThread = HandlerThread("HkDecode").also { it.start() }
    private val decodeHandler = Handler(decodeThread.looper)

    // Corpus warm-up runs on its own background-priority thread, NOT the decode
    // thread. Decoding the user's first keystroke must never queue behind the full
    // ~3.6 MB dictionary load. Index builds are thread-safe (synchronized `by lazy`),
    // so if a keystroke's decode reaches an index before warm-up finished it, the
    // decode just builds that one index — never the whole corpus.
    private val warmThread =
        HandlerThread("HkWarm", Process.THREAD_PRIORITY_BACKGROUND).also { it.start() }
    private val warmHandler = Handler(warmThread.looper)
    @Volatile private var warmStarted = false
    // Per-scheme readiness for the candidate bar's loading hint. Set true by warm-up
    // OR by the first decode that loads the scheme's index, so the hint shows only
    // until the dictionary is actually ready (no per-keystroke flicker afterwards).
    @Volatile private var quickWarm = false
    @Volatile private var jyutpingWarm = false
    @Volatile private var pinyinWarm = false
    private val pinyinSpaceIntent = PinyinSpaceIntentController()
    private var compositionSession = 0L
    private var compositionDecodeGeneration = 0L
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
    private var decodePendingSession: Long = 0L
    private val candidateDisplayPolicy = CandidateDisplayPolicy()
    private val jyutpingLearningPreview by lazy {
        JyutpingLearningPreview(corpus.jyutpingReadingLookup)
    }
    private var jyutpingConfirmationClear: Runnable? = null

    private var imeState = ImeStateData()
    private var imeCtx = ImeContext()
    private var activeEditorInfo: EditorInfo? = null
    private var symbolKeyboardState = SymbolKeyboardState()
    private val schemeTransition = InputSchemeTransitionCoordinator(Scheme.QUICK)
    private val schemeWrites = Channel<Scheme>(Channel.UNLIMITED)
    private var directLatinCommit = false
    // User override for direct-Latin detection (terminals / remote desktops).
    @Volatile private var directInputMode = DirectInputMode.AUTO
    // Mirror of the caret, so onUpdateSelection can tell our own edits apart from
    // the user tapping elsewhere. See SelectionChangePolicy.
    private val cursorTracker = ComposingCursorTracker()
    private var vibrationEnabled = true
    private var showCangjieRoots = true
    private var currentThemeColors: KeyboardThemeColors = KeyboardTheme.DARK.toColors()
    private var serviceDestroyed = false
    private val memoryClearToken = ChangeTokenTracker()
    private val customWordsToken = ChangeTokenTracker()
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
    private var symbolPanel: SymbolPageView? = null

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
            serviceScope.launch {
                mem.init(staleMemoryPredicate())
                markVariantPurgeDone()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Room init failed; using in-memory fallback", e)
            memory = null
            customWordDao = null
        }
        rebuildCommitController()
        reloadCustomWords()
        preloadT2S()

        // One consumer preserves rapid mode-key persistence writes in tap order.
        serviceScope.launch {
            InputSchemeWriteWorker { scheme ->
                KeyboardSettings.setInputScheme(applicationContext, scheme)
            }.consume(schemeWrites) { scheme, error ->
                android.util.Log.e(TAG, "Scheme persistence failed: $scheme", error)
                mainThread.post {
                    when (val action = schemeTransition.onPersistenceFailed(scheme)) {
                        is InputSchemeTransitionCoordinator.Action.Apply ->
                            applyInputScheme(action.scheme)
                        else -> Unit
                    }
                }
            }
        }

        // Observe settings changes and update imeCtx on main thread
        serviceScope.launch {
            try {
                KeyboardSettings.migrateLegacyInputScheme(applicationContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Legacy scheme migration failed; using fallback", e)
            }
            KeyboardSettings.flow(applicationContext)
                .catch { e -> android.util.Log.e(TAG, "Settings flow failed", e) }
                .collectLatest { prefs ->
                    simplifiedOutputSettings.onSetting(prefs.simplifiedOutput)
                    mainThread.post {
                        if (serviceDestroyed) return@post
                        vibrationEnabled = prefs.vibration
                        soundEnabled = prefs.sound
                        showCangjieRoots = prefs.showRoots
                        if (directInputMode != prefs.directInput) {
                            directInputMode = prefs.directInput
                            // Re-decide for the field already focused, so the change
                            // takes effect without closing the keyboard first.
                            activeEditorInfo?.let {
                                applyDirectInputPolicy(it)
                                rebuildCommitController()
                            }
                        }
                        applyTheme(prefs.theme.toColors())
                        when (val action = schemeTransition.onPersisted(prefs.inputScheme)) {
                            is InputSchemeTransitionCoordinator.Action.Apply ->
                                applyInputScheme(action.scheme)
                            else -> Unit
                        }
                        // The user cleared their dictionary: flush live caches now so
                        // suggestions stop appearing without waiting for a restart.
                        if (memoryClearToken.hasChanged(prefs.memoryClearToken)) {
                            memory?.clearCache()
                            fallbackMemory.clear()
                            reloadCustomWords()
                            if (::candidateBar.isInitialized) candidateBar.clear()
                            lastCandidates = emptyList()
                        }
                        if (customWordsToken.hasChanged(prefs.customWordsToken)) {
                            reloadCustomWords()
                        }
                        if (::keyboardView.isInitialized) {
                            keyboardView.vibrationEnabled = vibrationEnabled
                            keyboardView.showCangjieRoots = showCangjieRoots &&
                                InputSchemePreference.showsCangjieRoots(imeCtx.scheme)
                            keyboardView.spaceLabel = spaceLabelText()
                            keyboardView.modeLabel = schemeShort(imeCtx.scheme)
                            keyboardView.invalidate()
                        }
                        if (::candidateBar.isInitialized) {
                            candidateBar.vibrationEnabled = vibrationEnabled
                        }
                        candidateGrid?.vibrationEnabled = vibrationEnabled
                    }
                }
        }

        // Corpus warm-up is kicked from the settings flow's first emission (below),
        // where the active scheme is known so it can be loaded first.
    }

    /**
     * Builds the staleness check for [RoomUserMemory.init]. Two layers:
     * 1. Always: candidates containing a CJK char absent from the chars corpus
     *    (hard-simplified chars removed in the HK cleanup) can never be produced
     *    again, so their memory entries are dead weight that still outranks
     *    correct candidates.
     * 2. Once (flag-gated): candidates containing variant forms the corpus used
     *    to emit but now normalizes (爲→為, 臺→台, 衆→眾 …). These chars stay
     *    individually typeable, so this purge must not repeat — a user who later
     *    deliberately commits 臺 keeps that memory.
     */
    private fun staleMemoryPredicate(): (DecodeCandidate) -> Boolean {
        val purgeVariants =
            !getSharedPreferences("memory_migrations", MODE_PRIVATE)
                .getBoolean(VARIANT_PURGE_FLAG, false)
        val policy = MemoryStalenessPolicy(
            quickContains = { corpus.charByText[it] != null },
            purgeVariant = { text ->
                purgeVariants && text.codePointCount(0, text.length) == 1 &&
                    text[0] in STALE_VARIANT_CHARS
            }
        )
        return policy::isStale
    }

    /** Marks the one-time variant purge done — only after init/purge succeeded. */
    private fun markVariantPurgeDone() {
        getSharedPreferences("memory_migrations", MODE_PRIVATE)
            .edit().putBoolean(VARIANT_PURGE_FLAG, true).apply()
    }

    override fun onDestroy() {
        serviceDestroyed = true
        clearJyutpingConfirmation()
        super.onDestroy()
        cancelCandidateDecode()
        serviceJob.cancel()
        decodeThread.quitSafely()
        warmThread.quitSafely()
        typingHaptics?.release()
        typingHaptics = null
    }

    // ── Corpus warm-up ─────────────────────────────────────────────────────────

    // Kick the background warm-up exactly once, loading the active scheme's tables
    // first. Safe to call repeatedly (e.g. on every settings emission).
    private fun startWarmupOnce(scheme: Scheme) {
        if (warmStarted) return
        warmStarted = true
        warmHandler.post { warmCorpus(scheme) }
    }

    // Runs on the warm thread. Each step is wrapped so a corpus error can't crash
    // this (uncaught exceptions on a HandlerThread would take down the IME process).
    private fun warmCorpus(activeScheme: Scheme) {
        // One-time vibrator capability probe, off the first keystroke's path.
        runCatching { typingHaptics?.warmUp() }
        when (activeScheme) {
            Scheme.JYUTPING -> { warmJyutping(); warmQuick(); warmPinyin() }
            Scheme.PINYIN -> { warmPinyin(); warmQuick(); warmJyutping() }
            else -> { warmQuick(); warmJyutping(); warmPinyin() }
        }
        // Indices not needed for the first candidate: post-commit predictions and
        // the English-meaning assist/completion fallbacks. Warm them last.
        runCatching {
            corpus.nextCharIndex
            corpus.englishAssistIndex
            corpus.englishAssistPrefixIndex
            corpus.englishCompletionIndex
        }
        PerfTracer.mark("corpus_warm_done")
    }

    private fun warmQuick() {
        runCatching {
            corpus.quickIndex
            corpus.quickPrefixCandidateIndex
            corpus.jyutpingReadingLookup
        }
        quickWarm = true
        PerfTracer.mark("warm_quick_done")
    }

    private fun warmJyutping() {
        runCatching {
            corpus.jyutpingIndex
            corpus.jyutpingPrefixIndex
            corpus.jyutpingSegmenter
        }
        jyutpingWarm = true
        PerfTracer.mark("warm_jyutping_done")
    }

    private fun warmPinyin() {
        pinyinWarm = pinyinCorpusReady()
        if (pinyinWarm) {
            PerfTracer.mark("warm_pinyin_done")
        }
    }

    private fun schemeWarm(): Boolean = when (imeCtx.scheme) {
        Scheme.JYUTPING -> jyutpingWarm
        Scheme.PINYIN -> pinyinWarm
        else -> quickWarm
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
        val density = resources.displayMetrics.density
        val candidateBarHeight = KeyboardLayout.candidateBarHeightPx(density)
        val keyboardHeight = KeyboardLayout.keyboardHeightPx(density)
        val root = LinearLayout(this).also { inputRoot = it }.apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            minimumHeight = KeyboardLayout.inputViewMinHeightPx(density)
            setBackgroundColor(currentThemeColors.keyboardBackground)
        }

        candidateBar = CandidateBarView(this).apply {
            vibrationEnabled = this@HkImeService.vibrationEnabled
            haptics = typingHaptics
            themeColors = currentThemeColors
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, candidateBarHeight
            )
            visibility = View.VISIBLE
            candidateListener = object : CandidateBarView.CandidateListener {
                override fun onCandidateTap(candidate: DecodeCandidate) = handleCandidateTap(candidate)
                override fun onExpandTap() = handleExpandTap()
            }
            clear()
        }

        // Use an explicit pixel height. A raw View with WRAP_CONTENT ignores
        // minimumHeight under AT_MOST/EXACTLY measure specs, which can make the
        // keyboard render at the wrong height.
        keyboardView = KeyboardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, keyboardHeight
            )
            minimumHeight = keyboardHeight
            vibrationEnabled = this@HkImeService.vibrationEnabled
            haptics = typingHaptics
            themeColors = currentThemeColors
            showCangjieRoots = this@HkImeService.showCangjieRoots &&
                InputSchemePreference.showsCangjieRoots(imeCtx.scheme)
            spaceLabel = spaceLabelText()
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
    // mis-touches. Pad the keyboard in from the navigation bar (and any display
    // cutout) so the keys sit entirely clear of it. The padded strip shows
    // keyboard_bg behind the bar.
    private fun applyNavBarInset(root: View) {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val edge = systemEdgeInsets(v, insets)
            // Top is left untouched: the candidate bar sits at the top of the IME
            // window with no system bar above it.
            v.setPadding(edge.left, v.paddingTop, edge.right, edge.bottom)
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)
    }

    // Bottom/left/right insets the keyboard must avoid: the navigation bar (3-button or
    // gesture) plus any display cutout. In landscape the nav bar can sit on a side, so
    // we pad left/right too, not only the bottom.
    //
    // The insets dispatched to the input view can arrive already consumed (0) by the
    // IME window's decor on edge-to-edge (targetSdk 35), which intermittently left the
    // keyboard sitting under the nav bar (the space row overlapping the home key).
    // getRootWindowInsets() reports the window's real insets regardless of what parent
    // views consumed, so we take the per-side max of it and the dispatched value —
    // neither under-pads, and it self-heals once the root insets become available.
    private fun systemEdgeInsets(
        v: View,
        dispatched: androidx.core.view.WindowInsetsCompat
    ): androidx.core.graphics.Insets {
        val type = androidx.core.view.WindowInsetsCompat.Type.navigationBars() or
            androidx.core.view.WindowInsetsCompat.Type.displayCutout()
        val fromRoot = androidx.core.view.ViewCompat.getRootWindowInsets(v)?.getInsets(type)
            ?: androidx.core.graphics.Insets.NONE
        return androidx.core.graphics.Insets.max(fromRoot, dispatched.getInsets(type))
    }

    // The input view is created once (onCreateInputView) and reused for every show,
    // so its single initial inset pass can race the IME window sizing — losing that
    // race renders the keyboard too low for the whole session. Re-assert the nav-bar
    // padding on each show so the keys always sit above the navigation bar.
    override fun onWindowShown() {
        super.onWindowShown()
        applyTheme(currentThemeColors)
        if (::inputRoot.isInitialized) {
            androidx.core.view.ViewCompat.requestApplyInsets(inputRoot)
        }
    }

    /** Runs only on the main thread from the settings collector or IME lifecycle. */
    private fun applyTheme(colors: KeyboardThemeColors) {
        if (serviceDestroyed) return
        currentThemeColors = colors
        if (::inputRoot.isInitialized) inputRoot.setBackgroundColor(colors.keyboardBackground)
        if (::keyboardView.isInitialized) keyboardView.themeColors = colors
        if (::candidateBar.isInitialized) candidateBar.themeColors = colors
        candidateGrid?.themeColors = colors
        symbolPanel?.themeColors = colors
        (altPanel as? EmojiPanelView)?.themeColors = colors
    }

    private fun buildFallbackInputView(): View {
        val density = resources.displayMetrics.density
        val candidateBarHeight = KeyboardLayout.candidateBarHeightPx(density)
        val keyboardHeight = KeyboardLayout.keyboardHeightPx(density)
        val root = LinearLayout(this).also { inputRoot = it }.apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            minimumHeight = KeyboardLayout.inputViewMinHeightPx(density)
            setBackgroundColor(currentThemeColors.keyboardBackground)
        }
        candidateBar = CandidateBarView(this).apply {
            vibrationEnabled = this@HkImeService.vibrationEnabled
            haptics = typingHaptics
            themeColors = currentThemeColors
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, candidateBarHeight
            )
            visibility = View.VISIBLE
            clear()
        }
        root.addView(candidateBar)
        keyboardView = KeyboardView(this).apply {
            minimumHeight = keyboardHeight
            vibrationEnabled = this@HkImeService.vibrationEnabled
            haptics = typingHaptics
            themeColors = currentThemeColors
            showCangjieRoots = this@HkImeService.showCangjieRoots &&
                InputSchemePreference.showsCangjieRoots(imeCtx.scheme)
            spaceLabel = spaceLabelText()
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
        activeEditorInfo = attribute
        symbolKeyboardState = SymbolKeyboardState()
        val sensitive = SensitiveFieldDetector.isSensitive(attribute)
        applyDirectInputPolicy(attribute)
        imeCtx = imeCtx.copy(isSensitiveField = sensitive)
        rebuildCommitController()
        resetCompositionState()
        cursorTracker.syncTo(attribute.initialSelStart, attribute.initialSelEnd)
        shiftController.reset()
        refreshShiftVisual()
        if (::candidateBar.isInitialized) {
            if (sensitive) candidateBar.showSafeMode() else candidateBar.clearSystemMessage()
        }
    }

    // Direct-Latin commit and the Enter policy are two faces of the same decision:
    // in a terminal or remote session letters must reach the app unbuffered AND
    // Enter must always pass through, or the first press only ends a composition and
    // the command never runs.
    private fun applyDirectInputPolicy(attribute: EditorInfo) {
        directLatinCommit = DirectInputPolicy.shouldUseDirectLatinCommit(
            inputType = attribute.inputType,
            packageName = attribute.packageName?.toString(),
            privateImeOptions = attribute.privateImeOptions,
            imeOptions = attribute.imeOptions,
            mode = directInputMode
        )
        imeCtx = imeCtx.copy(
            enterPolicy = if (directLatinCommit) EnterPolicy.ALWAYS_PASS_THROUGH
                          else EnterPolicy.COMMIT_THEN_SWALLOW
        )
    }

    // The caret moved. If it left an active composing region — the user tapped
    // elsewhere, dragged the handle, or selected a range — the region is still
    // anchored at the old offsets, so the next setComposingText() would replace THAT
    // text and yank the caret back to the end of it. Finalize where it sits instead.
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        // Only consume a pending position for a collapsed caret; a range selection is
        // the user's doing whatever the mirror says.
        val matchesOurEdit = newSelStart == newSelEnd && cursorTracker.confirm(newSelStart)
        val external = SelectionChangePolicy.isExternalMove(
            composingBuffer = imeState.buffer,
            matchesOurEdit = matchesOurEdit,
            haveMirror = cursorTracker.hasMirror,
            newSelStart = newSelStart,
            newSelEnd = newSelEnd,
            candidatesStart = candidatesStart,
            candidatesEnd = candidatesEnd
        )
        if (external) {
            resetCompositionState()
            lastCandidates = emptyList()
            if (::candidateBar.isInitialized) {
                candidateGrid?.dismiss()
                if (imeCtx.isSensitiveField) candidateBar.showSafeMode() else candidateBar.clear()
            }
        }
        // Composition finished (or never started): the editor's position is now the
        // truth, so re-sync the mirror instead of extrapolating from it.
        if (imeState.buffer.isEmpty()) cursorTracker.syncTo(newSelStart, newSelEnd)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        activeEditorInfo = null
        resetCompositionState()
        cursorTracker.invalidate()
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
        if (label != KeyboardView.KEY_SPACE) pinyinSpaceIntent.cancel()
        if (soundEnabled) {
            audioManager?.playSoundEffect(android.media.AudioManager.FX_KEYPRESS_STANDARD)
        }
        val out = when (label) {
            KeyboardView.KEY_BACKSPACE -> ctrl.onBackspace(imeState,
                cursorJustAfterAutoCommit = isAtAutoCommitPosition())
            KeyboardView.KEY_SPACE    -> {
                if (imeCtx.scheme == Scheme.PINYIN && imeState.buffer.isNotEmpty()) {
                    handlePinyinSpace()
                    return
                }
                ctrl.onSpace(imeState)
            }
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
            "." -> ctrl.onPunctuation(".", imeState, PrecedingContext.LATIN)
            KeyboardView.KEY_COMMA,
            KeyboardView.KEY_PERIOD,
            KeyboardView.KEY_EXCLAIM  -> ctrl.onPunctuation(label, imeState, precedingContext())
            else                      -> {
                val typed = applyShiftCase(label)
                if (DirectInputPolicy.shouldCommitKeyDirectly(
                        typed, directLatinCommit, imeState.buffer)) {
                    if (isAsciiLetter(label)) { shiftController.consumeLetter(); refreshShiftVisual() }
                    committedPrefix = ""
                    commitDirectText(typed)
                    LatencyLogger.visualFeedback()
                    return
                }
                val r = ctrl.onKeyPress(typed, imeState)
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
        MainKeyboardLongPressPolicy.longPressTextFor(label)?.let { punctuation ->
            handleKey(punctuation)
            return
        }
        when (label) {
            KeyboardView.KEY_SYMBOL -> showEmojiPanel()
            KeyboardView.KEY_SPACE -> toggleSimplifiedOutput()
        }
    }

    private fun toggleSimplifiedOutput() {
        simplifiedOutputToggle.toggle(currentlyEnabled = simplifiedOutput)
    }

    private fun applySimplifiedOutputToggle(result: SimplifiedOutputToggleController.Result) {
        simplifiedOutput = result.enabled
        if (::keyboardView.isInitialized) {
            keyboardView.spaceLabel = spaceLabelText()
            keyboardView.invalidate()
        }
        typingHaptics?.perform(enabled = vibrationEnabled) {
            if (::keyboardView.isInitialized) {
                HapticFeedbackPolicy.performSelection(keyboardView, enabled = true)
            }
        }
        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
        serviceScope.launch {
            KeyboardSettings.setSimplifiedOutput(applicationContext, result.enabled)
        }
    }

    private fun applySimplifiedOutputSetting(enabled: Boolean) {
        simplifiedOutput = enabled
        if (::keyboardView.isInitialized) {
            keyboardView.spaceLabel = spaceLabelText()
            keyboardView.invalidate()
        }
    }

    private fun handleSpaceSwipe(delta: Int) {
        val ic = currentInputConnection ?: return
        // The caret is about to leave the composing region, so finalize it first —
        // otherwise the next keystroke's setComposingText() would drag the caret
        // straight back to the end of what was being typed.
        if (imeState.buffer.isNotEmpty()) {
            ic.finishComposingText()
            cursorTracker.onFinishComposing()
            clearCompositionAfterStandaloneInsert()
        }
        val keyCode = if (delta < 0) android.view.KeyEvent.KEYCODE_DPAD_LEFT
                      else android.view.KeyEvent.KEYCODE_DPAD_RIGHT
        ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
        ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
        // Where the key event actually lands is the editor's call; the
        // onUpdateSelection it triggers re-syncs the mirror.
        cursorTracker.invalidate()
    }

    private fun handlePinyinSpace() {
        val buffer = imeState.buffer
        val generation = if (compositionDecodeGeneration > 0L) {
            compositionDecodeGeneration
        } else {
            scheduleDecode(buffer)
        }
        when (val action = pinyinSpaceIntent.request(buffer, compositionSession, generation)) {
            is PinyinSpaceIntentController.Action.Ready -> resolvePinyinSpace(action.token)
            PinyinSpaceIntentController.Action.AwaitDecode -> {
                if (::candidateBar.isInitialized && !schemeWarm()) candidateBar.showLoading()
            }
        }
    }

    private fun resolvePinyinSpace(token: PinyinSpaceIntentController.Token) {
        val resolution = pinyinSpaceIntent.consume(token) ?: return
        if (imeCtx.scheme != Scheme.PINYIN ||
            imeState.buffer != token.key.buffer ||
            compositionSession != token.key.session) return
        val out = ctrl.onSpace(imeState, resolution.candidate)
        committedPrefix = if (isCjk(out.committedText)) out.committedText!! else ""
        applyOutput(out)
        LatencyLogger.visualFeedback()
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
        // Terminals and remote shells return nothing from getTextBeforeCursor, which
        // would fall through to NEUTRAL and emit full-width ，。？！ into a command
        // line. The field is Latin by construction, so say so.
        if (directLatinCommit) return PrecedingContext.LATIN
        val ic = currentInputConnection ?: return PrecedingContext.NEUTRAL
        val ch = ic.getTextBeforeCursor(1, 0)?.lastOrNull() ?: return PrecedingContext.NEUTRAL
        return when {
            isCjkChar(ch) -> PrecedingContext.CJK
            ch.code < 0x80 && ch.isLetterOrDigit() -> PrecedingContext.LATIN
            else -> PrecedingContext.NEUTRAL
        }
    }

    // Cycle the input scheme (速成 → 粵拼 → 拼音) from the on-keyboard mode key.
    // Apply immediately so the following keystroke uses the new decoder. Persistence
    // stays ordered in the background; stale flow emissions cannot regress the UI.
    private fun toggleScheme() {
        when (val action = schemeTransition.onToggle()) {
            is InputSchemeTransitionCoordinator.Action.ApplyAndPersist -> {
                applyInputScheme(action.scheme)
                schemeWrites.trySend(action.scheme)
            }
            else -> Unit
        }
    }

    private fun applyInputScheme(newScheme: Scheme) {
        val changed = imeCtx.scheme != newScheme
        imeCtx = imeCtx.copy(scheme = newScheme)
        startWarmupOnce(newScheme)
        rebuildCommitController()
        // A half-typed code is meaningless under a different scheme.
        if (changed) resetCompositionState()
        if (::keyboardView.isInitialized) {
            keyboardView.showCangjieRoots = showCangjieRoots &&
                InputSchemePreference.showsCangjieRoots(newScheme)
            keyboardView.spaceLabel = spaceLabelText()
            keyboardView.modeLabel = schemeShort(newScheme)
            keyboardView.invalidate()
        }
        if (changed && ::candidateBar.isInitialized) candidateBar.clearSystemMessage()
    }

    /** Text actually sent to the app: converted to Simplified when the mode is on. */
    private fun outputText(text: String): String =
        if (simplifiedOutput) t2s.convert(text) else text

    private fun preloadT2S() {
        if (t2s.isLoaded) return
        serviceScope.launch(Dispatchers.IO) { ensureT2SLoaded() }
    }

    @Synchronized
    private fun ensureT2SLoaded(): Boolean {
        if (t2s.isLoaded) return true
        return runCatching {
            assets.open("t2s/t2s_map.tsv").bufferedReader().use { t2s.load(it) }
            t2s.isLoaded
        }.onFailure { android.util.Log.e(TAG, "t2s table load failed", it) }
            .getOrDefault(false)
    }

    private fun spaceLabelText(): String =
        schemeName(imeCtx.scheme) + if (simplifiedOutput) "·簡" else ""

    private fun schemeName(scheme: Scheme): String =
        InputSchemePreference.name(scheme)

    private fun schemeShort(scheme: Scheme): String =
        InputSchemePreference.shortLabel(scheme)

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
        serviceScope.launch(Dispatchers.IO) {
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
        pinyinSpaceIntent.cancel()
        candidateGrid?.dismiss()
        // No active composition → this is a next-character prediction tap; commit
        // it directly and extend the chain so the bar offers the following char.
        if (imeState.buffer.isEmpty()) {
            commitPredictionChar(candidate.text)
            return
        }
        val learningLabel = jyutpingLearningPreview.committedLabel(imeCtx.scheme, candidate)
        val out = ctrl.onCandidateTap(candidate, imeState)
        committedPrefix = if (isCjk(out.committedText)) out.committedText!! else ""
        applyOutput(out)
        learningLabel?.let(::showJyutpingConfirmation)
    }

    private fun commitPredictionChar(text: String) {
        val ic = currentInputConnection ?: return
        val prefix = committedPrefix
        if (prefix.isNotEmpty()) {
            (memory ?: fallbackMemory).record(
                prefix,
                DecodeCandidate(text, prefix, SourceSchema.USER_MEMORY, CandidateType.CHAR, 1.0, true),
                imeCtx.isSensitiveField
            )
        }
        ic.beginBatchEdit()
        ic.finishComposingText()
        ic.commitText(outputText(text), 1)
        ic.endBatchEdit()
        // Prediction chains stay traditional: the dictionary is keyed on 繁體.
        committedPrefix = prefix + text
        imeState = ImeStateData()
        showNextCharPredictions()
    }

    private fun showJyutpingConfirmation(label: String) {
        if (!::candidateBar.isInitialized || imeCtx.isSensitiveField) return
        clearJyutpingConfirmation()
        candidateBar.setLearningPreview(label)
        val clear = Runnable {
            jyutpingConfirmationClear = null
            if (imeState.buffer.isEmpty() && !imeCtx.isSensitiveField) {
                candidateBar.setLearningPreview(null)
            }
        }
        jyutpingConfirmationClear = clear
        mainThread.postDelayed(clear, JYUTPING_CONFIRMATION_DURATION_MS)
    }

    private fun clearJyutpingConfirmation() {
        jyutpingConfirmationClear?.let(mainThread::removeCallbacks)
        jyutpingConfirmationClear = null
    }

    // Show the characters that commonly follow the committed prefix (我 → 們/哋…).
    private fun showNextCharPredictions() {
        if (!::candidateBar.isInitialized) return
        if (imeCtx.isSensitiveField) { candidateBar.showSafeMode(); return }
        val prefix = committedPrefix
        if (prefix.isEmpty()) { lastCandidates = emptyList(); candidateBar.clearSystemMessage(); return }
        schedulePredictions(prefix)
    }

    private fun handleExpandTap() {
        val grid = candidateGrid ?: CandidateGridView(this).also {
            it.vibrationEnabled = vibrationEnabled
            it.haptics = typingHaptics
            it.themeColors = currentThemeColors
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

    private fun commitDirectText(text: String) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        if (imeState.buffer.isNotEmpty()) {
            ic.finishComposingText()
            cursorTracker.onFinishComposing()
        }
        val committed = outputText(text)
        ic.commitText(committed, 1)
        cursorTracker.onCommit(committed.length)
        ic.endBatchEdit()
        clearCompositionAfterStandaloneInsert()
        updateDebugPanel()
    }

    private fun applyOutput(out: CommitOutput) {
        val ic = currentInputConnection ?: return

        ic.beginBatchEdit()

        // 1. Revert an earlier auto-commit (backspace): drop any composing region,
        //    then delete the already-committed characters before the cursor.
        if (out.deletedBefore > 0) {
            ic.finishComposingText()
            cursorTracker.onFinishComposing()
            ic.deleteSurroundingText(out.deletedBefore, 0)
            cursorTracker.onDeleteBefore(out.deletedBefore)
        }

        // 2. Commit finalized text. commitText() replaces the active composing
        //    region (the in-progress code/word) with the committed string.
        out.committedText?.let { text ->
            // A pass-through Enter can arrive with flushed text ahead of it ("ls\n"
            // in a terminal). Commit the text, then send Enter as a real key event —
            // committing "\n" as a character would insert a newline instead of
            // running the command.
            val split = EnterKeySplit.split(text, out.swallowEnter)
            val sendsEnter = split.sendEnter
            val body = split.text
            if (body.isNotEmpty()) {
                // Same-length t2s conversion keeps deletedBefore revert counts
                // valid (the controller records the traditional text's length).
                val committed = outputText(body)
                ic.commitText(committed, 1)
                cursorTracker.onCommit(committed.length)
            }
            if (sendsEnter) {
                ic.finishComposingText()
                cursorTracker.onFinishComposing()
                // A real ENTER key event inserts a newline in multi-line fields
                // (notes, chat) and triggers the action in single-line ones —
                // unlike sendDefaultEditorAction, which does nothing in notes.
                sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_ENTER)
                // Newline or editor action — the app decides; let the resulting
                // onUpdateSelection re-sync rather than guessing.
                cursorTracker.invalidate()
            }
        }

        // 3. Reflect the still-uncommitted buffer as inline composing text, so the
        //    user sees what they are typing (underlined) instead of a blank field
        //    until commit. Empty buffer with no commit means clear any leftover
        //    composition (e.g. backspaced to empty).
        val buf = out.newState.buffer
        if (buf.isNotEmpty()) {
            ic.setComposingText(buf, 1)
            cursorTracker.onCompose(buf.length)
        } else if (out.committedText == null) {
            ic.setComposingText("", 1)
            cursorTracker.onCompose(0)
            ic.finishComposingText()
            cursorTracker.onFinishComposing()
        }

        ic.endBatchEdit()

        val endedComposition = imeState.buffer.isNotEmpty() && out.newState.buffer.isEmpty()
        imeState = out.newState
        if (endedComposition) {
            compositionSession++
            compositionDecodeGeneration = 0L
            pinyinSpaceIntent.cancel()
        }
        updateCandidateBar(out)
        updateDebugPanel()
    }

    private fun updateCandidateBar(@Suppress("UNUSED_PARAMETER") out: CommitOutput) {
        if (!::candidateBar.isInitialized) return
        if (imeCtx.isSensitiveField) {
            clearJyutpingConfirmation()
            cancelCandidateDecode()
            candidateBar.showSafeMode()
            return
        }
        val bufSnapshot = imeState.buffer
        if (bufSnapshot.isEmpty()) {
            cancelCandidateDecode()
            candidateBar.clearSystemMessage()
            // Nothing composing → show next-character predictions for what was just
            // committed (empty prefix simply clears the bar).
            showNextCharPredictions()
            LatencyLogger.firstCandidateRender()
            return
        }
        clearJyutpingConfirmation()
        candidateBar.setLearningPreview(null)
        // Cold start: the dictionary for this scheme may still be loading. Show a
        // brief hint instead of a blank bar; the decode we schedule next builds the
        // index if needed and replaces the hint with real candidates.
        if (!schemeWarm()) candidateBar.showLoading()
        scheduleDecode(bufSnapshot)
    }

    private fun scheduleDecode(buffer: String): Long {
        val gen = candidateRequestGate.next()
        val session = compositionSession
        compositionDecodeGeneration = gen
        if (decodeInFlight) {
            decodePendingBuffer = buffer
            decodePendingGeneration = gen
            decodePendingSession = session
            return gen
        }
        decodeScheduled?.let { decodeHandler.removeCallbacks(it) }
        val runnable = Runnable { runDecode(buffer, gen, session) }
        decodeScheduled = runnable
        decodeHandler.postDelayed(runnable, DECODE_DEBOUNCE_MS)
        return gen
    }

    // Runs one decode on the decode thread and publishes its candidates. If a newer
    // buffer arrived while this was in flight, it chains straight into decoding that
    // one — so bursts collapse into at most one in-flight + one pending decode.
    private fun runDecode(buffer: String, gen: Long, session: Long) {
        if (!candidateRequestGate.isCurrent(gen)) return
        decodeInFlight = true
        PerfTracer.mark("decode_start") { "gen=$gen buf_len=${buffer.length}" }
        LatencyLogger.decodeStart()
        val decodeScheme = imeCtx.scheme
        if (decodeScheme == Scheme.PINYIN && !pinyinCorpusReady()) {
            pinyinWarm = false
            publishPinyinResolution(buffer, gen, session, candidate = null)
            publishPinyinUnavailable(buffer, gen)
            finishDecode()
            return
        }
        val cr = try {
            PerfTracer.time("classify") { classifier.classify(buffer, decodeScheme) }
        } catch (e: Exception) {
            // A corpus or index defect must degrade to an empty candidate bar. This
            // runs on the decode thread, where an escaping exception would reach the
            // default handler and kill the IME process mid-sentence.
            android.util.Log.e(TAG, "Decode failed for $decodeScheme", e)
            if (decodeScheme == Scheme.PINYIN) {
                pinyinWarm = false
                publishPinyinResolution(buffer, gen, session, candidate = null)
                publishPinyinUnavailable(buffer, gen)
            } else {
                publishDecodeFailed(buffer, gen)
            }
            finishDecode()
            return
        }
        val learned = composingLearnedSuggestions(
            buffer,
            CandidateDisplayPolicy.EXPANDED_LIMIT
        )
        if (decodeScheme == Scheme.PINYIN) {
            publishPinyinResolution(
                buffer,
                gen,
                session,
                PinyinImePolicy.spaceCandidate(
                    scheme = Scheme.PINYIN,
                    buffer = buffer,
                    candidates = cr.cnCandidates,
                    learned = learned
                )
            )
        }
        // The index for this scheme is now loaded (this decode built it if warm-up
        // hadn't yet); clear the loading hint for subsequent keystrokes.
        when (decodeScheme) {
            Scheme.JYUTPING -> jyutpingWarm = true
            Scheme.PINYIN -> pinyinWarm = true
            else -> quickWarm = true
        }
        LatencyLogger.decodeEnd(buffer.length, cr.cnCandidates.size, decodeScheme.name)
        val display = PerfTracer.time("build_display") {
            buildComposingDisplay(
                buffer,
                cr.cnCandidates,
                cr.cnHasPhraseMatch,
                learned,
                com.hkmixedkeyboard.engine.CandidateDisplayPolicy.BAR_LIMIT
            )
        }
        val expanded = PerfTracer.time("build_expanded_display") {
            buildComposingDisplay(
                buffer,
                cr.cnCandidates,
                cr.cnHasPhraseMatch,
                learned,
                com.hkmixedkeyboard.engine.CandidateDisplayPolicy.EXPANDED_LIMIT
            )
        }
        mainThread.post {
            if (candidateRequestGate.isCurrent(gen) && imeState.buffer == buffer &&
                compositionSession == session) {
                lastCandidates = expanded
                candidateBar.setCandidates(display)
                candidateBar.setLearningPreview(
                    jyutpingLearningPreview.liveLabel(decodeScheme, display)
                )
                LatencyLogger.firstCandidateRender()
                PerfTracer.mark("first_candidate_render") { "gen=$gen size=${display.size}" }
            }
        }
        finishDecode()
    }

    private fun publishPinyinResolution(
        buffer: String,
        gen: Long,
        session: Long,
        candidate: DecodeCandidate?
    ) {
        val token = pinyinSpaceIntent.onDecoded(buffer, session, gen, candidate) ?: return
        mainThread.post { resolvePinyinSpace(token) }
    }

    // Non-Pinyin decode failure: clear the bar rather than leaving stale candidates
    // that no longer correspond to the buffer.
    private fun publishDecodeFailed(buffer: String, gen: Long) {
        mainThread.post {
            if (candidateRequestGate.isCurrent(gen) && imeState.buffer == buffer) {
                lastCandidates = emptyList()
                if (::candidateBar.isInitialized) candidateBar.clear()
            }
        }
    }

    private fun publishPinyinUnavailable(buffer: String, gen: Long) {
        mainThread.post {
            if (candidateRequestGate.isCurrent(gen) && imeState.buffer == buffer) {
                lastCandidates = emptyList()
                candidateBar.showLoading()
            }
        }
    }

    private fun pinyinCorpusReady(): Boolean = runCatching {
        PinyinImePolicy.isCorpusReady(corpus.pinyinLexicon)
    }.getOrDefault(false)

    private fun finishDecode() {
        decodeInFlight = false
        val pending = decodePendingBuffer ?: return
        val pgen = decodePendingGeneration
        val psession = decodePendingSession
        decodePendingBuffer = null
        decodePendingGeneration = 0L
        decodePendingSession = 0L
        decodeHandler.post { runDecode(pending, pgen, psession) }
    }

    private fun schedulePredictions(prefix: String) {
        predictScheduled?.let { decodeHandler.removeCallbacks(it) }
        val runnable = Runnable {
            val decoded = corpus.nextCharIndex[prefix].orEmpty()
            val learned = (memory ?: fallbackMemory).suggestions(
                prefix,
                imeCtx.isSensitiveField,
                limit = CandidateDisplayPolicy.EXPANDED_LIMIT
            )
            val display = candidateDisplayPolicy.orderPredictions(
                learned = learned,
                decoded = decoded,
                limit = CandidateDisplayPolicy.BAR_LIMIT
            )
            val expanded = candidateDisplayPolicy.orderPredictions(
                learned = learned,
                decoded = decoded,
                limit = CandidateDisplayPolicy.EXPANDED_LIMIT
            )
            mainThread.post {
                if (committedPrefix == prefix && imeState.buffer.isEmpty()) {
                    lastCandidates = expanded
                    if (display.isEmpty()) candidateBar.clear(clearLearningPreview = false)
                    else candidateBar.setCandidates(display)
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
        phraseExact: Boolean,
        learnedSuggestions: List<com.hkmixedkeyboard.memory.MemorySuggestion>,
        limit: Int = com.hkmixedkeyboard.engine.CandidateDisplayPolicy.BAR_LIMIT
    ): List<DecodeCandidate> {
        val literal = DecodeCandidate(
            classifier.canonicalForm(buffer), "",
            com.hkmixedkeyboard.decoder.SourceSchema.ENGLISH,
            com.hkmixedkeyboard.decoder.CandidateType.EN_LITERAL, 0.0, false
        )
        val learned = PinyinImePolicy.filterLearnedSuggestions(
            imeCtx.scheme,
            learnedSuggestions
        )
        return candidateDisplayPolicy.order(
            buffer = buffer,
            learned = learned,
            // English-word completions only make sense for the Latin/Quick path;
            // in 粵拼 they would crowd out the Cantonese candidates.
            english = if (PinyinImePolicy.shouldOfferEnglishCompletions(imeCtx.scheme))
                corpus.englishCompletionIndex.forPrefix(buffer, limit = 5)
            else emptyList(),
            decoded = decoded,
            literal = literal,
            chineseFirst = PinyinImePolicy.isChineseFirst(imeCtx.scheme, phraseExact),
            limit = limit
        )
    }

    private fun composingLearnedSuggestions(
        buffer: String,
        limit: Int
    ): List<com.hkmixedkeyboard.memory.MemorySuggestion> =
        (memory ?: fallbackMemory).suggestions(buffer, imeCtx.isSensitiveField, limit)

    // ── Composition state ────────────────────────────────────────────────────

    private fun resetCompositionState() {
        cancelCandidateDecode()
        clearJyutpingConfirmation()
        pinyinSpaceIntent.cancel()
        compositionSession++
        compositionDecodeGeneration = 0L
        imeState = ImeStateData()
        committedPrefix = ""
        if (::candidateBar.isInitialized) candidateBar.setLearningPreview(null)
        currentInputConnection?.finishComposingText()
        cursorTracker.onFinishComposing()
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
        symbolKeyboardState = symbolKeyboardState.enterSymbols()
        val panel = SymbolPageView(this).apply {
            vibrationEnabled = this@HkImeService.vibrationEnabled
            haptics = typingHaptics
            themeColors = currentThemeColors
            symbolPage = symbolKeyboardState.symbolPage
            enterAction = SymbolEnterAction.fromImeOptions(activeEditorInfo?.imeOptions ?: 0)
            onKeyTap = ::handleSymbolKey
        }
        symbolPanel = panel
        swapToAltPanel(panel)
    }

    private fun handleSymbolKey(key: com.hkmixedkeyboard.ui.SymbolKeySpec) {
        when (val event = SymbolKeyboardRouting.eventFor(key)) {
            is SymbolKeyboardRouting.Event.CommitText -> insertStandaloneText(event.text)
            SymbolKeyboardRouting.Event.TogglePage -> {
                symbolKeyboardState = symbolKeyboardState.toggleSymbolPage()
                symbolPanel?.symbolPage = symbolKeyboardState.symbolPage
            }
            SymbolKeyboardRouting.Event.ReturnAlphabet -> {
                symbolKeyboardState = symbolKeyboardState.returnToAlphabet()
                closeAltPanel()
            }
            SymbolKeyboardRouting.Event.Space -> handleKey(KeyboardView.KEY_SPACE)
            SymbolKeyboardRouting.Event.Backspace -> handleKey(KeyboardView.KEY_BACKSPACE)
            SymbolKeyboardRouting.Event.Enter -> handleKey(KeyboardView.KEY_ENTER)
        }
    }

    private fun showEmojiPanel() {
        closeAltPanel()
        val panel = EmojiPanelView(this).apply {
            vibrationEnabled = this@HkImeService.vibrationEnabled
            haptics = typingHaptics
            themeColors = currentThemeColors
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
            cursorTracker.onFinishComposing()
        }
        val committed = outputText(text)
        ic.commitText(committed, 1)
        cursorTracker.onCommit(committed.length)
        ic.endBatchEdit()
        clearCompositionAfterStandaloneInsert()
    }

    // Finalize the active composing buffer (if any) as committed text without
    // inserting anything else. Used before an emoji-panel backspace.
    private fun flushComposingBuffer() {
        if (imeState.buffer.isEmpty()) return
        currentInputConnection?.finishComposingText()
        cursorTracker.onFinishComposing()
        clearCompositionAfterStandaloneInsert()
    }

    private fun clearCompositionAfterStandaloneInsert() {
        cancelCandidateDecode()
        clearJyutpingConfirmation()
        compositionSession++
        imeState = ImeStateData()
        committedPrefix = ""
        lastCandidates = emptyList()
        if (::candidateBar.isInitialized) {
            if (imeCtx.isSensitiveField) candidateBar.showSafeMode() else candidateBar.clearSystemMessage()
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
        if (p === symbolPanel) symbolPanel = null
        altPanel = null
    }

    // ── Debug panel (internal builds only) ──────────────────────────────────

    @SuppressLint("SetTextI18n")
    private fun buildDebugPanel(): View {
        return android.widget.TextView(this).apply {
            text = "[Debug Panel — ${BuildConfig.VERSION_NAME}]"
            textSize = 10f
            setPadding(8, 4, 8, 4)
        }
    }

    @SuppressLint("SetTextI18n")
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
        decodePendingSession = 0L
        decodeInFlight = false
        compositionDecodeGeneration = 0L
        pinyinSpaceIntent.cancel()
        candidateRequestGate.invalidate()
    }
}
