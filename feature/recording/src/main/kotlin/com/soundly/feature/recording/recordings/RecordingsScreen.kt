package com.soundly.feature.recording.recordings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soundly.feature.recording.R
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun RecordingsScreen(
    onRecordClick: () -> Unit,
    viewModel: RecordingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val playbackManager = remember(context) { PlaybackManager(context) }

    LaunchedEffect(uiState.pendingCommand) {
        when (val cmd = uiState.pendingCommand) {
            is RecordingsUiCommand.StartPlayback -> {
                val item = uiState.files.firstOrNull { it.uri == cmd.uri }
                if (item != null) {
                    playbackManager.play(item.file)
                    // optimistic UI: reflect buffering/selection immediately
                    viewModel.onIntent(RecordingsIntent.Buffering)
                    viewModel.onIntent(RecordingsIntent.SetSelectedId(item.uri))
                }
            }

            is RecordingsUiCommand.PauseOrResume -> {
                playbackManager.pauseOrResume()
            }

            is RecordingsUiCommand.StopPlayback -> {
                playbackManager.stop()
            }

            is RecordingsUiCommand.SeekTo -> {
                playbackManager.seekTo(cmd.position)
            }

            is RecordingsUiCommand.StartRecording -> {
                onRecordClick()
            }

            null -> {}
        }

        viewModel.onIntent(RecordingsIntent.PendingCommandHandled)
    }

    LaunchedEffect(playbackManager) {
        playbackManager.status.collect { st ->
            val selectedId = st.selectedFile?.path
            if (selectedId != uiState.playback.selectedId) {
                viewModel.onIntent(RecordingsIntent.SetSelectedId(selectedId))
            }
            when (st.state) {
                PlaybackState.Buffering -> viewModel.onIntent(RecordingsIntent.Buffering)
                PlaybackState.Playing -> viewModel.onIntent(RecordingsIntent.Playing(st.duration))
                PlaybackState.Paused -> viewModel.onIntent(RecordingsIntent.Paused)
                PlaybackState.Idle -> viewModel.onIntent(RecordingsIntent.Idle)
                PlaybackState.Error -> viewModel.onIntent(
                    RecordingsIntent.PlaybackError(
                        st.errorMessage ?: ""
                    )
                )
            }
            viewModel.onIntent(RecordingsIntent.Position(st.position))
        }
    }
    RecordingsScreen(
        modifier = Modifier.fillMaxWidth(),
        files = uiState.files,
        playback = uiState.playback,
        onPlayClick = { item ->
            if (uiState.playback.selectedId == item.uri && uiState.playback.state == PlaybackState.Paused) {
                viewModel.onIntent(RecordingsIntent.PauseOrResume)
            } else {
                viewModel.onIntent(RecordingsIntent.StartPlayback(item.uri))
            }
        },
        onPauseClick = { _ -> viewModel.onIntent(RecordingsIntent.PauseOrResume) },
        onDelete = { item -> viewModel.onIntent(RecordingsIntent.DeleteFile(item.uri)) },
        onRecordClick = { viewModel.onIntent(RecordingsIntent.StartRecording) },
        onSeek = { pos -> viewModel.onIntent(RecordingsIntent.SeekTo(pos)) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordingsScreen(
    modifier: Modifier = Modifier,
    files: List<RecordingItem>,
    playback: PlaybackUi,
    onPlayClick: (RecordingItem) -> Unit,
    onPauseClick: (RecordingItem) -> Unit,
    onDelete: (RecordingItem) -> Unit,
    onRecordClick: () -> Unit,
    onSeek: (Duration) -> Unit = {},
) {
    val isPlaying =
        playback.state == PlaybackState.Playing || playback.state == PlaybackState.Buffering

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Scaffold(
            modifier = Modifier.weight(1f),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(text = stringResource(id = R.string.feature_recording_title_recordings))
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onRecordClick,
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = stringResource(id = R.string.feature_recording_start_recording_content_description)
                        )
                    },
                    text = {
                        Text(stringResource(id = R.string.feature_recording_action_record))
                    },
                )
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                if (files.isEmpty()) {
                    EmptyState(
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // 기존 리스트 UI 재사용
                    RecordingsContent(
                        modifier = Modifier.fillMaxSize(),
                        items = files,
                        currentId = playback.selectedId,
                        isPlaying = isPlaying,
                        onPlayClick = onPlayClick,
                        onPauseClick = onPauseClick,
                        onDelete = onDelete,
                    )
                }
            }
        }

        if (playback.selectedId != null && (playback.state == PlaybackState.Playing || playback.state == PlaybackState.Paused)) {
            PlaybackSeekBar(
                selectedId = playback.selectedId,
                position = playback.position,
                duration = playback.duration,
                onSeek = onSeek,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.feature_recording_empty_state_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.feature_recording_empty_state_description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

private class RecordingsPreviewParams(
    val files: List<RecordingItem>,
    val playback: PlaybackUi,
)

private class RecordingsPreviewProvider : PreviewParameterProvider<RecordingsPreviewParams> {
    override val values: Sequence<RecordingsPreviewParams> = sequenceOf(
        RecordingsPreviewParams(
            files = emptyList(),
            playback = PlaybackUi.Default,
        ),
        RecordingsPreviewParams(
            files = listOf(
                RecordingItem("/a.wav", File("/a.wav"), "녹음 1", 20000, 0L, 25000.milliseconds),
                RecordingItem("/b.wav", File("/b.wav"), "녹음 2", 43000, 0L, 60000.milliseconds),
            ),
            playback = PlaybackUi.Default.copy(
                selectedId = "/a.wav",
                state = PlaybackState.Playing,
                position = 5000.milliseconds,
                duration = 25000.milliseconds
            ),
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun RecordingsScreenPreview(
    @PreviewParameter(RecordingsPreviewProvider::class) p: RecordingsPreviewParams
) {
    RecordingsScreen(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        files = p.files,
        playback = p.playback,
        onPlayClick = {},
        onPauseClick = {},

        onDelete = {},
        onRecordClick = {},
    )
}
