package com.soundly.sdk.v0.media.recorder.core

import android.media.MediaFormat
import com.soundly.sdk.v0.media.recorder.api.FlacRecorder
import com.soundly.sdk.v0.media.recorder.api.RecorderSimpleState
import com.soundly.sdk.v0.media.recorder.api.RecorderState
import com.soundly.sdk.v0.media.recorder.capture.PcmSource
import com.soundly.sdk.v0.media.recorder.encode.Encoder
import com.soundly.sdk.v0.media.recorder.encode.SampleCountingEncoder
import com.soundly.sdk.v0.media.recorder.io.FileBackedSink
import com.soundly.sdk.v0.media.recorder.io.Sink
import com.soundly.sdk.v0.media.recorder.util.FlacFinalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FLAC-only recorder: source -> encoder(FLAC) -> sink, with STREAMINFO patching on stop.
 */
internal class FlacRecorderImpl(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val source: PcmSource,
    override val encoder: Encoder,
    private val sink: Sink,
) : FlacRecorder {

    private val _state = MutableStateFlow(RecorderState(RecorderSimpleState.Idle))
    override val state: StateFlow<RecorderState> = _state.asStateFlow()

    private var streamJob: Job? = null
    @Volatile private var paused: Boolean = false

    override suspend fun prepare() {
        _state.update { RecorderState(RecorderSimpleState.Prepared) }
    }

    override suspend fun start() {
        if (_state.value.simple != RecorderSimpleState.Prepared && _state.value.simple != RecorderSimpleState.Paused) return
        _state.update { RecorderState(RecorderSimpleState.Recording) }

        streamJob = scope.launch {
            try {
                source.start().collect { pcm ->
                    if (paused) return@collect
                    val frames = encoder.encode(pcm)
                    for (f in frames) sink.write(f)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.update { RecorderState(RecorderSimpleState.Error, t.message) }
            }
        }
    }

    override suspend fun pause() {
        if (_state.value.simple != RecorderSimpleState.Recording) return
        paused = true
        _state.update { RecorderState(RecorderSimpleState.Paused) }
    }

    override suspend fun resume() {
        if (_state.value.simple != RecorderSimpleState.Paused) return
        paused = false
        _state.update { RecorderState(RecorderSimpleState.Recording) }
    }

    override suspend fun stop() {
        if (_state.value.simple == RecorderSimpleState.Stopped || _state.value.simple == RecorderSimpleState.Idle) return
        try {
            streamJob?.cancel()
            streamJob = null
            withContext(Dispatchers.IO) {
                encoder.signalEndOfStream()
                encoder.drain().forEach { sink.write(it) }
                sink.flush()
                sink.close()

                if (encoder.outMime == MediaFormat.MIMETYPE_AUDIO_FLAC) {
                    val file = (sink as? FileBackedSink)?.outputFile
                    val samples = (encoder as? SampleCountingEncoder)?.totalPcmSamplesSubmitted
                    if (file != null && samples != null) {
                        FlacFinalizer.patchTotalSamples(file, samples)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            _state.update { RecorderState(RecorderSimpleState.Error, t.message) }
        } finally {
            _state.update { RecorderState(RecorderSimpleState.Stopped) }
        }
    }

    override fun release() {
        runCatching {
            encoder.release()
            source.release()
            scope.cancel()
        }
    }
}


