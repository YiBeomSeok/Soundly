package com.soundly.media.recorder.core

import com.soundly.media.recorder.api.Recorder
import com.soundly.media.recorder.api.RecorderSimpleState
import com.soundly.media.recorder.api.RecorderState
import com.soundly.media.recorder.capture.PcmSource
import com.soundly.media.recorder.container.ContainerWriter
import com.soundly.media.recorder.encode.Encoder
import com.soundly.media.recorder.io.Sink
import com.soundly.media.recorder.io.FileBackedSink
import com.soundly.media.recorder.encode.SampleCountingEncoder
import com.soundly.media.recorder.util.FlacFinalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecordingPipeline(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val source: PcmSource,
    private val encoder: Encoder?,
    private val container: ContainerWriter?,
    private val sink: Sink,
) : Recorder {

    private val _state = MutableStateFlow(RecorderState(RecorderSimpleState.Idle))
    override val state: StateFlow<RecorderState> = _state.asStateFlow()

    private var streamJob: Job? = null

    override suspend fun prepare() {
        _state.value = RecorderState(RecorderSimpleState.Prepared)
    }

    override suspend fun start() {
        if (_state.value.simple != RecorderSimpleState.Prepared && _state.value.simple != RecorderSimpleState.Paused) return
        container?.writeHeader(sink)
        _state.value = RecorderState(RecorderSimpleState.Recording)
        streamJob = scope.launch {
            try {
                source.start().collect { pcm ->
                    if (encoder == null) {
                        container?.writePayload(sink, pcm) ?: sink.write(pcm)
                    } else {
                        val frames = encoder.encode(pcm)
                        for (f in frames) sink.write(f)
                    }
                }
            } catch (t: Throwable) {
                _state.value = RecorderState(RecorderSimpleState.Error, t.message)
            }
        }
    }

    override suspend fun pause() {
        if (_state.value.simple != RecorderSimpleState.Recording) return
        _state.value = RecorderState(RecorderSimpleState.Paused)
        // For initial version, rely on source implementation to stop emission when paused (future work)
    }

    override suspend fun resume() {
        if (_state.value.simple != RecorderSimpleState.Paused) return
        _state.value = RecorderState(RecorderSimpleState.Recording)
    }

    override suspend fun stop() {
        if (_state.value.simple == RecorderSimpleState.Stopped || _state.value.simple == RecorderSimpleState.Idle) return
        try {
            streamJob?.cancel()
            streamJob = null
            withContext(Dispatchers.IO) {
                encoder?.signalEndOfStream()
                encoder?.drain()?.forEach { sink.write(it) }
                container?.finalize(sink)
                sink.flush()
                sink.close()

                // FLAC finalize: patch STREAMINFO total samples so duration is known
                val isFlac = encoder?.outMime == android.media.MediaFormat.MIMETYPE_AUDIO_FLAC
                if (isFlac) {
                    val file = (sink as? FileBackedSink)?.outputFile
                    val samples = (encoder as? SampleCountingEncoder)?.totalPcmSamplesSubmitted
                    if (file != null && samples != null) {
                        FlacFinalizer.patchTotalSamples(file, samples)
                    }
                }
            }
        } catch (t: Throwable) {
            _state.value = RecorderState(RecorderSimpleState.Error, t.message)
        } finally {
            _state.value = RecorderState(RecorderSimpleState.Stopped)
        }
    }

    override fun release() {
        try {
            encoder?.release()
            source.release()
        } catch (_: Throwable) {
        }
    }
}
