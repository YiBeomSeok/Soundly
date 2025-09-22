package com.soundly.sdk.v0.media.recorder.container

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure functions for generating and updating WAV headers (RIFF/WAVE, PCM format).
 *
 * References: Microsoft RIFF WAVE file format. This implementation targets
 * 16-bit little-endian PCM with 1 or 2 channels and 8k-48k sample rate.
 */
object WavHeader {
    private const val RIFF_MAGIC = "RIFF"
    private const val WAVE_MAGIC = "WAVE"
    private const val FMT_MAGIC = "fmt "
    private const val DATA_MAGIC = "data"

    /** Standard PCM format code. */
    private const val AUDIO_FORMAT_PCM: Short = 0x0001

    /** Size in bytes of the fixed WAV header written before data. */
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
     * Creates a 44-byte WAV header with placeholder sizes for unknown data length.
     * The file size and data chunk size fields are filled with zero and can be
     * updated later via [updateSizesInPlace].
     */
    fun createProvisionalHeader(params: Params): ByteArray {
        val bytesPerSample = params.bitsPerSample / 8
        val byteRate = params.sampleRateHz * params.channelCount * bytesPerSample
        val blockAlign = params.channelCount * bytesPerSample

        val buffer = ByteBuffer.allocate(HEADER_SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        // ChunkID "RIFF"
        buffer.putAscii(RIFF_MAGIC)
        // ChunkSize (placeholder 0 for now)
        buffer.putInt(0)
        // Format "WAVE"
        buffer.putAscii(WAVE_MAGIC)

        // Subchunk1ID "fmt "
        buffer.putAscii(FMT_MAGIC)
        // Subchunk1Size 16 for PCM
        buffer.putInt(16)
        // AudioFormat 1 for PCM
        buffer.putShort(AUDIO_FORMAT_PCM)
        // NumChannels
        buffer.putShort(params.channelCount.toShort())
        // SampleRate
        buffer.putInt(params.sampleRateHz)
        // ByteRate
        buffer.putInt(byteRate)
        // BlockAlign
        buffer.putShort(blockAlign.toShort())
        // BitsPerSample
        buffer.putShort(params.bitsPerSample.toShort())

        // Subchunk2ID "data"
        buffer.putAscii(DATA_MAGIC)
        // Subchunk2Size (placeholder 0)
        buffer.putInt(0)

        return buffer.array()
    }

    /**
     * Updates the RIFF ChunkSize and data Subchunk2Size in-place.
     * @param header must be a 44-byte array returned from [createProvisionalHeader]
     * @param totalPcmBytes number of PCM data bytes written after the header
     * @throws IllegalArgumentException if sizes exceed 32-bit unsigned limit (WAV ~4 GB)
     */
    fun updateSizesInPlace(header: ByteArray, totalPcmBytes: Long) {
        require(header.size == HEADER_SIZE_BYTES) { "header must be 44 bytes" }
        require(totalPcmBytes >= 0) { "totalPcmBytes must be >= 0" }

        val maxDataSize = 0xFFFF_FFFFL // 4,294,967,295 (~4 GB)
        require(totalPcmBytes <= maxDataSize) { "WAV data exceeds 4GB limit" }

        val riffChunkSize = (HEADER_SIZE_BYTES - 8 + totalPcmBytes).toInt()
        val dataChunkSize = totalPcmBytes.toInt()

        // Offsets in standard 44-byte header
        // RIFF ChunkSize at byte offset 4 (4 bytes)
        // data Subchunk2Size at byte offset 40 (4 bytes)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(4)
        buffer.putInt(riffChunkSize)
        buffer.position(40)
        buffer.putInt(dataChunkSize)
    }
}

internal fun ByteBuffer.putAscii(text: String) {
    for (i in text.indices) {
        this.put(text[i].code.toByte())
    }
}


