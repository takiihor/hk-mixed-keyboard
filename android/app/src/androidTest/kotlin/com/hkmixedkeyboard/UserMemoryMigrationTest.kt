package com.hkmixedkeyboard

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hkmixedkeyboard.memory.UserMemoryDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserMemoryMigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        UserMemoryDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration3To4PreservesCustomWordsAsQuick() {
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                "INSERT INTO custom_words (id, display, quickCode, addedAt) " +
                    "VALUES (1, '我哋', 'qirp', 1)"
            )
            close()
        }

        helper.runMigrationsAndValidate(
            dbName,
            4,
            true,
            UserMemoryDatabase.MIGRATION_3_4
        ).query("SELECT scheme FROM custom_words WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals("QUICK", cursor.getString(0))
        }
    }
}
