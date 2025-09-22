package com.soundly.feature.recording.recorder

import java.io.File

enum class RecordingFormat { Auto, Wav, Flac }

data class RecordingRequest(
    val dir: File,
    val baseName: String,
    val prefer: RecordingFormat = RecordingFormat.Auto,
)



