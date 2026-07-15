package com.hkmixedkeyboard

import com.hkmixedkeyboard.ime.DecodeWorkCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecodeWorkCoordinatorTest {
    @Test
    fun `first request starts immediately`() {
        val coordinator = DecodeWorkCoordinator()
        val first = request("n", generation = 1)

        assertEquals(DecodeWorkCoordinator.Action.Post(first), coordinator.enqueue(first))
        assertTrue(coordinator.begin(first))
    }

    @Test
    fun `new request before decoding replaces scheduled work`() {
        val coordinator = DecodeWorkCoordinator()
        val first = request("n", generation = 1)
        val latest = request("ni", generation = 2)
        coordinator.enqueue(first)

        assertEquals(DecodeWorkCoordinator.Action.Post(latest), coordinator.enqueue(latest))
        assertFalse(coordinator.begin(first))
        assertTrue(coordinator.begin(latest))
    }

    @Test
    fun `new request while decoding replaces pending work`() {
        val coordinator = DecodeWorkCoordinator()
        val first = request("n", generation = 1)
        val second = request("ni", generation = 2)
        val latest = request("nih", generation = 3)
        coordinator.enqueue(first)
        coordinator.begin(first)

        assertEquals(DecodeWorkCoordinator.Action.Coalesced, coordinator.enqueue(second))
        assertEquals(DecodeWorkCoordinator.Action.Coalesced, coordinator.enqueue(latest))

        assertEquals(latest, coordinator.finish(first))
    }

    @Test
    fun `finish returns only the latest pending request`() {
        val coordinator = DecodeWorkCoordinator()
        val first = request("n", generation = 1)
        val latest = request("nih", generation = 3)
        coordinator.enqueue(first)
        coordinator.begin(first)
        coordinator.enqueue(request("ni", generation = 2))
        coordinator.enqueue(latest)

        val next = coordinator.finish(first)

        assertEquals(latest, next)
        assertTrue(coordinator.begin(next!!))
        assertNull(coordinator.finish(next))
    }

    @Test
    fun `cancel clears scheduled and pending work`() {
        val coordinator = DecodeWorkCoordinator()
        val first = request("n", generation = 1)
        val pending = request("ni", generation = 2)
        coordinator.enqueue(first)
        coordinator.begin(first)
        coordinator.enqueue(pending)

        coordinator.cancel()

        assertNull(coordinator.finish(first))
        assertFalse(coordinator.begin(pending))
        assertEquals(DecodeWorkCoordinator.Action.Post(request("nih", generation = 3)),
            coordinator.enqueue(request("nih", generation = 3)))
    }

    private fun request(buffer: String, generation: Long) =
        DecodeWorkCoordinator.Request(buffer, generation, session = 7)
}
