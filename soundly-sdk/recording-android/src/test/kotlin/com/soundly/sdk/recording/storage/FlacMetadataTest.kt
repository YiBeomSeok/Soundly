package com.soundly.sdk.recording.storage

import com.soundly.sdk.recording.api.RecordingTarget

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class FlacMetadataTest {
    @Test
    fun `STREAMINFO의 형식 필드를 보존하면서 36비트 표본 수를 갱신한다`() {
        val file = File.createTempFile("flac-header", ".flac")
        try {
            val header = ByteArray(42)
            "fLaC".toByteArray().copyInto(header)
            header[4] = 0x80.toByte()
            header[7] = 34
            byteArrayOf(0x12, 0x34, 0x56, 0x70).copyInto(header, 18)
            file.writeBytes(header)
            val samples = 0x812345678L
            FileRecordingOutput(RecordingTarget.flac(file)).patchStreamInfo(file, samples)
            val result = file.readBytes()
            assertArrayEquals(header.copyOfRange(0, 21), result.copyOfRange(0, 21))
            assertEquals(0x78.toByte(), result[21])
            assertArrayEquals(byteArrayOf(0x12, 0x34, 0x56, 0x78), result.copyOfRange(22, 26))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `STREAMINFO에 실제 프레임 최소 최대 크기를 24비트로 기록한다`() {
        val file = File.createTempFile("flac-header", ".flac")
        try {
            val header = ByteArray(42)
            "fLaC".toByteArray().copyInto(header)
            header[4] = 0x80.toByte()
            header[7] = 34
            file.writeBytes(header)
            FileRecordingOutput(RecordingTarget.flac(file)).patchStreamInfo(file, 4097, 11, 16405)
            assertArrayEquals(byteArrayOf(0, 0, 11, 0, 64, 21), file.readBytes().copyOfRange(12, 18))
            assertThrows(IllegalArgumentException::class.java) {
                FileRecordingOutput(RecordingTarget.flac(file)).patchStreamInfo(file, 4097, 100, 99)
            }
            assertThrows(IllegalArgumentException::class.java) {
                FileRecordingOutput(RecordingTarget.flac(file)).patchStreamInfo(file, 4097, 0, 0x1000000)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `잘못된 파일이나 잘린 헤더를 마감 성공으로 처리하지 않는다`() {
        val file = File.createTempFile("flac-header", ".flac")
        try {
            file.writeBytes(ByteArray(10))
            assertThrows(IllegalArgumentException::class.java) { FileRecordingOutput(RecordingTarget.flac(file)).patchStreamInfo(file, 1) }
            file.writeBytes(ByteArray(42))
            assertThrows(IllegalArgumentException::class.java) { FileRecordingOutput(RecordingTarget.flac(file)).patchStreamInfo(file, 1) }
        } finally {
            file.delete()
        }
    }
}
