package com.soundly.sdk.v0.media.recorder.core

import android.media.MediaFormat
import com.soundly.sdk.v0.media.recorder.api.RecorderSimpleState
import com.soundly.sdk.v0.media.recorder.api.SampleSpec
import com.soundly.sdk.v0.media.recorder.capture.PcmSource
import com.soundly.sdk.v0.media.recorder.encode.Encoder
import com.soundly.sdk.v0.media.recorder.encode.SampleCountingEncoder
import com.soundly.sdk.v0.media.recorder.io.FileBackedSink
import com.soundly.sdk.v0.media.recorder.io.Sink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

class FlacRecorderImplTest {

    private lateinit var scope: CoroutineScope
    private lateinit var source: FlacFakePcmSource
    private lateinit var encoder: FakeEncoder
    private lateinit var sink: FlacFakeSink
    private lateinit var recorder: FlacRecorderImpl

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Unconfined)
        val spec = SampleSpec(44100, 1, 16)
        source = FlacFakePcmSource(spec)
        encoder = FakeEncoder(spec)
        sink = FlacFakeSink()
        recorder = FlacRecorderImpl(scope, source, encoder, sink)
    }

    @After
    fun tearDown() {
        recorder.release()
    }

    @Test
    fun `prepare를 호출하면 상태는 Prepared여야 한다`() = runTest {
        recorder.prepare()
        assertEquals(RecorderSimpleState.Prepared, recorder.state.value.simple)
    }

    @Test
    fun `start 후 pcm이 들어오면 encoder 출력이 sink에 기록되어야 한다`() = runTest {
        recorder.prepare()
        recorder.start()

        val pcm = ByteArray(8) { 1 }
        assertTrue(source.emit(pcm))

        withTimeout(1000) {
            while (sink.writes.isEmpty()) yield()
        }
        assertTrue("첫 write는 FLAC 시그니처여야 함", sink.writes.first().take(4).map { it.toInt().toChar() } == listOf('f','L','a','C'))
        recorder.stop()
    }

    @Test
    fun `pause 중에는 encode가 수행되지 않아야 하고 resume 이후에는 수행되어야 한다`() = runTest {
        recorder.prepare()
        recorder.start()
        recorder.pause()

        val pausedPcm = ByteArray(10) { 2 }
        assertTrue(source.emit(pausedPcm))

        // give time to collector
        withTimeout(300) { yield() }
        val encodeCallsWhilePaused = encoder.encodeCalls
        assertEquals(0, encodeCallsWhilePaused)

        recorder.resume()
        val pcm = ByteArray(6) { 3 }
        assertTrue(source.emit(pcm))

        withTimeout(1000) {
            while (encoder.encodeCalls == 0) yield()
        }
        recorder.stop()
    }

    @Test
    fun `stop이면 EOS와 drain과 flush와 close가 호출되고 상태는 Stopped여야 한다`() = runTest {
        recorder.prepare()
        recorder.start()
        assertTrue(source.emit(ByteArray(4) { 4 }))

        recorder.stop()

        assertTrue(encoder.signaledEos)
        assertEquals(1, sink.flushCount)
        assertEquals(1, sink.closeCount)
        assertEquals(RecorderSimpleState.Stopped, recorder.state.value.simple)
    }

    @Test
    fun `outMime이 FLAC이고 FileBackedSink이면 stop에서 totalSamples를 패치해야 한다`() = runTest {
        val spec = SampleSpec(44100, 1, 16)
        source = FlacFakePcmSource(spec)
        encoder = FakeEncoder(spec, outMime = MediaFormat.MIMETYPE_AUDIO_FLAC)
        val file = File.createTempFile("flac_test_", ".flac")
        val fileSink = FileSinkFake(file)
        recorder = FlacRecorderImpl(scope, source, encoder, fileSink)

        recorder.prepare()
        recorder.start()

        // emit exactly 100 samples (per channel): 100 * 2 bytes = 200 bytes
        val stride = 2 * spec.channelCount
        val totalSamples = 100
        assertTrue(source.emit(ByteArray(totalSamples * stride) { 5 }))

        recorder.stop()

        // Verify STREAMINFO total samples patched
        val patched = readTotalSamples36Bits(file)
        assertEquals(totalSamples.toLong(), patched)

        file.delete()
    }
}

