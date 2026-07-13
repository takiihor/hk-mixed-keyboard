package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.settings.InputSchemePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class InputSchemePreferenceTest {

    @Test
    fun `missing setting defaults to Quick`() {
        assertEquals(Scheme.QUICK, InputSchemePreference.resolve(null, null))
    }

    @Test
    fun `legacy Jyutping true migrates to Jyutping`() {
        assertEquals(Scheme.JYUTPING, InputSchemePreference.resolve(null, true))
    }

    @Test
    fun `legacy Jyutping false migrates to Quick`() {
        assertEquals(Scheme.QUICK, InputSchemePreference.resolve(null, false))
    }

    @Test
    fun `stored three-state scheme takes precedence over legacy setting`() {
        assertEquals(Scheme.PINYIN, InputSchemePreference.resolve("pinyin", false))
        assertEquals(Scheme.QUICK, InputSchemePreference.resolve("quick", true))
    }

    @Test
    fun `unknown stored scheme safely defaults to Quick`() {
        assertEquals(Scheme.QUICK, InputSchemePreference.resolve("unknown", true))
    }

    @Test
    fun `legacy setting gets a one-time serialized migration value`() {
        assertEquals("jyutping", InputSchemePreference.migrationValue(null, true))
        assertEquals("quick", InputSchemePreference.migrationValue(null, false))
        assertEquals("quick", InputSchemePreference.migrationValue(null, null))
        assertEquals(null, InputSchemePreference.migrationValue("pinyin", true))
    }

    @Test
    fun `scheme serializes to stable lower-case preference`() {
        assertEquals("quick", InputSchemePreference.serialize(Scheme.QUICK))
        assertEquals("jyutping", InputSchemePreference.serialize(Scheme.JYUTPING))
        assertEquals("pinyin", InputSchemePreference.serialize(Scheme.PINYIN))
    }

    @Test
    fun `scheme cycles Quick Jyutping Pinyin Quick`() {
        assertEquals(Scheme.JYUTPING, InputSchemePreference.next(Scheme.QUICK))
        assertEquals(Scheme.PINYIN, InputSchemePreference.next(Scheme.JYUTPING))
        assertEquals(Scheme.QUICK, InputSchemePreference.next(Scheme.PINYIN))
    }

    @Test
    fun `scheme labels identify all three modes`() {
        assertEquals("速成", InputSchemePreference.name(Scheme.QUICK))
        assertEquals("粵拼", InputSchemePreference.name(Scheme.JYUTPING))
        assertEquals("拼音", InputSchemePreference.name(Scheme.PINYIN))
        assertEquals("速", InputSchemePreference.shortLabel(Scheme.QUICK))
        assertEquals("粵", InputSchemePreference.shortLabel(Scheme.JYUTPING))
        assertEquals("拼", InputSchemePreference.shortLabel(Scheme.PINYIN))
    }

    @Test
    fun `Cangjie roots are shown in Quick and Cangjie modes`() {
        assertEquals(true, InputSchemePreference.showsCangjieRoots(Scheme.QUICK))
        assertEquals(true, InputSchemePreference.showsCangjieRoots(Scheme.CANGJIE))
        assertEquals(false, InputSchemePreference.showsCangjieRoots(Scheme.JYUTPING))
        assertEquals(false, InputSchemePreference.showsCangjieRoots(Scheme.PINYIN))
    }

    @Test
    fun `Jyutping and Pinyin are romanization modes`() {
        assertEquals(false, InputSchemePreference.isRomanization(Scheme.QUICK))
        assertEquals(true, InputSchemePreference.isRomanization(Scheme.JYUTPING))
        assertEquals(true, InputSchemePreference.isRomanization(Scheme.PINYIN))
    }
}
