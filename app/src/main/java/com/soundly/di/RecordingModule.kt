package com.soundly.di

import com.soundly.sdk.recording.api.Recorder
import com.soundly.sdk.recording.api.Soundly
import com.soundly.sdk.recording.api.RecordingInputs
import com.soundly.sdk.recording.api.RecordingOutputs
import com.soundly.sdk.recording.capture.AudioInputs
import com.soundly.sdk.recording.storage.FileRecordingOutput
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object RecordingModule {
    @Provides
    fun recorder(): Recorder = Soundly

    @Provides
    fun inputs(): RecordingInputs = AudioInputs

    @Provides
    fun outputs(): RecordingOutputs = RecordingOutputs { target -> FileRecordingOutput(target) }
}
