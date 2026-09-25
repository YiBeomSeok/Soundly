package com.soundly.sdk.recording.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow

/**
 * PCM 수집 → 용량 확인·복사 → 진행량·결과 전달만 맡으며, 녹음 상태와 사용자 조작을 알지 못합니다.
 */
class MemoryOutput(private val maxBytes: Long) : RecordingOutput {
    init {
        require(maxBytes > 0) { "Memory limit must be positive" }
    }

    override fun write(source: Flow<PcmChunk>, config: RecordingConfig): Flow<OutputEvent> = flow {
        val chunks = mutableListOf<PcmChunk>()
        val bytesPerFrame = config.channelCount * Short.SIZE_BYTES
        var bytes = 0L

        source.filter { it.bytes.isNotEmpty() }.collect { chunk ->
            if (chunk.bytes.size % bytesPerFrame != 0) throw RecorderException(RecorderException.Reason.InvalidPcm)
            if (chunk.bytes.size > maxBytes - bytes) throw RecorderException(RecorderException.Reason.MemoryLimit)

            chunks += PcmChunk(chunk.bytes.copyOf())
            bytes += chunk.bytes.size

            emit(OutputEvent.Progress(RecordingProgress(bytes / bytesPerFrame, config.sampleRateHz)))
        }

        val progress = RecordingProgress(bytes / bytesPerFrame, config.sampleRateHz)
        val result = MemoryRecording(chunks.toList(), config, progress)

        emit(OutputEvent.Finished(result))
    }
}
