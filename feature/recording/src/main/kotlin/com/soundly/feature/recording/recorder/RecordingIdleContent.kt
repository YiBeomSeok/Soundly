package com.soundly.feature.recording.recorder


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
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
internal fun RecordingIdleContent(
    modifier: Modifier = Modifier,
    micGranted: Boolean,
    permanentlyDenied: Boolean,
    onRequestMicPermission: () -> Unit,
    onStartClick: () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.feature_recording_status_ready),
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(16.dp))

            if (micGranted) {
                FilledTonalButton(
                    onClick = onStartClick,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text = stringResource(id = R.string.feature_recording_action_start))
                }
            } else {
                FilledTonalButton(
                    onClick = onRequestMicPermission,
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
            onStartClick = {},
        )
    }
}