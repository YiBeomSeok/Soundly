package com.soundly.feature.recording.recordings

import android.os.Environment
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val playbackManager = remember(context) {
        PlaybackManager(
            context = context,
            callbacks = object : PlaybackManager.Callbacks {
                override fun onBuffering() = viewModel.onIntent(RecordingsIntent.Buffering)
                override fun onPlaying(duration: Duration) =
                    viewModel.onIntent(RecordingsIntent.Playing(duration))

                override fun onPaused() = viewModel.onIntent(RecordingsIntent.Paused)
                override fun onStopped() = viewModel.onIntent(RecordingsIntent.Idle)
                override fun onPosition(position: Duration) =
                    viewModel.onIntent(RecordingsIntent.Position(position))

                override fun onError(message: String) =
                    viewModel.onIntent(RecordingsIntent.PlaybackError(message))
            }
        )
    }

    LaunchedEffect(uiState.pendingCommand) {
        when (val cmd = uiState.pendingCommand) {
            is RecordingsUiCommand.StartPlayback -> {
                val item = uiState.files.firstOrNull { it.id == cmd.id }
                if (item != null) {
                    playbackManager.stop()
                    playbackManager.play(item.file)
                }
            }

            is RecordingsUiCommand.StopPlayback -> {
                playbackManager.stop()
            }

            is RecordingsUiCommand.StartRecording -> {
                onRecordClick()
            }

            null -> {}
        }

        viewModel.onIntent(RecordingsIntent.PendingCommandHandled)
    }
    RecordingsScreen(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        files = uiState.files,
        playback = uiState.playback,
        onPlayClick = { item -> viewModel.onIntent(RecordingsIntent.StartPlayback(item.id)) },
        onPauseClick = { _ -> viewModel.onIntent(RecordingsIntent.StopPlayback) },
        onDelete = { item -> viewModel.onIntent(RecordingsIntent.DeleteFile(item.id)) },
        onRecordClick = { viewModel.onIntent(RecordingsIntent.StartRecording) }
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
) {
    val isPlaying = playback.state == PlaybackState.Playing

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Recordings") }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRecordClick,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Start recording"
                    )
                },
                text = { Text("Record") },
                modifier = Modifier.semantics {
                    contentDescription = "녹음 시작"
                }
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (files.isEmpty()) {
                EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    onRecordClick = onRecordClick
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
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    onRecordClick: () -> Unit
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "아직 녹음이 없어요",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "오른쪽 아래 ‘Record’ 버튼을 눌러 시작해보세요.",
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
                RecordingItem("/a.wav", File("/a.wav"), "녹음 1", 20000, 0L, 25000),
                RecordingItem("/b.wav", File("/b.wav"), "녹음 2", 43000, 0L, 60000),
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
