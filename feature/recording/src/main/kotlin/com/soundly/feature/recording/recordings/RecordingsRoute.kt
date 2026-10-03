package com.soundly.feature.recording.recordings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soundly.sdk.player.PlaybackControls
import com.soundly.sdk.player.PlaybackManager
import com.soundly.sdk.player.PlaybackState
import com.soundly.sdk.player.PlaybackStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File
import kotlin.time.Duration

@Composable
internal fun RecordingsRoute(
    onRecordClick: () -> Unit,
    viewModel: RecordingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val player = remember(context) { PlaybackManager(context = context.applicationContext) }
    val playback = remember { MutableStateFlow(PlaybackStatus.Idle) }
    var selection by remember { mutableStateOf<PlaybackSelection?>(null) }
    val snackbar = remember { SnackbarHostState() }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }

    // 선택이 바뀌거나 화면이 사라지면 이전 수집을 취소하여 플레이어도 함께 해제합니다.
    LaunchedEffect(player, selection) {
        val selected = selection
        if (selected == null) {
            playback.value = PlaybackStatus.Idle
        } else {
            player
                .play(
                    file = selected.file,
                    controls = selected.controls
                )
                .collect { status ->
                    playback.value = status
                }
        }
    }

    // 목록에는 재생 상태만 전달하고, 자주 바뀌는 위치는 재생 패널에서 직접 관찰합니다.
    LaunchedEffect(playback) {
        playback
            .map { status -> status.copy(position = Duration.ZERO) }
            .distinctUntilChanged()
            .collect { status ->
                viewModel.observePlayback(status)
            }
    }

    LaunchedEffect(state.errorMessage, state.playback.errorMessage) {
        val message = state.errorMessage ?: state.playback.errorMessage
        if (message != null) {
            snackbar.showSnackbar(message = message)
            viewModel.dismissError()
        }
    }

    RecordingsScreen(
        state = state,
        snackbar = snackbar,
        pendingDeletion = state.files.firstOrNull { item -> item.uri == deletingId },
        onRecordClick = {
            selection = null
            onRecordClick()
        },
        onSearch = viewModel::search,
        onPlay = { item ->
            val selected = state.playback.selectedId == item.uri
            when {
                selected && state.isPlaying -> selection?.controls?.pause()
                selected && state.playback.state == PlaybackState.Paused -> selection?.controls?.resume()
                else -> selection = PlaybackSelection(file = item.file)
            }
        },
        onDeleteRequest = { item -> deletingId = item.uri },
        onDeleteDismiss = { deletingId = null },
        onDeleteConfirm = { item ->
            if (state.playback.selectedId == item.uri) selection = null
            viewModel.deleteFile(uri = item.uri)
            deletingId = null
        },
        playbackPanel = {
            PlaybackRoute(
                playback = playback,
                onPauseClick = { selection?.controls?.pause() },
                onResumeClick = {
                    val selected = selection
                    if (selected != null && playback.value.state == PlaybackState.Ended) {
                        selection = PlaybackSelection(file = selected.file)
                    } else {
                        selected?.controls?.resume()
                    }
                },
                onSeek = { position -> selection?.controls?.seekTo(position = position) },
                files = state.files,
                onCloseClick = { selection = null },
            )
        },
    )
}

private data class PlaybackSelection(
    val file: File,
    val controls: PlaybackControls = PlaybackControls(),
)
