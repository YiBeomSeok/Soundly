package com.soundly.feature.recording.recorder

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecorderScreen(
    state: RecorderUiState,
    micGranted: Boolean,
    permanentlyDenied: Boolean,
    selectedFormat: RecordingFormat,
    ready: Boolean,
    onBackClick: () -> Unit,
    onSelectFormat: (RecordingFormat) -> Unit,
    onRequestMicPermission: () -> Unit,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onStopClick: () -> Unit,
    onResetError: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(text = "새 녹음") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "목록으로",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues = padding)
                .consumeWindowInsets(paddingValues = padding),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                targetState = state.recordingState,
                label = "녹음 화면 전환",
            ) { current ->
                val contentModifier = Modifier.widthIn(max = 640.dp).fillMaxSize()

                when (current) {
                    RecordingState.Idle -> RecordingIdleContent(
                        modifier = contentModifier,
                        micGranted = micGranted,
                        permanentlyDenied = permanentlyDenied,
                        selectedFormat = selectedFormat,
                        onSelectFormat = onSelectFormat,
                        onRequestMicPermission = onRequestMicPermission,
                        onStartClick = onStartClick,
                        ready = ready,
                    )
                    RecordingState.Recording, RecordingState.Paused -> RecordingActiveContent(
                        modifier = contentModifier,
                        elapsed = state.elapsed,
                        isPaused = current == RecordingState.Paused,
                        onPauseClick = onPauseClick,
                        onStopClick = onStopClick,
                    )
                    RecordingState.Starting, RecordingState.Stopping, RecordingState.Stopped -> Column(
                        modifier = contentModifier,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(space = 24.dp, alignment = Alignment.CenterVertically),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = if (current == RecordingState.Starting) {
                                "마이크를 준비하고 있어요"
                            } else {
                                "남은 음성을 저장하고 있어요"
                            },
                        )
                    }
                    is RecordingState.Error -> Column(
                        modifier = contentModifier.padding(all = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(space = 20.dp, alignment = Alignment.CenterVertically),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "녹음을 완료하지 못했어요",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = current.t?.message ?: "다시 시도해주세요.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onResetError) {
                            Text(text = "다시 준비하기")
                        }
                    }
                }
            }
        }
    }
}
