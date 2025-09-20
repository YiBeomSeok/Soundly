package com.soundly.feature.recording.recorder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
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
internal fun RecordingStoppingContent(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(id = R.string.feature_recording_status_stopping),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecordingStoppingContentPreview() {
    SoundlyTheme {
        RecordingStoppingContent(
            modifier = Modifier.fillMaxSize(),
        )
    }
}