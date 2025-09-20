package com.soundly.feature.recording.recorder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soundly.domain.recording.Recording
import com.soundly.domain.recording.usecase.IndexRecordingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
internal class RecorderViewModel @Inject constructor(
    private val indexRecordingUseCase: IndexRecordingUseCase
) : ViewModel() {

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

            is RecorderIntent.SetRecording -> {
                viewModelScope.launch {
                    indexRecordingUseCase(
                        recording = Recording(
                            uri = intent.recording.file.absolutePath,
                            title = intent.recording.file.nameWithoutExtension,
                            createdAt = Instant.ofEpochMilli(intent.recording.file.lastModified()),
                            duration = intent.recording.duration
                        )
                    )
                }
            }

            is RecorderIntent.UpdateElapsed -> {
                _uiState.update { it.copy(elapsed = intent.duration) }
            }
        }
    }
}


