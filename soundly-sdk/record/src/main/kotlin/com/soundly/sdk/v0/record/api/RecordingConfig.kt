package com.soundly.sdk.v0.record.api

/**
 * Configuration for PCM recording.
 *
 * - Supports 16-bit little-endian PCM.
 * - Supports mono (1) and stereo (2) channel counts.
 * - Supports sample rates between 8000 and 48000 Hz (inclusive).
 */
data class RecordingConfig(
    val sampleRateHz: Int = 44100,
    val channelCount: Int = 1,
    val bufferSizeFactor: Int = 2,
) {
    init {
        require(sampleRateHz in 8_000..48_000) { "sampleRateHz must be in [8000, 48000]" }
        require(channelCount == 1 || channelCount == 2) { "channelCount must be 1 (mono) or 2 (stereo)" }
        require(bufferSizeFactor >= 1) { "bufferSizeFactor must be >= 1" }
    }

    /** Always 16-bit little-endian PCM for this library. */
    val bitsPerSample: Int = 16
}

/**
 * Public recording results.
 */
sealed class RecordingResult {
    data object Success : RecordingResult()
    data class Error(val message: String, val cause: Throwable? = null) : RecordingResult()
}
