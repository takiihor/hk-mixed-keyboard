package com.hkmixedkeyboard.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserMemoryDao {
    @Query("SELECT * FROM user_memory")
    suspend fun loadAll(): List<UserMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: UserMemoryEntity)

    @Query("DELETE FROM user_memory WHERE buffer = :buffer")
    suspend fun delete(buffer: String)

    @Query("DELETE FROM user_memory WHERE buffer = :buffer AND candidateText = :candidateText")
    suspend fun deleteEntry(buffer: String, candidateText: String)

    @Query("DELETE FROM user_memory")
    suspend fun clearAll()
}
