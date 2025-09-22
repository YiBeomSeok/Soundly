package com.soundly.media.recorder.encode

import com.soundly.media.recorder.api.SampleSpec

interface Encoder {
    val inSpec: SampleSpec
    val outMime: String
    fun encode(pcm: ByteArray): List<ByteArray>
    fun drain(): List<ByteArray>
    fun signalEndOfStream()
    fun release()
}

interface SampleCountingEncoder {
    val totalPcmSamplesSubmitted: Long
}
