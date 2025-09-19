package com.soundly.sdk.v0.record.pcm

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import com.soundly.sdk.v0.record.api.PcmRecorder
import com.soundly.sdk.v0.record.api.RecordingConfig
import com.soundly.sdk.v0.record.api.RecordingResult
import com.soundly.sdk.v0.record.api.RecordingState
import com.soundly.sdk.v0.record.wav.WavHeader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Android implementation for recording PCM to a file and producing a WAV file.
 * Writes a provisional 44-byte WAV header, streams PCM frames, and fixes sizes on stop.
 */
class AndroidPcmRecorder(
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : PcmRecorder {

    private val mutableState = MutableStateFlow(RecordingState.IDLE)
    override val state: StateFlow<RecordingState> = mutableState

    private var audioRecord: AudioRecord? = null
    private var writingJob: Job? = null
    private var outputFile: File? = null
    private var totalPcmBytesWritten: Long = 0
    private var currentConfig: RecordingConfig? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun start(outputFile: File, config: RecordingConfig): RecordingResult {
        if (mutableState.value == RecordingState.RECORDING) {
            return RecordingResult.Error("Already recording")
        }

        runCatching {
            this.outputFile = outputFile
            this.currentConfig = config

            val channelConfig = if (config.channelCount == 1) {
                AudioFormat.CHANNEL_IN_MONO
            } else {
                AudioFormat.CHANNEL_IN_STEREO
            }

            val minBufferBytes = AudioRecord.getMinBufferSize(
                config.sampleRateHz,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT
            )
            require(minBufferBytes > 0) { "Unsupported audio configuration" }

            val bufferSizeBytes = minBufferBytes * config.bufferSizeFactor

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(config.sampleRateHz)
                .setChannelMask(channelConfig)
                .build()

            audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSizeBytes)
                .build()

            val recorder = audioRecord ?: return RecordingResult.Error("Failed to create AudioRecord")

            // Prepare output with provisional WAV header
            outputFile.outputStream().use { fos ->
                val header = WavHeader.createProvisionalHeader(
                    WavHeader.Params(
                        sampleRateHz = config.sampleRateHz,
                        channelCount = config.channelCount,
                        bitsPerSample = 16
                    )
                )
                fos.write(header)
            }
            totalPcmBytesWritten = 0

            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("AudioRecord failed to start")
            }

            val buffer = ByteArray(bufferSizeBytes)
            mutableState.value = RecordingState.RECORDING
            writingJob = coroutineScope.launch {
                FileOutputStream(outputFile, true).use { fos ->
                    while (mutableState.value == RecordingState.RECORDING) {
                        val read = recorder.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            fos.write(buffer, 0, read)
                            totalPcmBytesWritten += read
                        } else if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) {
                            mutableState.value = RecordingState.ERROR
                            break
                        }
                    }
                }
            }
            RecordingResult.Success
        }.onFailure { e ->
            mutableState.value = RecordingState.ERROR
            return RecordingResult.Error("Failed to start recording", e)
        }

        return RecordingResult.Success
    }

    override fun stop(): RecordingResult {
        val recorder = audioRecord ?: return RecordingResult.Error("Not recording")
        if (mutableState.value != RecordingState.RECORDING) return RecordingResult.Error("Not recording")

        mutableState.value = RecordingState.STOPPING
        return runCatching {
            recorder.stop()
            writingJob?.cancel()
            runBlocking { writingJob?.join() }

            // Fix sizes in header
            val file = outputFile ?: error("outputFile null")
            RandomAccessFile(file, "rw").use { raf ->
                val header = ByteArray(WavHeader.HEADER_SIZE_BYTES)
                raf.seek(0)
                raf.readFully(header)
                WavHeader.updateSizesInPlace(header, totalPcmBytesWritten)
                raf.seek(0)
                raf.write(header)
            }

            mutableState.value = RecordingState.IDLE
            RecordingResult.Success
        }.getOrElse { e ->
            mutableState.value = RecordingState.ERROR
            RecordingResult.Error("Failed to stop recording", e)
        }
    }

    override fun release() {
        runCatching {
            writingJob?.cancel()
            val job = writingJob
            if (job != null) {
                try {
                    runBlocking { job.cancelAndJoin() }
                } catch (_: Throwable) {
                }
            }
            audioRecord?.release()
            audioRecord = null
            mutableState.value = RecordingState.IDLE
        }
    }
}


