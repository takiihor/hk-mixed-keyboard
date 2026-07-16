package com.hkmixedkeyboard

import com.hkmixedkeyboard.commit.AutoCommitRecord
import com.hkmixedkeyboard.commit.CommitOutput
import com.hkmixedkeyboard.commit.DeletionRequest
import com.hkmixedkeyboard.commit.DeletionUnit
import com.hkmixedkeyboard.commit.ImeContext
import com.hkmixedkeyboard.commit.ImeState
import com.hkmixedkeyboard.commit.ImeStateData
import com.hkmixedkeyboard.commit.MemoryWriteDecision
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.ime.SimplifiedCommitOutputPolicy
import org.junit.Assert.assertEquals
import org.junit.Test


class SimplifiedCommitOutputPolicyTest {
    @Test
    fun `converted auto-commit record matches the exact emitted text`() {
        val output = CommitOutput(
            committedText = "時",
            newState = ImeStateData(
                lastAutoCommit = AutoCommitRecord("時", "ai"),
                prevCommitted = "時",
                imeState = ImeState.PREDICTING
            ),
            memoryWrite = MemoryWriteDecision(false)
        )

        val converted = SimplifiedCommitOutputPolicy.convert(output) { "时" }
        val restored = makeCtrl(ctx = ImeContext(scheme = Scheme.QUICK)).onBackspace(
            converted.newState
        )

        assertEquals("时", converted.committedText)
        assertEquals(AutoCommitRecord("时", "ai"), converted.newState.lastAutoCommit)
        assertEquals("時", converted.newState.prevCommitted)
        assertEquals(DeletionRequest(DeletionUnit.UTF16_UNITS, 1), restored.deletion)
        assertEquals("ai", restored.newState.buffer)
    }
}
