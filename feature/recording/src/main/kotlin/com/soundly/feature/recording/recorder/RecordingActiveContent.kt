package com.soundly.feature.recording.recorder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
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

@Composable
internal fun RecordingActiveContent(
    modifier: Modifier = Modifier,
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

        Spacer(Modifier.height(16.dp))

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

                Spacer(Modifier.height(0.dp))

                Text(
                    text = stringResource(id = R.string.feature_recording_action_stop)
                )
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
            onStopClick = {},
        )
    }
}
