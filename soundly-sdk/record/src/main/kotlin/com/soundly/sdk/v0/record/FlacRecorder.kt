package com.soundly.sdk.v0.record

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Process
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real‑time FLAC recorder with start / pause / resume / stop.
 * - Captures PCM via AudioRecord
 * - Encodes with MediaCodec (audio/flac)
 * - Writes a pure .flac elementary stream to [outFile]
 *
 * API 29+ recommended. Falls back is NOT implemented here; add WAV if needed.
 */
class FlacRecorder(
    private val outFile: File,
    private val sampleRate: Int = 44100,
    private val channelCount: Int = 1,
    private val pcmEncoding: Int = AudioFormat.ENCODING_PCM_16BIT,
) {

    enum class State { IDLE, PREPARED, RECORDING, PAUSED, STOPPED, RELEASED }

    @Volatile
    private var state: State = State.IDLE

    private var audioRecord: AudioRecord? = null
    private var codec: MediaCodec? = null
    private var format: MediaFormat? = null
    private var fos: FileOutputStream? = null

    private val running = AtomicBoolean(false)

    @Volatile
    private var paused = false

    // Monotonic PTS based on encoded samples; do not advance while paused
    private var totalPcmSamplesSubmitted: Long = 0L // per channel samples

    // Scratch
    private var inBufSizeBytes: Int = 0

    // Coroutine dispatcher backed by a dedicated audio thread with THREAD_PRIORITY_AUDIO
    private val executor = Executors.newSingleThreadExecutor()
    private val audioDispatcher = executor.asCoroutineDispatcher()

    // Internal scope tied to recorder lifecycle
    private val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + audioDispatcher)

    private var job: Job? = null

    /** Check if a FLAC encoder is available on this device. */
    fun isFlacEncoderAvailable(): Boolean {
        val infos = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        return infos.any { info ->
            info.isEncoder && info.supportedTypes.any {
                it.equals(
                    MediaFormat.MIMETYPE_AUDIO_FLAC,
                    ignoreCase = true
                )
            }
        }
    }

    /** Prepare resources. Call once before start(). */
    @SuppressLint("MissingPermission")
    fun prepare() {
        check(state == State.IDLE) { "Invalid state: $state" }

        require(channelCount == 1 || channelCount == 2) { "channelCount must be 1 or 2" }
        require(pcmEncoding == AudioFormat.ENCODING_PCM_16BIT) { "FLAC encoder requires PCM 16-bit" }

        // ---- AudioRecord ----
        val channelConfig = when (channelCount) {
            1 -> AudioFormat.CHANNEL_IN_MONO
            else -> AudioFormat.CHANNEL_IN_STEREO
        }
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, pcmEncoding)
        require(minBuf != AudioRecord.ERROR && minBuf != AudioRecord.ERROR_BAD_VALUE) { "Bad AudioRecord buffer size" }
        val bufferBytes = (minBuf * 2).coerceAtLeast(4096)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            pcmEncoding,
            bufferBytes
        )
        check(audioRecord?.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord init failed" }

        // ---- MediaCodec (FLAC encoder) ----
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_FLAC)
        format =
            MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_FLAC, sampleRate, channelCount)
                .apply {
                    setInteger(MediaFormat.KEY_PCM_ENCODING, pcmEncoding)
                    // Optional: device dependent, some vendors support compression level key
                    // setInteger("flac-compression-level", 5)
                }
        codec!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec!!.start()

        outFile.parentFile?.mkdirs()
        fos = FileOutputStream(outFile)
        inBufSizeBytes = bufferBytes

        state = State.PREPARED
    }

    /** Start or resume actual recording+encoding. */
    fun start() {
        when (state) {
            State.PREPARED, State.PAUSED -> {
                paused = false
                if (state == State.PREPARED) {
                    startWorker()
                }
                audioRecord?.startRecording()
                state = State.RECORDING
            }

            else -> error("start() invalid in state=$state")
        }
    }

    /** Pause: stop feeding new PCM; PTS is preserved. */
    fun pause() {
        if (state != State.RECORDING) return
        paused = true
        audioRecord?.stop()
        state = State.PAUSED
    }

    /** Stop and finalize the stream. */
    fun stop() {
        if (state == State.STOPPED || state == State.RELEASED || state == State.IDLE) return
        paused = true
        audioRecord?.stop()

        codec?.let { c ->
            val inIx = c.dequeueInputBuffer(10_000)
            if (inIx >= 0) {
                c.queueInputBuffer(
                    inIx, 0, 0, ptsUsForSamples(totalPcmSamplesSubmitted),
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }
        }

        running.set(false)
        runBlocking { job?.join() }
        job = null

        drainAllRemaining()

        state = State.STOPPED
    }

    /** Free resources. */
    fun release() {
        if (state == State.RELEASED) return
        try {
            fos?.flush()
        } catch (_: Throwable) {
        }
        try {
            fos?.close()
        } catch (_: Throwable) {
        }
        fos = null
        try {
            codec?.stop()
        } catch (_: Throwable) {
        }
        try {
            codec?.release()
        } catch (_: Throwable) {
        }
        codec = null
        try {
            audioRecord?.release()
        } catch (_: Throwable) {
        }
        audioRecord = null
        // Close dispatcher/executor
        runCatching { audioDispatcher.close() }
        runCatching { executor.shutdownNow() }
        state = State.RELEASED
    }

    // ---------------- Coroutine worker ----------------
    private fun startWorker() {
        running.set(true)
        job = scope.launch {
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) }
            ioLoop()
        }
    }

    private suspend fun ioLoop() {
        val c = codec ?: return
        val ar = audioRecord ?: return
        val localBuf = ByteArray(inBufSizeBytes)
        val bufferInfo = MediaCodec.BufferInfo()

        while (running.get()) {
            if (paused) {
                drainEncoder(c, bufferInfo)
                delay(5)
                continue
            }

            val read = ar.read(localBuf, 0, localBuf.size)
            if (read > 0) {
                val inIx = c.dequeueInputBuffer(10_000)
                if (inIx >= 0) {
                    val inBuf = c.getInputBuffer(inIx) ?: ByteBuffer.allocate(0)
                    inBuf.clear()
                    inBuf.put(localBuf, 0, read)

                    val bytesPerSample = when (pcmEncoding) {
                        AudioFormat.ENCODING_PCM_16BIT -> 2
                        AudioFormat.ENCODING_PCM_8BIT -> 1
                        AudioFormat.ENCODING_PCM_FLOAT -> 4
                        else -> 2
                    }
                    val samplesInThisChunkPerChannel = read / (bytesPerSample * channelCount)
                    val ptsUs = ptsUsForSamples(totalPcmSamplesSubmitted)

                    c.queueInputBuffer(inIx, 0, read, ptsUs, 0)

                    totalPcmSamplesSubmitted += samplesInThisChunkPerChannel
                }
            } else if (read == 0) {
                drainEncoder(c, bufferInfo)
                delay(2)
            } else {
                drainEncoder(c, bufferInfo)
                delay(5)
            }

            drainEncoder(c, bufferInfo)
        }
    }

    private fun drainEncoder(c: MediaCodec, info: MediaCodec.BufferInfo) {
        while (true) {
            val outIx = c.dequeueOutputBuffer(info, 5_000)
            when {
                outIx == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                outIx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                outIx >= 0 -> {
                    val outBuf = c.getOutputBuffer(outIx)
                    if (outBuf != null && info.size > 0) {
                        val data = ByteArray(info.size)
                        outBuf.get(data)
                        outBuf.clear()
                        fos?.write(data)
                    }
                    c.releaseOutputBuffer(outIx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun drainAllRemaining() {
        val c = codec ?: return
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIx = c.dequeueOutputBuffer(info, 2_000)
            if (outIx == MediaCodec.INFO_TRY_AGAIN_LATER) break
            if (outIx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue
            if (outIx >= 0) {
                val outBuf = c.getOutputBuffer(outIx)
                if (outBuf != null && info.size > 0) {
                    val data = ByteArray(info.size)
                    outBuf.get(data)
                    fos?.write(data)
                }
                c.releaseOutputBuffer(outIx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
        try {
            fos?.flush()
        } catch (_: Throwable) {
        }
    }

    private fun ptsUsForSamples(samplesPerChannel: Long): Long {
        return (samplesPerChannel * 1_000_000L) / sampleRate
    }
}
