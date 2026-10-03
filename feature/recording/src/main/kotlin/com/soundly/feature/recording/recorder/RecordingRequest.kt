package com.soundly.feature.recording.recorder

import android.content.Context
import android.os.Environment
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class RecordingFormat { Auto, Wav, Flac }

data class RecordingRequest(
    val dir: File,
    val baseName: String,
    val prefer: RecordingFormat = RecordingFormat.Flac,
) {
    companion object {
        fun create(context: Context, prefer: RecordingFormat): RecordingRequest {
            val base = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
            val directory = File(base, "recordings")
            val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
            val suffix = UUID.randomUUID().toString().take(8)

            return RecordingRequest(directory, "rec_${stamp}_$suffix", prefer)
        }
    }
}
