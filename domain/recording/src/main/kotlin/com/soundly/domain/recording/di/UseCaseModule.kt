package com.soundly.domain.recording.di

import com.soundly.domain.recording.usecase.GetRecordingsUseCase
import com.soundly.domain.recording.usecase.GetRecordingsUseCaseImpl
import com.soundly.domain.recording.usecase.IndexRecordingUseCase
import com.soundly.domain.recording.usecase.IndexRecordingUseCaseImpl
import com.soundly.domain.recording.usecase.DeleteRecordingUseCase
import com.soundly.domain.recording.usecase.DeleteRecordingUseCaseImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class UseCaseModule {
    @Binds
    abstract fun bindGetRecordingsUseCase(
        impl: GetRecordingsUseCaseImpl
    ): GetRecordingsUseCase

    @Binds
    abstract fun bindIndexRecordingUseCase(
        impl: IndexRecordingUseCaseImpl,
    ): IndexRecordingUseCase

    @Binds
    abstract fun bindDeleteRecordingUseCase(
        impl: DeleteRecordingUseCaseImpl,
    ): DeleteRecordingUseCase
}