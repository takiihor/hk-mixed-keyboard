package com.hkmixedkeyboard

import android.content.Context
import android.content.ContextWrapper
import com.hkmixedkeyboard.commit.CommitController
import com.hkmixedkeyboard.commit.DeletionRequest
import com.hkmixedkeyboard.commit.DeletionUnit
import com.hkmixedkeyboard.commit.ImeContext
import com.hkmixedkeyboard.commit.ImeState
import com.hkmixedkeyboard.commit.ImeStateData
import com.hkmixedkeyboard.decoder.CorpusBackedDecoder
import com.hkmixedkeyboard.decoder.CorpusLoader
import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.EnglishAssistEntry
import com.hkmixedkeyboard.decoder.JyutpingEntry
import com.hkmixedkeyboard.decoder.PhraseEntry
import com.hkmixedkeyboard.decoder.PinyinEntry
import com.hkmixedkeyboard.decoder.PinyinLexicon
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.engine.Classifier
import com.hkmixedkeyboard.ime.CandidateCommitPolicy
import com.hkmixedkeyboard.memory.UserMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProductionPathThreeModeE2ETest {
    private val decoder by lazy { CorpusBackedDecoder(productionCorpus()) }
    private val classifier by lazy { Classifier(decoder) }

    @Test
    fun `Quick production path keeps candidates tappable while Space commits raw input`() {
        val buffer = "rryo"
        val expected = "唔該"
        val candidates = classifier.classify(buffer, Scheme.QUICK).cnCandidates
        val candidate = candidates.first { it.text == expected }
        val state = ImeStateData(buffer = buffer, imeState = ImeState.COMPOSING)

        assertEquals(expected, CommitController(
            UserMemory(),
            ImeContext(scheme = Scheme.QUICK)
        ).onCandidateTap(candidate, state).committedText)
        assertNull(CandidateCommitPolicy.selectForAutoCommit(
            Scheme.QUICK,
            buffer,
            candidates
        ))

        val spaceController = CommitController(UserMemory(), ImeContext(scheme = Scheme.QUICK))
        val spaced = spaceController.onSpace(state, candidate)
        assertEquals(buffer, spaced.committedText)
        assertNull(spaced.newState.lastAutoCommit)

        val ordinaryBackspace = spaceController.onBackspace(
            spaced.newState,
            cursorJustAfterAutoCommit = true
        )
        assertEquals(DeletionRequest(DeletionUnit.CODE_POINTS, 1), ordinaryBackspace.deletion)
        assertEquals("", ordinaryBackspace.newState.buffer)
        assertEquals(ImeState.PREDICTING, ordinaryBackspace.newState.imeState)

        assertEquals(
            "$expected。",
            CommitController(UserMemory(), ImeContext(scheme = Scheme.QUICK)).onPunctuation(
                "。",
                state,
                autoCommitCandidate = candidate
            ).committedText
        )
    }

    @Test
    fun `Jyutping exact phrase path supports tap Space punctuation and Backspace`() {
        assertProductionPath(
            scheme = Scheme.JYUTPING,
            buffer = "neihou",
            expected = "你好"
        )
    }

    @Test
    fun `Pinyin production path supports tap Space punctuation and Backspace`() {
        assertProductionPath(
            scheme = Scheme.PINYIN,
            buffer = "nihao",
            expected = "你好"
        )
    }

    @Test
    fun `romanization sentence composition requires reviewed phrase evidence`() {
        assertEquals(
            "你好嗎",
            decoder.decode("neihoumaa", Scheme.JYUTPING).candidates.first().text
        )
        assertEquals(
            "你好嗎",
            decoder.decode("nihaoma", Scheme.PINYIN).candidates.first().text
        )
        assertEquals(
            emptyList<String>(),
            decoder.decode("neimaa", Scheme.JYUTPING).candidates.map { it.text }
        )
    }

    @Test
    fun `misplaced tone digits cannot reach a committable production candidate`() {
        listOf(
            Scheme.JYUTPING to "n5eihou",
            Scheme.PINYIN to "n3ihao"
        ).forEach { (scheme, buffer) ->
            val result = decoder.decode(buffer, scheme)

            assertEquals("$scheme candidates", emptyList<String>(), result.candidates.map { it.text })
            assertNull(
                "$scheme malformed tone input must not auto-commit",
                CandidateCommitPolicy.selectForAutoCommit(scheme, buffer, result.candidates)
            )
        }
    }

    @Test
    fun `custom entries remain exact while Quick Space stays raw`() {
        val customDecoder = CorpusBackedDecoder(productionCorpus())
        customDecoder.setCustomWordsByScheme(
            mapOf(
                Scheme.QUICK to mapOf(
                    "qirp" to listOf(customCandidate("我哋", "qirp", SourceSchema.CUSTOM_QUICK))
                ),
                Scheme.JYUTPING to mapOf(
                    "ngodei" to listOf(customCandidate("我哋", "ngodei", SourceSchema.CUSTOM_JYUTPING))
                ),
                Scheme.PINYIN to mapOf(
                    "xianggang" to listOf(customCandidate("香港", "xianggang", SourceSchema.CUSTOM_PINYIN))
                )
            )
        )

        listOf(
            Triple(Scheme.QUICK, "qirp", "我哋"),
            Triple(Scheme.JYUTPING, "ngo5 dei6", "我哋"),
            Triple(Scheme.PINYIN, "xiang1 gang3", "香港")
        ).forEach { (scheme, buffer, expected) ->
            val result = customDecoder.decode(buffer, scheme)
            val candidate = result.candidates.first()
            assertEquals("$scheme custom candidate", expected, candidate.text)
            assertEquals(
                "$scheme exact custom code",
                true,
                result.cnExactParsed || result.cnHasPhraseMatch
            )
            val resolved = CandidateCommitPolicy.selectForAutoCommit(scheme, buffer, result.candidates)
            if (scheme == Scheme.QUICK) {
                assertNull("Quick custom candidate must not be Space-committable", resolved)
                assertEquals(
                    expected,
                    CommitController(UserMemory(), ImeContext(scheme = scheme))
                        .onCandidateTap(candidate, ImeStateData(buffer, imeState = ImeState.COMPOSING))
                        .committedText
                )
            } else {
                assertNotNull("$scheme custom candidate must be safely Space-committable", resolved)
                assertEquals(
                    expected,
                    CommitController(UserMemory(), ImeContext(scheme = scheme))
                        .onSpace(
                            ImeStateData(buffer, imeState = ImeState.COMPOSING),
                            resolved
                        )
                        .committedText
                )
            }
        }
    }

    private fun assertProductionPath(
        scheme: Scheme,
        buffer: String,
        expected: String
    ) {
        val classified = classifier.classify(buffer, scheme)
        val resolved = CandidateCommitPolicy.selectForAutoCommit(
            scheme = scheme,
            buffer = buffer,
            candidates = classified.cnCandidates
        )
        assertNotNull("$scheme must resolve an exact production candidate", resolved)
        assertEquals(expected, resolved!!.text)

        val state = ImeStateData(buffer = buffer, imeState = ImeState.COMPOSING)
        val tapController = CommitController(UserMemory(), ImeContext(scheme = scheme))
        assertEquals(expected, tapController.onCandidateTap(resolved, state).committedText)

        val spaceController = CommitController(UserMemory(), ImeContext(scheme = scheme))
        val spaced = spaceController.onSpace(state, resolved)
        assertEquals(expected, spaced.committedText)

        val punctuationController = CommitController(UserMemory(), ImeContext(scheme = scheme))
        assertEquals(
            "$expected。",
            punctuationController.onPunctuation(
                "。",
                state,
                autoCommitCandidate = resolved
            ).committedText
        )

        val restored = spaceController.onBackspace(
            spaced.newState,
            cursorJustAfterAutoCommit = true
        )
        assertEquals(
            DeletionRequest(DeletionUnit.UTF16_UNITS, expected.length),
            restored.deletion
        )
        assertEquals(buffer, restored.newState.buffer)
        assertEquals(ImeState.COMPOSING, restored.newState.imeState)
    }

    private fun productionCorpus(): CorpusLoader {
        val context = unsafe.javaClass
            .getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, ContextWrapper::class.java) as Context
        return CorpusLoader(context).also { loader ->
            loader.replaceLazy("chars", emptyList<Any>())
            loader.replaceLazy(
                "phrases",
                listOf(PhraseEntry("唔該", "rryo", 1.0, true))
            )
            loader.replaceLazy(
                "mixedPhrases",
                emptyList<com.hkmixedkeyboard.decoder.MixedPhraseEntry>()
            )
            loader.replaceLazy(
                "jyutping",
                listOf(
                    JyutpingEntry("nei", "你", 1.0),
                    JyutpingEntry("hou", "好", 1.0),
                    JyutpingEntry("maa", "嗎", 1.0),
                    JyutpingEntry("neihou", "你好", 1.2)
                )
            )
            loader.replaceLazy("englishAssist", emptyList<EnglishAssistEntry>())
            loader.replaceLazy(
                "pinyinLexicon",
                PinyinLexicon(
                    listOf(
                        PinyinEntry("nihao", "你好", 1.0),
                        PinyinEntry("ma", "嗎", 1.0)
                    )
                )
            )
        }
    }

    private fun customCandidate(
        text: String,
        code: String,
        source: SourceSchema
    ) = DecodeCandidate(
        text = text,
        code = code,
        sourceSchema = source,
        type = CandidateType.PHRASE,
        frequency = 1.0,
        isHkCore = true
    )

    private fun CorpusLoader.replaceLazy(name: String, value: Any) {
        val field = CorpusLoader::class.java.getDeclaredField("$name\$delegate")
        field.isAccessible = true
        field.set(this, lazyOf(value))
    }

    private companion object {
        val unsafe: Any by lazy {
            Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").run {
                isAccessible = true
                get(null)
            }
        }
    }
}
