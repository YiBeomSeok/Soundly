package com.soundly.sdk.recording.capture

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.soundly.sdk.recording.api.PcmChunk
import com.soundly.sdk.recording.api.RecordingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * 장치 준비 → 시작 → PCM 전달 → 정리만 맡으며, 일시정지 판단과 저장은 녹음 실행과 출력에 맡깁니다.
 */
internal object Microphone {
    fun capture(config: RecordingConfig): Flow<PcmChunk> = flow {
        val record = createDevice(config)

        // 수집 범위에서 중지와 해제를 함께 보장하며, 중지가 실패해도 장치를 해제합니다.
        AutoCloseable {
            AutoCloseable { record.release() }.use {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            }
        }.use {
            // 장치를 확인하고 시작한 뒤, 약 20ms 분량의 PCM16 읽기 버퍼를 준비합니다.
            check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord initialization failed" }
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone could not start" }

            val bytesPerFrame = config.channelCount * Short.SIZE_BYTES
            val framesPerRead = maxOf(1, config.sampleRateHz * 20 / 1000)
            val readBuffer = ByteArray(framesPerRead * bytesPerFrame)
            val context = currentCoroutineContext()

            while (context.isActive) {
                // 블로킹 읽기는 취소를 인식하지 않으므로 반환 직후 취소를 먼저 확인합니다.
                val bytesRead = record.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                context.ensureActive()

                val containsWholeFrames = bytesRead > 0 && bytesRead % bytesPerFrame == 0
                check(containsWholeFrames) { "Invalid AudioRecord read: $bytesRead" }

                // 다음 읽기가 이미 전달한 PCM을 덮어쓰지 않도록 유효한 데이터만 복사합니다.
                val pcmChunk = PcmChunk(bytes = readBuffer.copyOf(bytesRead))
                emit(pcmChunk)
            }
        }
    }.flowOn(Dispatchers.IO)

    @SuppressLint("MissingPermission")
    private fun createDevice(config: RecordingConfig): AudioRecord {
        // 채널 구성과 PCM16 형식에 필요한 장치의 최소 버퍼 크기를 확인합니다.
        val channelMask = if (config.channelCount == 1) {
            AudioFormat.CHANNEL_IN_MONO
        } else {
            AudioFormat.CHANNEL_IN_STEREO
        }
        val pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        val minimumBufferBytes = AudioRecord.getMinBufferSize(config.sampleRateHz, channelMask, pcmEncoding)

        check(minimumBufferBytes > 0) { "Unsupported AudioRecord configuration: $minimumBufferBytes" }

        // 설정 배수를 적용한 버퍼로 장치를 생성합니다. 녹음 권한은 호출 전에 확보해야 합니다.
        val scaledBufferBytes = minimumBufferBytes.toLong() * config.bufferSizeFactor
        val deviceBufferBytes = maxOf(4096L, scaledBufferBytes)

        require(deviceBufferBytes <= Int.MAX_VALUE) { "AudioRecord buffer size is too large" }

        return AudioRecord(
            MediaRecorder.AudioSource.MIC, config.sampleRateHz, channelMask,
            pcmEncoding, deviceBufferBytes.toInt(),
        )
    }
}
