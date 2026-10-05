package com.soundly.sdk.recording.api

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MemoryOutputTest {
    @Test
    fun `출력은 수집할 때마다 입력을 한 번 읽고 독립된 복사본을 만든다`() = runTest {
        val bytes = byteArrayOf(1, 0, 2, 0)
        var opened = 0
        val source = flow {
            opened++
            emit(PcmChunk(bytes))
        }
        val output = MemoryOutput(100).write(source, RecordingConfig())
        assertEquals(0, opened)

        val first = output.toList()
        val second = output.toList()
        bytes.fill(9)

        assertEquals(2, opened)
        for (events in listOf(first, second)) {
            assertEquals(2L, (events.first() as OutputEvent.Progress).progress.samples)
            val result = (events.last() as OutputEvent.Finished).result as MemoryRecording
            assertArrayEquals(byteArrayOf(1, 0, 2, 0), result.chunks.single().bytes)
            assertEquals(2L, result.progress.samples)
        }
        val firstResult = (first.last() as OutputEvent.Finished).result as MemoryRecording
        val secondResult = (second.last() as OutputEvent.Finished).result as MemoryRecording
        assertNotSame(firstResult.chunks.single().bytes, secondResult.chunks.single().bytes)
    }

    @Test
    fun `출력 이벤트 수집자 실패를 보존하고 같은 출력 흐름을 다시 수집한다`() = runTest {
        var opened = 0
        val source = flow {
            opened++
            emit(PcmChunk(byteArrayOf(1, 0)))
        }
        val output = MemoryOutput(100).write(source, RecordingConfig())
        val failure = IllegalStateException("collector failed")
        val error = runCatching {
            output.collect { throw failure }
        }.exceptionOrNull()
        assertSame(failure, error)

        val events = output.toList()
        assertEquals(2, opened)
        assertTrue(events.last() is OutputEvent.Finished)
    }
}