// ---------------------------- fakes ----------------------------

private class FlacFakePcmSource(
    override val spec: SampleSpec,
) : PcmSource {
    private val shared = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override fun start(): Flow<ByteArray> = shared
    override suspend fun stop() {}
    override fun release() {}
    fun emit(bytes: ByteArray): Boolean = shared.tryEmit(bytes)
}

private class FakeEncoder(
    override val inSpec: SampleSpec,
    override val outMime: String = "application/octet-stream",
) : Encoder, SampleCountingEncoder {
    var headerWritten: Boolean = false
    var signaledEos: Boolean = false
    var encodeCalls: Int = 0
    override var totalPcmSamplesSubmitted: Long = 0

    override fun encode(pcm: ByteArray): List<ByteArray> {
        encodeCalls += 1
        val outputs = mutableListOf<ByteArray>()
        if (!headerWritten) {
            outputs += minimalFlacHeader()
            headerWritten = true
        }
        // passthrough chunk
        outputs += pcm

        val bytesPerSamplePerChannel = 2 * inSpec.channelCount
        totalPcmSamplesSubmitted += (pcm.size / bytesPerSamplePerChannel)
        return outputs
    }

    override fun drain(): List<ByteArray> = emptyList()
    override fun signalEndOfStream() { signaledEos = true }
    override fun release() {}

    private fun minimalFlacHeader(): ByteArray {
        val out = ByteArray(4 + 4 + 34)
        // 'fLaC'
        out[0] = 'f'.code.toByte(); out[1] = 'L'.code.toByte(); out[2] = 'a'.code.toByte(); out[3] = 'C'.code.toByte()
        // STREAMINFO header: type=0, length=34
        out[4] = 0
        out[5] = 0
        out[6] = 0
        out[7] = 34
        // 34 bytes of zeros are fine; FlacFinalizer will patch lower 36 bits at offset 8+10
        return out
    }
}

private class FlacFakeSink : Sink {
    val writes: MutableList<ByteArray> = mutableListOf()
    var flushCount: Int = 0
    var closeCount: Int = 0
    override suspend fun write(bytes: ByteArray) { writes.add(bytes) }
    override suspend fun flush() { flushCount += 1 }
    override suspend fun close() { closeCount += 1 }
}

private class FileSinkFake(
    override val outputFile: File,
) : Sink, FileBackedSink {
    private val fos = FileOutputStream(outputFile)
    override suspend fun write(bytes: ByteArray) { fos.write(bytes) }
    override suspend fun flush() { fos.flush() }
    override suspend fun close() { fos.close() }
}

// ---------------------------- helpers ----------------------------

private fun readTotalSamples36Bits(file: File): Long {
    RandomAccessFile(file, "r").use { raf ->
        if (raf.length() < 4 + 4 + 34) return -1
        val sig = ByteArray(4)
        raf.readFully(sig)
        if (!(sig[0] == 'f'.code.toByte() && sig[1] == 'L'.code.toByte() && sig[2] == 'a'.code.toByte() && sig[3] == 'C'.code.toByte())) return -1
        val header = ByteArray(4)
        raf.readFully(header)
        val type = header[0].toInt() and 0x7F
        val length = ((header[1].toInt() and 0xFF) shl 16) or ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
        if (type != 0 || length != 34) return -1
        val fieldOffset = 8 + 10
        raf.seek(fieldOffset.toLong())
        val buf = ByteArray(8)
        raf.readFully(buf)
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (buf[i].toLong() and 0xFF)
        return v and ((1L shl 36) - 1)
    }
}


