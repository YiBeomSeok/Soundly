package com.soundly.sdk.v0.record.pcm

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import com.soundly.sdk.v0.record.api.PcmRecorder
import com.soundly.sdk.v0.record.api.RecordingConfig
import com.soundly.sdk.v0.record.api.RecordingResult
import com.soundly.sdk.v0.record.wav.WavHeader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class AndroidPcmRecorder(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : PcmRecorder {

    private var currentJob: Job? = null
    private var currentOutput: File? = null
    private var currentConfig: RecordingConfig? = null
    private var bytesWritten: Long = 0
    private var startedAtMs: Long = 0

    private val _status = MutableStateFlow(PcmRecorder.RecorderStatus.Idle)
    override val status: StateFlow<PcmRecorder.RecorderStatus> = _status.asStateFlow()

    @SuppressLint("MissingPermission")
    override fun startRecord(
        output: File,
        config: RecordingConfig,
    ) {
        check(currentJob == null) { "Recording already in progress" }
        currentOutput = output
        currentConfig = config
        bytesWritten = 0
        startedAtMs = SystemClock.elapsedRealtime()

        val channelMask = if (config.channelCount == 1) {
            AudioFormat.CHANNEL_IN_MONO
        } else {
            AudioFormat.CHANNEL_IN_STEREO
        }

        val minBuf = AudioRecord.getMinBufferSize(
            config.sampleRateHz,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBuf > 0) { "Unsupported audio configuration" }

        val bufferSize = minBuf * config.bufferSizeFactor
        val buffer = ByteArray(bufferSize)

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(config.sampleRateHz)
            .setChannelMask(channelMask)
            .build()

        AudioRecord(
            MediaRecorder.AudioSource.MIC,
            config.sampleRateHz,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT,
            config.sampleRateHz,
        )
        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .build()

        // provisional header
        val fos = FileOutputStream(output, false).also {
            val header = WavHeader.createProvisionalHeader(
                WavHeader.Params(
                    sampleRateHz = config.sampleRateHz,
                    channelCount = config.channelCount,
                    bitsPerSample = 16
                )
            )
            it.write(header)
        }

        recorder.startRecording()

        currentJob = scope.launch(dispatcher) {
            check(
                recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING
            ) { "AudioRecord failed to start" }

            // 취소는 isActive 로 감지
            while (isActive) {
                val read = recorder.read(buffer, 0, buffer.size)
                when {
                    read > 0 -> {
                        fos.write(buffer, 0, read)
                        bytesWritten += read
                        val elapsed = SystemClock.elapsedRealtime() - startedAtMs
                        status
                    }

                    read == 0 -> Unit // 계속
                    read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE ->
                        error("AudioRecord read error: $read")

                    else -> error("AudioRecord read returned $read")
                }
            }

            // 일부러 중단 지점을 만들지 않았으므로 이 코드는 실행되어야함
            runCatching {
                recorder.stop()
                fos.flush()
                fos.close()
                recorder.release()
            }
        }
    }

    override suspend fun stopRecord(): RecordingResult {
        val job = currentJob ?: return RecordingResult.Error("Not recording", null)
        val output = currentOutput ?: return RecordingResult.Error("No output", null)
        val config = currentConfig ?: return RecordingResult.Error("No config", null)

        job.cancelAndJoin()
        currentJob = null

        if (output.exists()) {
            RandomAccessFile(output, "rw").use { raf ->
                val header = ByteArray(WavHeader.HEADER_SIZE_BYTES)
                raf.seek(0)
                raf.readFully(header)
                WavHeader.updateSizesInPlace(header, bytesWritten)
                raf.seek(0)
                raf.write(header)
            }
        }

        val durationMs = (bytesWritten * 1000L) / (config.sampleRateHz * config.channelCount * 2)
        val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
        Log.d(
            "AndroidPcmRecorder",
            "bytesWritten=$bytesWritten, calcDurationMs=$durationMs, elapsedMs=$elapsedMs"
        )
        return RecordingResult.Success(
            file = output,
            duration = durationMs.milliseconds,
            sizeBytes = bytesWritten,
            sampleRateHz = config.sampleRateHz,
            channelCount = config.channelCount
        )
    }

    override fun pause() {
        TODO("Not yet implemented")
    }

    override fun resume() {
        TODO("Not yet implemented")
    }

    override fun isPaused(): Boolean {
        TODO("Not yet implemented")
    }
}