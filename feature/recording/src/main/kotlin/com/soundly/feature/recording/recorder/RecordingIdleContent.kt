package com.soundly.feature.recording.recorder


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp

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
            Text("준비됨", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            if (!micGranted) {
                if (permanentlyDenied) {
                    Text(
                        "마이크 권한이 영구적으로 거부되었습니다.\n설정에서 권한을 허용해 주세요.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRequestMicPermission) {
                        Text("설정 열기")
                    }
                } else {
                    Text("마이크 권한이 필요합니다.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRequestMicPermission) {
                        Text("권한 요청")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(onClick = onStartClick, enabled = micGranted) {
                Text("녹음 시작")
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
    RecordingIdleContent(
        modifier = Modifier.fillMaxSize(),
        micGranted = params.micGranted,
        permanentlyDenied = params.permanentlyDenied,
        onRequestMicPermission = {},
        onStartClick = {},
    )
}