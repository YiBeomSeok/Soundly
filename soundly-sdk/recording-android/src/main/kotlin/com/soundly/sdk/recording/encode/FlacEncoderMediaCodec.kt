package com.soundly.sdk.recording.encode

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import com.soundly.sdk.recording.api.PcmChunk
import com.soundly.sdk.recording.api.RecorderException
import java.nio.ByteBuffer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

/**
 * 코덱 준비 → PCM 변환 → 잔여 패킷 회수 → 해제만 맡으며, 녹음 제어와 파일 기록은 알지 못합니다.
 */
internal object FlacEncoderMediaCodec {
    fun encode(
        source: Flow<PcmChunk>,
        spec: SampleSpec,
        createCodec: (SampleSpec) -> FlacCodec = ::MediaCodecFlac,
        nanoTime: () -> Long = System::nanoTime,
        timeoutNs: Long = 5_000_000_000L,
    ): Flow<FlacOutput.Packet> = flow {
        require(spec.bitsPerSample == 16 && spec.channelCount in 1..2 && spec.sampleRate > 0)
        require(timeoutNs > 0)

        // 수집할 때 코덱을 만들며, 초기화 실패는 입력을 열기 전에 구분합니다.
        val codec = try {
            createCodec(spec)
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            throw RecorderException(RecorderException.Reason.Encoding, failure)
        }
        val bytesPerFrame = spec.channelCount * Short.SIZE_BYTES
        var submittedSamples = 0L

        codec.use {
            // PCM을 장치 입력 크기에 맞춰 제출하고, 입력 대기 중에도 출력을 회수합니다.
            source.collect { pcm ->
                require(pcm.bytes.size % bytesPerFrame == 0) { "PCM chunk must contain whole sample frames" }
                var offset = 0
                var progressAt = nanoTime()

                while (offset < pcm.bytes.size) {
                    currentCoroutineContext().ensureActive()
                    check(nanoTime() - progressAt < timeoutNs) { "Timed out waiting for FLAC input" }

                    val timestampUs = submittedSamples * 1_000_000L / spec.sampleRate
                    val queued = submit(codec, pcm.bytes, offset, bytesPerFrame, timestampUs)
                    if (queued > 0) {
                        offset += queued
                        submittedSamples += queued / bytesPerFrame
                        progressAt = nanoTime()
                    }

                    val ended = drain(codec, nanoTime, timeoutNs) { packet -> emit(packet) }
                    check(!ended) { "FLAC output ended before input EOS" }
                }
            }

            // 입력이 정상 종료한 경우에만 EOS를 제출하고 남은 패킷을 모두 회수합니다.
            // EOS 규격: https://developer.android.com/reference/android/media/MediaCodec#end-of-stream-handling
            val startedAt = nanoTime()
            val endTimestampUs = submittedSamples * 1_000_000L / spec.sampleRate
            var inputEnded = false
            var outputEnded = false

            while (!outputEnded) {
                currentCoroutineContext().ensureActive()
                check(nanoTime() - startedAt < timeoutNs) { "Timed out waiting for FLAC EOS" }

                if (!inputEnded) {
                    val index = codec.dequeueInput(WAIT_US)
                    if (index >= 0) {
                        codec.queueInput(index, 0, endTimestampUs, end = true)
                        inputEnded = true
                    }
                }

                val output = codec.dequeueOutput(if (inputEnded) WAIT_US else 0)
                if (output is FlacOutput.Packet) {
                    outputEnded = output.end
                    check(!outputEnded || inputEnded) { "FLAC output ended before input EOS" }
                    emit(output)
                }
            }
        }
    }.catch { failure ->
        if (failure is CancellationException || failure is RecorderException || failure is IllegalArgumentException) throw failure
        throw RecorderException(RecorderException.Reason.Encoding, failure)
    }

    private fun submit(
        codec: FlacCodec,
        pcm: ByteArray,
        offset: Int,
        bytesPerFrame: Int,
        timestampUs: Long,
    ): Int {
        val index = codec.dequeueInput(WAIT_US)
        if (index < 0) return 0

        val buffer = checkNotNull(codec.inputBuffer(index)) { "FLAC input buffer is missing" }
        buffer.clear()

        val available = minOf(buffer.remaining(), pcm.size - offset)
        val aligned = available - available % bytesPerFrame
        check(aligned > 0) { "FLAC input buffer cannot hold a sample frame" }

        buffer.put(pcm, offset, aligned)
        codec.queueInput(index, aligned, timestampUs, end = false)
        return aligned
    }

