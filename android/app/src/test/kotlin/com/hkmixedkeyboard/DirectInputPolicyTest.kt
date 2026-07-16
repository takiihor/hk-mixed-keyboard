package com.hkmixedkeyboard

import android.text.InputType
import com.hkmixedkeyboard.ime.DirectInputPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectInputPolicyTest {

    @Test
    fun `digits bypass composing in every field`() {
        assertTrue(DirectInputPolicy.shouldCommitKeyDirectly("1", directLatinCommit = false))
        assertTrue(DirectInputPolicy.shouldCommitKeyDirectly("0", directLatinCommit = false))
    }

    @Test
    fun `digits remain composing only while completing a Unicode fallback escape`() {
        assertFalse(
            DirectInputPolicy.shouldCommitKeyDirectly(
                "2", directLatinCommit = false, compositionBuffer = "u"
            )
        )
        assertFalse(
            DirectInputPolicy.shouldCommitKeyDirectly(
                "1", directLatinCommit = false, compositionBuffer = "u200c"
            )
        )
        assertTrue(
            DirectInputPolicy.shouldCommitKeyDirectly(
                "2", directLatinCommit = false, compositionBuffer = "u200cd"
            )
        )
        assertTrue(
            DirectInputPolicy.shouldCommitKeyDirectly(
                "2", directLatinCommit = true, compositionBuffer = "u"
            )
        )
    }

    @Test
    fun `letters use composition in normal text fields`() {
        assertFalse(DirectInputPolicy.shouldCommitKeyDirectly("a", directLatinCommit = false))
    }

    @Test
    fun `letters bypass composition in terminal like fields`() {
        assertTrue(
            DirectInputPolicy.shouldUseDirectLatinCommit(
                inputType = InputType.TYPE_NULL,
                packageName = "com.example.anything",
                privateImeOptions = null
            )
        )
        assertTrue(
            DirectInputPolicy.shouldUseDirectLatinCommit(
                inputType = InputType.TYPE_CLASS_TEXT,
                packageName = "com.termux",
                privateImeOptions = null
            )
        )
        assertTrue(DirectInputPolicy.shouldCommitKeyDirectly("a", directLatinCommit = true))
    }

    @Test
    fun `letters bypass composition in password style text fields`() {
        assertTrue(
            DirectInputPolicy.shouldUseDirectLatinCommit(
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                packageName = "com.example.app",
                privateImeOptions = null
            )
        )
    }

    @Test
    fun `email URI and phone editors commit their direct-entry characters`() {
        assertTrue(
            DirectInputPolicy.shouldUseDirectLatinCommit(
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                packageName = "com.example.app",
                privateImeOptions = null
            )
        )
        assertTrue(
            DirectInputPolicy.shouldUseDirectLatinCommit(
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
                packageName = "com.example.app",
                privateImeOptions = null
            )
        )
        assertTrue(
            DirectInputPolicy.shouldUseDirectLatinCommit(
                inputType = InputType.TYPE_CLASS_PHONE,
                packageName = "com.example.app",
                privateImeOptions = null
            )
        )
        assertTrue(DirectInputPolicy.shouldCommitKeyDirectly("@", directLatinCommit = true))
        assertTrue(DirectInputPolicy.shouldCommitKeyDirectly("/", directLatinCommit = true))
        assertTrue(DirectInputPolicy.shouldCommitKeyDirectly("+", directLatinCommit = true))
    }
}
