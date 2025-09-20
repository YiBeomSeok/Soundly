package com.soundly.feature.recording.recorder


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.feature_recording_status_ready),
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(16.dp))

            if (!micGranted) {
                if (permanentlyDenied) {
                    Text(
                        text = stringResource(id = R.string.feature_recording_mic_permission_denied_permanently),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(Modifier.height(8.dp))

                    FilledTonalButton(
                        onClick = onRequestMicPermission
                    ) {
                        Text(
                            text = stringResource(id = R.string.feature_recording_action_open_settings)
                        )
                    }
                } else {
                    Text(
                        text = stringResource(id = R.string.feature_recording_perm_mic_required),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(Modifier.height(8.dp))

                    FilledTonalButton(
                        onClick = onRequestMicPermission
                    ) {
                        Text(text = stringResource(id = R.string.feature_recording_action_request_permission))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            FilledTonalButton(
                onClick = onStartClick,
                enabled = micGranted
            ) {
                Text(text = stringResource(id = R.string.feature_recording_action_start))
            }
        }
    }
}

private data class IdlePreviewParams(
    val micGranted: Boolean,
    val permanentlyDenied: Boolean,
)

private class IdlePreviewParamsProvider : PreviewParameterProvider<IdlePreviewParams> {
    override val values = sequenceOf(
        IdlePreviewParams(micGranted = true, permanentlyDenied = false),
        IdlePreviewParams(micGranted = false, permanentlyDenied = false),
        IdlePreviewParams(micGranted = false, permanentlyDenied = true),
    )
}

@Preview(showBackground = true)
@Composable
private fun RecordingIdleContentPreview(
    @PreviewParameter(IdlePreviewParamsProvider::class) params: IdlePreviewParams
) {
    SoundlyTheme {
        RecordingIdleContent(
            modifier = Modifier.fillMaxSize(),
            micGranted = params.micGranted,
            permanentlyDenied = params.permanentlyDenied,
            onRequestMicPermission = {},
            onStartClick = {},
        )
    }
}