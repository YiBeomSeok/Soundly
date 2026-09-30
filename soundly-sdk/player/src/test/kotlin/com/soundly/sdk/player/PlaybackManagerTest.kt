package com.soundly.sdk.player

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackManagerTest {
    private val file = File("/memo.wav")

    @Test
    fun `수집할 때만 생성하고 취소하면 관찰과 플레이어를 함께 해제한다`() = runTest {
        val engines = mutableListOf<FakePlaybackEngine>()
        val player = PlaybackManager(
            createEngine = { FakePlaybackEngine().also { engines += it } },
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val playback = player.play(file = file)
        assertTrue(engines.isEmpty())

        val subscription = backgroundScope.launch { playback.collect() }
        runCurrent()
        assertEquals(file, engines.single().file)
        assertTrue(engines.single().observing)

        subscription.cancel()
        runCurrent()
        assertFalse(engines.single().observing)
        assertEquals(1, engines.single().closeCount)
    }

    @Test
    fun `명령 순서와 같은 위치의 반복 탐색을 보존하고 정지하면 반환한다`() = runTest {
        val engine = FakePlaybackEngine()
        val player = PlaybackManager(
            createEngine = { engine },
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val controls = PlaybackControls()
        val statuses = mutableListOf<PlaybackStatus>()
        val subscription = backgroundScope.launch {
            player.play(file = file, controls = controls).collect { statuses += it }
        }
        runCurrent()

        controls.pause()
        controls.seekTo(position = 5.seconds)
        controls.seekTo(position = 5.seconds)
        controls.resume()
        controls.stop()
        runCurrent()

        assertEquals(listOf("play", "pause", "seek:5s", "seek:5s", "resume"), engine.commands)
        assertEquals(PlaybackStatus.Idle, statuses.last())
        assertTrue(subscription.isCompleted)
        assertEquals(1, engine.closeCount)
    }

    @Test
    fun `위치는 재생 중에만 갱신하고 일시정지 뒤에는 이전 재생 상태를 전달하지 않는다`() = runTest {
        val engine = FakePlaybackEngine()
        val player = PlaybackManager(
            createEngine = { engine },
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val controls = PlaybackControls()
        val statuses = mutableListOf<PlaybackStatus>()
        backgroundScope.launch {
            player.play(file = file, controls = controls).collect { statuses += it }
        }
        runCurrent()

        engine.position = 3.seconds
        advanceTimeBy(101)
        runCurrent()
        assertEquals(3.seconds, statuses.last().position)

        controls.pause()
        runCurrent()
        val countAfterPause = statuses.size
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(countAfterPause, statuses.size)
        assertEquals(PlaybackState.Paused, statuses.last().state)

        controls.resume()
        runCurrent()
        engine.position = 4.seconds
        advanceTimeBy(101)
        runCurrent()
        assertEquals(PlaybackState.Playing, statuses.last().state)
        assertEquals(4.seconds, statuses.last().position)
    }

    @Test
    fun `재생 완료는 마지막 상태를 전달하고 자원을 해제한 뒤 반환한다`() = runTest {
        val engine = FakePlaybackEngine()
        val player = PlaybackManager(
            createEngine = { engine },
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val controls = PlaybackControls()
        val statuses = mutableListOf<PlaybackStatus>()
        backgroundScope.launch {
            player.play(file = file, controls = controls).collect { statuses += it }
        }
        runCurrent()

        engine.position = 10.seconds
        engine.changes.value = PlaybackStatus(state = PlaybackState.Ended, position = 10.seconds)
        runCurrent()
        val countAfterEnd = statuses.size
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(countAfterEnd, statuses.size)
        assertEquals(PlaybackState.Ended, statuses.last().state)
        assertEquals(1, engine.closeCount)
    }

    @Test
    fun `플레이어 오류는 오류 상태를 전달하고 자원을 해제한다`() = runTest {
        val engine = FakePlaybackEngine()
        val player = PlaybackManager(
            createEngine = { engine },
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val statuses = mutableListOf<PlaybackStatus>()
        val subscription = backgroundScope.launch {
            player.play(file = file).collect { statuses += it }
        }
        runCurrent()

        engine.changes.value = PlaybackStatus(state = PlaybackState.Error, errorMessage = "읽기 실패")
        runCurrent()
        assertEquals("읽기 실패", statuses.last().errorMessage)
        assertEquals(file, statuses.last().selectedFile)
        assertTrue(subscription.isCompleted)
        assertEquals(1, engine.closeCount)
    }

    @Test
    fun `준비 실패도 플레이어를 해제하고 오류 상태로 전달한다`() = runTest {
        val engine = FakePlaybackEngine().apply { playFailure = IllegalStateException("준비 실패") }
        val player = PlaybackManager(
            createEngine = { engine },
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val statuses = mutableListOf<PlaybackStatus>()
        player.play(file = file).collect { statuses += it }

        assertEquals(PlaybackState.Error, statuses.last().state)
        assertEquals("준비 실패", statuses.last().errorMessage)
        assertEquals(1, engine.closeCount)
    }

    @Test
    fun `수집자 실패를 재생 오류로 바꾸지 않고 플레이어를 해제한다`() = runTest {
        val engine = FakePlaybackEngine()
        val player = PlaybackManager(
            createEngine = { engine },
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val failure = IllegalStateException("화면 수집 실패")
        var caught: Throwable? = null
        try {
            player.play(file = file).collect { status ->
                if (status.state == PlaybackState.Playing) throw failure
            }
        } catch (error: IllegalStateException) {
            caught = error
        }

        assertEquals(failure.message, caught?.message)
        assertEquals(1, engine.closeCount)
        assertFalse(engine.observing)
    }

    @Test
    fun `같은 흐름의 재수집은 독립된 플레이어를 생성한다`() = runTest {
        val engines = mutableListOf<FakePlaybackEngine>()
        val player = PlaybackManager(
            createEngine = { FakePlaybackEngine().also { engines += it } },
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val playback = player.play(file = file)
        val first = backgroundScope.launch { playback.collect() }
        runCurrent()
        first.cancel()
        runCurrent()

        val second = backgroundScope.launch { playback.collect() }
        runCurrent()
        assertEquals(2, engines.size)
        assertEquals(1, engines.first().closeCount)
        assertEquals(0, engines.last().closeCount)
        second.cancel()
        runCurrent()
        assertEquals(1, engines.last().closeCount)
    }
}

private class FakePlaybackEngine : PlaybackEngine {
    val changes = MutableStateFlow(PlaybackStatus(state = PlaybackState.Playing))
    val commands = mutableListOf<String>()
    var file: File? = null
    var position: Duration = Duration.ZERO
    var observing = false
    var closeCount = 0
    var playFailure: Exception? = null

    override fun play(file: File) {
        this.file = file
        commands += "play"
        playFailure?.let { throw it }
    }

    override fun observe(): Flow<PlaybackStatus> {
        return changes.onStart { observing = true }.onCompletion { observing = false }
    }

    override fun readStatus(): PlaybackStatus = changes.value.copy(position = position)

    override fun pause() {
        commands += "pause"
        changes.value = readStatus().copy(state = PlaybackState.Paused)
    }

    override fun resume() {
        commands += "resume"
        if (changes.value.state == PlaybackState.Ended) position = Duration.ZERO
        changes.value = readStatus().copy(state = PlaybackState.Playing)
    }

    override fun seekTo(position: Duration) {
        commands += "seek:$position"
        this.position = position
        changes.value = readStatus()
    }

    override fun close() {
        closeCount += 1
    }
}
