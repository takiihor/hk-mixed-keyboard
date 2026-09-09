package com.hkmixedkeyboard

import com.hkmixedkeyboard.commit.*
import com.hkmixedkeyboard.settings.DirectInputMode
import com.hkmixedkeyboard.settings.DirectInputPreference
import com.hkmixedkeyboard.ime.DirectInputPolicy
import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Typing into a terminal or a remote-desktop session: letters must reach the app
 * unbuffered, Enter must run the command on the first press, and punctuation must
 * stay half-width ASCII rather than becoming ，。？！.
 */
class TerminalInputTest {

    // ── Enter ────────────────────────────────────────────────────────────────

    private val passThrough = ImeContext(enterPolicy = EnterPolicy.ALWAYS_PASS_THROUGH)

    @Test
    fun `Enter runs the command on the first press in a terminal`() {
        val out = makeCtrl(ctx = passThrough).onEnter(ImeStateData())
        assertEquals("\n", out.committedText)
        assertFalse("a swallowed Enter never reaches the shell", out.swallowEnter)
    }

    @Test
    fun `Enter in a terminal flushes a composing buffer ahead of the newline`() {
        val state = ImeStateData(buffer = "ls", imeState = ImeState.COMPOSING)
        val out = makeCtrl(ctx = passThrough).onEnter(state)
        assertEquals("ls\n", out.committedText)
        assertFalse(out.swallowEnter)
        assertEquals("", out.newState.buffer)
    }

    @Test
    fun `Enter still commits then swallows in an ordinary text field`() {
        val state = ImeStateData(buffer = "hoi", imeState = ImeState.COMPOSING)
        val out = makeCtrl(ctx = ImeContext()).onEnter(state)
        assertTrue("composing Enter must not insert a newline in a chat box", out.swallowEnter)
    }

    // ── Punctuation width ────────────────────────────────────────────────────

    @Test
    fun `Latin context keeps shell punctuation half-width`() {
        val ctrl = makeCtrl()
        listOf("，" to ",", "。" to ".", "？" to "?", "！" to "!").forEach { (key, ascii) ->
            val out = ctrl.onPunctuation(key, ImeStateData(), PrecedingContext.LATIN)
            assertEquals("$key must commit as $ascii in a command line", ascii, out.committedText)
        }
    }

    @Test
    fun `neutral context still produces full-width punctuation for Chinese`() {
        val out = makeCtrl().onPunctuation("。", ImeStateData(), PrecedingContext.NEUTRAL)
        assertEquals("。", out.committedText)
    }

    // ── Field detection ──────────────────────────────────────────────────────

    @Test
    fun `remote desktop clients are detected as direct input`() {
        listOf(
            "com.microsoft.rdc.androidx",
            "com.realvnc.viewer.android",
            "com.teamviewer.teamviewer.market.mobile",
            "com.rustdesk.rustdesk",
            "com.anydesk.anydeskandroid"
        ).forEach { pkg ->
            assertTrue(
                "$pkg forwards keystrokes to another machine",
                DirectInputPolicy.shouldUseDirectLatinCommit(
                    inputType = InputType.TYPE_CLASS_TEXT,
                    packageName = pkg,
                    privateImeOptions = null
                )
            )
        }
    }

    // Values captured from the device with dumpsys input_method, so a change in the
    // detector is checked against what these apps really report rather than a guess.
    private val relayShellInputType = 0x800b1   // TEXT | VARIATION_FILTER | NO_SUGGESTIONS
    private val relayShellImeOptions = 0x3000001 // ACTION_NONE | NO_FULLSCREEN | NO_PERSONALIZED_LEARNING

    @Test
    fun `an SSH client declaring a plain text field is detected`() {
        // RelayShell matches no package hint and is not TYPE_NULL; only the shape of
        // the field gives it away.
        assertTrue(
            DirectInputPolicy.shouldUseDirectLatinCommit(
                inputType = relayShellInputType,
                packageName = "com.relayshell.relayshell",
                privateImeOptions = null,
                imeOptions = relayShellImeOptions
            )
        )
    }

    @Test
    fun `a note body setting NO_SUGGESTIONS is left composing`() {
        // Google Keep: inputType 0xac001, imeOptions 0x54000001. Treating
        // NO_SUGGESTIONS alone as a terminal signal would break Chinese input here.
        assertFalse(
            DirectInputPolicy.shouldUseDirectLatinCommit(
                inputType = 0xac001,
                packageName = "com.google.android.keep",
                privateImeOptions = null,
                imeOptions = 0x54000001
            )
        )
    }

    @Test
    fun `a list filter box is left composing`() {
        // FILTER + NO_SUGGESTIONS without NO_PERSONALIZED_LEARNING is a search box;
        // direct commit there would stop the user searching in Chinese.
        assertFalse(
            DirectInputPolicy.shouldUseDirectLatinCommit(
                inputType = relayShellInputType,
                packageName = "com.example.launcher",
                privateImeOptions = null,
                imeOptions = 0x2000001
            )
        )
    }

    @Test
    fun `a private chat field is left composing`() {
        // NO_SUGGESTIONS + NO_PERSONALIZED_LEARNING on an ordinary message box is
        // what an incognito-keyboard setting produces, not a terminal.
        assertFalse(
            DirectInputPolicy.shouldUseDirectLatinCommit(
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                packageName = "org.example.messenger",
                privateImeOptions = null,
                imeOptions = 0x3000001
            )
        )
    }

    @Test
    fun `an ordinary chat field is left composing`() {
        assertFalse(
            DirectInputPolicy.shouldUseDirectLatinCommit(
                inputType = InputType.TYPE_CLASS_TEXT,
                packageName = "com.whatsapp",
                privateImeOptions = null
            )
        )
    }

    // ── User override ────────────────────────────────────────────────────────

    @Test
    fun `ALWAYS forces direct input where detection would decline`() {
        assertTrue(
            DirectInputPolicy.shouldUseDirectLatinCommit(
                inputType = InputType.TYPE_CLASS_TEXT,
                packageName = "com.whatsapp",
                privateImeOptions = null,
                mode = DirectInputMode.ALWAYS
            )
        )
    }

    @Test
    fun `NEVER restores Chinese input inside a terminal app`() {
        // Writing a commit message in vim over SSH still needs the composing buffer.
        assertFalse(
            DirectInputPolicy.shouldUseDirectLatinCommit(
                inputType = InputType.TYPE_NULL,
                packageName = "com.termux",
                privateImeOptions = null,
                mode = DirectInputMode.NEVER
            )
        )
    }

    @Test
    fun `direct input mode round-trips through preferences and defaults to AUTO`() {
        DirectInputMode.entries.forEach { mode ->
            assertEquals(mode, DirectInputPreference.resolve(DirectInputPreference.serialize(mode)))
        }
        assertEquals(DirectInputMode.AUTO, DirectInputPreference.resolve(null))
        assertEquals(DirectInputMode.AUTO, DirectInputPreference.resolve("nonsense"))
    }
}
