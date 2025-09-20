package com.soundly.feature.recording.recordings

import java.io.File
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
            pendingCommand = null,
        )
    }
}

data class PlaybackUi(
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

internal data class RecordingItem(
    val uri: String,
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val duration: Duration,
)

enum class PlaybackState { Idle, Buffering, Playing, Paused, Error }

internal sealed interface RecordingsUiCommand {
    data object StartRecording : RecordingsUiCommand
    data class StartPlayback(val uri: String) : RecordingsUiCommand
    data object PauseOrResume : RecordingsUiCommand
    data object StopPlayback : RecordingsUiCommand
    data class SeekTo(val position: Duration) : RecordingsUiCommand
}