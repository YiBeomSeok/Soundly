package com.soundly.feature.recording.recordings

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import com.soundly.feature.recording.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class PlaybackManager(
    private val context: Context,
) {
    private var mediaPlayer: MediaPlayer? = null
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
                val mp = mediaPlayer ?: return
                val pos = mp.currentPosition.milliseconds
                _status.value = _status.value.copy(position = pos)
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
        _status.value = _status.value.copy(
            selectedFile = file,
            state = PlaybackState.Buffering,
            position = 0.milliseconds,
            duration = 0.milliseconds,
            errorMessage = null,
        )
        try {
            val mp = MediaPlayer().apply {
                setDataSource(context, file.toUri())
                setOnPreparedListener { player ->
                    player.start()
                    val dur = player.duration.milliseconds
                    _status.value = _status.value.copy(
                        state = PlaybackState.Playing,
                        duration = dur,
                        position = 0.milliseconds,
                        errorMessage = null,
                    )
                    startTicker()
                }
                setOnErrorListener { _, what, extra ->
                    val msg =
                        context.getString(R.string.feature_recording_playback_error, what, extra)
                    _status.value = _status.value.copy(
                        state = PlaybackState.Error,
                        errorMessage = msg,
                    )
                    true
                }
                setOnCompletionListener {
                    _status.value = _status.value.copy(
                        state = PlaybackState.Idle,
                        position = 0.milliseconds,
                    )
                    stopTicker()
                }
                setOnSeekCompleteListener { p ->
                    val pos = p.currentPosition.milliseconds
                    _status.value = _status.value.copy(position = pos)
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            val msg = e.message ?: context.getString(R.string.feature_recording_unknown_error)
            _status.value = _status.value.copy(state = PlaybackState.Error, errorMessage = msg)
        }
    }

    fun pauseOrResume() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            _status.value = _status.value.copy(state = PlaybackState.Paused)
            stopTicker()
        } else {
            mp.start()
            _status.value = _status.value.copy(state = PlaybackState.Playing)
            startTicker()
        }
    }

    fun stop() {
        mediaPlayer?.run {
            runCatching {
                stop()
            }
            release()
        }
        mediaPlayer = null
        // emitStop retained for API parity, but no external callbacks now
        _status.value = Status.Idle
        currentFile = null
        stopTicker()
    }

    fun seekTo(position: Duration) {
        val mp = mediaPlayer ?: return
        // Optimistically update UI immediately
        _status.value = _status.value.copy(position = position)
        runCatching { mp.seekTo(position.inWholeMilliseconds.toInt()) }
    }

    // ---------------- Getters for UI polling (no callbacks to VM needed) ----------------
    fun currentPosition(): Duration? = mediaPlayer?.currentPosition?.toLong()?.milliseconds
    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true
    fun duration(): Duration? = mediaPlayer?.duration?.toLong()?.milliseconds
}


