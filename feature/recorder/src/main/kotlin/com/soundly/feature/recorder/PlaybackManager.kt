package com.soundly.feature.recorder

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import java.io.File

internal class PlaybackManager(
    private val context: Context,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        @MainThread fun onBuffering()
        @MainThread fun onPlaying(durationMs: Long)
        @MainThread fun onPaused()
        @MainThread fun onStopped()
        @MainThread fun onPosition(positionMs: Long)
        @MainThread fun onError(message: String)
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
                    callbacks.onPlaying(player.duration.toLong())
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
            callbacks.onPlaying(mp.duration.toLong())
        }
    }

    fun seekTo(positionMs: Long) {
        val mp = mediaPlayer ?: return
        mp.seekTo(positionMs.toInt())
        callbacks.onPosition(positionMs)
    }

    fun stop() {
        mediaPlayer?.run {
            try {
                stop()
            } catch (_: Exception) {
            }
            release()
        }
        mediaPlayer = null
        callbacks.onStopped()
    }

    fun release() {
        stop()
    }
}


