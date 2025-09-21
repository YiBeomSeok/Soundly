package com.soundly.sdk.v0.record.api

import android.Manifest
import androidx.annotation.RequiresPermission
import com.soundly.sdk.v0.record.pcm.AndroidPcmRecorder
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * Public API for recording raw PCM (16-bit little-endian) audio.
 *
 * Notes:
 * - This API does not perform runtime permission requests. The host app must
 *   request RECORD_AUDIO (and storage permissions if writing to external storage).
 */
interface PcmRecorder {
    /** Observable recorder status. */
    val status: StateFlow<RecorderStatus>

    /** Start recording into [output] with the given [config]. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecord(
        output: File,
        config: RecordingConfig,
    )

    /** Stop recording and return the result. */
    suspend fun stopRecord(): RecordingResult

    /** Pause audio capture; no bytes will be written while paused. */
    fun pause()

    /** Resume audio capture after [pause]. */
    fun resume()

    /** Whether the recorder is currently paused. */
    fun isPaused(): Boolean

    /** Sealed status published via [status]. */
    sealed interface RecorderStatus {
        data object Idle : RecorderStatus
        data class Recording(val elapsed: Duration, val bytesWritten: Long) : RecorderStatus
        data class Paused(val elapsed: Duration, val bytesWritten: Long) : RecorderStatus
        data class Error(val error: Throwable?) : RecorderStatus
    }
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


