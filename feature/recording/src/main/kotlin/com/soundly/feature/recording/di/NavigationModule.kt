package com.soundly.feature.recording.di

import com.soundly.feature.recording.api.RecordingNavController
import com.soundly.feature.recording.api.RecordingNavGraph
import com.soundly.feature.recording.navigation.RecordingNavControllerImpl
import com.soundly.feature.recording.navigation.RecordingNavGraphImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
internal abstract class NavigationModule {

    @Binds
    abstract fun bindRecordingNavController(
        impl: RecordingNavControllerImpl
    ): RecordingNavController

    @Binds
    abstract fun bindRecordingNavGraph(
        impl: RecordingNavGraphImpl
    ): RecordingNavGraph
}