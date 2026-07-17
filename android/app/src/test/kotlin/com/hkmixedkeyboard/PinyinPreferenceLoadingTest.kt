package com.hkmixedkeyboard

import android.content.Context
import android.content.ContextWrapper
import com.hkmixedkeyboard.decoder.CorpusLoader
import com.hkmixedkeyboard.decoder.PinyinEntry
import com.hkmixedkeyboard.decoder.PinyinLexicon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PinyinPreferenceLoadingTest {

    @Test
    fun `applying Pinyin preference does not initialize its corpus`() {
        val loader = bareLoader()
        val decoder = loader.lazyDelegate("pinyinDecoder")

        assertFalse(decoder.isInitialized())
        loader.setPinyinFuzzyEnabled(true)

        assertFalse(decoder.isInitialized())
    }

    @Test
    fun `lazy Pinyin decoder observes the latest preference`() {
        val loader = bareLoader().apply {
            setLazy(
                "pinyinLexicon",
                PinyinLexicon(listOf(PinyinEntry("zhong", "中", 1.0)))
            )
            setPinyinFuzzyEnabled(true)
        }

        assertEquals("中", loader.pinyinDecoder.decode("zong").candidates.first().text)
    }

    private fun CorpusLoader.lazyDelegate(name: String): Lazy<*> {
        val field = CorpusLoader::class.java.getDeclaredField("${name}\$delegate")
        field.isAccessible = true
        return field.get(this) as Lazy<*>
    }

    private fun CorpusLoader.setLazy(name: String, value: Any) {
        val field = CorpusLoader::class.java.getDeclaredField("${name}\$delegate")
        field.isAccessible = true
        field.set(this, lazyOf(value))
    }

    private fun bareLoader(): CorpusLoader {
        val context = unsafe.javaClass
            .getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, ContextWrapper::class.java) as Context
        return CorpusLoader(context)
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
