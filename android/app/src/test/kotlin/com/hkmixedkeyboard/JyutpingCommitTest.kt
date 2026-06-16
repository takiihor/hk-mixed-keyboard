package com.hkmixedkeyboard

import com.hkmixedkeyboard.commit.ImeContext
import com.hkmixedkeyboard.decoder.Scheme
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * In 粵拼 (Jyutping) mode Space should commit the exact Chinese candidate even for
 * short syllables, which the Quick length heuristic would otherwise force to English.
 */
class JyutpingCommitTest {

    private val jyutpingCtrl = makeCtrl(ctx = ImeContext(scheme = Scheme.JYUTPING))
    private val quickCtrl = makeCtrl(ctx = ImeContext(scheme = Scheme.QUICK))

    @Test
    fun `jyutping commits Chinese for a two-letter syllable`() {
        val c = clearChinese("ng", "五", code = "ng")
        assertEquals("五", jyutpingCtrl.selectSpaceCommitTarget("ng", c).text)
    }

    @Test
    fun `jyutping prefers Chinese over an English-word collision`() {
        // "go" is in the English whitelist; Quick would keep it English, 粵拼 commits 個.
        val c = collision("go", "個", isHkCore = false, enIsWord = true)
        assertEquals("個", jyutpingCtrl.selectSpaceCommitTarget("go", c).text)
    }

    @Test
    fun `quick still keeps short collisions English`() {
        val c = collision("go", "個", isHkCore = false, enIsWord = true)
        assertEquals("go", quickCtrl.selectSpaceCommitTarget("go", c).text)
    }

    @Test
    fun `jyutping falls back to English literal when nothing parses`() {
        val c = unknown("xyz")
        assertEquals("xyz", jyutpingCtrl.selectSpaceCommitTarget("xyz", c).text)
    }
}
