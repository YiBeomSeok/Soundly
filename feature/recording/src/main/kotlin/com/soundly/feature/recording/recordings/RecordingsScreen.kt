package com.soundly.feature.recording.recordings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun RecordingsScreen(
    state: RecordingsUiState,
    snackbar: SnackbarHostState,
    pendingDeletion: RecordingItem?,
    onRecordClick: () -> Unit,
    onSearch: (String) -> Unit,
    onPlay: (RecordingItem) -> Unit,
    onDeleteRequest: (RecordingItem) -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: (RecordingItem) -> Unit,
    playbackPanel: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRecordClick,
                icon = { Icon(imageVector = Icons.Default.Mic, contentDescription = null) },
                text = { Text(text = "새 녹음", fontWeight = FontWeight.SemiBold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(size = 24.dp),
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = state.shouldShowSeekBar,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                playbackPanel()
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues = padding)
                .consumeWindowInsets(paddingValues = padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 1120.dp)
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(space = 20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(size = 20.dp),
                            )
                            .padding(all = 18.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
                        Text(
                            text = "Soundly",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp,
                        )
                        Text(
                            text = "나의 소리, 차곡차곡",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "나의 녹음",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ) {
                        Text(
                            text = "${state.files.size}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                OutlinedTextField(
                    value = state.query,
                    onValueChange = onSearch,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(text = "녹음 검색") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { onSearch("") }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "검색 지우기")
                            }
                        }
                    },
                    shape = RoundedCornerShape(size = 20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )

                when {
                    state.isLoading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    state.visibleFiles.isEmpty() -> Column(
                        modifier = Modifier.fillMaxWidth().weight(weight = 1f).padding(bottom = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(space = 16.dp, alignment = Alignment.CenterVertically),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                        ) {
                            Icon(
                                imageVector = Icons.Default.MicNone,
                                contentDescription = null,
                                modifier = Modifier.padding(all = 28.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = if (state.query.isEmpty()) "첫 번째 소리를 담아보세요" else "검색 결과가 없어요",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = if (state.query.isEmpty()) "새 녹음을 눌러 음성 메모를 시작하세요." else "다른 이름으로 검색해보세요.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 300.dp),
                        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(space = 16.dp),
                        contentPadding = PaddingValues(bottom = 100.dp),
                    ) {
                        items(items = state.visibleFiles, key = { item -> item.uri }) { item ->
                            val selected = state.playback.selectedId == item.uri
                            RecordingCard(
                                item = item,
                                selected = selected,
                                playing = selected && state.isPlaying,
                                onPlay = { onPlay(item) },
                                onDelete = { onDeleteRequest(item) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDeletion?.let { item ->
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(text = "녹음을 삭제할까요?") },
            text = { Text(text = "‘${item.name}’ 파일이 삭제됩니다. 이 작업은 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = { onDeleteConfirm(item) }) {
                    Text(text = "삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) { Text(text = "취소") }
            },
        )
    }
}

@Composable
private fun RecordingCard(
    item: RecordingItem,
    selected: Boolean,
    playing: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = remember(item.lastModified) {
        SimpleDateFormat("M월 d일 · HH:mm", Locale.KOREAN).format(Date(item.lastModified))
    }
    val size = if (item.sizeBytes < 1024 * 1024) {
        "${item.sizeBytes / 1024} KB"
    } else {
        "%.1f MB".format(item.sizeBytes / 1048576.0)
    }
    val background by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 200),
        label = "녹음 선택 배경",
    )
    val border by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        label = "녹음 선택 테두리",
    )

    Surface(
        modifier = modifier,
        color = background,
        shape = RoundedCornerShape(size = 24.dp),
        border = BorderStroke(width = 1.dp, color = border),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(all = 20.dp),
            verticalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(size = 8.dp),
                ) {
                    Text(
                        text = item.file.extension.uppercase(),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.weight(weight = 1f))
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "${item.name} 삭제",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(weight = 1f),
                    verticalArrangement = Arrangement.spacedBy(space = 4.dp),
                ) {
                    Text(
                        text = "%02d:%02d".format(item.duration.inWholeMinutes, item.duration.inWholeSeconds % 60),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = size,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = onPlay) {
                    Crossfade(targetState = playing, label = "목록 재생 버튼") { active ->
                        Icon(
                            imageVector = if (active) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (active) "일시정지" else "${item.name} 재생",
                        )
                    }
                }
            }
        }
    }
}
