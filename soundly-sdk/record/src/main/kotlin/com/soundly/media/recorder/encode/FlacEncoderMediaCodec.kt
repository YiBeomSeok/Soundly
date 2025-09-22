package com.soundly.media.recorder.encode

import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import com.soundly.media.recorder.api.SampleSpec

class FlacEncoderMediaCodec(
    override val inSpec: SampleSpec,
) : Encoder {

    override val outMime: String = MediaFormat.MIMETYPE_AUDIO_FLAC

    private var codec: MediaCodec? = null
    private var csd0: ByteArray? = null
    private var headerEmitted = false

    init {
        require(inSpec.bitsPerSample == 16) { "FLAC requires 16-bit PCM" }
        val available = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(outMime, ignoreCase = true) }
        }
        require(available) { "No FLAC encoder found on device" }
        val c = MediaCodec.createEncoderByType(outMime)
        val fmt = MediaFormat.createAudioFormat(outMime, inSpec.sampleRate, inSpec.channelCount).apply {
            setInteger(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
        }
        c.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        c.start()
        codec = c
    }

    override fun encode(pcm: ByteArray): List<ByteArray> {
        val c = codec ?: return emptyList()
        val out = mutableListOf<ByteArray>()
        // queue input
        val inIx = c.dequeueInputBuffer(10_000)
        if (inIx >= 0) {
            val inBuf = c.getInputBuffer(inIx)
            if (inBuf != null) {
                inBuf.clear()
                inBuf.put(pcm)
                c.queueInputBuffer(inIx, 0, pcm.size, 0, 0)
            }
        }
        // drain available output
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIx = c.dequeueOutputBuffer(info, 0)
            when {
                outIx == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                outIx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val fmt = c.outputFormat
                    val bb = fmt.getByteBuffer("csd-0")
                    if (bb != null && bb.remaining() > 0) {
                        csd0 = ByteArray(bb.remaining()).also { bb.get(it) }
                        headerEmitted = false
                    }
                }
                outIx >= 0 -> {
                    c.getOutputBuffer(outIx)?.let { buf ->
                        if (info.size > 0) {
                            if (!headerEmitted && csd0 != null) {
                                out += csd0!!
                                headerEmitted = true
                            }
                            val data = ByteArray(info.size)
                            buf.get(data)
                            out += data
                        }
                        buf.clear()
                    }
                    c.releaseOutputBuffer(outIx, false)
                }
            }
        }
        return out
    }

    override fun drain(): List<ByteArray> {
        val c = codec ?: return emptyList()
        val out = mutableListOf<ByteArray>()
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIx = c.dequeueOutputBuffer(info, 0)
            when {
                outIx == MediaCodec.INFO_TRY_AGAIN_LATER -> return out
                outIx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val fmt = c.outputFormat
                    val bb = fmt.getByteBuffer("csd-0")
                    if (bb != null && bb.remaining() > 0) {
                        csd0 = ByteArray(bb.remaining()).also { bb.get(it) }
                        // do not emit here; will be emitted before first real frame
                    }
                }
                outIx >= 0 -> {
                    c.getOutputBuffer(outIx)?.let { buf ->
                        if (info.size > 0) {
                            if (!headerEmitted && csd0 != null) {
                                out += csd0!!
                                headerEmitted = true
                            }
                            val data = ByteArray(info.size)
                            buf.get(data)
                            out += data
                        }
                        buf.clear()
                    }
                    c.releaseOutputBuffer(outIx, false)
                }
            }
        }
    }

    override fun signalEndOfStream() {
        codec?.let { c ->
            val inIx = c.dequeueInputBuffer(10_000)
            if (inIx >= 0) c.queueInputBuffer(inIx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
    }

    override fun release() {
        codec?.run {
            runCatching { stop() }
            runCatching { release() }
        }
        codec = null
    }
}
