package com.soundly.feature.recording.recordings

import com.soundly.data.recording.Recording
import com.soundly.data.recording.RoomRecordingCatalog
import com.soundly.sdk.player.PlaybackStatus
import com.soundly.sdk.player.PlaybackState
import java.io.File
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import kotlin.time.Duration.Companion.seconds
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val rows = MutableStateFlow<List<Recording>>(emptyList())
    private val roomRecordingCatalog = Mockito.mock(RoomRecordingCatalog::class.java)
    private lateinit var viewModel: RecordingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        Mockito.`when`(roomRecordingCatalog.recordings).thenReturn(rows)
        viewModel = RecordingsViewModel(roomRecordingCatalog)
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `화면 구독이 시작될 때 저장소를 수집하고 구독 종료 후 해제한다`() = runTest(dispatcher) {
        var collecting = false
        Mockito.`when`(roomRecordingCatalog.recordings).thenReturn(rows
            .onStart { collecting = true }
            .onCompletion { collecting = false })
        val observed = RecordingsViewModel(roomRecordingCatalog)
        runCurrent()
        assertFalse(collecting)

        val subscription = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observed.uiState.collect()
        }
        runCurrent()
        assertTrue(collecting)

        subscription.cancel()
        advanceTimeBy(5_001)
        runCurrent()
        assertFalse(collecting)
    }

    @Test
    fun `저장소 결과를 파일 접근 없이 화면 목록으로 변환한다`() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        rows.value = listOf(Recording("/memo.flac", "회의 메모", Instant.EPOCH, 30.seconds, 2048, 1000))
        runCurrent()
        val item = viewModel.uiState.value.files.single()
        assertEquals("회의 메모", item.name)
        assertEquals(2048L, item.sizeBytes)
        assertEquals(1000L, item.lastModified)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `검색은 원본 목록을 유지하고 표시할 결과만 바꾼다`() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        rows.value = listOf(
            Recording("/a.flac", "회의 메모", Instant.EPOCH, 30.seconds),
            Recording("/b.wav", "산책", Instant.EPOCH, 10.seconds),
        )
        runCurrent()
        viewModel.search("회의")
        runCurrent()
        assertEquals(2, viewModel.uiState.value.files.size)
        assertEquals("회의 메모", viewModel.uiState.value.visibleFiles.single().name)
        viewModel.search("")
        runCurrent()
        assertEquals(2, viewModel.uiState.value.visibleFiles.size)
    }

    @Test
    fun `삭제 요청은 저장소로 전달하고 실패를 화면에 알린다`() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        Mockito.doThrow(IllegalStateException("삭제 실패"))
            .`when`(roomRecordingCatalog).deleteRecording("/a.flac")
        viewModel.deleteFile("/a.flac")
        runCurrent()
        Mockito.verify(roomRecordingCatalog).deleteRecording("/a.flac")
        assertEquals("삭제 실패", viewModel.uiState.value.errorMessage)
        viewModel.dismissError()
        runCurrent()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `재생 상태는 한 번의 갱신으로 반영하고 오류를 지운다`() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.observePlayback(PlaybackStatus(File("/a.flac"), PlaybackState.Error, 2.seconds, 10.seconds, "재생 실패"))
        runCurrent()
        assertEquals("재생 실패", viewModel.uiState.value.playback.errorMessage)
        viewModel.observePlayback(PlaybackStatus(File("/a.flac"), PlaybackState.Paused, 3.seconds, 10.seconds, null))
        runCurrent()
        val state = viewModel.uiState.value
        assertEquals(3.seconds, state.playback.position)
        assertNull(state.playback.errorMessage)
        assertTrue(state.shouldShowSeekBar)
        assertEquals(PlaybackState.Paused, state.playback.state)
    }

    @Test
    fun `재생이 끝나면 패널을 유지하고 재생 중 표시는 해제한다`() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.observePlayback(
            PlaybackStatus(File("/a.flac"), PlaybackState.Ended, 10.seconds, 10.seconds, null),
        )
        runCurrent()

        assertTrue(viewModel.uiState.value.shouldShowSeekBar)
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun `목록 로딩 실패를 닫으면 오류가 다시 나타나지 않는다`() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        Mockito.`when`(roomRecordingCatalog.recordings)
            .thenReturn(flow { throw IllegalStateException("목록 실패") })
        val failed = RecordingsViewModel(roomRecordingCatalog)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { failed.uiState.collect() }
        runCurrent()
        assertFalse(failed.uiState.value.isLoading)
        assertEquals("목록 실패", failed.uiState.value.errorMessage)

        failed.dismissError()
        runCurrent()
        assertNull(failed.uiState.value.errorMessage)
        runCurrent()
        assertNull(failed.uiState.value.errorMessage)
    }
}
