package com.soundly.sdk.recording.core

import com.soundly.sdk.recording.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowRecorderTest {
    @Test
    fun `구성은 자원을 열지 않고 같은 Flow를 수집할 때마다 새 실행을 만든다`() = runTest {
        var opened = 0
        var closed = 0
        val input = flow { opened++; emit(PcmChunk(byteArrayOf(1, 2))) }.onCompletion { closed++ }
        val recorder = Soundly
        val recording = recorder.record { this.input = input; output = MemoryOutput(100) }
        assertEquals(0, opened)

        repeat(2) {
            val result = (recording.toList().last() as RecordingEvent.Completed).result as MemoryRecording
            assertArrayEquals(byteArrayOf(1, 2), result.chunks.single().bytes)
        }
        assertEquals(2, opened)
        assertEquals(2, closed)
    }

    @Test
    fun `같은 녹음기의 동시 수집은 서로 기다리지 않고 독립된 상태로 실행한다`() = runTest {
        val recorder = Soundly
        val release = CompletableDeferred<Unit>()
        val firstEvents = mutableListOf<RecordingEvent>()
        val secondEvents = mutableListOf<RecordingEvent>()
        var secondOpened = false

        val first = launch {
            recorder.record {
                input = flow {
                    emit(PcmChunk(byteArrayOf(1, 0)))
                    release.await()
                }
                output = MemoryOutput(100)
            }.toList(firstEvents)
        }
        runCurrent()

        val second = launch {
            recorder.record {
                config = RecordingConfig(sampleRateHz = 8000)
                input = flow {
                    secondOpened = true
                    emit(PcmChunk(byteArrayOf(2, 0)))
                }
                output = MemoryOutput(100)
            }.toList(secondEvents)
        }
        runCurrent()
        assertTrue(secondOpened)
        assertTrue(second.isCompleted)
        assertFalse(first.isCompleted)

        release.complete(Unit)
        joinAll(first, second)

        val firstResult = (firstEvents.last() as RecordingEvent.Completed).result as MemoryRecording
        val secondResult = (secondEvents.last() as RecordingEvent.Completed).result as MemoryRecording
        assertArrayEquals(byteArrayOf(1, 0), firstResult.chunks.single().bytes)
        assertArrayEquals(byteArrayOf(2, 0), secondResult.chunks.single().bytes)
        assertEquals(1L, firstResult.progress.samples)
        assertEquals(1L, secondResult.progress.samples)
        assertEquals(8000, secondResult.progress.sampleRateHz)
        assertEquals(0L, (secondEvents.first() as RecordingEvent.State).progress.samples)
    }

    @Test
    fun `같은 Flow의 한 수집을 취소해도 다른 수집은 계속 기록한다`() = runTest {
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<RecordingEvent>()
        var opened = 0
        var closed = 0
        val recording = Soundly.record {
            input = flow {
                val value = ++opened
                emit(PcmChunk(byteArrayOf(value.toByte(), 0)))
                release.await()
            }.onCompletion { closed++ }
            output = MemoryOutput(100)
        }

        val first = launch { recording.collect() }
        runCurrent()
        val second = launch { recording.toList(events) }
        runCurrent()
        assertEquals(2, opened)

        first.cancelAndJoin()
        assertEquals(1, closed)
        assertTrue(second.isActive)

        release.complete(Unit)
        second.join()
        val result = (events.last() as RecordingEvent.Completed).result as MemoryRecording
        assertArrayEquals(byteArrayOf(2, 0), result.chunks.single().bytes)
        assertEquals(1L, result.progress.samples)
        assertEquals(2, closed)
    }

    @Test
    fun `느린 소비자 앞에 쌓인 PCM은 stop 후에도 순서대로 모두 처리한다`() = runTest {
        val packets = Channel<PcmChunk>(Channel.UNLIMITED)
        val gate = CompletableDeferred<Unit>()
        val output = TestOutput(gate)
        var closed = false
        val controls = RecordingControls()
        val recording = Soundly.record {
            input = packets.receiveAsFlow().onCompletion { closed = true }
            this.controls = controls
            this.output = output
        }
        val events = mutableListOf<RecordingEvent>()
        val job = launch { recording.toList(events) }
        runCurrent()
        listOf(1, 3, 5, 7).forEach { packets.send(PcmChunk(byteArrayOf(it.toByte(), (it + 1).toByte()))) }
        runCurrent()
        assertEquals(0, output.bytes.size)
        controls.stop()
        runCurrent()
        assertTrue(closed)
        assertFalse(output.finished)
        gate.complete(Unit)
        job.join()

        assertEquals((1..8).map { it.toByte() }, output.bytes)
        assertTrue(output.finished)
        assertEquals(4L, (events.last() as RecordingEvent.Completed).result.progress.samples)
    }

    @Test
    fun `파일 입력의 대기 정책은 작은 버퍼에서도 모든 PCM을 전달한다`() = runTest {
        val output = TestOutput(delayMillis = 5)
        val events = Soundly.record {
            config = RecordingConfig(sampleRateHz = 8000)
            bufferCapacity = 1
            input = flow { repeat(50) { emit(PcmChunk(byteArrayOf(it.toByte(), 0))) } }
            this.output = output
        }.toList()
        assertEquals(100, output.bytes.size)
        assertEquals(50L, (events.last() as RecordingEvent.Completed).result.progress.samples)
    }

    @Test
    fun `가득 찬 버퍼에서도 정지 명령 앞의 PCM을 생략하지 않고 마감한다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val output = TestOutput(gate)
        var closed = false
        val events = mutableListOf<RecordingEvent>()
        val job = launch {
            Soundly.record {
                bufferCapacity = 3
                input = flow<RecordingInput> {
                    repeat(50) { emit(PcmChunk(byteArrayOf(it.toByte(), 0))) }
                    emit(RecordingCommand.Stop)
                    emit(PcmChunk(byteArrayOf(99, 0)))
                }.onCompletion { closed = true }
                this.output = output
            }.toList(events)
        }
        runCurrent()
        assertFalse(output.finished)

        gate.complete(Unit)
        job.join()

        val expected = (0 until 50).flatMap { listOf(it.toByte(), 0.toByte()) }
        assertEquals(expected, output.bytes)
        assertEquals(50L, (events.last() as RecordingEvent.Completed).result.progress.samples)
        assertTrue(closed)
        assertTrue(output.finished)
    }

    @Test
    fun `대기열 용량이 없어도 정지 명령 앞의 PCM을 모두 기록한다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val output = TestOutput(gate)
        var closed = false
        val job = launch {
            Soundly.record {
                bufferCapacity = 0
                input = flow<RecordingInput> {
                    repeat(10) { emit(PcmChunk(byteArrayOf(it.toByte(), 0))) }
                    emit(RecordingCommand.Stop)
                }.onCompletion { closed = true }
                this.output = output
            }.collect()
        }
        runCurrent()
        assertFalse(output.finished)

        gate.complete(Unit)
        job.join()

        assertEquals((0 until 10).flatMap { listOf(it.toByte(), 0.toByte()) }, output.bytes)
        assertTrue(closed)
        assertTrue(output.finished)
    }

    @Test
    fun `가득 찬 버퍼에서도 입력 순서대로 명령을 적용하고 같은 출력을 유지한다`() = runTest {
        val packets = Channel<RecordingInput>(Channel.UNLIMITED)
        val gate = CompletableDeferred<Unit>()
        val output = TestOutput(gate)
        var opened = 0
        var closed = 0
        val job = launch {
            Soundly.record {
                bufferCapacity = 1
                input = packets.receiveAsFlow()
                    .onStart { opened++ }
                    .onCompletion { closed++ }
                this.output = output
            }.collect()
        }
        runCurrent()

        (1..10).forEach { packets.send(PcmChunk(byteArrayOf(it.toByte(), 0))) }
        packets.send(RecordingCommand.Pause)
        packets.send(PcmChunk(byteArrayOf(77, 0)))
        packets.send(RecordingCommand.Record)
        packets.send(PcmChunk(byteArrayOf(99, 0)))
        packets.send(RecordingCommand.Stop)
        packets.send(PcmChunk(byteArrayOf(100, 0)))
        runCurrent()
        assertEquals(0, closed)

        gate.complete(Unit)
        job.join()

        val expected = ((1..10).toList() + 99).flatMap { listOf(it.toByte(), 0.toByte()) }
        assertEquals(expected, output.bytes)
        assertEquals(1, opened)
        assertEquals(1, closed)
        assertEquals(1, output.opened)
    }

    @Test
    fun `처음부터 일시정지한 조합은 입력을 기록하지 않고 자연 종료한다`() = runTest {
        val controls = RecordingControls().apply { pause() }
        var opened = 0
        val events = Soundly.record {
            input = flow {
                opened++
                repeat(10) { emit(PcmChunk(byteArrayOf(it.toByte(), 0))) }
            }
            this.controls = controls
            output = MemoryOutput(100)
        }.toList()

        val result = (events.last() as RecordingEvent.Completed).result as MemoryRecording
        assertTrue(result.chunks.isEmpty())
        assertEquals(0L, result.progress.samples)
        assertEquals(1, opened)
        val states = events.filterIsInstance<RecordingEvent.State>().map { it.state }
        assertEquals(listOf(RecordingState.Starting, RecordingState.Paused, RecordingState.Stopping), states)
    }

    @Test
    fun `입력이 도착하지 않아도 제어 전환을 알리고 중지한다`() = runTest {
        val controls = RecordingControls()
        val events = mutableListOf<RecordingEvent>()
        val job = launch {
            Soundly.record {
                input = flow { awaitCancellation() }
                this.controls = controls
                output = MemoryOutput(100)
            }.toList(events)
        }
        runCurrent()
        controls.pause()
        runCurrent()
        controls.resume()
        runCurrent()
        controls.stop()
        job.join()

        val states = events.filterIsInstance<RecordingEvent.State>().map { it.state }
        assertEquals(listOf(RecordingState.Starting, RecordingState.Active, RecordingState.Paused,
            RecordingState.Active, RecordingState.Stopping), states)
        assertTrue(events.last() is RecordingEvent.Completed)
    }

    @Test
    fun `pause는 새 PCM을 제외하고 이미 받은 PCM과 같은 출력을 유지한다`() = runTest {
        val packets = Channel<PcmChunk>()
        val controls = RecordingControls()
        val output = TestOutput()
        val job = launch {
            Soundly.record {
                input = packets.receiveAsFlow()
                this.controls = controls
                this.output = output
            }.collect()
        }
        runCurrent()
        packets.send(PcmChunk(byteArrayOf(1, 2)))
        runCurrent()
        controls.pause()
        runCurrent()
        packets.send(PcmChunk(byteArrayOf(3, 4)))
        runCurrent()
        controls.resume()
        runCurrent()
        packets.send(PcmChunk(byteArrayOf(5, 6)))
        runCurrent()
        controls.stop()
        controls.resume()
        job.join()
        assertEquals(listOf<Byte>(1, 2, 5, 6), output.bytes)
        assertEquals(1, output.opened)
    }

    @Test
    fun `입력의 정상 종료와 빈 입력도 정상 마감한다`() = runTest {
        val output = TestOutput()
        val events = Soundly.record {
            input = emptyFlow()
            this.output = output
        }.toList()
        assertEquals(0L, (events.last() as RecordingEvent.Completed).result.progress.samples)
        assertTrue(output.finished)
    }

    @Test
    fun `수집 취소는 입력과 출력을 정리하고 완료를 만들지 않는다`() = runTest {
        var inputClosed = false
        val output = TestOutput()
        val events = mutableListOf<RecordingEvent>()
        val job = launch {
            Soundly.record {
                input = flow<PcmChunk> { awaitCancellation() }.onCompletion { inputClosed = true }
                this.output = output
            }.toList(events)
        }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(inputClosed)
        assertTrue(output.closed)
        assertFalse(output.finished)
        assertFalse(events.any { it is RecordingEvent.Completed })
    }

    @Test
    fun `입력 실패와 출력 정리 실패를 보존한다`() = runTest {
        val failure = IllegalStateException("input failed")
        val cleanup = IllegalStateException("cleanup failed")
        val output = TestOutput(closeFailure = cleanup)
        val failures = mutableListOf<Throwable>()
        Soundly.record {
            input = flow { emit(PcmChunk(ByteArray(2))); throw failure }
            this.output = output
        }.catch { failures += it }.collect()

        val causes = generateSequence(failures.single()) { it.cause }.toList()
        assertTrue(causes.any { it === failure })
        assertTrue(causes.any { cleanup in it.suppressed })
        assertTrue(output.closed)
    }

    @Test
    fun `입력의 취소 예외를 Recorder 예외로 바꾸지 않는다`() = runTest {
        val cancellation = CancellationException("source cancelled")
        val failure = runCatching {
            Soundly.record {
                input = flow { throw cancellation }
                output = MemoryOutput(100)
            }.collect()
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertTrue(generateSequence(failure) { it.cause }.any { it === cancellation })
    }

    @Test
    fun `이미 stop한 실행은 입력을 수집하지 않는다`() = runTest {
        var opened = false
        val events = Soundly.record {
            controls = RecordingControls().apply { stop() }
            input = flow { opened = true; emit(PcmChunk(ByteArray(2))) }
            output = MemoryOutput(100)
        }.toList()
        assertFalse(opened)
        assertEquals(0L, (events.last() as RecordingEvent.Completed).result.progress.samples)
    }

    @Test
    fun `메모리 한도와 불완전한 프레임을 명시적으로 거부한다`() = runTest {
        for ((bytes, limit, reason) in listOf(
            Triple(ByteArray(4), 2L, RecorderException.Reason.MemoryLimit),
            Triple(ByteArray(3), 100L, RecorderException.Reason.InvalidPcm),
        )) {
            val failures = mutableListOf<Throwable>()
            Soundly.record {
                input = flowOf(PcmChunk(bytes))
                output = MemoryOutput(limit)
            }.catch { failures += it }.collect()
            assertEquals(reason, (failures.single() as RecorderException).reason)
        }
    }

    @Test
    fun `출력이 마감 결과 없이 종료하면 완료를 만들지 않는다`() = runTest {
        val failures = mutableListOf<Throwable>()
        val events = mutableListOf<RecordingEvent>()
        Soundly.record {
            input = emptyFlow()
            output = object : RecordingOutput {
                override fun write(source: Flow<PcmChunk>, config: RecordingConfig): Flow<OutputEvent> = flow {
                    source.collect()
                }
            }
        }.catch { failures += it }.toList(events)

        assertEquals(RecorderException.Reason.OutputContract, (failures.single() as RecorderException).reason)
        assertFalse(events.any { it is RecordingEvent.Completed })
    }

    @Test
    fun `출력이 마감 결과를 두 번 내면 완료를 만들지 않는다`() = runTest {
        val events = mutableListOf<RecordingEvent>()
        val failures = mutableListOf<Throwable>()
        var closed = false

        Soundly.record {
            input = flowOf(PcmChunk(byteArrayOf(1, 0))).onCompletion { closed = true }
            output = object : RecordingOutput {
                override fun write(source: Flow<PcmChunk>, config: RecordingConfig): Flow<OutputEvent> = flow {
                    val chunks = source.toList()
                    val result = MemoryRecording(chunks, config, RecordingProgress(1, config.sampleRateHz))
                    repeat(2) { emit(OutputEvent.Finished(result)) }
                }
            }
        }.catch { failures += it }.toList(events)

        assertEquals(RecorderException.Reason.OutputContract, (failures.single() as RecorderException).reason)
        assertTrue(closed)
        assertFalse(events.any { it is RecordingEvent.Completed })
    }

    @Test
    fun `빈 PCM과 반복 명령은 진행량이나 상태 이벤트를 늘리지 않는다`() = runTest {
        val events = Soundly.record {
            input = flowOf(
                PcmChunk(ByteArray(0)),
                RecordingCommand.Record,
                PcmChunk(byteArrayOf(1, 0)),
                RecordingCommand.Pause,
                RecordingCommand.Pause,
                PcmChunk(byteArrayOf(2, 0)),
                RecordingCommand.Record,
                RecordingCommand.Record,
                PcmChunk(byteArrayOf(3, 0)),
                RecordingCommand.Stop,
            )
            output = MemoryOutput(100)
        }.toList()

        val states = events.filterIsInstance<RecordingEvent.State>().map { it.state }
        assertEquals(
            listOf(RecordingState.Starting, RecordingState.Active, RecordingState.Paused,
                RecordingState.Active, RecordingState.Stopping),
            states,
        )
        val samples = events.filterIsInstance<RecordingEvent.Progress>().map { it.progress.samples }
        assertEquals(listOf(1L, 2L), samples)
        val result = (events.last() as RecordingEvent.Completed).result as MemoryRecording
        assertEquals(listOf<Byte>(1, 0, 3, 0), result.chunks.flatMap { it.bytes.toList() })
    }

    @Test
    fun `완료는 지연된 입력 정리와 출력 마감이 모두 끝난 뒤 전달한다`() = runTest {
        val controls = RecordingControls()
        var inputClosed = false
        var outputFinished = false
        val events = mutableListOf<RecordingEvent>()

        val job = launch {
            Soundly.record {
                input = flow {
                    emit(PcmChunk(byteArrayOf(1, 0)))
                    awaitCancellation()
                }.onCompletion {
                    withContext(NonCancellable) {
                        delay(50)
                        inputClosed = true
                    }
                }
                this.controls = controls
                output = object : RecordingOutput {
                    override fun write(source: Flow<PcmChunk>, config: RecordingConfig): Flow<OutputEvent> {
                        return MemoryOutput(100).write(source, config).onEach { event ->
                            if (event is OutputEvent.Finished) {
                                delay(50)
                                outputFinished = true
                            }
                        }
                    }
                }
            }.collect { event ->
                if (event is RecordingEvent.Completed) {
                    assertTrue(inputClosed)
                    assertTrue(outputFinished)
                }
                events += event
            }
        }
        runCurrent()
        controls.stop()
        runCurrent()
        assertFalse(inputClosed)
        assertFalse(outputFinished)
        assertFalse(events.any { it is RecordingEvent.Completed })

        job.join()
        assertTrue(events.last() is RecordingEvent.Completed)
    }

    @Test
    fun `마감 결과 뒤에 출력이 실패해도 녹음 완료를 만들지 않는다`() = runTest {
        val failures = mutableListOf<Throwable>()
        val events = mutableListOf<RecordingEvent>()
        val failure = IllegalStateException("finish failed")
        Soundly.record {
            input = emptyFlow()
            output = object : RecordingOutput {
                override fun write(source: Flow<PcmChunk>, config: RecordingConfig): Flow<OutputEvent> = flow {
                    source.collect()
                    emit(OutputEvent.Finished(MemoryRecording(emptyList(), config, RecordingProgress(0, config.sampleRateHz))))
                    throw failure
                }
            }
        }.catch { failures += it }.toList(events)

        val error = failures.single() as RecorderException
        assertEquals(RecorderException.Reason.Storage, error.reason)
        assertSame(failure, error.cause)
        assertFalse(events.any { it is RecordingEvent.Completed })
    }

    @Test
    fun `녹음기는 출력이 전달한 진행량을 그대로 전달한다`() = runTest {
        val events = Soundly.record {
            input = flowOf(PcmChunk(byteArrayOf(1, 0)), PcmChunk(byteArrayOf(2, 0)))
            output = object : RecordingOutput {
                override fun write(source: Flow<PcmChunk>, config: RecordingConfig): Flow<OutputEvent> = flow {
                    val chunks = source.toList()
                    val progress = RecordingProgress(2, config.sampleRateHz)
                    emit(OutputEvent.Progress(progress))
                    emit(OutputEvent.Finished(MemoryRecording(chunks, config, progress)))
                }
            }
        }.toList()

        val samples = events.filterIsInstance<RecordingEvent.Progress>().map { it.progress.samples }
        assertEquals(listOf(2L), samples)
        assertEquals(2L, (events.last() as RecordingEvent.Completed).result.progress.samples)
    }

    @Test
    fun `표준 버퍼는 크기가 다른 청크도 생략하지 않고 순서대로 전달한다`() = runTest {
        val chunks = listOf(PcmChunk(ByteArray(10) { 1 }), PcmChunk(ByteArray(6) { 2 }), PcmChunk(ByteArray(2) { 3 }))
        val output = TestOutput(delayMillis = 5)
        Soundly.record {
            bufferCapacity = 1
            input = chunks.asFlow()
            this.output = output
        }.collect()

        assertEquals(chunks.flatMap { it.bytes.toList() }, output.bytes)
        assertTrue(output.finished)
    }

    @Test
    fun `느린 수집자에게도 진행 이벤트를 생략하지 않고 전달한다`() = runTest {
        val output = TestOutput()
        val events = mutableListOf<RecordingEvent>()
        Soundly.record {
            input = flow { repeat(100) { emit(PcmChunk(ByteArray(2))) } }
            this.output = output
        }.collect { event ->
            delay(10)
            events += event
        }

        val samples = events.filterIsInstance<RecordingEvent.Progress>().map { it.progress.samples }
        assertEquals((1L..100L).toList(), samples)
        assertTrue(output.finished)
        assertTrue(events.last() is RecordingEvent.Completed)
    }

    @Test
    fun `완료 수집자 예외는 출력 실패로 분류하지 않는다`() = runTest {
        val failure = IllegalStateException("collector failed")
        val error = runCatching {
            Soundly.record {
                input = emptyFlow()
                output = MemoryOutput(100)
            }.collect { if (it is RecordingEvent.Completed) throw failure }
        }.exceptionOrNull()
        assertFalse(error is RecorderException)
        assertTrue(generateSequence(error) { it.cause }.any { it === failure })
    }
}

private class TestOutput(
    private val gate: CompletableDeferred<Unit>? = null,
    private val delayMillis: Long = 0,
    private val closeFailure: Throwable? = null,
) : RecordingOutput {
    val bytes = mutableListOf<Byte>()
    var opened = 0
    var closed = false
    var finished = false

    override fun write(source: Flow<PcmChunk>, config: RecordingConfig): Flow<OutputEvent> = flow {
        val result = AutoCloseable { closed = true; closeFailure?.let { throw it } }.use {
            opened++
            source.collect { chunk ->
                gate?.await()
                delay(delayMillis)
                bytes += chunk.bytes.toList()

                val progress = RecordingProgress(bytes.size.toLong() / (config.channelCount * 2), config.sampleRateHz)
                emit(OutputEvent.Progress(progress))
            }
            finished = true
            MemoryRecording(emptyList(), config, RecordingProgress(bytes.size.toLong() / (config.channelCount * 2), config.sampleRateHz))
        }

        emit(OutputEvent.Finished(result))
    }
}
