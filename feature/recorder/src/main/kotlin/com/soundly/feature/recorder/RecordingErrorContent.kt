package com.soundly.feature.recorder

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
                text = "오류가 발생했습니다.",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            message?.let { msg ->
                Text(msg, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick = onResetError) { Text("확인") }
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
    RecordingErrorContent(
        modifier = Modifier.fillMaxSize(),
        message = message,
        onResetError = {},
    )
}