package com.soundly.sdk.v0.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class PlaybackManager(
    private val context: Context,
) {
    private var exoPlayer: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var positionUpdater: Runnable? = null
    private var currentFile: File? = null

    data class Status(
        val selectedFile: File?,
        val state: PlaybackState,
        val position: Duration,
        val duration: Duration,
        val errorMessage: String?,
    ) {
        companion object {
            val Idle = Status(
                selectedFile = null,
                state = PlaybackState.Idle,
                position = 0.milliseconds,
                duration = 0.milliseconds,
                errorMessage = null,
            )
        }
    }

    private val _status = MutableStateFlow(Status.Idle)
    val status = _status.asStateFlow()

    private fun startTicker() {
        stopTicker()
        val r = object : Runnable {
            override fun run() {
                val mp = exoPlayer ?: return
                val pos = mp.currentPosition.milliseconds
                _status.update { it.copy(position = pos) }
                handler.postDelayed(this, 250)
            }
        }
        positionUpdater = r
        handler.post(r)
    }

    private fun stopTicker() {
        positionUpdater?.let { handler.removeCallbacks(it) }
        positionUpdater = null
    }

    fun play(file: File) {
        stop()
        currentFile = file
        _status.update {
            it.copy(
                selectedFile = file,
                state = PlaybackState.Buffering,
                position = 0.milliseconds,
                duration = 0.milliseconds,
                errorMessage = null,
            )
        }
        val player = ExoPlayer.Builder(context).build()
        player.setMediaItem(MediaItem.fromUri(file.toUri()))
        player.prepare()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        _status.update { it.copy(state = PlaybackState.Buffering) }
                    }

                    Player.STATE_READY -> {
                        player.playWhenReady = true
                        val dur = player.duration.milliseconds
                        val pos = player.currentPosition.milliseconds
                        _status.update {
                            it.copy(
                                state = PlaybackState.Playing,
                                duration = dur,
                                position = pos,
                                errorMessage = null,
                            )
                        }
                        startTicker()
                    }

                    Player.STATE_ENDED -> {
                        _status.update {
                            it.copy(
                                state = PlaybackState.Idle,
                                position = 0.milliseconds
                            )
                        }
                        stopTicker()
                    }

                    Player.STATE_IDLE -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _status.update {
                    it.copy(
                        state = PlaybackState.Error,
                        errorMessage = error.message
                    )
                }
            }
        })
        exoPlayer = player
    }

    fun pause() {
        val mp = exoPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            _status.update { it.copy(state = PlaybackState.Paused) }
            stopTicker()
        }
    }

    fun resume() {
        val mp = exoPlayer ?: return
        if (!mp.isPlaying) {
            mp.play()
            _status.update { it.copy(state = PlaybackState.Playing) }
            startTicker()
        }
    }

    fun stop() {
        exoPlayer?.run {
            runCatching { stop() }
            release()
        }
        exoPlayer = null
        _status.update { Status.Idle }
        currentFile = null
        stopTicker()
    }

    fun seekTo(position: Duration) {
        val mp = exoPlayer ?: return
        _status.update { it.copy(position = position) }
        runCatching { mp.seekTo(position.inWholeMilliseconds) }
    }
}

enum class PlaybackState { Idle, Buffering, Playing, Paused, Error }