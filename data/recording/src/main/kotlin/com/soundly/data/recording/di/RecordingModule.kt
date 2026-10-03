package com.soundly.data.recording.di

import com.soundly.data.recording.LocalRecordingRepository
import com.soundly.data.recording.RecordingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RecordingModule {
    @Binds
    abstract fun bindRecordingRepository(
        repository: LocalRecordingRepository,
    ): RecordingRepository
}
