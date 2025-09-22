package com.soundly.feature.recording.recordings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soundly.sdk.v0.player.PlaybackState
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
                    ).also {
                        Log.e("RecordingItem", it.toString())
                    }
                }
                _uiState.update { it.copy(files = items) }
            }
        }
    }

    // ---------------------------- explicit actions ----------------------------

    fun deleteFile(id: String) {
        viewModelScope.launch { deleteRecording(id) }
    }

    // ---------------------------- playback state updates ----------------------------
    fun setSelectedId(id: String?) {
        _uiState.update { it.copy(playback = it.playback.copy(selectedId = id)) }
    }

    fun setBuffering() {
        _uiState.update { it.copy(playback = it.playback.copy(state = PlaybackState.Buffering)) }
    }

    fun setPlaying(duration: Duration) {
        _uiState.update {
            it.copy(playback = it.playback.copy(state = PlaybackState.Playing, duration = duration))
        }
    }

    fun setPaused() {
        _uiState.update { it.copy(playback = it.playback.copy(state = PlaybackState.Paused)) }
    }

    fun setIdle() {
        _uiState.update {
            it.copy(playback = it.playback.copy(state = PlaybackState.Idle, position = 0.milliseconds))
        }
    }

    fun setPosition(position: Duration) {
        _uiState.update { it.copy(playback = it.playback.copy(position = position)) }
    }

    fun setPlaybackError(message: String) {
        _uiState.update {
            it.copy(
                playback = it.playback.copy(
                    state = PlaybackState.Error,
                    errorMessage = message
                )
            )
        }
    }
}