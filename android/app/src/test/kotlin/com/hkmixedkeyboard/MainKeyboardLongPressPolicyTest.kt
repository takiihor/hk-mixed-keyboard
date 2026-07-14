package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.KeyboardLayout
import com.hkmixedkeyboard.ui.MainKeyboardLongPressPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainKeyboardLongPressPolicyTest {

    @Test
    fun `period short press keeps Chinese period`() {
        assertEquals("。", MainKeyboardLongPressPolicy.shortPressTextFor(KeyboardLayout.KEY_PERIOD))
    }

    @Test
    fun `period long press resolves only to half width period`() {
        assertEquals(".", MainKeyboardLongPressPolicy.longPressTextFor(KeyboardLayout.KEY_PERIOD))
        assertNull(MainKeyboardLongPressPolicy.longPressTextFor(KeyboardLayout.KEY_SYMBOL))
    }
}
