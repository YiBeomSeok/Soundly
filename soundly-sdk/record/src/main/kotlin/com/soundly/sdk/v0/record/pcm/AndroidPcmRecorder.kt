package com.soundly.sdk.v0.record.pcm

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import com.soundly.sdk.v0.record.api.PcmRecorder
import com.soundly.sdk.v0.record.api.RecordingConfig
import com.soundly.sdk.v0.record.api.RecordingResult
import com.soundly.sdk.v0.record.wav.WavHeader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.invoke
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.coroutines.cancellation.CancellationException

internal class AndroidPcmRecorder(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : PcmRecorder {

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override suspend fun record(
        output: File,
        config: RecordingConfig
    ): RecordingResult = dispatcher {
        var recorder: AudioRecord? = null
        var fos: FileOutputStream? = null
        var bytesWritten = 0L

        try {
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

            recorder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .build()

            // provisional header
            fos = FileOutputStream(output, false).also {
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

            check(
                recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING
            ) { "AudioRecord failed to start" }

            // --- 루프: 취소는 isActive 로 감지 (코루틴 취소가 들어오면 finally에서 stop 호출)
            while (isActive) {
                val read = recorder.read(buffer, 0, buffer.size)
                when {
                    read > 0 -> {
                        fos.write(buffer, 0, read)
                        bytesWritten += read
                    }

                    read == 0 -> Unit // 계속
                    read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE ->
                        error("AudioRecord read error: $read")

                    else -> error("AudioRecord read returned $read")
                }
            }

            RecordingResult.Success
        } catch (_: CancellationException) {
            // 취소는 정상 종료로 간주
            RecordingResult.Success
        } catch (t: Throwable) {
            RecordingResult.Error("Recording failed", t)
        } finally {
            // 취소/예외/정상 종료 모두: stop -> header fix -> release
            try {
                recorder?.runCatching { stop() }
                fos?.runCatching { flush() }
                // WAV 헤더 사이즈 수정
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
            } catch (t: Throwable) {
                throw t // 최종 예외 전파
            } finally {
                // 리소스 해제
                runCatching {
                    fos?.close()
                    recorder?.release()
                }
            }
        }
    }
}


