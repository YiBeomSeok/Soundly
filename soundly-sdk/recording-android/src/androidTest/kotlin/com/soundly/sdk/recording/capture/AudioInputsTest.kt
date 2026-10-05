package com.soundly.sdk.recording.capture

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.soundly.sdk.recording.api.PcmChunk
import com.soundly.sdk.recording.api.RecordingConfig
import com.soundly.sdk.recording.api.RecordingFormat
import com.soundly.sdk.recording.api.RecordingTarget
import com.soundly.sdk.recording.storage.FileRecordingOutput
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioInputsTest {
    @Test
    fun cancelledFileInputCanBeCollectedAgainFromTheBeginning() = runBlocking {
        withDirectory { directory ->
            val config = RecordingConfig(sampleRateHz = 48000, channelCount = 2)
            val expected = ByteArray(48_019 * 4) { (it * 37 + 19).toByte() }

            for (format in listOf(RecordingFormat.Wav, RecordingFormat.Flac)) {
                val file = File(directory, "input.${format.name.lowercase()}")
                FileRecordingOutput(RecordingTarget(file, format))
                    .write(flowOf(PcmChunk(expected)), config).collect()

                val input = AudioInputs.file(file, config)
                val cancellation = CancellationException("file consumer cancelled")
                val error = runCatching {
                    withTimeout(10_000) { input.collect { throw cancellation } }
                }.exceptionOrNull()
                assertSame(cancellation, error)

                val chunks = withTimeout(10_000) { input.toList() }
                val actual = chunks.flatMap { it.bytes.toList() }.toByteArray()
                assertArrayEquals(expected, actual)
            }
        }
    }

    @Test
    fun mismatchedFileFormatFailsBeforeDeliveringPcm() = runBlocking {
        withDirectory { directory ->
            val config = RecordingConfig()
            val pcm = PcmChunk(ByteArray(4097 * 2))

            for (format in listOf(RecordingFormat.Wav, RecordingFormat.Flac)) {
                val file = File(directory, "input.${format.name.lowercase()}")
                FileRecordingOutput(RecordingTarget(file, format)).write(flowOf(pcm), config).collect()
                val mismatches = listOf(config.copy(sampleRateHz = 48000), config.copy(channelCount = 2))

                for (mismatch in mismatches) {
                    var delivered = 0
                    val error = runCatching {
                        AudioInputs.file(file, mismatch).collect { delivered++ }
                    }.exceptionOrNull()

                    assertTrue(error is IllegalArgumentException)
                    assertEquals(0, delivered)
                }
            }
        }
    }

    @Test
    fun microphoneCanBeCollectedAfterCancellationAndEarlyCompletion() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        val input = AudioInputs.microphone(RecordingConfig())
        val cancellation = CancellationException("microphone consumer cancelled")

        val error = runCatching {
            withTimeout(10_000) { input.collect { throw cancellation } }
        }.exceptionOrNull()
        assertSame(cancellation, error)

        repeat(2) {
            val chunks = withTimeout(10_000) { input.take(3).toList() }
            assertEquals(3, chunks.size)
            assertTrue(chunks.all { it.bytes.isNotEmpty() && it.bytes.size % Short.SIZE_BYTES == 0 })
        }
    }

    private suspend fun withDirectory(block: suspend (File) -> Unit) {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val directory = File.createTempFile("soundly-input-", "", cache).apply { delete(); mkdir() }

        AutoCloseable { directory.deleteRecursively() }.use {
            block(directory)
        }
    }
}
