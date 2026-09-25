package com.soundly.sdk.recording.api

/**
 * 8~48kHz, 모노·스테레오의 리틀 엔디언 PCM16 녹음 설정입니다.
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