package com.soundly.sdk.v0.record

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import com.soundly.sdk.v0.record.wav.WavHeader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

class AudioRecorderWav(
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
    private var recordingThread: Thread? = null

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
        
        Log.d("AudioRecorderWav", "Recording started")

        recordingThread = Thread({
            val buffer = ByteArray(bufferSize)
            try {
                while (isRecording) {
                    if (isPaused) {
                        try {
                            Thread.sleep(50) // 약간 더 긴 대기
                        } catch (_: InterruptedException) {
                            break
                        }
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
                        bytesRead == 0 -> {
                            // 데이터가 없음, 계속 시도
                            continue
                        }
                        bytesRead == AudioRecord.ERROR_INVALID_OPERATION || 
                        bytesRead == AudioRecord.ERROR_BAD_VALUE -> {
                            Log.e("AudioRecorderWav", "AudioRecord read error: $bytesRead")
                            break
                        }
                        else -> {
                            Log.e("AudioRecorderWav", "Unexpected AudioRecord read result: $bytesRead")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioRecorderWav", "Recording thread error", e)
            } finally {
                Log.d("AudioRecorderWav", "Recording thread finished, totalPcmBytes: $totalPcmBytes")
            }
        }, "AudioRecord-WAV").also { it.start() }
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
    fun stop() {
        if (!isRecording) {
            Log.w("AudioRecorderWav", "Not recording, nothing to stop")
            return
        }

        Log.d("AudioRecorderWav", "Stopping recording...")
        
        // 1. 녹음 중지 플래그 설정
        isRecording = false

        // 2. 녹음 스레드 종료 대기
        recordingThread?.let { thread ->
            try {
                thread.join(5000) // 최대 5초 대기
                if (thread.isAlive) {
                    Log.w("AudioRecorderWav", "Recording thread did not finish in time")
                    thread.interrupt()
                }
            } catch (e: InterruptedException) {
                Log.w("AudioRecorderWav", "Interrupted while waiting for recording thread")
                Thread.currentThread().interrupt()
            }
        }
        recordingThread = null

        // 3. AudioRecord 중지 및 해제
        recorder?.let { audioRecord ->
            try {
                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop()
                    Log.d("AudioRecorderWav", "AudioRecord stopped")
                }
            } catch (e: IllegalStateException) {
                Log.w("AudioRecorderWav", "Error stopping AudioRecord", e)
            }
            
            try {
                audioRecord.release()
                Log.d("AudioRecorderWav", "AudioRecord released")
            } catch (e: Exception) {
                Log.w("AudioRecorderWav", "Error releasing AudioRecord", e)
            }
        }
        recorder = null

        // 4. 파일 스트림 플러시 및 닫기
        fos?.let { stream ->
            try {
                stream.flush()
                stream.close()
                Log.d("AudioRecorderWav", "FileOutputStream closed")
            } catch (e: IOException) {
                Log.w("AudioRecorderWav", "Error closing FileOutputStream", e)
            }
        }
        fos = null

        // 5. WAV 헤더 업데이트
        wavFile?.let { file ->
            Log.d("AudioRecorderWav", "Checking file for header update...")
            Log.d("AudioRecorderWav", "File path: ${file.absolutePath}")
            Log.d("AudioRecorderWav", "File exists: ${file.exists()}")
            Log.d("AudioRecorderWav", "Total PCM bytes: $totalPcmBytes")
            
            if (file.exists() && totalPcmBytes > 0) {
                try {
                    Log.d("AudioRecorderWav", "File size before header update: ${file.length()}")
                    updateWavHeaderWithWavHeader(file, totalPcmBytes)
                    Log.d("AudioRecorderWav", "WAV header updated successfully")
                    Log.d("AudioRecorderWav", "Final file size: ${file.length()}")
                    
                    // 파일 내용 검증
                    if (file.length() > 44) { // WAV 헤더(44바이트) + 데이터
                        Log.d("AudioRecorderWav", "File validation passed - file has header + data")
                    } else {
                        Log.w("AudioRecorderWav", "File validation warning - file size is suspiciously small: ${file.length()}")
                    }
                } catch (e: IOException) {
                    Log.e("AudioRecorderWav", "Error updating WAV header", e)
                    throw e
                }
            } else {
                Log.w("AudioRecorderWav", "File validation failed!")
                Log.w("AudioRecorderWav", "File exists: ${file.exists()}")
                Log.w("AudioRecorderWav", "PCM bytes: $totalPcmBytes")
                if (file.exists()) {
                    Log.w("AudioRecorderWav", "File size: ${file.length()}")
                }
            }
        } ?: Log.e("AudioRecorderWav", "wavFile is null!")
        
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