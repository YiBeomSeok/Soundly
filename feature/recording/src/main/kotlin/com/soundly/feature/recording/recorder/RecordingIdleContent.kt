package com.soundly.feature.recording.recorder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.soundly.core.designsystem.theme.SoundlyTheme
import com.soundly.feature.recording.R

@Composable
internal fun RecordingIdleContent(
    modifier: Modifier = Modifier,
    micGranted: Boolean,
    permanentlyDenied: Boolean,
    onRequestMicPermission: () -> Unit,
    selectedFormat: RecordingFormat,
    onSelectFormat: (RecordingFormat) -> Unit,
    onStartClick: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (micGranted) {
            Text(
                text = stringResource(id = R.string.feature_recording_status_ready),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(48.dp))

            // Format selector
            Text(
                text = stringResource(id = R.string.feature_recording_select_format),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = { onSelectFormat(RecordingFormat.Flac) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (selectedFormat == RecordingFormat.Flac) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (selectedFormat == RecordingFormat.Flac) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                ) { Text("FLAC") }

                FilledTonalButton(
                    onClick = { onSelectFormat(RecordingFormat.Wav) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (selectedFormat == RecordingFormat.Wav) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (selectedFormat == RecordingFormat.Wav) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                ) { Text("WAV") }
            }
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onStartClick,
                shape = CircleShape,
                modifier = Modifier.size(120.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = stringResource(id = R.string.feature_recording_action_start),
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        } else {
            Text(
                text = stringResource(id = R.string.feature_recording_permission_required_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.feature_recording_permission_required_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(280.dp)
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onRequestMicPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (permanentlyDenied) {
                        stringResource(id = R.string.feature_recording_action_open_settings)
                    } else {
                        stringResource(id = R.string.feature_recording_action_request_permission)
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecordingIdleContentPreview() {
    SoundlyTheme {
        RecordingIdleContent(
            modifier = Modifier.fillMaxSize(),
            micGranted = true,
            permanentlyDenied = false,
            onRequestMicPermission = {},
            selectedFormat = RecordingFormat.Flac,
            onSelectFormat = {},
            onStartClick = {},
        )
    }
}