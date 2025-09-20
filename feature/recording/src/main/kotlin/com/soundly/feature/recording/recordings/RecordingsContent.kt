package com.soundly.feature.recording.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
internal fun RecordingsContent(
    modifier: Modifier = Modifier,
    items: List<RecordingItem>,
    currentId: String?,
    isPlaying: Boolean,
    onPlayClick: (RecordingItem) -> Unit,
    onPauseClick: (RecordingItem) -> Unit,
    onDelete: (RecordingItem) -> Unit,
) {
    if (items.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "저장된 녹음이 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = modifier
        ) {
            items(
                items = items,
                key = { it.id }
            ) { item ->
                val playing = isPlaying && item.id == currentId
                RecordingItem(
                    item = item,
                    isPlaying = playing,
                    onPlayClick = { onPlayClick(item) },
                    onPauseClick = { onPauseClick(item) },
                    onDelete = { onDelete(item) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
private fun RecordingItem(
    item: RecordingItem,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = if (isPlaying) onPauseClick else onPlayClick
            ) {
                Text(text = if (isPlaying) "Pause" else "Play")
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                val sizeKb = item.sizeBytes / 1024
                Text(
                    text = "${sizeKb}KB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(onClick = onDelete) {
                Text("Delete")
            }
        }
    }
}

private class PreviewParameterProvider : PreviewParameterProvider<List<RecordingItem>> {
    override val values: Sequence<List<RecordingItem>> = sequenceOf(
        emptyList(),
        listOf(
            RecordingItem("/a.wav", File("/a.wav"), "녹음 1", 20_000, 0L, 24500),
            RecordingItem(
                "/b.wav",
                File("/b.wav"),
                "아주아주 긴 파일 이름 샘플입니다.wav",
                43_000,
                0L,
                60123
            ),
        )
    )
}

@Preview(showBackground = true)
@Composable
private fun RecordingsListContentPreview(
    @PreviewParameter(PreviewParameterProvider::class) items: List<RecordingItem>
) {
    RecordingsContent(
        modifier = Modifier,
        items = items,
        currentId = items.firstOrNull()?.id,
        isPlaying = true,
        onPlayClick = {},
        onPauseClick = {},
        onDelete = {},
    )
}


