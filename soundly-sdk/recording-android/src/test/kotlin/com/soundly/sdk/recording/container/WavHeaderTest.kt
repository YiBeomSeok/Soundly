package com.soundly.sdk.recording.container

import io.kotest.matchers.shouldBe
import org.junit.Test

class WavHeaderTest {

    @Test
    fun `샘플레이트 44100Hz, 채널 1로 임시 헤더 생성 시 마커는 RIFF-WAVE-fmt-data 이어야 한다`() {
        // 준비
        val params = WavHeader.Params(sampleRateHz = 44100, channelCount = 1)

        // 실행
        val header = WavHeader.createProvisionalHeader(params)

        // 검증
        header.size shouldBe 44
        String(header.copyOfRange(0, 4)) shouldBe "RIFF"
        String(header.copyOfRange(8, 12)) shouldBe "WAVE"
        String(header.copyOfRange(12, 16)) shouldBe "fmt "
        String(header.copyOfRange(36, 40)) shouldBe "data"
    }

    @Test
    fun `샘플레이트 16000Hz, 채널 2로 생성 후 PCM 1024바이트 갱신 시 헤더 사이즈는 1060, 데이터 사이즈는 1024이어야 한다`() {
        // 준비
        val header = WavHeader.createProvisionalHeader(
            WavHeader.Params(sampleRateHz = 16000, channelCount = 2)
        )

        // 실행
        WavHeader.updateSizesInPlace(header, totalPcmBytes = 1024)

        fun leIntAt(offset: Int): Int {
            val b0 = header[offset].toInt() and 0xFF
            val b1 = header[offset + 1].toInt() and 0xFF
            val b2 = header[offset + 2].toInt() and 0xFF
            val b3 = header[offset + 3].toInt() and 0xFF
            return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }

        // 검증
        leIntAt(4) shouldBe 36 + 1024
        leIntAt(40) shouldBe 1024
    }

    @Test
    fun `샘플레이트 44100Hz, 채널 1 헤더에서 PCM 크기가 4GB+1일 경우 예외 메시지는 WAV data exceeds 4GB limit 이어야 한다`() {
        // 준비
        val header = WavHeader.createProvisionalHeader(
            WavHeader.Params(sampleRateHz = 44100, channelCount = 1)
        )
        val tooLarge = 0x1_0000_0000L // 4GB보다 1바이트 큽니다.

        // 실행
        val error = runCatching {
            WavHeader.updateSizesInPlace(header, tooLarge)
        }.exceptionOrNull()

        // 검증
        error!!.message shouldBe "WAV data exceeds 4GB limit"
    }
    @Test
    fun `데이터 크기가 32비트 안이어도 RIFF 전체 크기가 넘치면 거부한다`() {
        val header = WavHeader.createProvisionalHeader(WavHeader.Params(44100, 1))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            WavHeader.updateSizesInPlace(header, 0xFFFF_FFFFL - 35)
        }
    }
}