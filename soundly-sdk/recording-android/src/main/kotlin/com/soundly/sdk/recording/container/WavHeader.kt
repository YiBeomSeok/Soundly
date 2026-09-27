package com.soundly.sdk.recording.container

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PCM WAV 규격(3-22~3-26절): https://www.mmsp.ece.mcgill.ca/Documents/AudioFormats/WAVE/Docs/riffmci.pdf
 */
internal object WavHeader {
    private const val RIFF_MAGIC = "RIFF"
    private const val WAVE_MAGIC = "WAVE"
    private const val FMT_MAGIC = "fmt "
    private const val DATA_MAGIC = "data"

    /**
     * PCM 형식을 나타내는 코드입니다.
     */
    private const val AUDIO_FORMAT_PCM: Short = 0x0001

    /**
     * 데이터 앞에 기록하는 고정 헤더의 바이트 수입니다.
     */
    const val HEADER_SIZE_BYTES: Int = 44

    data class Params(
        val sampleRateHz: Int,
        val channelCount: Int,
        val bitsPerSample: Int = 16,
    ) {
        init {
            require(sampleRateHz in 8_000..48_000) { "sampleRateHz must be in [8000, 48000]" }
            require(channelCount == 1 || channelCount == 2) { "channelCount must be 1 or 2" }
            require(bitsPerSample == 16) { "Only 16-bit PCM supported" }
        }
    }

    /**
     * 데이터 길이를 0으로 둔 44바이트 헤더를 만들고, 마감할 때 크기를 갱신합니다.
     */
    fun createProvisionalHeader(params: Params): ByteArray {
        val bytesPerSample = params.bitsPerSample / 8
        val byteRate = params.sampleRateHz * params.channelCount * bytesPerSample
        val blockAlign = params.channelCount * bytesPerSample

        val buffer = ByteBuffer.allocate(HEADER_SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            putAscii(RIFF_MAGIC)
            putInt(0)
            putAscii(WAVE_MAGIC)

            putAscii(FMT_MAGIC)
            putInt(16)
            putShort(AUDIO_FORMAT_PCM)
            putShort(params.channelCount.toShort())
            putInt(params.sampleRateHz)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(params.bitsPerSample.toShort())

            putAscii(DATA_MAGIC)
            putInt(0)
        }
        return buffer.array()
    }

    /**
     * RIFF와 데이터 크기를 갱신하며, 32비트 부호 없는 정수 범위를 검사합니다.
     */
    fun updateSizesInPlace(header: ByteArray, totalPcmBytes: Long) {
        require(header.size == HEADER_SIZE_BYTES) { "header must be 44 bytes" }
        require(totalPcmBytes >= 0) { "totalPcmBytes must be >= 0" }

        val maxDataSize = 0xFFFF_FFFFL - 36
        require(totalPcmBytes <= maxDataSize) { "WAV data exceeds 4GB limit" }

        val riffChunkSize = (HEADER_SIZE_BYTES - 8 + totalPcmBytes).toInt()
        val dataChunkSize = totalPcmBytes.toInt()

        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(4)
        buffer.putInt(riffChunkSize)
        buffer.position(40)
        buffer.putInt(dataChunkSize)
    }

    private fun ByteBuffer.putAscii(text: String) {
        put(text.toByteArray(Charsets.US_ASCII))
    }
}
