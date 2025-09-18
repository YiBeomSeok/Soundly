package com.soundly.sdk.v0.record

import android.media.AudioFormat
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException

interface SleepRecorder {
    val state: StateFlow<RecordingState>

    @Throws(IllegalStateException::class, IOException::class, SecurityException::class)
    suspend fun record(output: File)
}


/**
 * Represents the current state of the sleep recorder.
 */
enum class RecordingState {
    IDLE,
    RECORDING,
    STOPPING,
    ERROR
}

/**
 * Represents recording configuration parameters.
 */
data class RecordingConfig(
    val sampleRate: Int = 44100,
    val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    val bufferSizeFactor: Int = 2
)

/**
 * Represents recording result.
 */
sealed interface RecordingResult {
    object Success : RecordingResult
    data class Error(val message: String, val cause: Throwable? = null) : RecordingResult
}