package com.soundly.sdk.recording.storage

import com.soundly.sdk.recording.api.*
import com.soundly.sdk.recording.encode.FlacCodec
import com.soundly.sdk.recording.encode.FlacOutput
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class FileRecordingOutputTest {
    @Test
    fun `WAV는 마감 뒤 공개되고 데이터 크기와 샘플 수가 일치한다`() = runBlocking {
        directory { dir ->
            val file = File(dir, "audio.wav")
            val pcm = ByteArray(4097 * 4) { (it * 37).toByte() }
            val recording = FileRecordingOutput(RecordingTarget.wav(file)).write(
                flow { assertFalse(file.exists()); emit(PcmChunk(pcm)) }, RecordingConfig(channelCount = 2),
            )
            assertEquals(0, dir.list()!!.size)

            val events = recording.toList()
            assertEquals(4097L, (events.first() as OutputEvent.Progress).progress.samples)
            val result = (events.last() as OutputEvent.Finished).result as RecordedAudio
            val bytes = file.readBytes()
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(bytes.size - 8, header.getInt(4))
            assertEquals(pcm.size, header.getInt(40))
            assertArrayEquals(pcm, bytes.copyOfRange(44, bytes.size))
            assertEquals(4097L, result.progress.samples)
            assertEquals(listOf("audio.wav"), dir.list()!!.toList())
        }
    }

    @Test
    fun `취소 뒤 임시 파일과 경로 점유를 해제하고 같은 Flow를 다시 수집한다`() = runBlocking {
        directory { dir ->
            val file = File(dir, "audio.wav")
            val packets = Channel<PcmChunk>()
            var opened = 0
            var closed = 0
            val controls = RecordingControls()
            val events = Channel<RecordingEvent>(Channel.UNLIMITED)
            val recording = Soundly.record {
                input = flow { opened++; for (pcm in packets) emit(pcm) }.onCompletion { closed++ }
                this.controls = controls
                output = FileRecordingOutput(RecordingTarget.wav(file))
            }
            val first = launch { recording.collect { events.send(it) } }
            awaitProgress(events, 0)
            packets.send(PcmChunk(byteArrayOf(1, 2)))
            awaitProgress(events, 1)
            first.cancelAndJoin()
            assertEquals(1, closed)
            assertEquals(0, dir.list()!!.size)
            val second = launch { recording.collect { events.send(it) } }
            awaitProgress(events, 0)
            packets.send(PcmChunk(byteArrayOf(3, 4)))
            awaitProgress(events, 1)
            controls.stop()
            withTimeout(10_000) { second.join() }
            assertEquals(2, opened)
            assertEquals(2, closed)
            assertEquals(46L, file.length())
        }
    }

    @Test
    fun `중복 수집과 기존 파일 덮어쓰기를 거부한다`() = runBlocking {
        directory { dir ->
            val file = File(dir, "audio.wav")
            val output = FileRecordingOutput(RecordingTarget.wav(file))
            val acquired = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val first = launch { output.write(flow { acquired.complete(Unit); release.await() }, RecordingConfig()).collect() }
            acquired.await()
            assertTrue(runCatching { output.write(emptyFlow(), RecordingConfig()).collect() }.exceptionOrNull() is IllegalStateException)
            release.complete(Unit)
            first.join()
            val original = file.readBytes()
            assertTrue(runCatching { output.write(emptyFlow(), RecordingConfig()).collect() }.exceptionOrNull() is IllegalStateException)
            assertArrayEquals(original, file.readBytes())
        }
    }

    @Test
    fun `Auto는 코덱 초기화 실패에만 WAV로 전환한다`() = runBlocking {
        directory { dir ->
            val output = FileRecordingOutput(RecordingTarget.auto(File(dir, "audio.flac"))) { throw IllegalStateException("unavailable") }
            val result = output.write(flowOf(PcmChunk(byteArrayOf(1, 2))), RecordingConfig())
                .filterIsInstance<OutputEvent.Finished>().single().result as RecordedAudio
            assertEquals(RecordingFormat.Wav, result.format)
            assertEquals("audio.wav", result.file.name)
            assertEquals(listOf("audio.wav"), dir.list()!!.toList())
        }
    }

    @Test
    fun `Auto도 코덱 준비 중 취소되면 입력을 열거나 WAV로 재시도하지 않는다`() = runBlocking {
        directory { dir ->
            val cancellation = CancellationException("codec initialization cancelled")
            var inputOpened = false
            var codecAttempts = 0
            val output = FileRecordingOutput(RecordingTarget.auto(File(dir, "audio.flac"))) {
                codecAttempts++
                throw cancellation
            }
            val input = flow {
                inputOpened = true
                emit(PcmChunk(byteArrayOf(1, 2)))
            }

            val error = runCatching { output.write(input, RecordingConfig()).collect() }.exceptionOrNull()

            assertTrue(error is CancellationException)
            assertTrue(generateSequence(error) { it.cause }.any { it === cancellation })
            assertEquals(1, codecAttempts)
            assertFalse(inputOpened)
            assertEquals(0, dir.list()!!.size)
        }
    }

    @Test
    fun `FLAC 마감 실패 시 코덱과 임시 파일을 정리하고 WAV로 재시도하지 않는다`() = runBlocking {
        directory { dir ->
            val failure = IllegalStateException("finish failed")
            val codec = OutputTestCodec(failure)
            val error = runCatching {
                FileRecordingOutput(RecordingTarget.auto(File(dir, "audio.flac"))) { codec }
                    .write(flowOf(PcmChunk(byteArrayOf(1, 2))), RecordingConfig()).collect()
            }.exceptionOrNull()
            assertTrue(generateSequence(error) { it.cause }.any { it === failure })
            assertTrue(codec.closed)
            assertEquals(0, dir.list()!!.size)
        }
    }

    @Test
    fun `WAV는 불완전한 프레임을 거부하고 경로를 다시 사용할 수 있다`() = runBlocking {
        directory { dir ->
            val file = File(dir, "audio.wav")
            val output = FileRecordingOutput(RecordingTarget.wav(file))
            val config = RecordingConfig(channelCount = 2)
            assertTrue(runCatching { output.write(flowOf(PcmChunk(ByteArray(6))), config).collect() }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, dir.list()!!.size)
            val result = output.write(flowOf(PcmChunk(ByteArray(4))), config)
                .filterIsInstance<OutputEvent.Finished>().single().result as RecordedAudio
            assertEquals(1L, result.progress.samples)
            assertEquals(48L, file.length())
        }
    }

    @Test
    fun `FLAC도 불완전한 프레임을 인코딩 전에 거부하고 코덱을 정리한다`() = runBlocking {
        directory { dir ->
            val codec = OutputTestCodec()
            val error = runCatching {
                FileRecordingOutput(RecordingTarget.flac(File(dir, "audio.flac"))) { codec }
                    .write(flowOf(PcmChunk(ByteArray(6))), RecordingConfig(channelCount = 2)).collect()
            }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
            assertFalse(codec.queued)
            assertTrue(codec.closed)
            assertEquals(0, dir.list()!!.size)
        }
    }

    private suspend fun awaitProgress(events: Channel<RecordingEvent>, samples: Long) = withTimeout(10_000) {
        var event = events.receive()
        while (!((event is RecordingEvent.Progress && event.progress.samples == samples) ||
                (event is RecordingEvent.State && event.state == RecordingState.Active && samples == 0L))) {
            event = events.receive()
        }
    }

    private suspend fun directory(block: suspend (File) -> Unit) {
        val dir = Files.createTempDirectory("soundly-output-").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }
}

private class OutputTestCodec(private val finishFailure: Throwable? = null) : FlacCodec {
    var closed = false
    var queued = false
    private var ended = false
    override fun dequeueInput(timeoutUs: Long): Int = 0
    override fun inputBuffer(index: Int): ByteBuffer = ByteBuffer.allocate(16)
    override fun queueInput(index: Int, size: Int, timestampUs: Long, end: Boolean) { queued = true; ended = end }
    override fun dequeueOutput(timeoutUs: Long): FlacOutput {
        if (ended) finishFailure?.let { throw it }
        return FlacOutput.Unavailable
    }
    override fun close() { closed = true }
}
