package com.soundly.feature.recording.recordings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class RecordingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(RecordingsUiState.Default)
    val uiState = _uiState.asStateFlow()

    // ---------------------------- intents ----------------------------
    fun onIntent(intent: RecordingsIntent) {
        when (intent) {
            is RecordingsIntent.RefreshFiles -> {
                _uiState.update { it.copy(pendingCommand = RecordingsUiCommand.RefreshFiles) }
            }

            is RecordingsIntent.StartPlayback -> {
                _uiState.update { it.copy(pendingCommand = RecordingsUiCommand.StartPlayback(intent.id)) }
            }

            is RecordingsIntent.StopPlayback -> {
                _uiState.update { it.copy(pendingCommand = RecordingsUiCommand.StopPlayback) }
            }

            is RecordingsIntent.DeleteFile -> {
                _uiState.update { it.copy(pendingCommand = RecordingsUiCommand.DeleteFile(intent.id)) }
            }

            is RecordingsIntent.PendingCommandHandled -> {
                _uiState.update { it.copy(pendingCommand = null) }
            }

            is RecordingsIntent.SetFiles -> {
                _uiState.update { it.copy(files = intent.files) }
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