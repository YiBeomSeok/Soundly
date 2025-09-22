package com.soundly.feature.recording.recordings

import androidx.compose.foundation.background
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
    position: Duration,
    duration: Duration,
    onSeek: (Duration) -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationMs = duration.inWholeMilliseconds.coerceAtLeast(1)

    var sliderPosition by remember(position, duration) {
        mutableFloatStateOf(position.toPercentOf(durationMs))
    }
    var isUserSliding by remember(sliderPosition) { mutableStateOf(false) }

    // 플레이어 위치 동기화 (드래그 중이 아닐 때만)
    LaunchedEffect(isUserSliding) {
        if (!isUserSliding) {
            sliderPosition = position.toPercentOf(durationMs)
        }
    }

    Column(
        modifier = modifier
            .background(color = MaterialTheme.colorScheme.surface)
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Slider(
            value = sliderPosition,
            onValueChange = {
                isUserSliding = true
                sliderPosition = it.coerceIn(0f, 100f)
            },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                thumbColor = MaterialTheme.colorScheme.primary,
            ),
            onValueChangeFinished = {
                val newPos = sliderPosition.percentToDuration(durationMs)
                onSeek(newPos)
                isUserSliding = false
            }
        )
        Text(
            text = "${position.formatAsHMS()} / ${duration.formatAsHMS()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 확장 함수들 */

private fun Duration.toPercentOf(totalMs: Long): Float =
    (inWholeMilliseconds.toFloat() / totalMs.toFloat() * 100f).coerceIn(0f, 100f)

private fun Float.percentToDuration(totalMs: Long): Duration =
    ((this / 100f) * totalMs.toFloat()).toLong().milliseconds

private fun Duration.formatAsHMS(): String =
    "%02d:%02d:%02d".format(inWholeHours, inWholeMinutes % 60, inWholeSeconds % 60)

@Preview(showBackground = true)
@Composable
private fun PlaybackSeekBarPreview() {
    SoundlyTheme {
        PlaybackSeekBar(
            position = 30.seconds,
            duration = 1.minutes,
            onSeek = {}
        )
    }
}