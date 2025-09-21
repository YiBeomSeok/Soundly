package com.soundly.sdk.v0.record

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import com.soundly.sdk.v0.record.wav.WavHeader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

class AudioRecorderWav(
    private val coroutineScope: CoroutineScope,
    private val sampleRate: Int = 44100,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    private val bitsPerSample = 16
    private val numChannels = if (channelConfig == AudioFormat.CHANNEL_IN_MONO) 1 else 2

    private val bufferSize: Int =
        AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            .takeIf { it > 0 } ?: throw IllegalStateException("Invalid buffer size")

    private var recorder: AudioRecord? = null
    private var recordingJob: Job? = null

    @Volatile
    private var isRecording = false

    @Volatile
    private var isPaused = false

    private var wavFile: File? = null
    private var fos: FileOutputStream? = null
    private var totalPcmBytes: Long = 0

    /** 녹음 시작: outFile은 .wav 권장 */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @Throws(IOException::class)
    fun record(outFile: File) {
        if (isRecording) {
            Log.w("AudioRecorderWav", "Recording already in progress")
            return
        }

        wavFile = outFile
        totalPcmBytes = 0

        // WavHeader를 사용하여 임시 헤더 생성
        val wavParams = WavHeader.Params(
            sampleRateHz = sampleRate,
            channelCount = numChannels,
            bitsPerSample = bitsPerSample
        )

        fos = FileOutputStream(outFile, false).also {
            val header = WavHeader.createProvisionalHeader(wavParams)
            it.write(header)
            Log.d("AudioRecorderWav", "WAV header written to ${outFile.absolutePath}")
        }

        // AudioRecord 생성 및 초기화
        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        ).also { audioRecord ->
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                fos?.close()
                throw IllegalStateException("AudioRecord initialization failed")
            }
        }

        // 녹음 시작
        recorder?.startRecording()

        // 녹음 상태 확인
        if (recorder?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            recorder?.release()
            fos?.close()
            throw IllegalStateException("AudioRecord failed to start recording")
        }
        isRecording = true
        isPaused = false

        recordingJob = coroutineScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            try {
                while (isActive && isRecording) {
                    if (isPaused) {
                        delay(50)
                        continue
                    }

                    val bytesRead = recorder?.read(buffer, 0, buffer.size) ?: break

                    when {
                        bytesRead > 0 -> {
                            try {
                                fos?.write(buffer, 0, bytesRead)
                                totalPcmBytes += bytesRead
                            } catch (e: IOException) {
                                Log.e("AudioRecorderWav", "Error writing audio data", e)
                                break
                            }
                        }

                        // 데이터가 없음, 계속 시도
                        bytesRead == 0 -> continue

                        else -> break

                    }
                }
            } catch (e: Exception) {
                Log.e("AudioRecorderWav", "Recording coroutine error", e)
            } finally {
                Log.e(
                    "AudioRecorderWav",
                    "Recording coroutine finished, totalPcmBytes: $totalPcmBytes"
                )
            }
        }
    }

    /** 일시정지 */
    fun pause() {
        isPaused = true
    }

    /** 다시 시작 */
    fun resume() {
        isPaused = false
    }

    /** 녹음 종료 + WAV 헤더 갱신 */
    @Throws(IOException::class)
    suspend fun stop() {
        if (!isRecording) {
            Log.w("AudioRecorderWav", "Not recording, nothing to stop")
            return
        }

        Log.d("AudioRecorderWav", "Stopping recording...")

        // 녹음 중지 플래그 설정
        isRecording = false

        // 녹음 종료 대기
        recordingJob?.cancelAndJoin()
        recordingJob = null

        // AudioRecord 중지 및 해제
        withContext(Dispatchers.IO) {
            runCatching {
                recorder?.let { audioRecord ->
                    if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop()
                    }
                    audioRecord.release()
                }
                recorder = null

                fos?.flush()
                fos?.close()
                fos = null
            }.onFailure { t ->
                Log.i("AudioRecorderWav", "Error stopping recording: ${t.message}")
            }
        }

        // WAV 헤더 업데이트
        val wf = wavFile ?: error("wavFile is null")
        Log.d(
            "AudioRecorderWav",
            """
                    Checking file for header update...
                    "File path: ${wf.absolutePath}"
                    "File exists: ${wf.exists()}"
                    "Total PCM bytes: $totalPcmBytes"
                """.trimIndent()
        )

        if (wf.exists() && totalPcmBytes > 0) {
            Log.d("AudioRecorderWav", "File size before header update: ${wf.length()}")
            updateWavHeaderWithWavHeader(wf, totalPcmBytes)
            Log.d(
                "AudioRecorderWav",
                """
                    WAV header updated successfully
                    Final file size: ${wf.length()}
                """.trimIndent()
            )
        }

        totalPcmBytes = 0
        wavFile = null
        Log.d("AudioRecorderWav", "Recording stopped successfully")
    }

    // ---------- WAV Utils ----------

    /** WavHeader 클래스를 사용하여 헤더 업데이트 */
    @Throws(IOException::class)
    private fun updateWavHeaderWithWavHeader(file: File, pcmBytes: Long) {
        RandomAccessFile(file, "rw").use { raf ->
            val header = ByteArray(WavHeader.HEADER_SIZE_BYTES)
            raf.seek(0)
            raf.readFully(header)
            WavHeader.updateSizesInPlace(header, pcmBytes)
            raf.seek(0)
            raf.write(header)
        }
    }
}