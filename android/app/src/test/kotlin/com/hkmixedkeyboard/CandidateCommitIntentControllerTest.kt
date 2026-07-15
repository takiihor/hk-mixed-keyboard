package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.ime.CandidateCommitIntent
import com.hkmixedkeyboard.ime.CandidateCommitIntentController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test


class CandidateCommitIntentControllerTest {
    @Test
    fun `Space intent consumes only the matching decode`() {
        val controller = CandidateCommitIntentController()
        assertTrue(
            controller.request(CandidateCommitIntent.Space, "rr", 1, 4) is
                CandidateCommitIntentController.Action.AwaitDecode
        )

        val token = controller.onDecoded("rr", 1, 4, quick("唔"))
        val resolution = controller.consume(token!!)

        assertEquals(CandidateCommitIntent.Space, resolution?.intent)
        assertEquals("唔", resolution?.candidate?.text)
    }

    @Test
    fun `punctuation intent survives a cached matching decode`() {
        val controller = CandidateCommitIntentController()
        controller.onDecoded("neihou", 2, 7, jyutping("你好", "neihou"))
        val intent = CandidateCommitIntent.Punctuation("。")

        val action = controller.request(intent, "neihou", 2, 7)
        val resolution = controller.consume(
            (action as CandidateCommitIntentController.Action.Ready).token
        )

        assertEquals(intent, resolution?.intent)
        assertEquals("你好", resolution?.candidate?.text)
    }

    @Test
    fun `further input cancels a token before main thread consumption`() {
        val controller = CandidateCommitIntentController()
        controller.request(CandidateCommitIntent.Space, "ni", 1, 4)
        val token = controller.onDecoded("ni", 1, 4, pinyin("你"))!!

        controller.cancel()

        assertNull(controller.consume(token))
    }

    @Test
    fun `old session cannot satisfy a new request with the same buffer`() {
        val controller = CandidateCommitIntentController()
        controller.request(CandidateCommitIntent.Space, "ni", 1, 4)
        val old = controller.onDecoded("ni", 1, 4, pinyin("你"))!!
        controller.consume(old)

        val action = controller.request(CandidateCommitIntent.Space, "ni", 2, 5)

        assertTrue(action is CandidateCommitIntentController.Action.AwaitDecode)
        assertNull(controller.onDecoded("ni", 1, 4, pinyin("你")))
        val fresh = controller.onDecoded("ni", 2, 5, pinyin("妳"))!!
        assertEquals("妳", controller.consume(fresh)?.candidate?.text)
    }

    private fun quick(text: String) = DecodeCandidate(
        text, "rr", SourceSchema.QUICK, CandidateType.CHAR, 1.0, true
    )

    private fun jyutping(text: String, code: String) = DecodeCandidate(
        text, code, SourceSchema.JYUTPING, CandidateType.JYUTPING, 1.0, true
    )

    private fun pinyin(text: String) = DecodeCandidate(
        text, "ni", SourceSchema.PINYIN, CandidateType.CHAR, 1.0, true
    )
}
