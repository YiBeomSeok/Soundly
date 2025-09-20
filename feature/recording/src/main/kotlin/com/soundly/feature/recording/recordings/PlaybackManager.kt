package com.soundly.feature.recording.recordings

import android.content.Context
import android.media.MediaPlayer
import androidx.annotation.MainThread
import androidx.core.net.toUri
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class PlaybackManager(
    private val context: Context,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        @MainThread
        fun onBuffering()

        @MainThread
        fun onPlaying(duration: Duration)

        @MainThread
        fun onPaused()

        @MainThread
        fun onStopped()

        @MainThread
        fun onPosition(position: Duration)

        @MainThread
        fun onError(message: String)
    }

    private var mediaPlayer: MediaPlayer? = null

    fun play(file: File) {
        stop()
        callbacks.onBuffering()
        try {
            val mp = MediaPlayer().apply {
                setDataSource(context, file.toUri())
                setOnPreparedListener { player ->
                    player.start()
                    callbacks.onPlaying(player.duration.milliseconds)
                }
                setOnErrorListener { _, what, extra ->
                    callbacks.onError("Playback error: $what/$extra")
                    true
                }
                setOnCompletionListener {
                    callbacks.onStopped()
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            callbacks.onError(e.message ?: "Unknown error")
        }
    }

    fun pauseOrResume() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            callbacks.onPaused()
        } else {
            mp.start()
            callbacks.onPlaying(mp.duration.milliseconds)
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
        callbacks.onStopped()
    }
}


