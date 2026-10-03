package com.soundly.feature.recording.di

import com.soundly.feature.recording.api.RecordingNavigation
import com.soundly.feature.recording.navigation.RecordingNavigationImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
internal abstract class NavigationModule {
    @Binds
    abstract fun bindRecordingNavigation(impl: RecordingNavigationImpl): RecordingNavigation
}
