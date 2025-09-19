package com.soundly.feature.recorder

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal data class RecordingUiState(
    val recordingState: RecordingState,
    val elapsedMs: Long,
    val showList: Boolean,
    val files: List<RecordingItem>,
    val playback: PlaybackUi,
    val pendingCommand: UiCommand?,
) {
    companion object {
        val Default = RecordingUiState(
            recordingState = RecordingState.Idle,
            elapsedMs = 0L,
            showList = false,
            files = emptyList(),
            playback = PlaybackUi.Default,
            pendingCommand = null,
        )
    }
}

internal data class PlaybackUi(
    val selectedId: String?,
    val state: PlaybackState,
    val position: Duration,
    val duration: Duration,
    val errorMessage: String?,
) {
    companion object {
        val Default = PlaybackUi(
            selectedId = null,
            state = PlaybackState.Idle,
            position = 0.milliseconds,
            duration = 0.milliseconds,
            errorMessage = null,
        )
    }
}

internal sealed interface UiCommand {
    data object RequestMicPermission : UiCommand
    data object OpenAppSettings : UiCommand
    data class StartPlayback(val id: String) : UiCommand
    data class TogglePlayback(val id: String) : UiCommand
    data object StopPlayback : UiCommand
    data class DeleteFile(val id: String) : UiCommand
}

internal sealed interface RecordingState {
    data object Idle : RecordingState
    data object Recording : RecordingState
    data object Stopping : RecordingState
    data class Error(val t: Throwable? = null) : RecordingState
}


