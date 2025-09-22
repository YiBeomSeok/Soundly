package com.soundly.sdk.v0.media.recorder.container

import io.kotest.matchers.shouldBe
import org.junit.Test

class WavHeaderTest {

    @Test
    fun `샘플레이트 44100Hz, 채널 1로 임시 헤더 생성 시 마커는 RIFF-WAVE-fmt-data 이어야 한다`() {
        // Given
        val params = WavHeader.Params(sampleRateHz = 44100, channelCount = 1)

        // When
        val header = WavHeader.createProvisionalHeader(params)

        // Then
        header.size shouldBe 44
        String(header.copyOfRange(0, 4)) shouldBe "RIFF"
        String(header.copyOfRange(8, 12)) shouldBe "WAVE"
        String(header.copyOfRange(12, 16)) shouldBe "fmt "
        String(header.copyOfRange(36, 40)) shouldBe "data"
    }

    @Test
    fun `샘플레이트 16000Hz, 채널 2로 생성 후 PCM 1024바이트 갱신 시 헤더 사이즈는 1060, 데이터 사이즈는 1024이어야 한다`() {
        // Given
        val header = WavHeader.createProvisionalHeader(
            WavHeader.Params(sampleRateHz = 16000, channelCount = 2)
        )

        // When
        WavHeader.updateSizesInPlace(header, totalPcmBytes = 1024)

        fun leIntAt(offset: Int): Int {
            val b0 = header[offset].toInt() and 0xFF
            val b1 = header[offset + 1].toInt() and 0xFF
            val b2 = header[offset + 2].toInt() and 0xFF
            val b3 = header[offset + 3].toInt() and 0xFF
            return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }

        // Then
        leIntAt(4) shouldBe 36 + 1024
        leIntAt(40) shouldBe 1024
    }

    @Test
    fun `샘플레이트 44100Hz, 채널 1 헤더에서 PCM 크기가 4GB+1일 경우 예외 메시지는 WAV data exceeds 4GB limit 이어야 한다`() {
        // Given
        val header = WavHeader.createProvisionalHeader(
            WavHeader.Params(sampleRateHz = 44100, channelCount = 1)
        )
        val tooLarge = 0x1_0000_0000L // 4GB + 1

        // When
        val error = runCatching {
            WavHeader.updateSizesInPlace(header, tooLarge)
        }.exceptionOrNull()

        // Then
        error!!.message shouldBe "WAV data exceeds 4GB limit"
    }
}