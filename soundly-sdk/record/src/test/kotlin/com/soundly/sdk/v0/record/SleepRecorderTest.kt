package com.soundly.sdk.v0.record

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

class SleepRecorderTest : FunSpec({
    
    test("RecordingState should have correct values") {
        RecordingState.values().size shouldBe 4
        RecordingState.IDLE shouldNotBe null
        RecordingState.RECORDING shouldNotBe null
        RecordingState.STOPPING shouldNotBe null
        RecordingState.ERROR shouldNotBe null
    }
    
    test("RecordingConfig should have default values") {
        val config = RecordingConfig()
        config.sampleRate shouldBe 44100
        config.channelConfig shouldBe android.media.AudioFormat.CHANNEL_IN_MONO
        config.audioFormat shouldBe android.media.AudioFormat.ENCODING_PCM_16BIT
        config.bufferSizeFactor shouldBe 2
    }
    
    test("RecordingResult should have correct types") {
        val success = RecordingResult.Success
        val error = RecordingResult.Error("Test error")
        
        success shouldNotBe null
        error shouldNotBe null
        error.message shouldBe "Test error"
    }
})
