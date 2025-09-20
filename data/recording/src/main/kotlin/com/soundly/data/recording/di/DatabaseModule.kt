package com.soundly.data.recording.di

import android.content.Context
import androidx.room.Room
import com.soundly.data.recording.db.RecordingDao
import com.soundly.data.recording.db.RecordingDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal class DatabaseModule {
    @Provides
    @Singleton
    fun provideRecordingDb(
        @ApplicationContext context: Context
    ): RecordingDatabase {
        return Room.databaseBuilder(
            context = context,
            klass = RecordingDatabase::class.java,
            name = "recordings.db"
        ).build()
    }

    @Provides
    fun provideRecordingDao(
        db: RecordingDatabase
    ): RecordingDao {
        return db.recordings()
    }
}