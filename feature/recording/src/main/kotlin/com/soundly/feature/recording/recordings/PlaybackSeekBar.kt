package com.soundly.feature.recording.recordings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.soundly.core.designsystem.theme.SoundlyTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun PlaybackSeekBar(
    selectedId: String?,
    position: Duration,
    duration: Duration,
    onSeek: (Duration) -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationMs = duration.inWholeMilliseconds.coerceAtLeast(1)

    var sliderPosition by remember(selectedId) { mutableFloatStateOf(0f) } // 0f..100f
    var isUserSliding by remember(selectedId) { mutableStateOf(false) }

    // Sync from player while not dragging
    LaunchedEffect(position, selectedId, isUserSliding, durationMs) {
        if (!isUserSliding) {
            val percent = (position.inWholeMilliseconds.toFloat() / durationMs.toFloat()) * 100f
            sliderPosition = percent.coerceIn(0f, 100f)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Slider(
            value = sliderPosition,
            onValueChange = { p ->
                isUserSliding = true
                sliderPosition = p.coerceIn(0f, 100f)
            },
            valueRange = 0f..100f,
            steps = 0,
            colors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                thumbColor = MaterialTheme.colorScheme.primary,
            ),
            onValueChangeFinished = {
                val newPos = ((sliderPosition / 100f) * durationMs.toFloat()).toLong().milliseconds
                onSeek(newPos)
                isUserSliding = false
            }
        )
        Text(
            text = "%02d:%02d:%02d / %02d:%02d:%02d".format(
                position.inWholeHours,
                (position.inWholeMinutes % 60),
                (position.inWholeSeconds % 60),
                duration.inWholeHours,
                (duration.inWholeMinutes % 60),
                (duration.inWholeSeconds % 60)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaybackSeekBarPreview() {
    SoundlyTheme {
        PlaybackSeekBar(
            selectedId = "some-id",
            position = 30.seconds,
            duration = 1.minutes,
            onSeek = {}
        )
    }
}
