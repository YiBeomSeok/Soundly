package com.soundly.sdk.v0.media.recorder.api

import kotlinx.coroutines.flow.StateFlow
import com.soundly.sdk.v0.media.recorder.container.ContainerWriter
import com.soundly.sdk.v0.media.recorder.encode.Encoder

data class SampleSpec(
    val sampleRate: Int,
    val channelCount: Int,
    val bitsPerSample: Int,
)

enum class RecorderSimpleState { Idle, Prepared, Recording, Paused, Stopped, Error }

data class RecorderState(
    val simple: RecorderSimpleState,
    val errorMessage: String? = null,
)

interface Recorder {
    val state: StateFlow<RecorderState>
    suspend fun prepare()
    suspend fun start()
    suspend fun pause()
    suspend fun resume()
    suspend fun stop()
    fun release()
}

interface WavRecorder : Recorder {
    val container: ContainerWriter
}

interface FlacRecorder : Recorder {
    val encoder: Encoder
}
