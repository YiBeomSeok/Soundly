package com.soundly.feature.recording.recordings

import com.soundly.sdk.player.PlaybackState
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal data class RecordingsUiState(
    val files: List<RecordingItem>,
    val playback: PlaybackUi,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val query: String = "",
) {
    val visibleFiles: List<RecordingItem>
        get() = files.filter { it.name.contains(query, ignoreCase = true) }

    val shouldShowSeekBar: Boolean
        get() = playback.selectedId != null && (
                playback.state == PlaybackState.Playing ||
                        playback.state == PlaybackState.Paused ||
                        playback.state == PlaybackState.Buffering ||
                        playback.state == PlaybackState.Ended
                )

    val isPlaying: Boolean
        get() = playback.state == PlaybackState.Playing || playback.state == PlaybackState.Buffering

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
    val timeLabel: String
        get() = listOf(position, duration).joinToString(" / ") { time ->
            val minutes = time.inWholeMinutes
            val seconds = time.inWholeSeconds % 60

            "%02d:%02d".format(minutes, seconds)
        }

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
