package com.soundly.sdk.recording.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.soundly.sdk.recording.api.*
import com.soundly.sdk.recording.api.RecordingTarget.Companion.wav
import com.soundly.sdk.recording.api.RecordingTarget.Companion.flac
import com.soundly.sdk.recording.capture.AudioInputs
import com.soundly.sdk.recording.encode.FlacTestDecoder
import com.soundly.sdk.recording.storage.FileRecordingOutput
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.flow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingFlowTest {
    @Test
    fun wavPauseResumeStopsWithExactSampleCount() = runBlocking {
        withDirectory { dir ->
            val harness = Harness(wav(File(dir, "audio.wav")))
            val job = harness.start(this)
            try {
                harness.await { it is RecordingEvent.State && it.state == RecordingState.Active }
                val first = ByteArray(4097 * 2) { (it * 31).toByte() }
                val last = ByteArray(101 * 2) { (it * 13 + 7).toByte() }
                harness.submit(first)
                harness.await { it is RecordingEvent.Progress && it.progress.samples == 4097L }
                harness.controls.pause()
                harness.await { it is RecordingEvent.State && it.state == RecordingState.Paused }
                harness.submit(ByteArray(1024 * 2) { 99 })
                harness.controls.resume()
                harness.await { it is RecordingEvent.State && it.state == RecordingState.Active }
                harness.submit(last)
                harness.await { it is RecordingEvent.Progress && it.progress.samples == 4198L }
                harness.controls.stop()
                val result = harness.await { it is RecordingEvent.Completed } as RecordingEvent.Completed
                job.join()
                val bytes = (result.result as RecordedAudio).file.readBytes()
                val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                assertEquals(4198L, (result.result as RecordedAudio).progress.samples)
                assertEquals(8396, header.getInt(40))
                assertArrayEquals(first + last, bytes.copyOfRange(44, bytes.size))
                assertEquals(1, harness.inputClosed)
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    @Test
    fun cancelledFlacCollectionDiscardsPartialFileAndCanBeCollectedAgain() = runBlocking {
        withDirectory { dir ->
            val file = File(dir, "audio.flac")
            val harness = Harness(flac(file))
            val first = harness.start(this)
            harness.await { it is RecordingEvent.State && it.state == RecordingState.Active }
            harness.submit(ByteArray(4097 * 2) { it.toByte() })
            harness.await { it is RecordingEvent.Progress && it.progress.samples == 4097L }
            first.cancelAndJoin()
            assertEquals(1, harness.inputClosed)
            assertEquals(0, dir.list()!!.size)
            val second = harness.start(this)
            try {
                harness.await { it is RecordingEvent.State && it.state == RecordingState.Active }
                val pcm = ByteArray(101 * 2) { (it * 37 + 13).toByte() }
                harness.submit(pcm)
                harness.await { it is RecordingEvent.Progress && it.progress.samples == 101L }
                harness.controls.stop()
                val result = harness.await { it is RecordingEvent.Completed } as RecordingEvent.Completed
                second.join()
                assertEquals(101L, (result.result as RecordedAudio).progress.samples)
                assertArrayEquals(pcm, FlacTestDecoder.decode(file))
                assertEquals(2, harness.inputClosed)
                assertEquals(listOf("audio.flac"), dir.list()!!.toList())
            } finally {
                second.cancelAndJoin()
            }
        }
    }

    @Test
    fun wavAndFlacFileInputsCanBeCollectedIntoMemory() = runBlocking {
        withDirectory { dir ->
            val config = RecordingConfig()
            val pcm = ByteArray(4097 * 2) { (it * 37 + 19).toByte() }
            for (format in listOf(RecordingFormat.Wav, RecordingFormat.Flac)) {
                val file = File(dir, "source.${format.name.lowercase()}")
                FileRecordingOutput(RecordingTarget(file, format)).write(flowOf(PcmChunk(pcm)), config).collect()
                val events = Soundly.record {
                    input = AudioInputs.file(file, config)
                    output = MemoryOutput(pcm.size.toLong())
                    this.config = config
                    bufferCapacity = 2
                }.toList()
                val result = (events.last() as RecordingEvent.Completed).result as MemoryRecording
                assertEquals(4097L, result.progress.samples)
                assertArrayEquals(pcm, result.chunks.flatMap { it.bytes.toList() }.toByteArray())
            }
        }
    }

    private suspend fun withDirectory(block: suspend (File) -> Unit) {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val dir = File.createTempFile("flow-recording-", "", cache).apply { delete(); mkdir() }
        try { block(dir) } finally { dir.deleteRecursively() }
    }
}

private class Harness(target: RecordingTarget) {
    val controls = RecordingControls()
    var inputClosed = 0
    private val packets = Channel<Pair<ByteArray, CompletableDeferred<Unit>>>()
    private val events = Channel<RecordingEvent>(Channel.UNLIMITED)
    private val input = flow {
        for ((pcm, accepted) in packets) {
            emit(PcmChunk(pcm))
            accepted.complete(Unit)
        }
    }.onCompletion { inputClosed++ }
    private val recording = Soundly.record {
        this.input = this@Harness.input
        this.controls = this@Harness.controls
        output = FileRecordingOutput(target)
    }

    fun start(scope: CoroutineScope): Job = scope.launch { recording.collect { events.send(it) } }

    suspend fun submit(pcm: ByteArray) = withTimeout(10_000) {
        val accepted = CompletableDeferred<Unit>()
        packets.send(pcm to accepted)
        accepted.await()
    }

    suspend fun await(predicate: (RecordingEvent) -> Boolean): RecordingEvent = withTimeout(10_000) {
        var event = events.receive()
        while (!predicate(event)) event = events.receive()
        event
    }
}
