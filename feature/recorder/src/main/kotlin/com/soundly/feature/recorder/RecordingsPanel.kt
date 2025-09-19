package com.soundly.feature.recorder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun RecordingsPanel(
    modifier: Modifier = Modifier,
    showList: Boolean,
    files: List<RecordingItem>,
    playback: PlaybackUi,
    onToggleList: () -> Unit,
    onPlayClick: (RecordingItem) -> Unit,
    onPauseClick: (RecordingItem) -> Unit,
    onDelete: (RecordingItem) -> Unit,
) {
    Surface(modifier = modifier) {
        Column {
            RecordingsPanelHeader(
                modifier = Modifier.fillMaxWidth(),
                expanded = showList,
                onToggle = onToggleList,
            )
            if (showList) {
                RecordingsContent(
                    modifier = Modifier.fillMaxWidth(),
                    items = files,
                    currentId = playback.selectedId,
                    isPlaying = playback.state == PlaybackState.Playing,
                    onPlayClick = onPlayClick,
                    onPauseClick = onPauseClick,
                    onDelete = onDelete,
                )
            }
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
        onPlayClick = {},
        onPauseClick = {},
        onDelete = {},
    )
}
