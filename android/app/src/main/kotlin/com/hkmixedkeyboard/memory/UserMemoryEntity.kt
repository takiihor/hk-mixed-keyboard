package com.hkmixedkeyboard.memory

import androidx.room.Entity

@Entity(
    tableName = "user_memory",
    primaryKeys = ["buffer", "candidateText"]
)
data class UserMemoryEntity(
    val buffer: String,
    val candidateText: String,
    val candidateCode: String,
    val sourceSchema: String,
    val candidateType: String,
    val frequency: Double,
    val isHkCore: Boolean,
    val count: Int,
    val cnCount: Int,
    val enCount: Int
)
