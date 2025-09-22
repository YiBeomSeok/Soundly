package com.soundly.sdk.v0.media.recorder.factory

import com.soundly.sdk.v0.media.recorder.api.SampleSpec
import com.soundly.sdk.v0.media.recorder.api.Recorder
import com.soundly.sdk.v0.media.recorder.capture.PcmSourceAudioRecord
import com.soundly.sdk.v0.media.recorder.container.WavMuxer
import com.soundly.sdk.v0.media.recorder.core.RecordingPipeline
import com.soundly.sdk.v0.media.recorder.io.FileSink
import com.soundly.sdk.v0.media.recorder.encode.FlacEncoderMediaCodec
import java.io.File

object RecorderFactory {
    fun createWavRecorder(
        outputFile: File,
        sampleRate: Int = 44100,
        channelCount: Int = 1,
        bitsPerSample: Int = 16,
    ): Recorder {
        val spec = SampleSpec(sampleRate, channelCount, bitsPerSample)
        val source = PcmSourceAudioRecord(spec)
        val container = WavMuxer(sampleRate, channelCount, bitsPerSample)
        val sink = FileSink(outputFile)
        return RecordingPipeline(
            source = source,
            encoder = null,
            container = container,
            sink = sink,
        )
    }

    // Placeholder for FLAC; wiring will be added in step5
    fun createFlacRecorder(
        outputFile: File,
        sampleRate: Int = 44100,
        channelCount: Int = 1,
    ): Recorder {
        val spec = SampleSpec(sampleRate, channelCount, 16)
        val source = PcmSourceAudioRecord(spec)
        val encoder = FlacEncoderMediaCodec(spec)
        val sink = FileSink(outputFile)
        return RecordingPipeline(
            source = source,
            encoder = encoder,
            container = null,
            sink = sink,
        )
    }
}
