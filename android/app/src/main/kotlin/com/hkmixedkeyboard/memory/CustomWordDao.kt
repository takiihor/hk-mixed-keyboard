package com.hkmixedkeyboard.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CustomWordDao {
    @Query("SELECT * FROM custom_words ORDER BY addedAt DESC")
    suspend fun loadAll(): List<CustomWordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CustomWordEntity)

    @Query("DELETE FROM custom_words WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM custom_words")
    suspend fun clearAll()
}
