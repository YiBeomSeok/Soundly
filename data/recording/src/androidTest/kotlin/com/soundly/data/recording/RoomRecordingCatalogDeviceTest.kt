package com.soundly.data.recording

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.soundly.data.recording.db.RecordingDatabase
import com.soundly.sdk.recording.api.PcmChunk
import com.soundly.sdk.recording.api.RecordingConfig
import com.soundly.sdk.recording.api.RecordingTarget.Companion.flac
import com.soundly.sdk.recording.storage.FileRecordingOutput
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRecordingCatalogDeviceTest {
    @Test
    fun completedWavIsRecoveredWithActualDurationIntoRoom() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "recordings").apply { mkdirs() }
        val file = File(directory, "rec_20261012_120000_abcdef12.wav")
        val database = Room.inMemoryDatabaseBuilder(context, RecordingDatabase::class.java).build()
        try {
            writeOneSecondWav(file)
            val catalog = RoomRecordingCatalog(database.recordings(), listOf(directory))

            catalog.reconcileWithFiles()
            val row = database.recordings().getAll().single()
            assertEquals(file.absolutePath, row.id)
            assertTrue(row.durationMs in 900L..1_100L)
        } finally {
            database.close()
            file.delete()
        }
    }

    @Test
    fun completedFlacMissingFromRoomIsRecoveredWithActualDuration() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "recordings").apply { mkdirs() }
        val file = File(directory, "rec_20261012_120000_abcdef12.flac")
        val database = Room.inMemoryDatabaseBuilder(context, RecordingDatabase::class.java).build()
        try {
            val config = RecordingConfig(sampleRateHz = 44_100)
            val pcm = flow {
                var remaining = config.sampleRateHz
                while (remaining > 0) {
                    val samples = minOf(remaining, 2_048)
                    emit(PcmChunk(ByteArray(samples * 2)))
                    remaining -= samples
                }
            }
            FileRecordingOutput(flac(file)).write(pcm, config).collect()
            assertTrue(file.isFile)
            assertTrue(database.recordings().getAll().isEmpty())

            val catalog = RoomRecordingCatalog(database.recordings(), listOf(directory))
            catalog.reconcileWithFiles()
            val recovered = database.recordings().getAll().single()
            assertEquals(file.absolutePath, recovered.id)
            assertTrue("FLAC duration: ${recovered.durationMs}", recovered.durationMs in 900L..1_100L)

            catalog.reconcileWithFiles()
            assertEquals(1, database.recordings().getAll().size)
        } finally {
            database.close()
            file.delete()
        }
    }

    private fun writeOneSecondWav(file: File) {
        val pcm = ByteArray(8_000 * 2)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + pcm.size)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(8_000)
            putInt(8_000 * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray())
            putInt(pcm.size)
        }.array()
        file.outputStream().use { output ->
            output.write(header)
            output.write(pcm)
        }
    }
}
