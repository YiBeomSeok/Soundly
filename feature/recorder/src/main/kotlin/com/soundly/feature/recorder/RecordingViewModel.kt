package com.soundly.feature.recorder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel holds only UI state and timers; no Android or recording dependencies.
 */
class RecordingViewModel : ViewModel() {

    private val mutableState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = mutableState

    private var timerJob: Job? = null
    private var startTime: Long = 0L

    fun updateCanRecord(canRecord: Boolean) {
        mutableState.value = mutableState.value.copy(canRecord = canRecord)
    }

    fun markStartRecording(filePath: String?) {
        startTime = System.currentTimeMillis()
        mutableState.value = mutableState.value.copy(
            isRecording = true,
            currentFilePath = filePath,
            errorMessage = null,
            elapsedMs = 0L
        )
        startTimer()
    }

    fun markStartFailed(message: String) {
        mutableState.value = mutableState.value.copy(errorMessage = message)
    }

    fun markStopRecording() {
        timerJob?.cancel()
        mutableState.value = mutableState.value.copy(isRecording = false)
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && mutableState.value.isRecording) {
                val now = System.currentTimeMillis()
                mutableState.value = mutableState.value.copy(elapsedMs = now - startTime)
                delay(200)
            }
        }
    }
}


