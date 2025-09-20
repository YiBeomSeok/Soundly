package com.soundly.data.recording.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "recordings",
)
internal data class RecordingEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAtEpochMs: Long,
    val durationMs: Long,
)