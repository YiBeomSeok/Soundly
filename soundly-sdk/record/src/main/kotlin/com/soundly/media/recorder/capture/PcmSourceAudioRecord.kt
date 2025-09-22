package com.soundly.media.recorder.capture

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.soundly.media.recorder.api.SampleSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class PcmSourceAudioRecord(
    override val spec: SampleSpec,
) : PcmSource {

    private var audioRecord: AudioRecord? = null

    @SuppressLint("MissingPermission")
    private fun ensureAudioRecord(): AudioRecord {
        val channelConfig =
            if (spec.channelCount == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
        val encoding = when (spec.bitsPerSample) {
            16 -> AudioFormat.ENCODING_PCM_16BIT
            8 -> AudioFormat.ENCODING_PCM_8BIT
            32 -> AudioFormat.ENCODING_PCM_FLOAT
            else -> AudioFormat.ENCODING_PCM_16BIT
        }
        val minSize = AudioRecord.getMinBufferSize(spec.sampleRate, channelConfig, encoding)
        val buf = (minSize * 2).coerceAtLeast(4096)
        return AudioRecord(
            MediaRecorder.AudioSource.MIC,
            spec.sampleRate,
            channelConfig,
            encoding,
            buf
        ).also {
            require(it.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord init failed" }
        }
    }

    override fun start(): Flow<ByteArray> = flow {
        val ar = (audioRecord ?: ensureAudioRecord().also { audioRecord = it })
        val bufferSize = maxOf(4096, ar.bufferSizeInFrames * spec.channelCount)
        val buf = ByteArray(bufferSize)
        ar.startRecording()
        try {
            while (ar.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = ar.read(buf, 0, buf.size)
                if (read > 0) {
                    emit(buf.copyOf(read))
                }
                delay(10)
            }
        } catch (e: Exception) {
            Log.e("AudioRecord", "Error reading audio data", e)
        } finally {
            runCatching { ar.stop() }
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.IO) {
            runCatching { audioRecord?.stop() }
        }
    }

    override fun release() {
        runCatching { audioRecord?.release() }
        audioRecord = null
    }
}
