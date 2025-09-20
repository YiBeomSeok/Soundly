package com.soundly.feature.recording.recorder

import com.soundly.feature.recording.recordings.RecordingItem

internal data class RecorderUiState(
    val recordingState: RecordingState,
    val elapsedMs: Long,
    val files: List<RecordingItem>,
    val pendingCommand: RecorderUiCommand?,
) {
    companion object Companion {
        val Default = RecorderUiState(
            recordingState = RecordingState.Idle,
            elapsedMs = 0L,
            files = emptyList(),
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


