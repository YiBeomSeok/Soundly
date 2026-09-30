package com.soundly.sdk.player

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class PlaybackFlowTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var file: File

    @Before
    fun prepareAudio() {
        file = File(context.cacheDir, "playback-flow-test.wav")
        val sampleRate = 16_000
        val sampleCount = sampleRate
        val pcmSize = sampleCount * 2
        val wave = ByteBuffer.allocate(44 + pcmSize).order(ByteOrder.LITTLE_ENDIAN)
        wave.put("RIFF".toByteArray()).putInt(36 + pcmSize)
        wave.put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
        wave.putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        wave.put("data".toByteArray()).putInt(pcmSize)
        repeat(sampleCount) { index ->
            val sample = (sin(2 * PI * 440 * index / sampleRate) * 2_000).toInt().toShort()
            wave.putShort(sample)
        }
        file.writeBytes(wave.array())
    }

    @After
    fun deleteAudio() {
        file.delete()
    }

    @Test
    fun wavPlaybackReturnsAfterTheFinalEndedState() = runBlocking {
        val player: Playback = PlaybackManager(context = context)
        val statuses = withTimeout(timeMillis = 10_000) {
            player.play(file = file).toList()
        }

        assertTrue(statuses.any { it.state == PlaybackState.Playing })
        assertEquals(PlaybackState.Ended, statuses.last().state)
        assertEquals(1_000.milliseconds, statuses.last().duration)
        assertTrue(statuses.last().position >= 900.milliseconds)
    }

    @Test
    fun earlyCollectionExitAllowsTheSameFlowToPlayAgain() = runBlocking {
        val player: Playback = PlaybackManager(context = context)
        val playback = player.play(file = file)
        withTimeout(timeMillis = 10_000) {
            playback.first { it.state == PlaybackState.Playing }
        }
        val statuses = withTimeout(timeMillis = 10_000) { playback.toList() }

        assertEquals(PlaybackState.Ended, statuses.last().state)
        assertTrue(statuses.none { it.state == PlaybackState.Error })
    }

    @Test
    fun pauseSeekResumeAndStopUseTheSamePlayback() = runBlocking {
        val player: Playback = PlaybackManager(context = context)
        val controls = PlaybackControls()
        val statuses = mutableListOf<PlaybackStatus>()
        var phase = 0
        withTimeout(timeMillis = 10_000) {
            player.play(file = file, controls = controls).collect { status ->
                statuses += status
                when {
                    phase == 0 && status.state == PlaybackState.Playing -> {
                        phase = 1
                        controls.pause()
                    }
                    phase == 1 && status.state == PlaybackState.Paused -> {
                        phase = 2
                        controls.seekTo(position = 400.milliseconds)
                    }
                    phase == 2 && status.state == PlaybackState.Paused && status.position >= 400.milliseconds -> {
                        phase = 3
                        controls.resume()
                    }
                    phase == 3 && status.state == PlaybackState.Playing -> {
                        phase = 4
                        controls.stop()
                    }
                }
            }
        }

        assertEquals(4, phase)
        assertEquals(PlaybackStatus.Idle, statuses.last())
        assertTrue(statuses.none { it.state == PlaybackState.Error })
    }

    @Test
    fun unreadableFileReturnsAnErrorWithoutHanging() = runBlocking {
        val player: Playback = PlaybackManager(context = context)
        val missing = File(context.cacheDir, "missing-playback-file.wav")
        val statuses = withTimeout(timeMillis = 10_000) {
            player.play(file = missing).toList()
        }

        assertEquals(PlaybackState.Error, statuses.last().state)
        assertTrue(!statuses.last().errorMessage.isNullOrBlank())
    }
}
