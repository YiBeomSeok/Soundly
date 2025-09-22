package com.soundly.sdk.v0.player

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.RandomAccessFile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class PlaybackManager(
    private val context: Context,
) {
    companion object {
        private const val TAG = "PlaybackManager"
    }

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

    @SuppressLint("UnsafeOptInUsageError")
    fun play(file: File) {
        stop()
        currentFile = file
        runCatching {
            val exists = file.exists()
            val length = if (exists) file.length() else -1L
            val lm = if (exists) file.lastModified() else 0L
            val perms = "r=${file.canRead()} w=${file.canWrite()} x=${file.canExecute()}"
            val ext = file.extension.lowercase()
            val guessedMime = when (ext) {
                "flac" -> "audio/flac"
                "wav" -> "audio/wav"
                else -> "(unknown)"
            }
            val header = try {
                RandomAccessFile(file, "r").use { raf ->
                    val buf = ByteArray(16)
                    val n = raf.read(buf)
                    if (n > 0) buf.copyOf(n) else ByteArray(0)
                }
            } catch (_: Throwable) {
                ByteArray(0)
            }
            val headerHex = header.joinToString(" ") { String.format("%02X", it) }
            Log.e(
                TAG,
                "play(): path=${file.absolutePath}, name=${file.name}, exists=$exists, size=$length, lastModified=$lm, perms=[$perms], ext=$ext, mime=$guessedMime, uri=${file.toUri()}, headerHex=$headerHex"
            )
        }
        _status.update {
            it.copy(
                selectedFile = file,
                state = PlaybackState.Buffering,
                position = 0.milliseconds,
                duration = 0.milliseconds,
                errorMessage = null,
            )
        }
        val factory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        val player = ExoPlayer.Builder(context, factory).build()
        player.setMediaItem(MediaItem.fromUri(file.toUri()))
        player.prepare()
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                try {
                    Log.e(TAG, "onTracksChanged(): groups=${tracks.groups.size}")
                    tracks.groups.forEachIndexed { gi, group ->
                        val tg = group.mediaTrackGroup
                        for (i in 0 until tg.length) {
                            val f = tg.getFormat(i)
                            val selected = group.isTrackSelected(i)
                            Log.e(
                                TAG,
                                "track[$gi:$i]: selected=$selected type=${f.sampleMimeType} codecs=${f.codecs} ch=${f.channelCount} sr=${f.sampleRate} br=${f.bitrate} lang=${f.language} label=${f.label}"
                            )
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "onTracksChanged() log failed: ${t.message}")
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        _status.update { it.copy(state = PlaybackState.Buffering) }
                        Log.e(TAG, "STATE_BUFFERING: uri=${file.toUri()}")
                    }

                    Player.STATE_READY -> {
                        player.playWhenReady = true
                        val pos = player.currentPosition.milliseconds
                        val dur = player.duration.milliseconds
                        runCatching {
                            val af = player.audioFormat
                            Log.e(
                                TAG,
                                "STATE_READY: durationMs=${player.duration} posMs=${player.currentPosition} audioFormat sampleMime=${af?.sampleMimeType} ch=${af?.channelCount} sr=${af?.sampleRate} br=${af?.bitrate} encoderDelay=${af?.encoderDelay} encoderPadding=${af?.encoderPadding}"
                            )
                        }
                        _status.update {
                            it.copy(
                                state = PlaybackState.Playing,
                                position = pos,
                                duration = dur,
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
                        Log.e(TAG, "STATE_ENDED")
                        stopTicker()
                    }

                    Player.STATE_IDLE -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(
                    TAG,
                    "onPlayerError: type=${error.errorCodeName} code=${error.errorCode} msg=${error.message} cause=${error.cause}"
                )
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