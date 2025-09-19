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
}


