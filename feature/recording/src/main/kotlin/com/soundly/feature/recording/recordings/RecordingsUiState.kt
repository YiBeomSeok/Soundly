package com.soundly.feature.recording.recordings

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal data class RecordingsUiState(
    val files: List<RecordingItem>,
    val playback: PlaybackUi,
    val pendingCommand: RecordingsUiCommand?,
) {
    companion object Companion {
        val Default = RecordingsUiState(
            files = emptyList(),
            playback = PlaybackUi.Default,
            pendingCommand = RecordingsUiCommand.RefreshFiles,
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

internal sealed interface RecordingsUiCommand {
    data object RefreshFiles : RecordingsUiCommand
    data class StartPlayback(val id: String) : RecordingsUiCommand
    data object StopPlayback : RecordingsUiCommand
    data class DeleteFile(val id: String) : RecordingsUiCommand
}