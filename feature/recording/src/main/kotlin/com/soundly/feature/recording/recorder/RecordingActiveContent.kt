package com.soundly.feature.recording.recorder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = if (isPaused) stringResource(id = R.string.feature_recording_status_paused)
            else stringResource(id = R.string.feature_recording_status_recording),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )

        Spacer(Modifier.height(24.dp))

        val hours = elapsed.toLong(DurationUnit.HOURS)
        val minutes = elapsed.inWholeMinutes % 60
        val seconds = elapsed.inWholeSeconds % 60
        Text(
            text = "%02d:%02d:%02d".format(hours, minutes, seconds),
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 72.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onPauseClick,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = null
                )
                Text(
                    text = if (isPaused) stringResource(id = R.string.feature_recording_action_resume)
                    else stringResource(id = R.string.feature_recording_action_pause)
                )
            }
            Button(
                onClick = onStopClick,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = null
                )
                Text(text = stringResource(id = R.string.feature_recording_action_stop))
            }
        }
        Spacer(Modifier.height(32.dp))
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
