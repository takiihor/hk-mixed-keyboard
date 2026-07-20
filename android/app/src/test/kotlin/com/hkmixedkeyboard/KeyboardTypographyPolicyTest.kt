package com.hkmixedkeyboard

import com.hkmixedkeyboard.ui.KeyboardTypographyPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardTypographyPolicyTest {

    @Test
    fun `Cangjie roots shrink without changing other key text sizes`() {
        assertEquals(17f, KeyboardTypographyPolicy.MAIN_LABEL_TEXT_SIZE_SP)
        assertEquals(16f, KeyboardTypographyPolicy.CANGJIE_ROOT_TEXT_SIZE_SP)
        assertEquals(13f, KeyboardTypographyPolicy.LATIN_HINT_TEXT_SIZE_SP)
        assertEquals(13f, KeyboardTypographyPolicy.SPACE_LABEL_TEXT_SIZE_SP)
        assertEquals(21f, KeyboardTypographyPolicy.POPUP_LABEL_TEXT_SIZE_SP)
    }

    @Test
    fun `Latin hint stays three dp inside the top right corner`() {
        assertEquals(3f, KeyboardTypographyPolicy.HINT_INSET_DP)
        assertEquals(
            97f,
            KeyboardTypographyPolicy.latinHintX(keyRightPx = 100f, density = 1f),
            0.001f
        )
        assertEquals(
            128f,
            KeyboardTypographyPolicy.latinHintBaseline(
                keyTopPx = 100f,
                density = 2f,
                hintAscentPx = -22f
            ),
            0.001f
        )
    }
}
