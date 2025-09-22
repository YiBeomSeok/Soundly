package com.soundly.sdk.v0.media.recorder.api

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
}