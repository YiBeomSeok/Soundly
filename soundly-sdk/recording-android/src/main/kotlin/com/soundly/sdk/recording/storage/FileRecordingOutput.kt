package com.soundly.sdk.recording.storage

import com.soundly.sdk.recording.api.OutputEvent
import com.soundly.sdk.recording.api.PcmChunk
import com.soundly.sdk.recording.api.RecordedAudio
import com.soundly.sdk.recording.api.RecorderException
import com.soundly.sdk.recording.api.RecordingConfig
import com.soundly.sdk.recording.api.RecordingFormat
import com.soundly.sdk.recording.api.RecordingOutput
import com.soundly.sdk.recording.api.RecordingProgress
import com.soundly.sdk.recording.api.RecordingTarget
import com.soundly.sdk.recording.container.WavHeader
import com.soundly.sdk.recording.encode.FlacCodec
import com.soundly.sdk.recording.encode.FlacEncoderMediaCodec
import com.soundly.sdk.recording.encode.MediaCodecFlac
import com.soundly.sdk.recording.encode.SampleSpec
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.transform

/**
 * 파일 준비 → PCM 기록 → 마감·정리 → 결과 전달 순서로 실행하며, 녹음 제어나 입력 장치를 다루지 않습니다.
 */
class FileRecordingOutput internal constructor(
    private val target: RecordingTarget,
    private val createCodec: (SampleSpec) -> FlacCodec,
) : RecordingOutput {
    constructor(target: RecordingTarget) : this(target, ::MediaCodecFlac)

    companion object {
        /** Call once at process startup, before any recording output can begin. */
        fun deleteAbandonedTemporaryFiles(directory: File) {
            if (!directory.isDirectory) return

            Files.newDirectoryStream(directory.toPath(), ".soundly-*.part").use { entries ->
                for (path in entries) {
                    if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        Files.delete(path)
                    }
                }
            }
        }
    }

    override fun write(source: Flow<PcmChunk>, config: RecordingConfig): Flow<OutputEvent> = flow {
        val requestedFile = target.file.canonicalFile
        val wavFallbackFile = File(requestedFile.parentFile, "${requestedFile.nameWithoutExtension}.wav")
        val candidates = if (target.format == RecordingFormat.Auto) {
            listOf(requestedFile, wavFallbackFile)
        } else {
            listOf(requestedFile)
        }

        // 경로와 임시 파일은 출력 수집 범위에서만 소유합니다.
        val result = RecordingPathReservations.acquire(candidates).use {
            val directory = checkNotNull(requestedFile.parentFile)
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create recording directory" }
            val temporary = File.createTempFile(".soundly-", ".part", directory)

            AutoCloseable { Files.deleteIfExists(temporary.toPath()) }.use {
                var inputStarted = false
                var actualFormat = if (target.format == RecordingFormat.Auto) RecordingFormat.Flac else target.format
                val pcm = source
                    .onStart { inputStarted = true }
                    .filter { it.bytes.isNotEmpty() }
                val onWritten: suspend (Long) -> Unit = { samples ->
                    emit(OutputEvent.Progress(RecordingProgress(samples, config.sampleRateHz)))
                }

                // 입력을 수집하기 전의 코덱 초기화 실패에만 파일 형식을 전환합니다.
                val samples = flow {
                    val writtenSamples = if (actualFormat == RecordingFormat.Wav) {
                        writeWav(temporary, config, pcm, onWritten)
                    } else {
                        writeFlac(temporary, config, pcm, onWritten)
                    }
                    emit(writtenSamples)
                }.retryWhen { failure, _ ->
                    val initializationFailure = !inputStarted && failure is RecorderException &&
                        failure.reason == RecorderException.Reason.Encoding
                    val canFallback = target.format == RecordingFormat.Auto &&
                        actualFormat == RecordingFormat.Flac && initializationFailure

                    if (canFallback) actualFormat = RecordingFormat.Wav
                    canFallback
                }.single()

                val finalFile = if (target.format == RecordingFormat.Auto && actualFormat == RecordingFormat.Wav) {
                    wavFallbackFile
                } else {
                    requestedFile
                }

                // 스트림·코덱 종료와 헤더 보정 이후에만 최종 파일을 공개합니다.
                currentCoroutineContext().ensureActive()
                Files.move(temporary.toPath(), finalFile.toPath())
                RecordedAudio(finalFile, actualFormat, config, RecordingProgress(samples, config.sampleRateHz))
            }
        }

        emit(OutputEvent.Finished(result))
    }.flowOn(Dispatchers.IO)

    private suspend fun writeFlac(
        file: File,
        config: RecordingConfig,
        source: Flow<PcmChunk>,
        onWritten: suspend (Long) -> Unit,
    ): Long {
        var samples = 0L
        var minimumFrameBytes = 0
        var maximumFrameBytes = 0
        val spec = SampleSpec(config.sampleRateHz, config.channelCount, 16)
        val bytesPerFrame = config.channelCount * Short.SIZE_BYTES

        // 인코더가 청크를 처리하고 반환한 뒤에만 진행량을 갱신합니다.
        val pcm = source.transform { chunk ->
            emit(chunk)
            samples += chunk.bytes.size / bytesPerFrame
            onWritten(samples)
        }

        FileOutputStream(file).use { stream ->
            FlacEncoderMediaCodec.encode(pcm, spec, createCodec).collect { packet ->
                stream.write(packet.bytes)

                if (!packet.codecConfig && packet.bytes.isNotEmpty()) {
                    val size = packet.bytes.size
                    minimumFrameBytes = if (minimumFrameBytes == 0) size else minOf(minimumFrameBytes, size)
                    maximumFrameBytes = maxOf(maximumFrameBytes, size)
                }
            }
        }

        patchStreamInfo(file, samples, minimumFrameBytes, maximumFrameBytes)
        return samples
    }

    private suspend fun writeWav(
        file: File,
        config: RecordingConfig,
        source: Flow<PcmChunk>,
        onWritten: suspend (Long) -> Unit,
    ): Long {
        val header = WavHeader.createProvisionalHeader(WavHeader.Params(config.sampleRateHz, config.channelCount))
        val maximumPcmBytes = 0xFFFF_FFFFL - (WavHeader.HEADER_SIZE_BYTES - 8)
        val bytesPerFrame = config.channelCount * Short.SIZE_BYTES
        var bytes = 0L

        // 같은 파일 범위에서 임시 헤더, PCM, 확정 헤더를 순서대로 기록합니다.
        RandomAccessFile(file, "rw").use { stream ->
            stream.setLength(0)
            stream.write(header)
            source.collect { chunk ->
                require(chunk.bytes.size % bytesPerFrame == 0) { "PCM chunk must contain whole sample frames" }
                require(chunk.bytes.size <= maximumPcmBytes - bytes) { "PCM data exceeds WAV size limit" }

                stream.write(chunk.bytes)
                bytes += chunk.bytes.size
                onWritten(bytes / bytesPerFrame)
            }
            WavHeader.updateSizesInPlace(header, bytes)
            stream.seek(0)
            stream.write(header)
        }

        return bytes / bytesPerFrame
    }

    /**
     * STREAMINFO 규격: https://www.rfc-editor.org/rfc/rfc9639.html#section-8.2
     */
    internal fun patchStreamInfo(
        file: File,
        totalSamples: Long,
        minFrameBytes: Int = 0,
        maxFrameBytes: Int = 0,
    ) {
        require(minFrameBytes in 0..0xFFFFFF && maxFrameBytes in minFrameBytes..0xFFFFFF) {
            "FLAC frame size out of range"
        }
        require(totalSamples in 0..((1L shl 36) - 1)) { "totalSamples out of range" }
        RandomAccessFile(file, "rw").use { stream ->
            require(stream.length() >= 4 + 4 + 34) { "FLAC STREAMINFO is missing or truncated" }
            val signature = ByteArray(4)
            stream.readFully(signature)
            require(signature.contentEquals("fLaC".toByteArray(Charsets.US_ASCII))) { "Invalid FLAC stream marker" }

            val header = ByteArray(4)
            stream.readFully(header)
            val type = header[0].toInt() and 0x7F
            val length = ((header[1].toInt() and 0xFF) shl 16) or
                ((header[2].toInt() and 0xFF) shl 8) or
                (header[3].toInt() and 0xFF)
            require(type == 0 && length == 34) { "Invalid FLAC STREAMINFO block" }

            // 프레임 크기 미지정은 규격상 허용되지만, 일부 추출기의 큰 프레임 읽기가 실패합니다.
            if (maxFrameBytes > 0) {
                stream.seek(12)
                for (size in intArrayOf(minFrameBytes, maxFrameBytes)) {
                    stream.write(byteArrayOf((size ushr 16).toByte(), (size ushr 8).toByte(), size.toByte()))
                }
            }

            val streamInfoStart = 8
            val fieldOffset = streamInfoStart + 10
            stream.seek(fieldOffset.toLong())
            val formatAndSamplesBytes = ByteArray(Long.SIZE_BYTES)
            stream.readFully(formatAndSamplesBytes)

            // 상위 28비트의 오디오 형식을 보존하고 하위 36비트의 샘플 수만 갱신합니다.
            val formatAndSamples = ByteBuffer.wrap(formatAndSamplesBytes).getLong()
            val updated = (formatAndSamples and (-1L shl 36)) or totalSamples
            ByteBuffer.wrap(formatAndSamplesBytes).putLong(updated)
            stream.seek(fieldOffset.toLong())
            stream.write(formatAndSamplesBytes)
        }
    }
}

/**
 * 동시에 기록 중인 파일 경로만 점유하며, 수집 범위가 끝나면 점유를 해제합니다.
 */
private object RecordingPathReservations {
    private val active = mutableSetOf<String>()

    fun acquire(files: List<File>): AutoCloseable {
        val paths = files.map { it.canonicalPath }.toSet()

        synchronized(active) {
            check(paths.none { it in active }) { "Recording output is already in use" }
            check(files.none { it.exists() }) { "Recording output already exists" }
            active.addAll(paths)
        }

        return AutoCloseable { synchronized(active) { active.removeAll(paths) } }
    }
}
