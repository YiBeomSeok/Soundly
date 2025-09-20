package com.soundly.feature.recording.recorder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
internal fun RecordingErrorContent(
    modifier: Modifier = Modifier,
    message: String?,
    onResetError: () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.feature_recording_error_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(Modifier.height(8.dp))

            message?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
            }
            FilledTonalButton(
                onClick = onResetError
            ) {
                Text(stringResource(id = R.string.feature_recording_action_ok))
            }
        }
    }
}

private class ErrorMessageProvider : PreviewParameterProvider<String?> {
    override val values = sequenceOf(
        null,
        "에러 메시지 샘플",
    )
}

@Preview(showBackground = true)
@Composable
private fun RecordingErrorContentPreview(
    @PreviewParameter(ErrorMessageProvider::class) message: String?
) {
    SoundlyTheme {
        RecordingErrorContent(
            modifier = Modifier.fillMaxSize(),
            message = message,
            onResetError = {},
        )
    }
}