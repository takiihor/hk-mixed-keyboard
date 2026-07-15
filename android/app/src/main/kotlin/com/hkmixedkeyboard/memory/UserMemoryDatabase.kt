package com.hkmixedkeyboard.memory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UserMemoryEntity::class, CustomWordEntity::class],
    version = 4,
    // Schemas are exported to app/schemas (see build.gradle.kts) so future version
    // bumps can ship verifiable Room migrations instead of silently wiping data.
    exportSchema = true
)
abstract class UserMemoryDatabase : RoomDatabase() {
    abstract fun dao(): UserMemoryDao
    abstract fun customWordDao(): CustomWordDao

    companion object {
        @Volatile private var INSTANCE: UserMemoryDatabase? = null

        fun get(ctx: Context): UserMemoryDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(ctx.applicationContext,
                UserMemoryDatabase::class.java, "hk_user_memory.db")
                // Only destroy on downgrade. An upgrade without a registered migration
                // throws instead of silently wiping the personal dictionary; the IME
                // catches that and degrades to in-memory, leaving on-disk data intact
                // until a migration is added. Register MIGRATION_x_y here when bumping
                // `version` above.
                .fallbackToDestructiveMigrationOnDowngrade()
                .addMigrations(MIGRATION_3_4)
                .build()
                .also { INSTANCE = it }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE custom_words ADD COLUMN scheme TEXT NOT NULL DEFAULT 'QUICK'"
                )
            }
        }
    }
}
