package com.soundly.feature.recorder

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel holds only UI state and timers; no Android or recording dependencies.
 */
internal class RecordingViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingUiState.Default)
    val uiState = _uiState.asStateFlow()

    fun setRecording() {
        _uiState.update {
            it.copy(recordingState = RecordingState.Recording)
        }
    }

    fun setStopping() {
        _uiState.update {
            it.copy(recordingState = RecordingState.Stopping)
        }
    }

    fun setIdle() {
        _uiState.update {
            it.copy(recordingState = RecordingState.Idle)
        }
    }

    fun toggleListVisibility() {
        _uiState.update { it.copy(showList = !it.showList) }
    }

    fun loadFiles(files: List<RecordingItem>) {
        _uiState.update { it.copy(files = files) }
    }

    fun onFileRecorded(newFile: java.io.File) {
        _uiState.update { state ->
            val newItem = RecordingItem(
                id = newFile.absolutePath,
                file = newFile,
                name = newFile.name,
                sizeBytes = newFile.length(),
                lastModified = newFile.lastModified(),
                durationMs = null,
            )
            state.copy(files = listOf(newItem) + state.files)
        }
    }

    fun select(id: String?) {
        _uiState.update { it.copy(playback = it.playback.copy(selectedId = id)) }
    }

    fun onBuffering() { _uiState.update { it.copy(playback = it.playback.copy(state = PlaybackState.Buffering)) } }
    fun onPlaying(durationMs: Long) {
        _uiState.update { it.copy(playback = it.playback.copy(state = PlaybackState.Playing, durationMs = durationMs)) }
    }
    fun onPaused() { _uiState.update { it.copy(playback = it.playback.copy(state = PlaybackState.Paused)) } }
    fun onStopped() { _uiState.update { it.copy(playback = it.playback.copy(state = PlaybackState.Idle, positionMs = 0L)) } }
    fun onPosition(positionMs: Long) { _uiState.update { it.copy(playback = it.playback.copy(positionMs = positionMs)) } }
    fun onPlaybackError(message: String) { _uiState.update { it.copy(playback = it.playback.copy(state = PlaybackState.Error, errorMessage = message)) } }

    // Intent methods (set-only, no side effects)
    fun intentRequestMicPermission() {
        _uiState.update { it.copy(pendingCommand = UiCommand.RequestMicPermission) }
    }
    fun intentOpenAppSettings() {
        _uiState.update { it.copy(pendingCommand = UiCommand.OpenAppSettings) }
    }
    fun intentStartRecording() {
        _uiState.update { it.copy(recordingState = RecordingState.Recording) }
    }
    fun intentStopRecording() {
        _uiState.update { it.copy(recordingState = RecordingState.Stopping) }
    }
    fun intentStartPlayback(id: String) {
        _uiState.update { it.copy(pendingCommand = UiCommand.StartPlayback(id)) }
    }
    fun intentTogglePlayback(id: String) {
        _uiState.update { it.copy(pendingCommand = UiCommand.TogglePlayback(id)) }
    }
    fun intentSeek(positionMs: Long) {
        _uiState.update { it.copy(pendingCommand = UiCommand.SeekPlayback(positionMs)) }
    }
    fun intentStopPlayback() {
        _uiState.update { it.copy(pendingCommand = UiCommand.StopPlayback) }
    }
    fun intentDeleteFile(id: String) {
        _uiState.update { it.copy(pendingCommand = UiCommand.DeleteFile(id)) }
    }
    fun clearPendingCommand() {
        _uiState.update { it.copy(pendingCommand = null) }
    }
}


