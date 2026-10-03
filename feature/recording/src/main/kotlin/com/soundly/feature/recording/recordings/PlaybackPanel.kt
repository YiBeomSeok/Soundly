package com.soundly.feature.recording.recordings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soundly.sdk.player.PlaybackStatus
import com.soundly.sdk.player.PlaybackState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun PlaybackRoute(
    playback: StateFlow<PlaybackStatus>,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onSeek: (Duration) -> Unit,
    files: List<RecordingItem>,
    onCloseClick: () -> Unit,
) {
    // 종료 애니메이션 동안 마지막 재생 내용을 유지하고, 위치 갱신은 이 범위에만 반영합니다.
    val observed = remember(playback) { playback.filter { it.selectedFile != null } }
    val status by observed.collectAsStateWithLifecycle(initialValue = PlaybackStatus.Idle)
    var dragging by remember(status.selectedFile) { mutableStateOf(false) }
    var dragPosition by remember(status.selectedFile) { mutableFloatStateOf(0f) }
    val durationMillis = status.duration.inWholeMilliseconds.coerceAtLeast(1)
    val fraction = (status.position.inWholeMilliseconds.toFloat() / durationMillis).coerceIn(0f, 1f)
    val playing = status.state == PlaybackState.Playing
    val position by animateFloatAsState(
        targetValue = fraction,
        animationSpec = if (playing && !dragging) tween(durationMillis = 100, easing = LinearEasing) else snap(),
        label = "재생 위치",
    )
    val displayedPosition = if (dragging) dragPosition else position
    val file = status.selectedFile
    val name = files.firstOrNull { item -> item.file == file }?.name ?: file?.nameWithoutExtension.orEmpty()

    PlaybackPanel(
        name = name,
        state = status.state,
        position = displayedPosition,
        elapsed = if (dragging) (dragPosition * durationMillis).toLong().milliseconds else status.position,
        duration = status.duration,
        onPositionChange = { value ->
            dragging = true
            dragPosition = value
        },
        onPositionChangeFinished = {
            onSeek((dragPosition * durationMillis).toLong().milliseconds)
            dragging = false
        },
        onPlayClick = {
            if (status.state == PlaybackState.Playing || status.state == PlaybackState.Buffering) {
                onPauseClick()
            } else {
                onResumeClick()
            }
        },
        onReplayClick = { onSeek(status.position - 10.seconds) },
        onForwardClick = { onSeek(status.position + 10.seconds) },
        onCloseClick = onCloseClick,
    )
}

@Composable
private fun PlaybackPanel(
    name: String,
    state: PlaybackState,
    position: Float,
    elapsed: Duration,
    duration: Duration,
    onPositionChange: (Float) -> Unit,
    onPositionChangeFinished: () -> Unit,
    onPlayClick: () -> Unit,
    onReplayClick: () -> Unit,
    onForwardClick: () -> Unit,
    onCloseClick: () -> Unit,
) {
    val active = state == PlaybackState.Playing || state == PlaybackState.Buffering
    val preparing = state == PlaybackState.Buffering
    val elapsedLabel = "%02d:%02d".format(elapsed.inWholeMinutes, elapsed.inWholeSeconds % 60)
    val durationLabel = "%02d:%02d".format(duration.inWholeMinutes, duration.inWholeSeconds % 60)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(insets = WindowInsets.safeDrawing.only(sides = WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(size = 28.dp),
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(space = 8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(size = 16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.padding(all = 14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(weight = 1f),
                        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
                    ) {
                        Text(
                            text = when (state) {
                                PlaybackState.Buffering -> "재생 준비 중"
                                PlaybackState.Playing -> "지금 듣고 있어요"
                                PlaybackState.Ended -> "재생 완료"
                                else -> "잠시 멈췄어요"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onCloseClick) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "재생 종료")
                    }
                }

                Slider(
                    value = position,
                    onValueChange = onPositionChange,
                    onValueChangeFinished = onPositionChangeFinished,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = duration > Duration.ZERO && !preparing && state != PlaybackState.Ended,
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = elapsedLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.weight(weight = 1f))
                    Text(
                        text = durationLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(space = 20.dp, alignment = Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onReplayClick, enabled = !preparing && state != PlaybackState.Ended) {
                        Icon(imageVector = Icons.Default.Replay10, contentDescription = "10초 뒤로")
                    }
                    FilledTonalButton(
                        onClick = onPlayClick,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
                    ) {
                        Crossfade(targetState = active, label = "재생 버튼 전환") { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playing) "일시정지" else "재생",
                            )
                        }
                    }
                    IconButton(onClick = onForwardClick, enabled = !preparing && state != PlaybackState.Ended) {
                        Icon(imageVector = Icons.Default.Forward10, contentDescription = "10초 앞으로")
                    }
                }
            }
        }
    }
}
