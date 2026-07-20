package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.JyutpingAnnotation
import com.hkmixedkeyboard.decoder.JyutpingNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JyutpingNormalizerTest {
    @Test
    fun `accepts canonical tone digits separators and continuous input`() {
        assertEquals("neihou", JyutpingNormalizer.normalize("nei5hou2")?.key)
        assertEquals("neihou", JyutpingNormalizer.normalize("nei5 hou2")?.key)
        assertEquals("neihou", JyutpingNormalizer.normalize("nei-hou")?.key)
        assertEquals("neihou", JyutpingNormalizer.normalize("NEI'HOU")?.key)
        assertEquals(listOf(5, 2), JyutpingNormalizer.normalize("nei5hou2")?.tones)
        assertEquals(
            listOf(null, 2),
            JyutpingNormalizer.normalize("nei hou2")?.toneBySyllable
        )
    }

    @Test
    fun `rejects invalid tones and punctuation`() {
        assertNull(JyutpingNormalizer.normalize("nei0"))
        assertNull(JyutpingNormalizer.normalize("hou7"))
        assertNull(JyutpingNormalizer.normalize("n5eihou"))
        assertNull(JyutpingNormalizer.normalize("nei5h2ou"))
        assertNull(JyutpingNormalizer.normalize("n'eihou"))
        assertNull(JyutpingNormalizer.normalize("nei_hou"))
        assertNull(JyutpingNormalizer.normalize(""))
    }

    @Test
    fun `annotation exposes input reading and reverse lookup`() {
        assertEquals("hai6", JyutpingAnnotation.forInput("hai6"))
        assertEquals("hai", JyutpingAnnotation.reverseLookup("係", mapOf("係" to listOf("hai"))))
    }
}
