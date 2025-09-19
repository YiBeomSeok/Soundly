package com.soundly.feature.recorder

internal data class RecordingUiState(
    val recordingState: RecordingState,
    val elapsedMs: Long,
) {
    companion object {
        val Default = RecordingUiState(
            recordingState = RecordingState.Idle,
            elapsedMs = 0L
        )
    }
}

internal sealed interface RecordingState {
    data object Idle : RecordingState
    data object Recording : RecordingState
    data object Stopping : RecordingState
    data class Error(val t: Throwable? = null) : RecordingState
}


