package com.soundly.sdk.player

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal interface PlaybackEngine : AutoCloseable {
    fun play(file: File)
    fun observe(): Flow<PlaybackStatus>
    fun readStatus(): PlaybackStatus
    fun pause()
    fun resume()
    fun seekTo(position: Duration)
}

/**
 * Media3의 명령과 상태 읽기만 담당하며, 리스너는 관찰 Flow의 수집 범위에 묶습니다.
 */
@SuppressLint("UnsafeOptInUsageError")
internal class Media3PlaybackEngine(context: Context) : PlaybackEngine {
    private val renderers = DefaultRenderersFactory(context)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
    private val player = ExoPlayer.Builder(context, renderers).build()

    override fun play(file: File) {
        player.setMediaItem(MediaItem.fromUri(file.toUri()))
        player.prepare()
        player.play()
    }

    override fun observe(): Flow<PlaybackStatus> {
        return callbackFlow {
            val listener = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    trySend(readStatus())
                }
            }
            player.addListener(listener)
            trySend(readStatus())

            awaitClose { player.removeListener(listener) }
        }.buffer(capacity = Channel.UNLIMITED)
    }

    override fun readStatus(): PlaybackStatus {
        val error = player.playerError
        val state = when {
            error != null -> PlaybackState.Error
            player.playbackState == Player.STATE_ENDED -> PlaybackState.Ended
            player.playbackState == Player.STATE_BUFFERING -> PlaybackState.Buffering
            player.isPlaying -> PlaybackState.Playing
            player.playWhenReady && player.playbackState == Player.STATE_IDLE -> PlaybackState.Buffering
            else -> PlaybackState.Paused
        }

        return PlaybackStatus(
            state = state,
            position = player.currentPosition.coerceAtLeast(minimumValue = 0).milliseconds,
            duration = player.duration.coerceAtLeast(minimumValue = 0).milliseconds,
            errorMessage = error?.let { it.message ?: "재생하지 못했습니다." },
        )
    }

    override fun pause() {
        player.pause()
    }

    override fun resume() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
    }

    override fun seekTo(position: Duration) {
        val maximum = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val requestedPosition = position.inWholeMilliseconds.coerceIn(minimumValue = 0, maximumValue = maximum)
        player.seekTo(requestedPosition)
    }

    override fun close() {
        player.release()
    }
}
