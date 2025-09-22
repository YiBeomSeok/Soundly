package com.soundly.feature.recording.recordings

import com.soundly.sdk.v0.player.PlaybackState
import com.soundly.domain.recording.Recording
import com.soundly.domain.recording.usecase.DeleteRecordingUseCase
import com.soundly.domain.recording.usecase.GetRecordingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingsViewModelTest {

    private lateinit var recordingsFlow: MutableStateFlow<List<Recording>>
    private lateinit var getRecordingsUseCase: GetRecordingsUseCase
    private lateinit var deleteRecordingUseCase: FakeDeleteRecordingUseCase
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: RecordingsViewModel
    private val tempFiles = mutableListOf<File>()

    private fun createTempRecording(
        title: String,
        duration: kotlin.time.Duration,
        lastModifiedMs: Long,
        sizeBytes: Int,
    ): Recording {
        val file = File.createTempFile("rec_", ".wav")
        file.writeBytes(ByteArray(sizeBytes))
        file.setLastModified(lastModifiedMs)
        tempFiles.add(file)
        return Recording(
            uri = file.absolutePath,
            title = title,
            createdAt = Instant.ofEpochMilli(lastModifiedMs),
            duration = duration,
        )
    }

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        recordingsFlow = MutableStateFlow(emptyList())
        getRecordingsUseCase = FakeGetRecordingsUseCase(recordingsFlow)
        deleteRecordingUseCase = FakeDeleteRecordingUseCase()
        viewModel = RecordingsViewModel(getRecordingsUseCase, deleteRecordingUseCase)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        tempFiles.forEach { runCatching { it.delete() } }
    }

    @Test
    fun `녹음 플로우가 2개를 방출하면 UI 파일 목록도 2개다`() = runTest {
        // Given
        val r1 = createTempRecording(
            title = "테스트 녹음 1",
            duration = 30.seconds,
            lastModifiedMs = 1700000000000L,
            sizeBytes = 1024,
        )
        val r2 = createTempRecording(
            title = "테스트 녹음 2",
            duration = 60.seconds,
            lastModifiedMs = 1700000001000L,
            sizeBytes = 2048,
        )
        recordingsFlow.value = listOf(r1, r2)

        // When
        advanceUntilIdle()

        // Then
        val uiState = viewModel.uiState.value
        assertEquals(2, uiState.files.size)
        assertEquals("테스트 녹음 1", uiState.files[0].name)
        assertEquals("테스트 녹음 2", uiState.files[1].name)
        assertEquals(30.seconds, uiState.files[0].duration)
        assertEquals(60.seconds, uiState.files[1].duration)
    }

    @Test
    fun `파일 ID X를 삭제하면 DeleteRecordingUseCase에 X가 전달된다`() = runTest {
        // Given
        val fileId = "/test/recording.wav"

        // When
        viewModel.deleteFile(fileId)
        advanceUntilIdle()

        // Then
        assertEquals(fileId, deleteRecordingUseCase.lastRequestedUri)
    }

    @Test
    fun `선택된 파일 ID가 설정된다`() {
        // Given
        val selectedId = "/test/recording.wav"

        // When
        viewModel.setSelectedId(selectedId)

        // Then
        val uiState = viewModel.uiState.value
        assertEquals(selectedId, uiState.playback.selectedId)
    }

    @Test
    fun `선택된 파일 ID가 null로 설정된다`() {
        // Given
        val selectedId: String? = null

        // When
        viewModel.setSelectedId(selectedId)

        // Then
        val uiState = viewModel.uiState.value
        assertEquals(null, uiState.playback.selectedId)
    }

    @Test
    fun `버퍼링 상태가 설정된다`() {
        // Given
        // 초기 상태는 Idle

        // When
        viewModel.setBuffering()

        // Then
        val uiState = viewModel.uiState.value
        assertEquals(PlaybackState.Buffering, uiState.playback.state)
    }

    @Test
    fun `재생 상태와 지속시간이 설정된다`() {
        // Given
        val duration = 120.seconds

        // When
        viewModel.setPlaying(duration)

        // Then
        val uiState = viewModel.uiState.value
        assertEquals(PlaybackState.Playing, uiState.playback.state)
        assertEquals(duration, uiState.playback.duration)
    }

    @Test
    fun `일시정지 상태가 설정된다`() {
        // Given
        // 초기 상태는 Idle

        // When
        viewModel.setPaused()

        // Then
        val uiState = viewModel.uiState.value
        assertEquals(PlaybackState.Paused, uiState.playback.state)
    }

    @Test
    fun `유휴 상태로 설정되고 위치가 0으로 초기화된다`() {
        // Given
        // 먼저 재생 상태로 설정
        viewModel.setPlaying(60.seconds)
        viewModel.setPosition(30.seconds)

        // When
        viewModel.setIdle()

        // Then
        val uiState = viewModel.uiState.value
        assertEquals(PlaybackState.Idle, uiState.playback.state)
        assertEquals(0.milliseconds, uiState.playback.position)
    }

    @Test
    fun `재생 위치가 설정된다`() {
        // Given
        val position = 45.seconds

        // When
        viewModel.setPosition(position)

        // Then
        val uiState = viewModel.uiState.value
        assertEquals(position, uiState.playback.position)
    }

    @Test
    fun `재생 오류 상태와 메시지가 설정된다`() {
        // Given
        val errorMessage = "재생 중 오류가 발생했습니다"

        // When
        viewModel.setPlaybackError(errorMessage)

        // Then
        val uiState = viewModel.uiState.value
        assertEquals(PlaybackState.Error, uiState.playback.state)
        assertEquals(errorMessage, uiState.playback.errorMessage)
    }

    @Test
    fun `선택된 ID가 있고 상태가 Playing이면 isPlaying은 true다`() = runTest {
        // Given
        val rec = createTempRecording(
            title = "테스트 녹음",
            duration = 30.seconds,
            lastModifiedMs = 1700000000000L,
            sizeBytes = 1024,
        )
        recordingsFlow.value = listOf(rec)

        // When
        viewModel.setSelectedId(rec.uri)
        viewModel.setPlaying(30.seconds)
        advanceUntilIdle()

        // Then
        val uiState = viewModel.uiState.value
        assertTrue(uiState.isPlaying)
    }

    @Test
    fun `선택된 파일이 동일하고 상태가 Paused면 isPaused(item)는 true다`() = runTest {
        // Given
        val rec = createTempRecording(
            title = "테스트 녹음",
            duration = 30.seconds,
            lastModifiedMs = 1700000000000L,
            sizeBytes = 1024,
        )
        recordingsFlow.value = listOf(rec)

        // When
        viewModel.setSelectedId(rec.uri)
        viewModel.setPaused()
        advanceUntilIdle()

        // Then
        val uiState = viewModel.uiState.value
        val recordingItem = uiState.files.first()
        assertTrue(uiState.isPaused(recordingItem))
    }

    @Test
    fun `선택된 ID가 있고 상태가 Playing이면 shouldShowSeekBar는 true다`() = runTest {
        // Given
        val rec = createTempRecording(
            title = "테스트 녹음",
            duration = 30.seconds,
            lastModifiedMs = 1700000000000L,
            sizeBytes = 1024,
        )
        recordingsFlow.value = listOf(rec)

        // When
        viewModel.setSelectedId(rec.uri)
        viewModel.setPlaying(30.seconds)
        advanceUntilIdle()

        // Then
        val uiState = viewModel.uiState.value
        assertTrue(uiState.shouldShowSeekBar)
    }

    @Test
    fun `파일이 선택되지 않으면 shouldShowSeekBar가 false다`() {
        // Given
        // 초기 상태 (선택된 파일 없음)

        // When
        viewModel.setPlaying(30.seconds)

        // Then
        val uiState = viewModel.uiState.value
        assertFalse(uiState.shouldShowSeekBar)
    }

    @Test
    fun `녹음 플로우가 빈 리스트면 UI 파일 목록도 비어있다`() = runTest {
        // Given
        recordingsFlow.value = emptyList()

        // When
        advanceUntilIdle()

        // Then
        val uiState = viewModel.uiState.value
        assertTrue(uiState.files.isEmpty())
        assertEquals(PlaybackUi.Default, uiState.playback)
    }

    @Test
    fun `여러 상태 변경이 순차적으로 적용된다`() {
        // Given
        val selectedId = "/test/recording.wav"
        val duration = 60.seconds
        val position = 30.seconds

        // When
        viewModel.setSelectedId(selectedId)
        viewModel.setBuffering()
        viewModel.setPlaying(duration)
        viewModel.setPosition(position)
        viewModel.setPaused()

        // Then
        val uiState = viewModel.uiState.value
        assertEquals(selectedId, uiState.playback.selectedId)
        assertEquals(PlaybackState.Paused, uiState.playback.state)
        assertEquals(duration, uiState.playback.duration)
        assertEquals(position, uiState.playback.position)
    }
}

private class FakeGetRecordingsUseCase(
    private val source: Flow<List<Recording>>
) : GetRecordingsUseCase {
    override fun invoke(): Flow<List<Recording>> = source
}

private class FakeDeleteRecordingUseCase : DeleteRecordingUseCase {
    var lastRequestedUri: String? = null
    var result: Boolean = true
    override suspend fun invoke(uri: String): Boolean {
        lastRequestedUri = uri
        return result
    }
}

 
