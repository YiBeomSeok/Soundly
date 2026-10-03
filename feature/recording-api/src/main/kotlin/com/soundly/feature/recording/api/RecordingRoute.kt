package com.soundly.feature.recording.api

import kotlinx.serialization.Serializable
import androidx.navigation3.runtime.NavKey

@Serializable
sealed interface RecordingRoute : NavKey {
    @Serializable
    data object Recorder : RecordingRoute

    @Serializable
    data object Recordings : RecordingRoute
}
