package com.soundly.feature.recording.recordings

import android.os.Environment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun RecordingsScreen(
    viewModel: RecordingsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val playbackManager = remember(context) {
        PlaybackManager(
            context = context,
            callbacks = object : PlaybackManager.Callbacks {
                override fun onBuffering() = viewModel.setBuffering()
                override fun onPlaying(duration: Duration) = viewModel.setPlaying(duration)
                override fun onPaused() = viewModel.setPaused()
                override fun onStopped() = viewModel.setIdle()
                override fun onPosition(position: Duration) = viewModel.setPosition(position)
                override fun onError(message: String) = viewModel.setPlaybackError(message)
            }
        )
    }

    LaunchedEffect(uiState.pendingCommand) {
        when (val cmd = uiState.pendingCommand) {
            is RecordingsUiCommand.RefreshFiles -> {
                val dir = context
                    .getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                    ?: context.filesDir
                val recordingsDir = File(dir, "recordings").apply { mkdirs() }
                val list = recordingsDir
                    .listFiles()
                    ?.sortedByDescending { it.lastModified() }
                    ?.map { f ->
                        RecordingItem(
                            id = f.absolutePath,
                            file = f,
                            name = f.name,
                            sizeBytes = f.length(),
                            lastModified = f.lastModified(),
                            durationMs = null,
                        )
                    }
                    ?: emptyList()

                viewModel.setFiles(list)
            }

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

            is RecordingsUiCommand.DeleteFile -> {
                val item = uiState.files.firstOrNull { it.id == cmd.id }
                if (item != null) {
                    runCatching { item.file.delete() }
                    val dir = item.file.parentFile
                    val list = dir?.listFiles()
                        ?.sortedByDescending { it.lastModified() }
                        ?.map { f ->
                            RecordingItem(
                                id = f.absolutePath,
                                file = f,
                                name = f.name,
                                sizeBytes = f.length(),
                                lastModified = f.lastModified(),
                                durationMs = null,
                            )
                        }
                        ?: emptyList()

                    viewModel.setFiles(list)
                    if (uiState.playback.selectedId == item.id) {
                        viewModel.setSelectedId(null)
                    }
                }
            }

            null -> {}
        }

        viewModel.handlePendingCommand()
    }
    RecordingsScreen(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        files = uiState.files,
        playback = uiState.playback,
        onPlayClick = { item ->
            viewModel.intentStartPlayback(item.id)
        },
        onPauseClick = { item ->
            viewModel.intentStopPlayback()
        },
        onDelete = { item -> viewModel.intentDeleteFile(item.id) },
    )
}

@Composable
internal fun RecordingsScreen(
    modifier: Modifier = Modifier,
    files: List<RecordingItem>,
    playback: PlaybackUi,
    onPlayClick: (RecordingItem) -> Unit,
    onPauseClick: (RecordingItem) -> Unit,
    onDelete: (RecordingItem) -> Unit,
) {
    Surface(modifier = modifier) {
        RecordingsContent(
            modifier = Modifier.fillMaxSize(),
            items = files,
            currentId = playback.selectedId,
            isPlaying = playback.state == PlaybackState.Playing,
            onPlayClick = onPlayClick,
            onPauseClick = onPauseClick,
            onDelete = onDelete,
        )
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
    )
}
