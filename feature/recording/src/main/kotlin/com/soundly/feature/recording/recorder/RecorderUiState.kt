package com.soundly.feature.recording.recorder

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal data class RecorderUiState(
    val recordingState: RecordingState = RecordingState.Idle,
    val elapsed: Duration = 0.seconds,
    val isPaused: Boolean = false,
    val prefer: RecordingFormat = RecordingFormat.Flac,
)

internal sealed interface RecordingState {
    data object Idle : RecordingState
    data object Recording : RecordingState
    data object Paused : RecordingState
    data object Stopping : RecordingState
    data object Stopped : RecordingState
    data class Error(val t: Throwable?) : RecordingState
}

