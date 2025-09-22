package com.soundly.sdk.v0.media.recorder.core

import com.soundly.sdk.v0.media.recorder.api.RecorderSimpleState
import com.soundly.sdk.v0.media.recorder.api.SampleSpec
import com.soundly.sdk.v0.media.recorder.capture.PcmSource
import com.soundly.sdk.v0.media.recorder.container.ContainerWriter
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

class WavRecorderImplTest {

    private lateinit var scope: CoroutineScope
    private lateinit var source: FakePcmSource
    private lateinit var container: FakeContainerWriter
    private lateinit var sink: FakeSink
    private lateinit var recorder: WavRecorderImpl

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Unconfined)
        source = FakePcmSource(SampleSpec(44100, 1, 16))
        container = FakeContainerWriter()
        sink = FakeSink()
        recorder = WavRecorderImpl(scope, source, container, sink)
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
    fun `Prepared 후 start면 헤더 1회 쓰이고 pcm이 payload로 전달되어야 한다`() = runTest {
        recorder.prepare()
        recorder.start()

        val pcm1 = ByteArray(8) { 1 }
        val pcm2 = ByteArray(4) { 2 }
        assertTrue(source.emit(pcm1))
        assertTrue(source.emit(pcm2))

        withTimeout(1000) {
            while (container.payloads.size < 2) yield()
        }

        assertEquals(1, container.headerCount)
        assertEquals(listOf(pcm1.toList(), pcm2.toList()), container.payloads.map { it.toList() })
        recorder.stop()
    }

    @Test
    fun `pause 중에는 payload가 기록되지 않아야 하고 resume 이후에는 기록되어야 한다`() = runTest {
        recorder.prepare()
        recorder.start()

        recorder.pause()
        val pausedPcm = ByteArray(6) { 9 }
        assertTrue(source.emit(pausedPcm))

        // Give the collector a chance to process the paused sample
        withTimeout(500) { yield() }
        assertEquals(0, container.payloads.size)

        recorder.resume()
        val pcm = ByteArray(5) { 7 }
        assertTrue(source.emit(pcm))

        withTimeout(1000) {
            while (container.payloads.isEmpty()) yield()
        }
        assertEquals(listOf(pcm.toList()), container.payloads.map { it.toList() })
        recorder.stop()
    }

    @Test
    fun `stop을 호출하면 finalize와 flush와 close가 호출되고 상태는 Stopped여야 한다`() = runTest {
        recorder.prepare()
        recorder.start()

        recorder.stop()

        assertEquals(1, container.finalizeCount)
        assertEquals(1, sink.flushCount)
        assertEquals(1, sink.closeCount)
        assertEquals(RecorderSimpleState.Stopped, recorder.state.value.simple)
    }
}

// ---------------------------- fakes ----------------------------

private class FakePcmSource(
    override val spec: SampleSpec,
) : PcmSource {
    private val shared = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override fun start(): Flow<ByteArray> = shared
    override suspend fun stop() {}
    override fun release() {}
    fun emit(bytes: ByteArray): Boolean = shared.tryEmit(bytes)
}

private class FakeContainerWriter : ContainerWriter {
    var headerCount: Int = 0
    val payloads: MutableList<ByteArray> = mutableListOf()
    var finalizeCount: Int = 0
    override suspend fun writeHeader(sink: Sink) { headerCount += 1 }
    override suspend fun writePayload(sink: Sink, payload: ByteArray) { payloads.add(payload) }
    override suspend fun finalize(sink: Sink) { finalizeCount += 1 }
}

private class FakeSink : Sink {
    val writes: MutableList<ByteArray> = mutableListOf()
    var flushCount: Int = 0
    var closeCount: Int = 0
    override suspend fun write(bytes: ByteArray) { writes.add(bytes) }
    override suspend fun flush() { flushCount += 1 }
    override suspend fun close() { closeCount += 1 }
}


