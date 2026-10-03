package com.soundly.feature.recording.recorder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun RecordingIdleContent(
    modifier: Modifier = Modifier,
    micGranted: Boolean,
    permanentlyDenied: Boolean,
    onRequestMicPermission: () -> Unit,
    selectedFormat: RecordingFormat,
    onSelectFormat: (RecordingFormat) -> Unit,
    onStartClick: () -> Unit,
    ready: Boolean,
) {
    Column(
        modifier = modifier
            .verticalScroll(state = rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space = 24.dp, alignment = Alignment.CenterVertically),
    ) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
            Icon(
                imageVector = Icons.Default.MicNone,
                contentDescription = null,
                modifier = Modifier.padding(all = 32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {
            Text(
                text = "지금의 소리를 담아보세요",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "음성 메모부터 긴 대화까지\n녹음하는 동안 파일에 차곡차곡 저장해요.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = RoundedCornerShape(size = 24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(all = 24.dp),
                verticalArrangement = Arrangement.spacedBy(space = 12.dp),
            ) {
                Text(text = "저장 형식", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
                    listOf(RecordingFormat.Flac, RecordingFormat.Wav, RecordingFormat.Auto).forEach { format ->
                        FilterChip(
                            selected = format == selectedFormat,
                            onClick = { onSelectFormat(format) },
                            label = { Text(text = if (format == RecordingFormat.Auto) "자동" else format.name.uppercase()) },
                        )
                    }
                }
                Text(
                    text = when (selectedFormat) {
                        RecordingFormat.Flac -> "음질 손실 없이 용량을 줄여요. 긴 녹음에 추천해요."
                        RecordingFormat.Wav -> "압축 없이 저장해요. 파일 용량이 커질 수 있어요."
                        RecordingFormat.Auto -> "FLAC을 우선 사용하고 지원되지 않으면 WAV로 저장해요."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {
            if (micGranted) {
                Button(
                    onClick = onStartClick,
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(size = 20.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = null)
                        Text(text = if (ready) "녹음 시작" else "준비 중", style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                Text(text = "녹음을 위해 마이크 접근을 허용해주세요.", style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = onRequestMicPermission,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
                ) {
                    Text(text = if (permanentlyDenied) "설정에서 마이크 허용" else "마이크 권한 허용")
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
            ) {
                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "녹음 파일은 이 기기에 저장됩니다",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
