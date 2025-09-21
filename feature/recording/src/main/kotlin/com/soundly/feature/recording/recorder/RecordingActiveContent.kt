package com.soundly.feature.recording.recorder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.soundly.core.designsystem.theme.SoundlyTheme
import com.soundly.feature.recording.R
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit

@Composable
internal fun RecordingActiveContent(
    modifier: Modifier = Modifier,
    elapsed: Duration,
    isPaused: Boolean,
    onPauseClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(48.dp)
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(id = R.string.feature_recording_status_recording),
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(Modifier.height(4.dp))

        val hours = elapsed.toLong(DurationUnit.HOURS)
        val minutes = elapsed.inWholeMinutes % 60
        val seconds = elapsed.inWholeSeconds % 60
        Text(
            text = "%02d:%02d:%02d".format(hours, minutes, seconds),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = onPauseClick
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isPaused) {
                            Icons.Filled.PlayArrow
                        } else {
                            Icons.Filled.Pause
                        },
                        contentDescription = null
                    )

                    Text(
                        text = if (isPaused) {
                            stringResource(id = R.string.feature_recording_action_record)
                        } else {
                            stringResource(id = R.string.feature_recording_action_pause)
                        }
                    )
                }
            }

            FilledTonalButton(
                onClick = onStopClick
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null
                    )
                    Text(
                        text = stringResource(id = R.string.feature_recording_action_stop)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecordingActiveContentPreview() {
    SoundlyTheme {
        RecordingActiveContent(
            modifier = Modifier.fillMaxSize(),
            isPaused = false,
            onPauseClick = {},
            onStopClick = {},
            elapsed = 34.minutes
        )
    }
}
