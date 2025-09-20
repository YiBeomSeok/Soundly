package com.soundly.feature.recording.recordings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soundly.domain.recording.usecase.DeleteRecordingUseCase
import com.soundly.domain.recording.usecase.GetRecordingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
internal class RecordingsViewModel @Inject constructor(
    private val getRecordings: GetRecordingsUseCase,
    private val deleteRecording: DeleteRecordingUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecordingsUiState.Default)
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getRecordings().collect { list ->
                val items = list.map { rec ->
                    RecordingItem(
                        uri = rec.uri,
                        file = File(rec.uri),
                        name = rec.title,
                        sizeBytes = File(rec.uri).length(),
                        lastModified = File(rec.uri).lastModified(),
                        duration = rec.duration,
                    )
                }
                _uiState.update { it.copy(files = items) }
            }
        }
    }

    // ---------------------------- intents ----------------------------
    fun onIntent(intent: RecordingsIntent) {
        when (intent) {
            is RecordingsIntent.StartPlayback -> {
                _uiState.update {
                    it.copy(
                        playback = it.playback.copy(selectedId = intent.id),
                        pendingCommand = RecordingsUiCommand.StartPlayback(intent.id)
                    )
                }
            }

            is RecordingsIntent.PauseOrResume -> {
                _uiState.update { it.copy(pendingCommand = RecordingsUiCommand.PauseOrResume) }
            }

            is RecordingsIntent.StopPlayback -> {
                _uiState.update {
                    it.copy(
                        playback = it.playback.copy(selectedId = null),
                        pendingCommand = RecordingsUiCommand.StopPlayback
                    )
                }
            }

            is RecordingsIntent.DeleteFile -> {
                viewModelScope.launch { deleteRecording(intent.id) }
            }

            is RecordingsIntent.StartRecording -> {
                _uiState.update { it.copy(pendingCommand = RecordingsUiCommand.StartRecording) }
            }

            is RecordingsIntent.PendingCommandHandled -> {
                _uiState.update { it.copy(pendingCommand = null) }
            }

            is RecordingsIntent.SetSelectedId -> {
                _uiState.update { it.copy(playback = it.playback.copy(selectedId = intent.id)) }
            }

            is RecordingsIntent.Buffering -> {
                _uiState.update { it.copy(playback = it.playback.copy(state = PlaybackState.Buffering)) }
            }

            is RecordingsIntent.Playing -> {
                val duration: Duration = intent.duration
                _uiState.update {
                    it.copy(
                        playback = it.playback.copy(
                            state = PlaybackState.Playing,
                            duration = duration
                        )
                    )
                }
            }

            is RecordingsIntent.Paused -> {
                _uiState.update { it.copy(playback = it.playback.copy(state = PlaybackState.Paused)) }
            }

            is RecordingsIntent.Idle -> {
                _uiState.update {
                    it.copy(
                        playback = it.playback.copy(
                            state = PlaybackState.Idle,
                            position = 0.milliseconds
                        )
                    )
                }
            }

            is RecordingsIntent.Position -> {
                _uiState.update { it.copy(playback = it.playback.copy(position = intent.position)) }
            }

            is RecordingsIntent.PlaybackError -> {
                _uiState.update {
                    it.copy(
                        playback = it.playback.copy(
                            state = PlaybackState.Error,
                            errorMessage = intent.message
                        )
                    )
                }
            }
        }
    }
}