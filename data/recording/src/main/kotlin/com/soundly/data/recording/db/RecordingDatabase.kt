package com.soundly.data.recording.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RecordingEntity::class],
    version = 1,
    exportSchema = false
)
internal abstract class RecordingDatabase : RoomDatabase() {
    abstract fun recordings(): RecordingDao
}