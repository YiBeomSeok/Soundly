package com.soundly.sdk.v0.record.api

import android.Manifest
import androidx.annotation.RequiresPermission
import com.soundly.sdk.v0.record.pcm.AndroidPcmRecorder
import java.io.File

/**
 * Public API for recording raw PCM (16-bit little-endian) audio.
 *
 * Notes:
 * - This API does not perform runtime permission requests. The host app must
 *   request RECORD_AUDIO (and storage permissions if writing to external storage).
 */
interface PcmRecorder {
    /** Starts recording into [output] with the given [config]. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun record(output: File, config: RecordingConfig): RecordingResult
}

fun PcmRecorder(): PcmRecorder = AndroidPcmRecorder()


