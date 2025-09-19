package com.soundly.sdk.v0.record.api

import android.Manifest
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Public API for recording raw PCM (16-bit little-endian) audio.
 *
 * Notes:
 * - This API does not perform runtime permission requests. The host app must
 *   request RECORD_AUDIO (and storage permissions if writing to external storage).
 */
interface PcmRecorder {
    val state: StateFlow<RecordingState>

    /** Starts recording into [outputFile] with the given [config]. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(outputFile: File, config: RecordingConfig): RecordingResult

    /** Requests recording to stop. Blocks until underlying resources are stopped. */
    fun stop(): RecordingResult

    /** Releases native resources. After calling this, the instance should not be reused. */
    fun release()
}


