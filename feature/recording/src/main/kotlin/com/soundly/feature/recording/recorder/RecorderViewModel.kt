package com.soundly.feature.recording.recorder

import androidx.lifecycle.ViewModel
import com.soundly.feature.recording.recordings.RecordingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

internal class RecorderViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RecorderUiState.Default)
    val uiState = _uiState.asStateFlow()

    fun setIdle() {
        _uiState.update {
            it.copy(recordingState = RecordingState.Idle)
        }
    }

    fun onFileRecorded(newFile: File) {
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

    // ---------------------------- intent ----------------------------

    fun intentRequestMicPermission() {
        _uiState.update { it.copy(pendingCommand = RecorderUiCommand.RequestMicPermission) }
    }

    fun intentOpenAppSettings() {
        _uiState.update { it.copy(pendingCommand = RecorderUiCommand.OpenAppSettings) }
    }

    fun intentStartRecording() {
        _uiState.update { it.copy(recordingState = RecordingState.Recording) }
    }

    fun intentStopRecording() {
        _uiState.update { it.copy(recordingState = RecordingState.Stopping) }
    }

    fun handlePendingCommand() {
        _uiState.update { it.copy(pendingCommand = null) }
    }
}


