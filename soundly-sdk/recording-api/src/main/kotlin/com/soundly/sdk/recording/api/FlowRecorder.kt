package com.soundly.sdk.recording.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.transformWhile

/**
 * 상태 전이 → 버퍼 → 출력 → 완료를 위에서 아래로 읽으며, 기록·진행량 계산·자원 정리는 각 구현에 맡깁니다.
 */
internal class FlowRecorder(private val config: RecordingConfig) {
    private var state = RecordingState.Starting
    private val progress = MutableStateFlow(RecordingProgress(0, config.sampleRateHz))

    fun record(
        input: Flow<RecordingInput>,
        output: RecordingOutput,
        bufferCapacity: Int,
    ): Flow<RecordingEvent> = channelFlow {
        send(RecordingEvent.State(state, progress.value))

        // 상태에 맞는 동작을 선택하고, 활성 구간의 PCM만 출력 버퍼에 전달합니다.
        val pcm = input
            .transformWhile { event ->
                when (event) {
                    RecordingCommand.Record -> if (state != RecordingState.Active) {
                        state = RecordingState.Active
                        send(RecordingEvent.State(state, progress.value))
                    }
                    RecordingCommand.Pause -> if (state != RecordingState.Paused) {
                        state = RecordingState.Paused
                        send(RecordingEvent.State(state, progress.value))
                    }
                    RecordingCommand.Stop -> {
                        state = RecordingState.Stopping
                        send(RecordingEvent.State(state, progress.value))
                    }
                    is PcmChunk -> if (state == RecordingState.Active && event.bytes.isNotEmpty()) {
                        emit(event)
                    }
                    is RecordingInputFailure -> {
                        val failure = event.cause
                        if (failure is CancellationException || failure is RecorderException) throw failure
                        throw RecorderException(RecorderException.Reason.Capture, failure)
                    }
                }

                state != RecordingState.Stopping
            }
            .buffer(capacity = bufferCapacity)

        // 출력이 알린 진행량을 전달하고, 출력 흐름 전체가 정상 종료할 때까지 기다립니다.
        val finished = output.write(pcm, config)
            .onEach { event ->
                if (event is OutputEvent.Progress) {
                    progress.value = event.progress
                    send(RecordingEvent.Progress(event.progress))
                }
            }
            .filterIsInstance<OutputEvent.Finished>()
            .catch { failure ->
                if (failure is CancellationException || failure is RecorderException) throw failure
                throw RecorderException(RecorderException.Reason.Storage, failure)
            }
            .singleOrNull()
            ?: throw RecorderException(RecorderException.Reason.OutputContract)

        send(RecordingEvent.Completed(finished.result))
    }
}
