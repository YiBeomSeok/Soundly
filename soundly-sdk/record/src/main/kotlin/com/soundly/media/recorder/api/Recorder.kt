package com.soundly.media.recorder.api

import kotlinx.coroutines.flow.StateFlow

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
