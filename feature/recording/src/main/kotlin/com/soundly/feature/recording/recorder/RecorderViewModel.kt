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

    // ---------------------------- intents ----------------------------
    fun onIntent(intent: RecorderIntent) {
        when (intent) {
            is RecorderIntent.RequestMicPermission -> {
                _uiState.update { it.copy(pendingCommand = RecorderUiCommand.RequestMicPermission) }
            }

            is RecorderIntent.OpenAppSettings -> {
                _uiState.update { it.copy(pendingCommand = RecorderUiCommand.OpenAppSettings) }
            }

            is RecorderIntent.StartRecording -> {
                _uiState.update { it.copy(recordingState = RecordingState.Recording) }
            }

            is RecorderIntent.StopRecording -> {
                _uiState.update { it.copy(recordingState = RecordingState.Stopping) }
            }

            is RecorderIntent.ResetError -> {
                _uiState.update { it.copy(recordingState = RecordingState.Idle) }
            }

            is RecorderIntent.PendingCommandHandled -> {
                _uiState.update { it.copy(pendingCommand = null) }
            }

            is RecorderIntent.SetIdle -> {
                _uiState.update { it.copy(recordingState = RecordingState.Idle) }
            }

            is RecorderIntent.SetRecordedFile -> {
                val newFile: File = intent.file
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
        }
    }
}


