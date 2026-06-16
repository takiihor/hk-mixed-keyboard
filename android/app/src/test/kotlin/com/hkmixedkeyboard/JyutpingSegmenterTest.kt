package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.JyutpingSegmenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the Jyutping syllable segmenter. Uses a small synthetic syllable set
 * so the logic is verified independently of the 57k-entry corpus.
 */
class JyutpingSegmenterTest {

    // A handful of real toneless syllables, including the syllabic nasals (m/ng) and
    // some that are prefixes of others (ng ⊂ ngo, ho ⊂ hou) to exercise maximal-munch.
    private val seg = JyutpingSegmenter(
        setOf("nei", "hou", "ngo", "dei", "ng", "m", "goi", "ho", "ngaa", "aa", "gong", "hoeng")
    )

    @Test
    fun `splits a two-syllable buffer`() {
        assertEquals(listOf("nei", "hou"), seg.segment("neihou"))
        assertEquals(listOf("ngo", "dei"), seg.segment("ngodei"))
    }

    @Test
    fun `prefers fewer syllables (maximal munch)`() {
        // "ngaa" is a valid syllable; within a phrase it must stay whole rather than
        // splitting into ng + aa ([ngaa][nei] = 2 syllables beats [ng][aa][nei] = 3).
        assertEquals(listOf("ngaa", "nei"), seg.segment("ngaanei"))
        assertEquals(listOf("hoeng", "gong"), seg.segment("hoenggong"))
    }

    @Test
    fun `uses syllabic nasals`() {
        assertEquals(listOf("m", "goi"), seg.segment("mgoi"))
        assertEquals(listOf("ngo", "dei", "m", "goi"), seg.segment("ngodeimgoi"))
    }

    @Test
    fun `returns null when the buffer cannot be fully covered`() {
        assertNull(seg.segment("neih"))   // trailing "h" is not a syllable
        assertNull(seg.segment("xyz"))
    }

    @Test
    fun `returns null for a single syllable (handled by exact match)`() {
        assertNull(seg.segment("nei"))
        assertNull(seg.segment("ngaa"))
    }

    @Test
    fun `returns null for empty input`() {
        assertNull(seg.segment(""))
    }
}
