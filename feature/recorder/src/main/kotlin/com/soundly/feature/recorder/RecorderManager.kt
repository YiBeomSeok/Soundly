package com.soundly.feature.recorder

import android.Manifest
import androidx.annotation.RequiresPermission
import com.soundly.sdk.v0.record.api.PcmRecorder
import com.soundly.sdk.v0.record.api.RecordingConfig
import com.soundly.sdk.v0.record.api.RecordingResult
import java.io.File

/**
 * UI-layer controller that owns the Android recorder and coordinates start/stop.
 * Keeps non-UI logic out of Composables while remaining Android-specific.
 */
class RecorderManager(
    private val recorder: PcmRecorder
) {
    private val defaultFileName: String
        get() = "rec_${System.currentTimeMillis()}.wav"

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(
        targetDir: File,
        onSuccess: (filePath: String) -> Unit,
        onError: (message: String) -> Unit,
    ) {
        val outFile = File(targetDir, defaultFileName)
        when (val result = recorder.start(outFile, RecordingConfig())) {
            is RecordingResult.Success -> onSuccess(outFile.absolutePath)
            is RecordingResult.Error -> onError(result.message)
        }
    }

    fun stop(
        onStopped: () -> Unit,
        onError: (message: String) -> Unit,
    ) {
        when (val result = recorder.stop()) {
            is RecordingResult.Success -> onStopped()
            is RecordingResult.Error -> onError(result.message)
        }
    }

    fun release() {
        recorder.release()
    }
}


