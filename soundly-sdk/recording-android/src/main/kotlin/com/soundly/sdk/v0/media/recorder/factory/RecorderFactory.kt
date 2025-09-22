package com.soundly.sdk.v0.media.recorder.factory

import com.soundly.sdk.v0.media.recorder.api.FlacRecorder
import com.soundly.sdk.v0.media.recorder.api.SampleSpec
import com.soundly.sdk.v0.media.recorder.api.WavRecorder
import com.soundly.sdk.v0.media.recorder.capture.PcmSourceAudioRecord
import com.soundly.sdk.v0.media.recorder.container.WavMuxer
import com.soundly.sdk.v0.media.recorder.core.FlacRecorderImpl
import com.soundly.sdk.v0.media.recorder.core.WavRecorderImpl
import com.soundly.sdk.v0.media.recorder.encode.FlacEncoderMediaCodec
import com.soundly.sdk.v0.media.recorder.io.FileSink
import java.io.File

object RecorderFactory {
    fun WavRecorder(
        outputFile: File,
        sampleRate: Int = 44100,
        channelCount: Int = 1,
        bitsPerSample: Int = 16,
    ): WavRecorder {
        val spec = SampleSpec(sampleRate, channelCount, bitsPerSample)
        val source = PcmSourceAudioRecord(spec)
        val container = WavMuxer(sampleRate, channelCount, bitsPerSample)
        val sink = FileSink(outputFile)
        return WavRecorderImpl(
            source = source,
            container = container,
            sink = sink,
        )
    }

    fun FlacRecorder(
        outputFile: File,
        sampleRate: Int = 44100,
        channelCount: Int = 1,
    ): FlacRecorder {
        val spec = SampleSpec(sampleRate, channelCount, 16)
        val source = PcmSourceAudioRecord(spec)
        val encoder = FlacEncoderMediaCodec(spec)
        val sink = FileSink(outputFile)
        return FlacRecorderImpl(
            source = source,
            encoder = encoder,
            sink = sink,
        )
    }
}
