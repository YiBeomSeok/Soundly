package com.soundly.feature.recorder

data class RecordingUiState(
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0L,
    val canRecord: Boolean = true,
    val currentFilePath: String? = null,
    val errorMessage: String? = null,
)


