package com.soundly.sdk.v0.record

import com.soundly.sdk.v0.record.api.RecordingConfig
import io.kotest.assertions.throwables.shouldThrow
import org.junit.Test

class RecordingConfigTest {

    @Test
    fun invalidSampleRate_throws() {
        shouldThrow<IllegalArgumentException> { RecordingConfig(sampleRateHz = 7999) }
        shouldThrow<IllegalArgumentException> { RecordingConfig(sampleRateHz = 48001) }
    }

    @Test
    fun invalidChannelCount_throws() {
        shouldThrow<IllegalArgumentException> { RecordingConfig(channelCount = 0) }
        shouldThrow<IllegalArgumentException> { RecordingConfig(channelCount = 3) }
    }
}


