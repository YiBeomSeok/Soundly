package com.soundly.sdk.v0.media.recorder.util

import java.io.File
import java.io.RandomAccessFile

object FlacFinalizer {
    fun patchTotalSamples(file: File, totalSamples: Long) {
        require(totalSamples in 0..((1L shl 36) - 1)) { "totalSamples out of range" }
        RandomAccessFile(file, "rw").use { raf ->
            if (raf.length() < 4 + 4 + 34) return
            val sig = ByteArray(4)
            raf.readFully(sig)
            if (!(sig[0] == 'f'.code.toByte() && sig[1] == 'L'.code.toByte()
                        && sig[2] == 'a'.code.toByte() && sig[3] == 'C'.code.toByte())) return

            val header = ByteArray(4)
            raf.readFully(header)
            val type = header[0].toInt() and 0x7F
            val length = ((header[1].toInt() and 0xFF) shl 16) or
                         ((header[2].toInt() and 0xFF) shl 8) or
                         (header[3].toInt() and 0xFF)
            if (type != 0 || length != 34) return

            val streamInfoStart = 8
            val fieldOffset = streamInfoStart + 10
            raf.seek(fieldOffset.toLong())
            val buf = ByteArray(8)
            raf.readFully(buf)

            var v = 0L
            for (i in 0 until 8) v = (v shl 8) or (buf[i].toLong() and 0xFF)
            val top28 = v ushr 36
            val newV = (top28 shl 36) or (totalSamples and ((1L shl 36) - 1))

            for (i in 7 downTo 0) {
                buf[i] = (newV ushr ((7 - i) * 8) and 0xFF).toByte()
            }
            raf.seek(fieldOffset.toLong())
            raf.write(buf)
        }
    }
}


