package com.soundly.sdk.recording.capture

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.soundly.sdk.recording.api.PcmChunk
import com.soundly.sdk.recording.api.RecordingInputs
import com.soundly.sdk.recording.api.RecordingConfig
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * 선택한 입력을 PCM으로 제공하며, 파일은 트랙 선택 → 읽기 또는 디코딩 → 정리 순서로 처리합니다.
 */
object AudioInputs : RecordingInputs {
    override fun microphone(config: RecordingConfig): Flow<PcmChunk> = Microphone.capture(config)

    override fun file(file: File, config: RecordingConfig): Flow<PcmChunk> = flow {
        val extractor = MediaExtractor()

        AutoCloseable { extractor.release() }.use {
            // 오디오 트랙을 선택하고, 요청한 PCM 형식과 일치하는지 확인합니다.
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).first { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            extractor.selectTrack(track)

            val format = extractor.getTrackFormat(track)
            validateFormat(format, config)

            // 비압축 트랙은 그대로 읽고, 압축 트랙만 디코더를 거쳐 전달합니다.
            val pcm = if (format.getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_RAW) {
                readPcm(extractor, format)
            } else {
                decode(extractor, format, config)
            }

            emitAll(pcm)
        }
    }.flowOn(Dispatchers.IO)

    private fun readPcm(extractor: MediaExtractor, format: MediaFormat): Flow<PcmChunk> = flow {
        val minimumBufferBytes = 65_536
        val bufferBytes = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            maxOf(minimumBufferBytes, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
        } else {
            minimumBufferBytes
        }
        val buffer = ByteBuffer.allocate(bufferBytes)
        var bytesRead = extractor.readSampleData(buffer, 0)

        while (bytesRead >= 0) {
            currentCoroutineContext().ensureActive()
            val bytes = ByteArray(bytesRead)
            buffer.position(0)
            buffer.get(bytes)
            if (bytes.isNotEmpty()) emit(PcmChunk(bytes))

            extractor.advance()
            buffer.clear()
            bytesRead = extractor.readSampleData(buffer, 0)
        }
    }

    private fun decode(
        extractor: MediaExtractor,
        format: MediaFormat,
        config: RecordingConfig,
    ): Flow<PcmChunk> = flow {
        val mime = checkNotNull(format.getString(MediaFormat.KEY_MIME))
        val codec = MediaCodec.createDecoderByType(mime)

        AutoCloseable { codec.release() }.use {
            // 코덱은 이 수집 범위에서만 소유하며, 설정이나 시작이 실패해도 해제합니다.
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var progressAt = System.nanoTime()

            while (!outputEnded) {
                currentCoroutineContext().ensureActive()
                check(System.nanoTime() - progressAt < 5_000_000_000L) { "Timed out decoding audio file" }

                // 입력 공간이 생기면 파일의 다음 압축 샘플 또는 EOS를 제출합니다.
                if (!inputEnded) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = checkNotNull(codec.getInputBuffer(index))
                        buffer.clear()

                        val size = extractor.readSampleData(buffer, 0)
                        inputEnded = size < 0
                        val flags = if (inputEnded) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                        val timestampUs = if (inputEnded) 0L else extractor.sampleTime

                        codec.queueInputBuffer(index, 0, maxOf(0, size), timestampUs, flags)
                        if (!inputEnded) extractor.advance()
                        progressAt = System.nanoTime()
                    }
                }

                // 출력 EOS까지 읽으며, 장치 버퍼는 복사 직후 반환하고 PCM만 전달합니다.
                val index = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> validateFormat(codec.outputFormat, config)
                    index >= 0 -> {
                        val bytes = AutoCloseable { codec.releaseOutputBuffer(index, false) }.use {
                            val bytes = ByteArray(info.size)
                            if (bytes.isNotEmpty()) {
                                val buffer = checkNotNull(codec.getOutputBuffer(index))
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                buffer.get(bytes)
                            }
                            bytes
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (bytes.isNotEmpty()) emit(PcmChunk(bytes))
                        progressAt = System.nanoTime()
                    }
                }
            }
        }
    }

    private fun validateFormat(format: MediaFormat, config: RecordingConfig) {
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        require(sampleRate == config.sampleRateHz) { "Audio sample rate differs from recording config" }
        require(channelCount == config.channelCount) { "Audio channels differ from recording config" }
        if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            require(format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_16BIT) { "PCM16 input is required" }
        }
    }
}
