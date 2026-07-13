package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.settings.InputSchemeWriteWorker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class InputSchemeWriteWorkerTest {

    @Test
    fun `failed write does not prevent later queued write`() = runBlocking {
        val attempts = mutableListOf<Scheme>()
        val failures = mutableListOf<Scheme>()
        val worker = InputSchemeWriteWorker { scheme ->
            attempts += scheme
            if (scheme == Scheme.JYUTPING) error("disk unavailable")
        }
        val queue = Channel<Scheme>(Channel.UNLIMITED)
        queue.send(Scheme.JYUTPING)
        queue.send(Scheme.PINYIN)
        queue.close()

        worker.consume(queue) { scheme, _ -> failures += scheme }

        assertEquals(listOf(Scheme.JYUTPING, Scheme.PINYIN), attempts)
        assertEquals(listOf(Scheme.JYUTPING), failures)
    }
}
