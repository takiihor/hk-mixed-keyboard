package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.ime.PinyinSpaceIntentController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinSpaceIntentControllerTest {
    @Test
    fun `decoded intent commits only while session buffer and generation still match`() {
        val controller = PinyinSpaceIntentController()
        assertTrue(controller.request("ni", session = 1, generation = 4) is
            PinyinSpaceIntentController.Action.AwaitDecode)

        val token = controller.onDecoded("ni", session = 1, generation = 4, candidate = ni("妳"))

        assertEquals("妳", controller.consume(token!!)?.candidate?.text)
    }

    @Test
    fun `further input cancels decoded result before main thread consumes it`() {
        val controller = PinyinSpaceIntentController()
        controller.request("ni", 1, 4)
        val token = controller.onDecoded("ni", 1, 4, ni("你"))!!

        controller.cancel()

        assertNull(controller.consume(token))
    }

    @Test
    fun `same buffer in a new session cannot reuse the prior cache`() {
        val controller = PinyinSpaceIntentController()
        controller.request("ni", 1, 4)
        val old = controller.onDecoded("ni", 1, 4, ni("你"))!!
        controller.consume(old)

        val action = controller.request("ni", session = 2, generation = 5)

        assertTrue(action is PinyinSpaceIntentController.Action.AwaitDecode)
        assertNull(controller.onDecoded("ni", session = 1, generation = 4, candidate = ni("你")))
        val fresh = controller.onDecoded("ni", 2, 5, ni("妳"))!!
        assertEquals("妳", controller.consume(fresh)?.candidate?.text)
    }

    @Test
    fun `decode with no exact candidate resolves conservatively`() {
        val controller = PinyinSpaceIntentController()
        controller.request("nix", 3, 8)

        val token = controller.onDecoded("nix", 3, 8, candidate = null)!!

        assertNull(controller.consume(token)?.candidate)
    }

    private fun ni(text: String) = DecodeCandidate(
        text, "ni", SourceSchema.PINYIN, CandidateType.CHAR, 1.0, false
    )
}
