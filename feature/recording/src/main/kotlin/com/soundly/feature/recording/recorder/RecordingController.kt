package com.soundly.feature.recording.recorder

import com.soundly.data.recording.RoomRecordingCatalog
import com.soundly.sdk.recording.api.RecordedAudio
import com.soundly.sdk.recording.api.Recorder
import com.soundly.sdk.recording.api.RecordingControls
import com.soundly.sdk.recording.api.RecordingEvent
import com.soundly.sdk.recording.api.RecordingInputs
import com.soundly.sdk.recording.api.RecordingOutputs
import com.soundly.sdk.recording.api.RecordingState as SdkRecordingState
import com.soundly.sdk.recording.api.RecordingTarget.Companion.auto
import com.soundly.sdk.recording.api.RecordingTarget.Companion.flac
import com.soundly.sdk.recording.api.RecordingTarget.Companion.wav
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

/** 서비스 수명 동안 SDK 녹음 세션과 그 상태를 소유합니다. */
internal class RecordingController @Inject constructor(
    private val recorder: Recorder,
    private val inputs: RecordingInputs,
    private val outputs: RecordingOutputs,
    private val catalog: RoomRecordingCatalog,
) {
    private val mutableState = MutableStateFlow(RecorderUiState())
    val uiState = mutableState.asStateFlow()

    private var recordingJob: Job? = null
    private var controls: RecordingControls? = null

    val isRunning: Boolean get() = recordingJob != null

    fun start(request: RecordingRequest, scope: CoroutineScope, startForeground: () -> Unit) {
        if (isRunning) return

        val sessionControls = RecordingControls()
        controls = sessionControls

        // Job을 필드에 보관한 뒤 시작하여 연속 요청을 같은 세션 경계에서 처리합니다.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var completedState: RecorderUiState? = null
            var pendingRegistration: RecordedAudio? = null

            flow {
                val target = when (request.prefer) {
                    RecordingFormat.Wav -> wav(File(request.dir, "${request.baseName}.wav"))
                    RecordingFormat.Flac -> flac(File(request.dir, "${request.baseName}.flac"))
                    RecordingFormat.Auto -> auto(File(request.dir, "${request.baseName}.flac"))
                }
                emitAll(recorder.record {
                    input = inputs.microphone(config)
                    output = outputs.file(target)
                    controls = sessionControls
                })
            }.transform { event ->
                when (event) {
                    is RecordingEvent.State -> emit(RecorderUiState(
                        recordingState = when (event.state) {
                            SdkRecordingState.Starting -> RecordingState.Starting
                            SdkRecordingState.Active -> RecordingState.Recording
                            SdkRecordingState.Paused -> RecordingState.Paused
                            SdkRecordingState.Stopping -> RecordingState.Stopping
                        },
                        elapsed = event.progress.duration,
                    ))
                    is RecordingEvent.Progress -> emit(mutableState.value.copy(elapsed = event.progress.duration))
                    is RecordingEvent.Completed -> {
                        val audio = checkNotNull(event.result as? RecordedAudio) { "File output is required" }
                        pendingRegistration = audio
                        catalog.registerCompletedFile(audio.file, audio.progress.duration)
                        pendingRegistration = null
                        completedState = RecorderUiState(
                            recordingState = RecordingState.Stopped,
                            elapsed = audio.progress.duration,
                        )
                    }
                }
            }.onStart {
                emit(RecorderUiState(recordingState = RecordingState.Starting))
                startForeground()
            }.onCompletion { cause ->
                controls = null
                recordingJob = null
                if (cause == null) {
                    emit(completedState ?: RecorderUiState(
                        recordingState = RecordingState.Error(
                            IllegalStateException("완료 이벤트 없이 녹음이 끝났습니다"),
                        ),
                    ))
                }
            }.catch { failure ->
                if (failure is CancellationException) throw failure
                val error = pendingRegistration?.let { audio ->
                    IllegalStateException(
                        "녹음 파일은 저장됐지만 목록 등록에 실패했습니다: ${audio.file.name}",
                        failure,
                    )
                } ?: failure
                emit(mutableState.value.copy(recordingState = RecordingState.Error(error)))
            }.collect { state ->
                mutableState.value = state
            }
        }
        recordingJob = job
        job.start()
    }

    fun pause() { controls?.pause() }
    fun resume() { controls?.resume() }
    fun stop() { controls?.stop() }

    fun resetError() {
        if (!isRunning && mutableState.value.recordingState is RecordingState.Error) {
            mutableState.value = RecorderUiState()
        }
    }
}
