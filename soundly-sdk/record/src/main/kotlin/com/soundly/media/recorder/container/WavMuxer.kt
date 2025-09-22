package com.soundly.media.recorder.container

import com.soundly.media.recorder.io.Sink

class WavMuxer(
    private val sampleRate: Int,
    private val channelCount: Int,
    private val bitsPerSample: Int,
) : ContainerWriter {

    private var dataSize: Long = 0

    override suspend fun writeHeader(sink: Sink) {
        // Provisional header with placeholder sizes. Real finalize requires random access.
        val header = provisionalHeader()
        sink.write(header)
    }

    override suspend fun writePayload(sink: Sink, payload: ByteArray) {
        dataSize += payload.size
        sink.write(payload)
    }

    override suspend fun finalize(sink: Sink) {
        // Note: Proper WAV finalize needs random access to patch header sizes at [4] and [40].
        // Our generic Sink lacks seek; in production, use a FileSinkWithRandomAccess to patch.
        // For now, just flush.
        sink.flush()
    }

    private fun provisionalHeader(): ByteArray {
        val byteRate = sampleRate * channelCount * bitsPerSample / 8
        val blockAlign = channelCount * bitsPerSample / 8
        val header = ByteArray(44)
        fun putStr(pos: Int, s: String) { s.toByteArray().copyInto(header, pos) }
        fun putLE32(pos: Int, v: Long) {
            header[pos + 0] = (v and 0xFF).toByte()
            header[pos + 1] = ((v shr 8) and 0xFF).toByte()
            header[pos + 2] = ((v shr 16) and 0xFF).toByte()
            header[pos + 3] = ((v shr 24) and 0xFF).toByte()
        }
        fun putLE16(pos: Int, v: Int) {
            header[pos + 0] = (v and 0xFF).toByte()
            header[pos + 1] = ((v shr 8) and 0xFF).toByte()
        }
        putStr(0, "RIFF")
        putLE32(4, 36) // placeholder, should be 36 + dataSize on finalize
        putStr(8, "WAVE")
        putStr(12, "fmt ")
        putLE32(16, 16)
        putLE16(20, 1)
        putLE16(22, channelCount)
        putLE32(24, sampleRate.toLong())
        putLE32(28, byteRate.toLong())
        putLE16(32, blockAlign)
        putLE16(34, bitsPerSample)
        putStr(36, "data")
        putLE32(40, 0) // placeholder
        return header
    }
}
