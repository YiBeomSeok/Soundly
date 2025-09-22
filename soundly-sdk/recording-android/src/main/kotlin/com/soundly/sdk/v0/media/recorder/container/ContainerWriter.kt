package com.soundly.sdk.v0.media.recorder.container

import com.soundly.sdk.v0.media.recorder.io.Sink

interface ContainerWriter {
    suspend fun writeHeader(sink: Sink)
    suspend fun writePayload(sink: Sink, payload: ByteArray)
    suspend fun finalize(sink: Sink)
}
