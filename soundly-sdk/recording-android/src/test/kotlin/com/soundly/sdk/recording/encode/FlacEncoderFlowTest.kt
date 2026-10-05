package com.soundly.sdk.recording.encode

import com.soundly.sdk.recording.api.PcmChunk
import com.soundly.sdk.recording.api.RecorderException
import java.nio.ByteBuffer
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class FlacEncoderFlowTest {
    @Test
    fun `EOS 입력 대기 중 꺼낸 출력과 마지막 출력을 모두 보존한다`() = runBlocking {
        val codec = FakeFlacCodec().apply {
            inputs.addAll(listOf(-1, -1, 0))
            outputs.addAll(listOf(
                FlacOutput.FormatChanged, packet(1), FlacOutput.Unavailable,
                packet(2), FlacOutput.Unavailable, packet(3, end = true),
            ))
        }
        val packets = FlacEncoderMediaCodec.encode(emptyFlow(), SampleSpec(44100, 1, 16), { codec }).toList()
        assertEquals(listOf<Byte>(1, 2, 3), packets.flatMap { it.bytes.toList() })
        assertEquals(1, codec.queued.count { it.end })
        assertEquals(1, codec.closeCount)
    }

    @Test
    fun `스테레오 프레임 경계를 유지하고 누적 샘플 수로 PTS를 계산한다`() = runBlocking {
        val codec = FakeFlacCodec(capacity = 7).apply {
            outputs.addAll(listOf(FlacOutput.Unavailable, FlacOutput.Unavailable, FlacOutput.Unavailable, packet(0, end = true)))
        }
        val pcm = ByteArray(12) { it.toByte() }
        FlacEncoderMediaCodec.encode(flowOf(PcmChunk(pcm)), SampleSpec(8000, 2, 16), { codec }).collect()
        val audio = codec.queued.filterNot { it.end }
        assertEquals(listOf(4, 4, 4), audio.map { it.bytes.size })
        assertEquals(listOf(0L, 125L, 250L), audio.map { it.timestampUs })
        assertArrayEquals(pcm, audio.flatMap { it.bytes.toList() }.toByteArray())
        assertEquals(375L, codec.queued.last().timestampUs)
    }

    @Test
    fun `입력 EOS 공간이 없으면 시간 제한으로 실패하고 해제한다`() = runBlocking {
        val codec = FakeFlacCodec().apply { defaultInput = -1 }
        var clock = 0L
        val error = runCatching {
            FlacEncoderMediaCodec.encode(emptyFlow(), SampleSpec(44100, 1, 16), { codec }, { clock++ }, 20).collect()
        }.exceptionOrNull()
        assertTrue(error is RecorderException)
        assertTrue(codec.queued.isEmpty())
        assertEquals(1, codec.closeCount)
    }

    @Test
    fun `출력 EOS가 없으면 시간 제한으로 실패한다`() = runBlocking {
        val codec = FakeFlacCodec()
        var clock = 0L
        val error = runCatching {
            FlacEncoderMediaCodec.encode(emptyFlow(), SampleSpec(44100, 1, 16), { codec }, { clock++ }, 20).collect()
        }.exceptionOrNull()
        assertTrue(error is RecorderException)
        assertEquals(1, codec.queued.count { it.end })
        assertEquals(1, codec.closeCount)
    }

    @Test
    fun `PCM 입력 공간이 계속 없으면 무한 반복하지 않는다`() = runBlocking {
        val codec = FakeFlacCodec().apply { defaultInput = -1 }
        var clock = 0L
        val error = runCatching {
            FlacEncoderMediaCodec.encode(flowOf(PcmChunk(ByteArray(2))), SampleSpec(44100, 1, 16), { codec }, { clock++ }, 20).collect()
        }.exceptionOrNull()
        assertTrue(error is RecorderException)
        assertEquals(1, codec.closeCount)
    }

    @Test
    fun `불완전한 PCM 프레임은 제출하지 않고 해제한다`() = runBlocking {
        val codec = FakeFlacCodec()
        val error = runCatching {
            FlacEncoderMediaCodec.encode(flowOf(PcmChunk(ByteArray(6))), SampleSpec(44100, 2, 16), { codec }).collect()
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(codec.queued.isEmpty())
        assertEquals(1, codec.closeCount)
    }

    @Test
    fun `Flow를 다시 수집하면 코덱도 새로 생성한다`() = runBlocking {
        val codecs = mutableListOf<FakeFlacCodec>()
        val encoding = FlacEncoderMediaCodec.encode(emptyFlow(), SampleSpec(44100, 1, 16), {
            FakeFlacCodec().apply { outputs.add(packet(1, end = true)); codecs += this }
        })
        assertTrue(codecs.isEmpty())
        repeat(2) { encoding.collect() }
        assertEquals(2, codecs.size)
        assertTrue(codecs.all { it.closeCount == 1 })
    }

    @Test
    fun `수집자가 실패하면 EOS를 시도하지 않고 코덱을 해제한다`() = runBlocking {
        val codec = FakeFlacCodec().apply { outputs.add(packet(1)) }
        val failure = IllegalStateException("collector failed")
        val error = runCatching {
            FlacEncoderMediaCodec.encode(flowOf(PcmChunk(ByteArray(2))), SampleSpec(44100, 1, 16), { codec })
                .collect { throw failure }
        }.exceptionOrNull()
        assertSame(failure, error)
        assertEquals(1, codec.closeCount)
        assertTrue(codec.queued.none { it.end })
    }

    @Test
    fun `입력이 취소되면 EOS 없이 정리하고 원래 취소를 전달한다`() = runBlocking {
        val codec = FakeFlacCodec()
        val cancellation = CancellationException("input cancelled")
        val source = flow {
            emit(PcmChunk(ByteArray(2)))
            throw cancellation
        }

        val error = runCatching {
            FlacEncoderMediaCodec.encode(source, SampleSpec(44100, 1, 16), { codec }).collect()
        }.exceptionOrNull()

        assertSame(cancellation, error)
        assertEquals(1, codec.closeCount)
        assertEquals(1, codec.queued.size)
        assertTrue(codec.queued.none { it.end })
    }

    @Test
    fun `수집자가 첫 패킷에서 끝내면 EOS 없이 코덱을 해제한다`() = runBlocking {
        val codec = FakeFlacCodec().apply { outputs.add(packet(7)) }

        val packets = FlacEncoderMediaCodec.encode(
            flowOf(PcmChunk(ByteArray(2))), SampleSpec(44100, 1, 16), { codec },
        ).take(1).toList()

        assertArrayEquals(byteArrayOf(7), packets.single().bytes)
        assertEquals(1, codec.closeCount)
        assertTrue(codec.queued.none { it.end })
    }

    private fun packet(value: Int, end: Boolean = false): FlacOutput.Packet =
        FlacOutput.Packet(byteArrayOf(value.toByte()), end)
}

private class FakeFlacCodec(capacity: Int = 16) : FlacCodec {
    data class Queued(val bytes: ByteArray, val timestampUs: Long, val end: Boolean)
    val inputs = ArrayDeque<Int>()
    val outputs = ArrayDeque<FlacOutput>()
    val queued = mutableListOf<Queued>()
    var defaultInput = 0
    var closeCount = 0
    private val buffer = ByteBuffer.allocate(capacity)
    override fun dequeueInput(timeoutUs: Long): Int = inputs.pollFirst() ?: defaultInput
    override fun inputBuffer(index: Int): ByteBuffer = buffer
    override fun queueInput(index: Int, size: Int, timestampUs: Long, end: Boolean) {
        queued += Queued(buffer.array().copyOf(size), timestampUs, end)
    }
    override fun dequeueOutput(timeoutUs: Long): FlacOutput = outputs.pollFirst() ?: FlacOutput.Unavailable
    override fun close() { closeCount++ }
}