    private suspend fun drain(
        codec: FlacCodec,
        nanoTime: () -> Long,
        timeoutNs: Long,
        onPacket: suspend (FlacOutput.Packet) -> Unit,
    ): Boolean {
        val startedAt = nanoTime()
        var ended = false

        while (!ended) {
            currentCoroutineContext().ensureActive()
            check(nanoTime() - startedAt < timeoutNs) { "Timed out draining FLAC output" }

            when (val output = codec.dequeueOutput(0)) {
                FlacOutput.Unavailable -> return false
                FlacOutput.FormatChanged -> Unit
                is FlacOutput.Packet -> {
                    onPacket(output)
                    ended = output.end
                }
            }
        }
        return ended
    }

    private const val WAIT_US = 10_000L
}

internal interface FlacCodec : AutoCloseable {
    fun dequeueInput(timeoutUs: Long): Int
    fun inputBuffer(index: Int): ByteBuffer?
    fun queueInput(index: Int, size: Int, timestampUs: Long, end: Boolean)
    fun dequeueOutput(timeoutUs: Long): FlacOutput
}

internal sealed interface FlacOutput {
    data object Unavailable : FlacOutput
    data object FormatChanged : FlacOutput
    class Packet(val bytes: ByteArray, val end: Boolean = false, val codecConfig: Boolean = false) : FlacOutput
}

internal data class SampleSpec(val sampleRate: Int, val channelCount: Int, val bitsPerSample: Int)

/**
 * 안드로이드 버퍼의 획득과 반환은 이 어댑터 안에서 처리합니다.
 */
internal class MediaCodecFlac(spec: SampleSpec) : FlacCodec {
    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_FLAC)
    private val info = MediaCodec.BufferInfo()

    init {
        try {
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_FLAC, spec.sampleRate, spec.channelCount,
            ).apply { setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) }

            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } catch (failure: Throwable) {
            // 초기화 실패 시 네이티브 자원을 회수하고, 정리 실패도 원인에 함께 남깁니다.
            try {
                codec.release()
            } catch (cleanup: Throwable) {
                failure.addSuppressed(cleanup)
            }
            throw failure
        }
    }

    override fun dequeueInput(timeoutUs: Long): Int = codec.dequeueInputBuffer(timeoutUs)
    override fun inputBuffer(index: Int): ByteBuffer? = codec.getInputBuffer(index)

    override fun queueInput(index: Int, size: Int, timestampUs: Long, end: Boolean) {
        val flags = if (end) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
        codec.queueInputBuffer(index, 0, size, timestampUs, flags)
    }

    override fun dequeueOutput(timeoutUs: Long): FlacOutput {
        val index = codec.dequeueOutputBuffer(info, timeoutUs)
        return when {
            index == MediaCodec.INFO_TRY_AGAIN_LATER -> FlacOutput.Unavailable
            index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> FlacOutput.FormatChanged
            index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> FlacOutput.FormatChanged
            index >= 0 -> readPacket(index)
            else -> throw RecorderException(
                RecorderException.Reason.Encoding,
                IllegalStateException("Unexpected FLAC output index: $index"),
            )
        }
    }

    private fun readPacket(index: Int): FlacOutput.Packet {
        // 출력 버퍼의 대여 범위를 벗어날 때 복사 성공 여부와 관계없이 반환합니다.
        return AutoCloseable { codec.releaseOutputBuffer(index, false) }.use {
            val bytes = ByteArray(info.size)

            if (bytes.isNotEmpty()) {
                val buffer = checkNotNull(codec.getOutputBuffer(index)) { "FLAC output buffer is missing: $index" }
                val startOffset = info.offset
                val endOffset = startOffset + info.size

                buffer.position(startOffset)
                buffer.limit(endOffset)
                buffer.get(bytes)
            }

            // 코덱 설정 출력에 FLAC 스트림 헤더가 포함되므로 함께 저장합니다.
            val outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            val containsHeader = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0

            FlacOutput.Packet(bytes, end = outputEnded, codecConfig = containsHeader)
        }
    }

    override fun close() {
        // 중지가 실패해도 해제하며, 두 실패가 겹치면 use가 정리 실패를 함께 보존합니다.
        AutoCloseable { codec.release() }.use {
            codec.stop()
        }
    }
}
