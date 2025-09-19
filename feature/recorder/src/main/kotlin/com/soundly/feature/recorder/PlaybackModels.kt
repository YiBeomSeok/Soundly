package com.soundly.feature.recorder

import java.io.File

internal data class RecordingItem(
    val id: String, // absolute path
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val durationMs: Long?,
)

internal enum class PlaybackState { Idle, Buffering, Playing, Paused, Error }

internal data class PlaybackUiState(
    val files: List<RecordingItem>,
    val selectedId: String?,
    val playbackState: PlaybackState,
    val positionMs: Long,
    val durationMs: Long,
    val errorMessage: String?,
) {
    companion object {
        val Default = PlaybackUiState(
            files = emptyList(),
            selectedId = null,
            playbackState = PlaybackState.Idle,
            positionMs = 0L,
            durationMs = 0L,
            errorMessage = null,
        )
    }
}


