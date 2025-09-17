package com.soundly.sdk.v0.record

import java.io.File

/**
 * Sleep recording interface for capturing audio data during sleep analysis.
 */
interface SleepRecorder {
    
    /**
     * Starts recording audio data.
     * @param outputFile The file where the recorded audio will be saved
     * @return true if recording started successfully, false otherwise
     */
    suspend fun startRecording(outputFile: File): Boolean
    
    /**
     * Stops recording audio data.
     * @return true if recording stopped successfully, false otherwise
     */
    suspend fun stopRecording(): Boolean
    
    /**
     * Checks if recording is currently in progress.
     * @return true if recording is active, false otherwise
     */
    fun isRecording(): Boolean
    
    /**
     * Gets the current recording state.
     * @return RecordingState indicating the current state
     */
    fun getRecordingState(): RecordingState
    
    /**
     * Releases all resources used by the recorder.
     */
    fun release()
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
    val channelConfig: Int = android.media.AudioFormat.CHANNEL_IN_MONO,
    val audioFormat: Int = android.media.AudioFormat.ENCODING_PCM_16BIT,
    val bufferSizeFactor: Int = 2
)

/**
 * Represents recording result.
 */
sealed class RecordingResult {
    object Success : RecordingResult()
    data class Error(val message: String, val cause: Throwable? = null) : RecordingResult()
}