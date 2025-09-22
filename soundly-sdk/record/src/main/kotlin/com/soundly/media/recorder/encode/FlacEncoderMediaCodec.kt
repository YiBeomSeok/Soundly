package com.soundly.media.recorder.encode

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import com.soundly.media.recorder.api.SampleSpec
import java.nio.ByteBuffer

class FlacEncoderMediaCodec(
    override val inSpec: SampleSpec,
) : Encoder {

    override val outMime: String = MediaFormat.MIMETYPE_AUDIO_FLAC

    private var codec: MediaCodec? = null
    private val bufferInfo: MediaCodec.BufferInfo = MediaCodec.BufferInfo()

    // PTS bookkeeping: per-channel sample count submitted to encoder
    private var totalPcmSamplesSubmitted: Long = 0L
    private var signaledEos: Boolean = false

    init {
        require(inSpec.bitsPerSample == 16) { "FLAC MediaCodec requires 16-bit PCM input" }
        require(inSpec.channelCount == 1 || inSpec.channelCount == 2) { "channelCount must be 1 or 2" }
        ensureCodec()
    }

    private fun ensureCodec() {
        if (codec != null) return
        val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_FLAC)
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_FLAC,
            inSpec.sampleRate,
            inSpec.channelCount,
        ).apply {
            setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            // Some devices may support additional FLAC params (e.g., compression level)
            // setInteger("flac-compression-level", 5)
        }
        c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        c.start()
        codec = c
    }

    override fun encode(pcm: ByteArray): List<ByteArray> {
        if (pcm.isEmpty()) return emptyList()
        ensureCodec()
        val c = codec ?: return emptyList()

        val outputs = mutableListOf<ByteArray>()
        var offset = 0
        val bytesPerSample = 2 // enforced by require(bitsPerSample==16)
        val frameStride = bytesPerSample * inSpec.channelCount

        while (offset < pcm.size) {
            val inIx = c.dequeueInputBuffer(10_000)
            if (inIx < 0) {
                // No input buffer available; drain what we can and retry
                outputs += drainInternal(c, 0)
                continue
            }
            val inBuf: ByteBuffer = c.getInputBuffer(inIx) ?: ByteBuffer.allocate(0)
            inBuf.clear()
            val toWrite = minOf(inBuf.remaining(), pcm.size - offset)
            inBuf.put(pcm, offset, toWrite)

            val samplesInThisChunkPerChannel = (toWrite / frameStride)
            val ptsUs = ptsUsForSamples(totalPcmSamplesSubmitted)

            c.queueInputBuffer(inIx, 0, toWrite, ptsUs, 0)
            totalPcmSamplesSubmitted += samplesInThisChunkPerChannel
            offset += toWrite

            // Drain after queueing input to minimize latency
            outputs += drainInternal(c, 0)
        }

        // Final non-blocking drain to pick up any residual
        outputs += drainInternal(c, 0)
        return outputs
    }

    override fun drain(): List<ByteArray> {
        ensureCodec()
        val c = codec ?: return emptyList()
        return drainInternal(c, 2_000)
    }

    override fun signalEndOfStream() {
        ensureCodec()
        val c = codec ?: return
        if (signaledEos) return
        // Try to obtain an input buffer to post EOS
        repeat(3) {
            val inIx = c.dequeueInputBuffer(10_000)
            if (inIx >= 0) {
                c.queueInputBuffer(
                    inIx,
                    0,
                    0,
                    ptsUsForSamples(totalPcmSamplesSubmitted),
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
                signaledEos = true
                return
            }
            // If not available, attempt a quick drain to free buffers
            drainInternal(c, 0)
        }
        // If we could not signal now, mark as signaled; subsequent drains will flush remaining output
        signaledEos = true
    }

    override fun release() {
        val c = codec
        codec = null
        try {
            c?.stop()
        } catch (_: Throwable) {
        }
        try {
            c?.release()
        } catch (_: Throwable) {
        }
    }

    private fun drainInternal(c: MediaCodec, timeoutUs: Long): List<ByteArray> {
        val outputs = mutableListOf<ByteArray>()
        while (true) {
            val outIx = c.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outIx == MediaCodec.INFO_TRY_AGAIN_LATER -> return outputs
                outIx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // Ignored: raw FLAC elementary stream
                }

                outIx >= 0 -> {
                    val outBuf = c.getOutputBuffer(outIx)
                    if (outBuf != null && bufferInfo.size > 0) {
                        val data = ByteArray(bufferInfo.size)
                        outBuf.get(data)
                        outBuf.clear()
                        outputs.add(data)
                    }
                    val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    c.releaseOutputBuffer(outIx, false)
                    if (eos) return outputs
                }

                else -> return outputs
            }
        }
    }

    private fun ptsUsForSamples(samplesPerChannel: Long): Long {
        return (samplesPerChannel * 1_000_000L) / inSpec.sampleRate
    }
}