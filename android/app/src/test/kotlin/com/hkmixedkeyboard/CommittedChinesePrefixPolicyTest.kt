package com.hkmixedkeyboard

import com.hkmixedkeyboard.ime.CommittedChinesePrefixPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class CommittedChinesePrefixPolicyTest {

    @Test
    fun `Space commit keeps a Chinese prefix after Quick or Jyutping delimiter`() {
        assertEquals("巴士", CommittedChinesePrefixPolicy.fromSpaceCommit("巴士 "))
    }

    @Test
    fun `Space commit keeps a Chinese prefix when Pinyin emits no delimiter`() {
        assertEquals("你好", CommittedChinesePrefixPolicy.fromSpaceCommit("你好"))
    }

    @Test
    fun `only fully Han candidate commits start a prediction and translation chain`() {
        assertEquals("巴士", CommittedChinesePrefixPolicy.fromCandidateTap("巴士"))
        assertEquals("", CommittedChinesePrefixPolicy.fromCandidateTap("巴士。"))
        assertEquals("", CommittedChinesePrefixPolicy.fromSpaceCommit("bus "))
    }
}
