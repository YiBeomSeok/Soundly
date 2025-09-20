package com.soundly.feature.recording.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.soundly.core.designsystem.theme.SoundlyTheme
import com.soundly.feature.recording.R
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

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
                text = stringResource(id = R.string.feature_recording_no_recordings),
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
                key = { it.uri }
            ) { item ->
                val playing = isPlaying && item.uri == currentId

                RecordingRow(
                    item = item,
                    isPlaying = playing,
                    onPlayClick = { onPlayClick(item) },
                    onPauseClick = { onPauseClick(item) },
                    onDelete = { onDelete(item) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )

                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun RecordingRow(
    item: RecordingItem,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(4.dp)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = if (isPlaying) {
                        onPauseClick
                    } else {
                        onPlayClick
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) {
                                Icons.Filled.Pause
                            } else {
                                Icons.Filled.PlayArrow
                            },
                            contentDescription = if (isPlaying) {
                                stringResource(id = R.string.feature_recording_action_pause)
                            } else {
                                stringResource(
                                    id = R.string.feature_recording_action_play
                                )
                            }
                        )
                        Text(
                            text = if (isPlaying) {
                                stringResource(id = R.string.feature_recording_action_pause)
                            } else {
                                stringResource(
                                    id = R.string.feature_recording_action_play
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.weight(1f))

                TextButton(onClick = onDelete) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(id = R.string.feature_recording_action_delete)
                        )
                        Text(stringResource(id = R.string.feature_recording_action_delete))
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatRecordedDate(item.lastModified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val hours = item.duration.toLong(DurationUnit.HOURS)
                val minutes = item.duration.inWholeMinutes % 60
                val seconds = item.duration.inWholeSeconds % 60
                Text(
                    text = "%02d:%02d:%02d".format(hours, minutes, seconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatSize(item.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}

private fun formatRecordedDate(lastModified: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(lastModified))
}

private data class RecordingsContentPreviewParameter(
    val items: List<RecordingItem>,
)

private class RecordingsContentPreviewParameterProvider :
    PreviewParameterProvider<RecordingsContentPreviewParameter> {
    override val values: Sequence<RecordingsContentPreviewParameter> = sequenceOf(
        RecordingsContentPreviewParameter(
            emptyList()
        ),
        RecordingsContentPreviewParameter(
            listOf(
                RecordingItem("/a.wav", File("/a.wav"), "녹음 1", 20_000, 0L, 24500.milliseconds),
                RecordingItem(
                    "/b.wav",
                    File("/b.wav"),
                    "아주아주 긴 파일 이름 샘플입니다.wav",
                    43_000,
                    0L,
                    60123.milliseconds
                ),
            )
        )
    )
}

@Preview(showBackground = true)
@Composable
private fun RecordingsContentPreview(
    @PreviewParameter(RecordingsContentPreviewParameterProvider::class)
    param: RecordingsContentPreviewParameter
) {
    SoundlyTheme {
        RecordingsContent(
            modifier = Modifier.fillMaxWidth(),
            items = param.items,
            currentId = param.items.firstOrNull()?.uri,
            isPlaying = true,
            onPlayClick = {},
            onPauseClick = {},
            onDelete = {},
        )
    }
}


