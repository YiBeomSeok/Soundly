package com.soundly.feature.recording.recordings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class RecordingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(RecordingsUiState.Default)
    val uiState = _uiState.asStateFlow()

    fun setSelectedId(id: String?) {
        _uiState.update { it.copy(playback = it.playback.copy(selectedId = id)) }
    }

    fun setBuffering() {
        _uiState.update { it.copy(playback = it.playback.copy(state = PlaybackState.Buffering)) }
    }

    fun setPlaying(duration: Duration) {
        _uiState.update {
            it.copy(
                playback = it.playback.copy(
                    state = PlaybackState.Playing,
                    duration = duration
                )
            )
        }
    }

    fun setPaused() {
        _uiState.update { it.copy(playback = it.playback.copy(state = PlaybackState.Paused)) }
    }

    fun setIdle() {
        _uiState.update {
            it.copy(
                playback = it.playback.copy(
                    state = PlaybackState.Idle,
                    position = 0.milliseconds
                )
            )
        }
    }

    fun setPosition(position: Duration) {
        _uiState.update { it.copy(playback = it.playback.copy(position = position)) }
    }

    fun setPlaybackError(message: String) {
        _uiState.update {
            it.copy(
                playback = it.playback.copy(
                    state = PlaybackState.Error,
                    errorMessage = message
                )
            )
        }
    }
    fun setFiles(files: List<RecordingItem>) {
        _uiState.update { it.copy(files = files) }
    }

    // ---------------------------- intent ----------------------------

    fun intentStartPlayback(id: String) {
        _uiState.update { it.copy(pendingCommand = RecordingsUiCommand.StartPlayback(id)) }
    }

    fun intentStopPlayback() {
        _uiState.update { it.copy(pendingCommand = RecordingsUiCommand.StopPlayback) }
    }

    fun intentDeleteFile(id: String) {
        _uiState.update { it.copy(pendingCommand = RecordingsUiCommand.DeleteFile(id)) }
    }

    fun handlePendingCommand() {
        _uiState.update { it.copy(pendingCommand = null) }
    }
}