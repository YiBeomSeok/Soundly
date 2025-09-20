package com.soundly.feature.recording.api

import kotlinx.serialization.Serializable

@Serializable
sealed interface RecordingRoute {
    @Serializable
    data object Recorder : RecordingRoute

    @Serializable
    data object Recordings : RecordingRoute
}