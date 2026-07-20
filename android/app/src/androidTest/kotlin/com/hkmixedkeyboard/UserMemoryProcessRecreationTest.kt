package com.hkmixedkeyboard

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.memory.CustomWordEntity
import com.hkmixedkeyboard.memory.UserMemoryDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserMemoryProcessRecreationTest {
    @Test
    fun closingAndReopeningDatabasePreservesCommittedCustomData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "process-recreation-test.db"
        context.deleteDatabase(name)
        try {
            val original = Room.databaseBuilder(context, UserMemoryDatabase::class.java, name)
                .addMigrations(UserMemoryDatabase.MIGRATION_3_4)
                .build()
            try {
                original.customWordDao().insert(
                        CustomWordEntity(
                            display = "我哋",
                            quickCode = "ngodei",
                            scheme = Scheme.JYUTPING.name,
                            addedAt = 1L
                        )
                    )
            } finally {
                original.close()
            }

            val reopened = Room.databaseBuilder(context, UserMemoryDatabase::class.java, name)
                .addMigrations(UserMemoryDatabase.MIGRATION_3_4)
                .build()
            try {
                val rows = reopened.customWordDao().loadAll()
                assertEquals(1, rows.size)
                assertEquals("我哋", rows.single().display)
                assertEquals(Scheme.JYUTPING.name, rows.single().scheme)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
