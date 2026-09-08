package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.ReadingLookup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingLookupTest {

    @Test
    fun `loads exact word readings with tones and spaces`() {
        val lookup = ReadingLookup.from(
            "text,jyutping\n香港,hoeng1 gong2\n你,nei5\n".reader()
        )

        assertEquals("hoeng1 gong2", lookup.readingFor("香港"))
        assertNull(lookup.readingFor("香"))
    }

    @Test
    fun `keeps first reading and skips malformed rows`() {
        val lookup = ReadingLookup.from(
            """
            # generated data
            text,jyutping
            你,nei5
            你,lei5
            missing-reading
            ,ngo5
            """.trimIndent().reader()
        )

        assertEquals("nei5", lookup.readingFor("你"))
        assertNull(lookup.readingFor(""))
    }
}
