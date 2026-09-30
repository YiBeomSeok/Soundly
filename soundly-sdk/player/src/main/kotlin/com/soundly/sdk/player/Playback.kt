package com.soundly.sdk.player

import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.time.Duration

/**
 * 수집하는 동안 재생하며, 완료·정지·실패·수집 취소 시 플레이어를 해제합니다.
 */
interface Playback {
    fun play(
        file: File,
        controls: PlaybackControls = PlaybackControls(),
    ): Flow<PlaybackStatus>
}

data class PlaybackStatus(
    val selectedFile: File? = null,
    val state: PlaybackState = PlaybackState.Idle,
    val position: Duration = Duration.ZERO,
    val duration: Duration = Duration.ZERO,
    val errorMessage: String? = null,
) {
    companion object {
        val Idle: PlaybackStatus = PlaybackStatus()
    }
}

enum class PlaybackState { Idle, Buffering, Playing, Paused, Ended, Error }

/**
 * 한 번의 재생에 사용할 명령을 순서대로 전달하며, 반복된 탐색 요청도 보존합니다.
 */
class PlaybackControls {
    private val pending = Channel<PlaybackCommand>(capacity = Channel.UNLIMITED)
    internal val commands: Flow<PlaybackCommand> = pending.receiveAsFlow()

    fun pause() {
        pending.trySend(PlaybackCommand.Pause)
    }

    fun resume() {
        pending.trySend(PlaybackCommand.Resume)
    }

    fun seekTo(position: Duration) {
        pending.trySend(PlaybackCommand.Seek(position = position))
    }

    fun stop() {
        pending.trySend(PlaybackCommand.Stop)
    }
}

internal sealed interface PlaybackCommand {
    data object Pause : PlaybackCommand
    data object Resume : PlaybackCommand
    data object Stop : PlaybackCommand
    data class Seek(val position: Duration) : PlaybackCommand
}
