package com.soundly.feature.recording.recordings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soundly.data.recording.RoomRecordingCatalog
import com.soundly.sdk.player.PlaybackStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 목록과 재생 관찰 결과를 화면 상태로 변환하며, 파일·DB·플레이어 자원은 소유하지 않습니다.
 */
@HiltViewModel
internal class RecordingsViewModel @Inject constructor(
    private val roomRecordingCatalog: RoomRecordingCatalog,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RecordingsUiState.Default)
    val uiState: StateFlow<RecordingsUiState> = combine(
        roomRecordingCatalog.recordings.map { rows ->
            val items = rows.map { row ->
                RecordingItem(
                    uri = row.uri,
                    file = File(row.uri),
                    name = row.title,
                    sizeBytes = row.sizeBytes,
                    lastModified = row.lastModified,
                    duration = row.duration,
                )
            }
            StoredList(files = items, isLoading = false)
        }.catch { failure ->
            mutableState.update {
                it.copy(errorMessage = failure.message ?: "녹음 목록을 불러오지 못했습니다.")
            }
            emit(StoredList(files = emptyList(), isLoading = false))
        },
        mutableState,
    ) { stored, local ->
        local.copy(
            files = stored.files,
            isLoading = stored.isLoading,
            errorMessage = local.errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = RecordingsUiState.Default,
    )

    fun deleteFile(uri: String) {
        viewModelScope.launch {
            try {
                roomRecordingCatalog.deleteRecording(uri = uri)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure

                mutableState.update {
                    it.copy(errorMessage = failure.message ?: "녹음을 삭제하지 못했습니다.")
                }
            }
        }
    }

    fun observePlayback(status: PlaybackStatus) {
        val playback = PlaybackUi(
            selectedId = status.selectedFile?.path,
            state = status.state,
            position = status.position,
            duration = status.duration,
            errorMessage = status.errorMessage,
        )
        mutableState.update { it.copy(playback = playback) }
    }

    fun search(query: String) {
        mutableState.update { it.copy(query = query) }
    }

    fun dismissError() {
        mutableState.update { it.copy(errorMessage = null) }
    }
}

private data class StoredList(
    val files: List<RecordingItem>,
    val isLoading: Boolean,
)
