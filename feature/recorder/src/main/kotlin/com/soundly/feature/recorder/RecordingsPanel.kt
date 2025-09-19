package com.soundly.feature.recorder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider

@Composable
internal fun RecordingsPanel(
    modifier: Modifier = Modifier,
    showList: Boolean,
    files: List<RecordingItem>,
    playback: PlaybackUi,
    onToggleList: () -> Unit,
    onPlayPause: (RecordingItem) -> Unit,
    onDelete: (RecordingItem) -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val current = files.firstOrNull { it.id == playback.selectedId }
    Column(modifier = modifier) {
        RecordingsPanelHeader(
            modifier = Modifier.fillMaxWidth(),
            expanded = showList,
            onToggle = onToggleList,
        )
        if (showList) {
            RecordingsContent(
                modifier = Modifier.fillMaxSize(),
                items = files,
                currentId = playback.selectedId,
                isPlaying = playback.state == PlaybackState.Playing,
                onPlayPause = onPlayPause,
                onDelete = onDelete,
            )
        }
        if (current != null) {
            PlaybackMiniBar(
                item = current,
                isPlaying = playback.state == PlaybackState.Playing,
                positionMs = playback.positionMs,
                durationMs = playback.durationMs,
                onPlayPause = { onPlayPause(current) },
                onSeek = onSeek,
                onClose = onClose,
            )
        }
    }
}

private class PanelPreviewParams(
    val showList: Boolean,
    val files: List<RecordingItem>,
    val playback: PlaybackUi,
)

private class PanelPreviewProvider : PreviewParameterProvider<PanelPreviewParams> {
    override val values: Sequence<PanelPreviewParams> = sequenceOf(
        PanelPreviewParams(
            showList = false,
            files = emptyList(),
            playback = PlaybackUi.Default,
        ),
        PanelPreviewParams(
            showList = true,
            files = listOf(
                RecordingItem("/a.wav", java.io.File("/a.wav"), "녹음 1", 20000, 0L, 25000),
                RecordingItem("/b.wav", java.io.File("/b.wav"), "녹음 2", 43000, 0L, 60000),
            ),
            playback = PlaybackUi.Default.copy(selectedId = "/a.wav", state = PlaybackState.Playing, positionMs = 5000, durationMs = 25000),
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun RecordingsPanelPreview(
    @PreviewParameter(PanelPreviewProvider::class) p: PanelPreviewParams
) {
    RecordingsPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        showList = p.showList,
        files = p.files,
        playback = p.playback,
        onToggleList = {},
        onPlayPause = {},
        onDelete = {},
        onSeek = {},
        onClose = {},
    )
}
