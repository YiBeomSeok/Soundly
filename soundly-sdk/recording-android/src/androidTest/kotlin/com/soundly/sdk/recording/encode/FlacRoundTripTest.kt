package com.soundly.sdk.recording.encode

import com.soundly.sdk.recording.api.PcmChunk
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.soundly.sdk.recording.encode.SampleSpec
import com.soundly.sdk.recording.api.RecordingControls
import com.soundly.sdk.recording.api.RecordingEvent
import com.soundly.sdk.recording.api.*
import com.soundly.sdk.recording.api.RecordingTarget.Companion.flac
import com.soundly.sdk.recording.storage.FileRecordingOutput
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlacRoundTripTest {
    @Test
    fun monoShortAndLongInputsRoundTripWithoutLosingTailSamples() {
        listOf(1, 31, 4097, 44100 * 20 + 17).forEach { frames -> roundTrip(frames, 1, 44100) }
    }

    @Test
    fun stereoInputRoundTripsAcrossUnevenChunkBoundaries() {
        roundTrip(48000 + 19, 2, 48000)
    }

    @Test
    fun immediateFinishProducesAStreamHeader() = runBlocking {
        val packets = FlacEncoderMediaCodec.encode(emptyFlow(), SampleSpec(44100, 1, 16)).toList()
        val bytes = packets.flatMap { it.bytes.toList() }.toByteArray()
        assertTrue(bytes.size >= 42)
        assertArrayEquals("fLaC".toByteArray(), bytes.copyOfRange(0, 4))
    }

    @Test
    fun pauseResumeAndStopCompleteARealFlacRecording() = runBlocking {
        val spec = SampleSpec(44100, 1, 16)
        val packets = Channel<Pair<ByteArray, CompletableDeferred<Unit>>>()
        val source = flow {
            for ((bytes, accepted) in packets) {
                emit(PcmChunk(bytes))
                accepted.complete(Unit)
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("flac-recorder-", ".flac", context.cacheDir)
        file.delete()
        val controls = RecordingControls()
        val events = Channel<RecordingEvent>(Channel.UNLIMITED)
        val recorder = Soundly
        val job = launch {
            recorder.record { input = source; output = FileRecordingOutput(flac(file)); this.controls = controls }.collect { events.send(it) }
        }
        suspend fun awaitEvent(predicate: (RecordingEvent) -> Boolean): RecordingEvent = withTimeout(10_000) {
            var event = events.receive()
            while (!predicate(event)) event = events.receive()
            event
        }
        suspend fun submit(bytes: ByteArray) = withTimeout(10_000) {
            val accepted = CompletableDeferred<Unit>()
            packets.send(bytes to accepted)
            accepted.await()
        }
        val first = ByteArray(4097 * 2) { (it * 31).toByte() }
        val last = ByteArray(101 * 2) { (it * 13 + 7).toByte() }
        try {
            awaitEvent { it is RecordingEvent.State && it.state == RecordingState.Active }
            submit(first)
            awaitEvent { it is RecordingEvent.Progress && it.progress.samples == 4097L }
            controls.pause()
            awaitEvent { it is RecordingEvent.State && it.state == RecordingState.Paused }
            submit(ByteArray(1024 * 2) { 99 })
            controls.resume()
            awaitEvent { it is RecordingEvent.State && it.state == RecordingState.Active }
            submit(last)
            awaitEvent { it is RecordingEvent.Progress && it.progress.samples == 4198L }
            controls.stop()
            controls.resume()
            val completed = awaitEvent { it is RecordingEvent.Completed } as RecordingEvent.Completed
            job.join()
            assertEquals(4198L, (completed.result as RecordedAudio).progress.samples)
            assertArrayEquals(first + last, FlacTestDecoder.decode(file))
        } finally {
            job.cancelAndJoin()
            file.delete()
        }
    }

    private fun roundTrip(frames: Int, channels: Int, sampleRate: Int) = runBlocking {
        val pcm = ByteArray(frames * channels * 2) { ((it * 37 + it / 7 + 13) and 255).toByte() }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("flac-roundtrip-", ".flac", context.cacheDir).apply { delete() }
        try {
            val source = flow {
                var offset = 0
                while (offset < pcm.size) {
                    val end = minOf(pcm.size, offset + 997 * channels * 2)
                    emit(PcmChunk(pcm.copyOfRange(offset, end)))
                    offset = end
                }
            }
            val result = FileRecordingOutput(flac(file)).write(source, RecordingConfig(sampleRate, channels))
                .filterIsInstance<OutputEvent.Finished>().single().result as RecordedAudio
            assertEquals(frames.toLong(), result.progress.samples)
            assertArrayEquals(pcm, FlacTestDecoder.decode(file))
        } finally {
            file.delete()
        }
    }

}

internal object FlacTestDecoder {
    fun decode(file: File): ByteArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            assertEquals(1, extractor.trackCount)
            extractor.selectTrack(0)
            val format = extractor.getTrackFormat(0)
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            val codec = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val output = ByteArrayOutputStream()
                val info = MediaCodec.BufferInfo()
                var inputEnded = false
                var outputEnded = false
                val deadline = System.nanoTime() + 30_000_000_000L
                while (!outputEnded) {
                    check(System.nanoTime() < deadline) { "Decoder did not reach output EOS" }
                    if (!inputEnded) {
                        val index = codec.dequeueInputBuffer(10_000)
                        if (index >= 0) {
                            val buffer = checkNotNull(codec.getInputBuffer(index)).apply { clear() }
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val index = codec.dequeueOutputBuffer(info, 10_000)
                    if (index >= 0) {
                        try {
                            if (info.size > 0) {
                                val buffer = checkNotNull(codec.getOutputBuffer(index))
                                val bytes = ByteArray(info.size)
                                buffer.get(bytes)
                                output.write(bytes)
                            }
                            outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        } finally {
                            codec.releaseOutputBuffer(index, false)
                        }
                    }
                }
                return output.toByteArray()
            } finally {
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }
}
