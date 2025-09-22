package com.soundly.sdk.v0.media.recorder.capture

import com.soundly.sdk.v0.media.recorder.api.SampleSpec
import kotlinx.coroutines.flow.Flow

interface PcmSource {
    val spec: SampleSpec
    fun start(): Flow<ByteArray>
    suspend fun stop()
    fun release()
}
