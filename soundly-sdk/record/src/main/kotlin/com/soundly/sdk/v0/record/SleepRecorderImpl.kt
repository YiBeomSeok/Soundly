package com.soundly.sdk.v0.record

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Implementation of SleepRecorder using Android's AudioRecord API.
 * - PCM 8/16-bit WAV 저장
 * - 구조화된 동시성: record() 코루틴을 cancel 하면 곧 stop
 */
class SleepRecorderImpl(
    private val config: RecordingConfig = RecordingConfig()
) : SleepRecorder {

    private val _state = MutableStateFlow(RecordingState.IDLE)
    override val state = _state.asStateFlow()

    /**
     * 취소(cancellation)로 중단되는 원샷 녹음 함수.
     * 호출부는 launch { record(file) }로 실행하고, 중단 시 job.cancel().
     */
    @Throws(IllegalStateException::class, IOException::class, SecurityException::class)
    override suspend fun record(output: File) {
        // 상태 전이
        when (_state.value) {
            RecordingState.IDLE, RecordingState.ERROR -> _state.update { RecordingState.RECORDING }
            else -> throw IllegalStateException("Recorder is already running or stopping")
        }

        // AudioRecord 준비
        val ar = buildAudioRecord(config) // 초기화 실패 시 예외 발생
        // 출력 파일/헤더 준비
        val out = openAndWriteProvisionalWavHeader(output, config)

        var writtenAudioBytes = 0L
        try {
            withContext(Dispatchers.IO) {
                ar.startRecording()
                if (ar.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    throw IllegalStateException("AudioRecord did not enter RECORDSTATE_RECORDING")
                }

                val buf = ByteArray(calcBuffer(config))
                while (isActive) {
                    val n = ar.read(buf, 0, buf.size)
                    if (n > 0) {
                        out.write(buf, 0, n)
                        writtenAudioBytes += n
                    } else {
                        checkReadResult(n) // 오류면 예외 발생
                        // n == 0 이면 살짝 양보
                        yield()
                    }
                }
                // 취소되면 루프 탈출
            }
        } finally {
            // 취소/오류 관계없이 반드시 리소스 정리 및 헤더 패치
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching {
                    finalizeWavHeader(output, writtenAudioBytes)
                    out.flush()
                    out.close()
                    ar.stop()
                    ar.release()
                }
                _state.value = RecordingState.IDLE
            }
        }
    }

    // ---------- 내부 유틸리티 ----------

    @SuppressLint("MissingPermission")
    @Throws(IllegalStateException::class, IOException::class)
    private fun buildAudioRecord(cfg: RecordingConfig): AudioRecord {
        if (cfg.audioFormat == AudioFormat.ENCODING_PCM_FLOAT) {
            // 이 구현은 float PCM을 WAV로 직접 쓰지 않음
            throw IllegalArgumentException("ENCODING_PCM_FLOAT is not supported for WAV writer")
        }

        val minBuf = AudioRecord.getMinBufferSize(
            cfg.sampleRate,
            cfg.channelConfig,
            cfg.audioFormat
        )
        if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
            throw IOException("Invalid min buffer size: $minBuf")
        }

        val bufferSize = (minBuf * cfg.bufferSizeFactor).coerceAtLeast(minBuf)

        val ar = AudioRecord(
            /* audioSource = */ MediaRecorder.AudioSource.VOICE_RECOGNITION,
            /* sampleRateInHz = */ cfg.sampleRate,
            /* channelConfig = */ cfg.channelConfig,
            /* audioFormat = */ cfg.audioFormat,
            /* bufferSizeInBytes = */ bufferSize
        )

        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            ar.release()
            throw IllegalStateException("AudioRecord failed to initialize")
        }
        return ar
    }

    private fun calcBuffer(cfg: RecordingConfig): Int {
        val min = AudioRecord.getMinBufferSize(cfg.sampleRate, cfg.channelConfig, cfg.audioFormat)
        return (min * cfg.bufferSizeFactor).coerceAtLeast(min)
    }

    /**
     * WAV(PCM) 44바이트 헤더를 placeholder로 기록.
     * 실제 데이터 크기를 모르는 상태에서 파일을 시작할 때 사용.
     */
    @Throws(IOException::class)
    private fun openAndWriteProvisionalWavHeader(
        file: File,
        cfg: RecordingConfig
    ): FileOutputStream {
        // 파일이 이미 존재하면 예외 발생 (덮어쓰지 않음)
        require(!file.exists()) { "File already exists: ${file.absolutePath}" }

        // 출력 스트림 생성 (append = false → 항상 새로 쓰기)
        val out = FileOutputStream(file, /* append = */ false)

        // 채널 수 (예: MONO=1, STEREO=2)
        val channels = channelCountOf(cfg.channelConfig)
        // 샘플당 비트 수 (예: 16bit PCM → 16)
        val bitsPerSample = bitsPerSampleOf(cfg.audioFormat)
        // 초당 바이트 수 = sampleRate * channels * (bitsPerSample / 8)
        val byteRate = cfg.sampleRate * channels * (bitsPerSample / 8)
        // 한 프레임(샘플 × 채널)의 바이트 단위 크기
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()

        // WAV 헤더 버퍼 준비 (44바이트 고정, 리틀 엔디언)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        // "RIFF" (Chunk ID)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        // ChunkSize (파일 전체 크기 - 8) → 아직 모름, placeholder=0
        header.putInt(0)
        // "WAVE" (Format)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))

        // "fmt " (Subchunk1 ID)
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        // Subchunk1Size = 16 (PCM 고정)
        header.putInt(16)
        // AudioFormat = 1 (PCM)
        header.putShort(1)
        // NumChannels (모노=1, 스테레오=2)
        header.putShort(channels.toShort())
        // SampleRate (예: 44100)
        header.putInt(cfg.sampleRate)
        // ByteRate (초당 바이트 수)
        header.putInt(byteRate)
        // BlockAlign (프레임 크기)
        header.putShort(blockAlign)
        // BitsPerSample (샘플당 비트 수)
        header.putShort(bitsPerSample.toShort())

        // "data" (Subchunk2 ID)
        header.put("data".toByteArray(Charsets.US_ASCII))
        // Subchunk2Size (오디오 데이터 크기) → 아직 모름, placeholder=0
        header.putInt(0)

        // 헤더를 파일에 기록
        out.write(header.array())
        // 버퍼 강제 flush
        out.flush()
        // 스트림 반환 (이후 오디오 PCM 데이터 기록에 사용)
        return out
    }

    /**
     * 녹음이 끝난 뒤 RIFF/데이터 사이즈를 패치.
     */
    private fun finalizeWavHeader(file: File, dataSizeBytes: Long) {
        if (!file.exists()) return
        RandomAccessFile(file, "rw").use { raf ->
            val chunkSize = (36L + dataSizeBytes).coerceAtLeast(36L).toInt()
            raf.seek(4)  // ChunkSize 오프셋
            raf.write(intLE(chunkSize))
            raf.seek(40) // Subchunk2Size 오프셋
            raf.write(intLE(dataSizeBytes.coerceAtLeast(0L).toInt()))
        }
    }

    private fun channelCountOf(channelConfig: Int): Int = when (channelConfig) {
        AudioFormat.CHANNEL_IN_MONO -> 1
        AudioFormat.CHANNEL_IN_STEREO -> 2
        else -> error("Unsupported channel config: $channelConfig")
    }

    private fun bitsPerSampleOf(audioFormat: Int): Int = when (audioFormat) {
        AudioFormat.ENCODING_PCM_8BIT -> 8
        AudioFormat.ENCODING_PCM_16BIT -> 16
        else -> error("Unsupported audio format: $audioFormat") // float 미지원
    }

    private fun intLE(value: Int): ByteArray {
        return ByteBuffer
            .allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(value)
            .array()
    }

    /**
     * AudioRecord.read 반환값 검사
     */
    @Throws(IOException::class, IllegalStateException::class)
    private suspend fun checkReadResult(code: Int) {
        when (code) {
            0 -> {
                // 드물게 0이 나올 수 있으니 한 텀 양보
                yield()
            }

            AudioRecord.ERROR_INVALID_OPERATION -> throw IllegalStateException("AudioRecord ERROR_INVALID_OPERATION")
            AudioRecord.ERROR_BAD_VALUE -> throw IOException("AudioRecord ERROR_BAD_VALUE")
            AudioRecord.ERROR_DEAD_OBJECT -> throw IOException("AudioRecord ERROR_DEAD_OBJECT")
        }
    }
}