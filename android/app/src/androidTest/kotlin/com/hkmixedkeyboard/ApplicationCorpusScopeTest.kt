package com.hkmixedkeyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApplicationCorpusScopeTest {
    @Test
    fun applicationReturnsOneCorpusToRepeatedServiceAcquisitions() {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as HkApplication

        assertSame(application.corpus, application.corpus)
    }
}
