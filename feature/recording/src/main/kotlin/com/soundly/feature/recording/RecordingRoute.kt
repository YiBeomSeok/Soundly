package com.soundly.feature.recording

import androidx.compose.runtime.Composable
import com.soundly.feature.recording.recorder.RecorderScreen
import com.soundly.feature.recording.recordings.RecordingsScreen
import kotlinx.serialization.Serializable

@Serializable
sealed interface RecordingRoute {

    @Serializable
    object Recorder : RecordingRoute {

        @Composable
        operator fun invoke() {
            RecorderScreen()
        }
    }

    @Serializable
    object Recordings : RecordingRoute {

        @Composable
        operator fun invoke() {
            RecordingsScreen()
        }
    }
}