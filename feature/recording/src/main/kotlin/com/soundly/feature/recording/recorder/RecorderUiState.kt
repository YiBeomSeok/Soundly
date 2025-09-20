package com.soundly.feature.recording.recorder

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal data class RecorderUiState(
    val recordingState: RecordingState,
    val elapsed: Duration,
    val pendingCommand: RecorderUiCommand?,
) {
    companion object Companion {
        val Default = RecorderUiState(
            recordingState = RecordingState.Idle,
            elapsed = 0.seconds,
            pendingCommand = null,
        )
    }
}

internal sealed interface RecorderUiCommand {
    data object RequestMicPermission : RecorderUiCommand
    data object OpenAppSettings : RecorderUiCommand
}

internal sealed interface RecordingState {
    data object Idle : RecordingState
    data object Recording : RecordingState
    data object Stopping : RecordingState
    data class Error(val t: Throwable? = null) : RecordingState
}


