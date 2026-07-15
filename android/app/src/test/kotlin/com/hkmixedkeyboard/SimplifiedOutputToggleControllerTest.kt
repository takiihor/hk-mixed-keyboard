package com.hkmixedkeyboard

import com.hkmixedkeyboard.ime.SimplifiedOutputToggleController
import org.junit.Assert.assertEquals
import org.junit.Test

class SimplifiedOutputToggleControllerTest {

    @Test
    fun `enable while converter is not ready only reports loading`() {
        val fixture = Fixture(ready = false)

        fixture.controller.toggle(currentlyEnabled = false)

        assertEquals(emptyList<SimplifiedOutputToggleController.Result>(), fixture.applied)
        assertEquals(1, fixture.loadingCount)
    }

    @Test
    fun `enable while converter is ready applies exact success message`() {
        val fixture = Fixture(ready = true)

        fixture.controller.toggle(currentlyEnabled = false)

        assertEquals(
            listOf(SimplifiedOutputToggleController.Result(true)),
            fixture.applied
        )
        assertEquals(0, fixture.loadingCount)
    }

    @Test
    fun `disable is immediate even when converter is not ready`() {
        val fixture = Fixture(ready = false)

        fixture.controller.toggle(currentlyEnabled = true)

        assertEquals(
            listOf(SimplifiedOutputToggleController.Result(false)),
            fixture.applied
        )
        assertEquals(0, fixture.loadingCount)
    }

    @Test
    fun `not-ready attempt has no delayed completion when converter later becomes ready`() {
        val fixture = Fixture(ready = false)

        fixture.controller.toggle(currentlyEnabled = false)
        fixture.ready = true

        assertEquals(emptyList<SimplifiedOutputToggleController.Result>(), fixture.applied)
        assertEquals(1, fixture.loadingCount)
    }

    private class Fixture(ready: Boolean) {
        var ready = ready
        val applied = mutableListOf<SimplifiedOutputToggleController.Result>()
        var loadingCount = 0
        val controller = SimplifiedOutputToggleController(
            isConverterReady = { this.ready },
            apply = { applied += it },
            showLoading = { loadingCount++ }
        )
    }
}
