package com.soundly.media.recorder.capture

import com.soundly.media.recorder.api.SampleSpec
import kotlinx.coroutines.flow.Flow

interface PcmSource {
    val spec: SampleSpec
    fun start(): Flow<ByteArray>
    suspend fun stop()
    fun release()
}
