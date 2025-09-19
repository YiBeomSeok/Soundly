package com.soundly.feature.recorder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun PlaybackMiniBar(
    modifier: Modifier = Modifier,
    item: RecordingItem,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = onPlayPause) { Text(if (isPlaying) "Pause" else "Play") }
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        val progress = if (durationMs > 0) positionMs.coerceAtLeast(0).coerceAtMost(durationMs).toFloat() / durationMs else 0f
        Slider(
            value = progress,
            onValueChange = { frac -> onSeek((frac * durationMs).toLong()) },
            modifier = Modifier.weight(1.5f)
        )
        Button(onClick = onClose) { Text("Close") }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaybackMiniBarPreview() {
    PlaybackMiniBar(
        item = RecordingItem("/a.wav", java.io.File("/a.wav"), "녹음 1", 20_000, 0L, 24500),
        isPlaying = true,
        positionMs = 5000,
        durationMs = 24000,
        onPlayPause = {},
        onSeek = {},
        onClose = {},
    )
}


