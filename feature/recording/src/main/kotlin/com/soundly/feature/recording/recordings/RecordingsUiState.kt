package com.soundly.feature.recording.recordings

import com.soundly.sdk.v0.player.PlaybackState
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal data class RecordingsUiState(
    val files: List<RecordingItem>,
    val playback: PlaybackUi,
) {
    val shouldShowSeekBar: Boolean
        get() = playback.selectedId != null && (
                playback.state == PlaybackState.Playing ||
                        playback.state == PlaybackState.Paused ||
                        playback.state == PlaybackState.Buffering
                )

    val isPlaying: Boolean
        get() = playback.state == PlaybackState.Playing || playback.state == PlaybackState.Buffering

    fun isPaused(recordingItem: RecordingItem): Boolean {
        return playback.selectedId == recordingItem.uri && playback.state == PlaybackState.Paused
    }

    companion object Companion {
        val Default = RecordingsUiState(
            files = emptyList(),
            playback = PlaybackUi.Default,
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
