package com.hkmixedkeyboard.memory

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hkmixedkeyboard.decoder.Scheme

@Entity(tableName = "custom_words")
data class CustomWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val display: String,    // The text shown / committed (e.g. "佢哋")
    val quickCode: String,  // Quick code user assigned (e.g. "ogrp")
    val scheme: String = Scheme.QUICK.name,
    val addedAt: Long = System.currentTimeMillis()
)
