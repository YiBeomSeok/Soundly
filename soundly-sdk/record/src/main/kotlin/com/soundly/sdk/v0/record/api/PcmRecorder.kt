package com.soundly.sdk.v0.record.api

import android.Manifest
import androidx.annotation.RequiresPermission
import com.soundly.sdk.v0.record.pcm.AndroidPcmRecorder
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Duration

/**
 * Public API for recording raw PCM (16-bit little-endian) audio.
 *
 * Notes:
 * - This API does not perform runtime permission requests. The host app must
 *   request RECORD_AUDIO (and storage permissions if writing to external storage).
 */
interface PcmRecorder {
    /** Start recording into [output] with the given [config]. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecord(
        output: File,
        config: RecordingConfig,
        onProgress: ((elapsed: Duration, bytesWritten: Long) -> Unit)? = null,
    )

    /** Stop recording and return the result. */
    suspend fun stopRecord(): RecordingResult
}

fun PcmRecorder(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): PcmRecorder = AndroidPcmRecorder(scope = scope, dispatcher = dispatcher)

@Deprecated("Inject a CoroutineScope explicitly", ReplaceWith("PcmRecorder(scope)"))
fun PcmRecorder(): PcmRecorder = AndroidPcmRecorder(
    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    dispatcher = Dispatchers.IO
)


