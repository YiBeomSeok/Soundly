package com.soundly.feature.recording.recordings

import android.content.res.Configuration
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
import com.soundly.sdk.v0.player.PlaybackManager
import com.soundly.sdk.v0.player.PlaybackState
import com.soundly.core.designsystem.theme.SoundlyTheme
import com.soundly.feature.recording.R
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun RecordingsScreen(
    onRecordClick: () -> Unit,
    viewModel: RecordingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val playbackManager = remember(context) { PlaybackManager(context) }
    val playbackState by playbackManager.status.collectAsStateWithLifecycle()

    LaunchedEffect(playbackState) {
        val selectedId = playbackState.selectedFile?.path
        if (selectedId != uiState.playback.selectedId) {
            viewModel.setSelectedId(selectedId)
        }
        viewModel.setPosition(playbackState.position)
        when (playbackState.state) {
            PlaybackState.Buffering -> viewModel.setBuffering()
            PlaybackState.Playing -> viewModel.setPlaying(playbackState.duration)
            PlaybackState.Paused -> viewModel.setPaused()
            PlaybackState.Idle -> viewModel.setIdle()
            PlaybackState.Error -> viewModel.setPlaybackError(playbackState.errorMessage ?: "")
        }
    }

    RecordingsScreen(
        uiState = uiState,
        onPlayClick = { item ->
            if (uiState.isPaused(item)) {
                playbackManager.resume()
            } else {
                playbackManager.play(item.file)
                viewModel.setBuffering()
                viewModel.setSelectedId(item.uri)
            }
        },
        onPauseClick = { _ -> playbackManager.pause() },
        onDelete = { item -> viewModel.deleteFile(item.uri) },
        onRecordClick = onRecordClick,
        onSeek = { pos -> playbackManager.seekTo(pos) }
    )
}

@Composable
internal fun RecordingsScreen(
    uiState: RecordingsUiState,
    modifier: Modifier = Modifier,
    onPlayClick: (RecordingItem) -> Unit,
    onPauseClick: (RecordingItem) -> Unit,
    onDelete: (RecordingItem) -> Unit,
    onRecordClick: () -> Unit,
    onSeek: (Duration) -> Unit = {},
) {
    RecordingsScreen(
        modifier = modifier,
        files = uiState.files,
        playback = uiState.playback,
        isPlaying = uiState.isPlaying,
        shouldShowSeekBar = uiState.shouldShowSeekBar,
        onPlayClick = onPlayClick,
        onPauseClick = onPauseClick,
        onDelete = onDelete,
        onRecordClick = onRecordClick,
        onSeek = onSeek,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordingsScreen(
    modifier: Modifier = Modifier,
    files: List<RecordingItem>,
    playback: PlaybackUi,
    isPlaying: Boolean,
    shouldShowSeekBar: Boolean,
    onPlayClick: (RecordingItem) -> Unit,
    onPauseClick: (RecordingItem) -> Unit,
    onDelete: (RecordingItem) -> Unit,
    onRecordClick: () -> Unit,
    onSeek: (Duration) -> Unit = {},
) {
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

        if (shouldShowSeekBar) {
            PlaybackSeekBar(
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

private data class RecordingsPreviewParams(
    val uiState: RecordingsUiState,
)

private class RecordingsPreviewProvider : PreviewParameterProvider<RecordingsPreviewParams> {
    override val values: Sequence<RecordingsPreviewParams> = sequenceOf(
        // Empty
        RecordingsPreviewParams(
            uiState = RecordingsUiState(
                files = emptyList(),
                playback = PlaybackUi.Default,
            )
        ),
        // Two items, playing first
        RecordingsPreviewParams(
            uiState = RecordingsUiState(
                files = listOf(
                    RecordingItem("/a.wav", File("/a.wav"), "녹음 1", 20000, 0L, 25000.milliseconds),
                    RecordingItem("/b.wav", File("/b.wav"), "녹음 2", 43000, 0L, 60000.milliseconds),
                ),
                playback = PlaybackUi.Default.copy(
                    selectedId = "/a.wav",
                    state = PlaybackState.Playing,
                    position = 15.seconds,
                    duration = 30.seconds,
                ),
            )
        ),
        // Buffering
        RecordingsPreviewParams(
            uiState = RecordingsUiState(
                files = listOf(
                    RecordingItem(
                        "/c.wav",
                        File("/c.wav"),
                        "버퍼링 테스트",
                        12000,
                        0L,
                        40000.milliseconds
                    ),
                ),
                playback = PlaybackUi.Default.copy(
                    selectedId = "/c.wav",
                    state = PlaybackState.Buffering,
                    position = 0.milliseconds,
                    duration = 40000.milliseconds,
                ),
            )
        ),
        // Paused mid-track
        RecordingsPreviewParams(
            uiState = RecordingsUiState(
                files = listOf(
                    RecordingItem(
                        "/d.wav",
                        File("/d.wav"),
                        "일시정지 상태",
                        56000,
                        0L,
                        90000.milliseconds
                    ),
                    RecordingItem("/e.wav", File("/e.wav"), "다른 파일", 33000, 0L, 30000.milliseconds),
                ),
                playback = PlaybackUi.Default.copy(
                    selectedId = "/d.wav",
                    state = PlaybackState.Paused,
                    position = 45000.milliseconds,
                    duration = 90000.milliseconds,
                ),
            )
        ),
        // Error
        RecordingsPreviewParams(
            uiState = RecordingsUiState(
                files = listOf(
                    RecordingItem("/f.wav", File("/f.wav"), "에러 상태 예시", 0, 0L, 0.milliseconds),
                ),
                playback = PlaybackUi(
                    selectedId = "/f.wav",
                    state = PlaybackState.Error,
                    position = 0.milliseconds,
                    duration = 0.milliseconds,
                    errorMessage = "재생 오류",
                ),
            )
        ),
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecordingsScreenPreview(
    @PreviewParameter(RecordingsPreviewProvider::class) p: RecordingsPreviewParams
) {
    SoundlyTheme {
        RecordingsScreen(
            modifier = Modifier,
            uiState = p.uiState,
            onPlayClick = {},
            onPauseClick = {},
            onDelete = {},
            onRecordClick = {},
        )
    }
}
