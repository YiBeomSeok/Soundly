package com.soundly.sdk.recording.api

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 수집 범위에서 입력·버퍼·출력을 소유하고, 정상 중지 시 대기 중인 PCM까지 처리합니다.
 */
interface Recorder {
    fun record(configure: RecordingBuilder.() -> Unit): Flow<RecordingEvent>
}

/**
 * 설정 확인 → 입력과 명령 연결 → 요청 확정만 수행하며, 실행 상태를 관리하거나 자원을 열지 않습니다.
 */
class RecordingBuilder {
    var input: Flow<RecordingInput>? = null
    var output: RecordingOutput? = null
    var config: RecordingConfig = RecordingConfig()
    var controls: RecordingControls = RecordingControls()

    /**
     * 대기할 PCM 청크 수이며, 가득 차면 입력 전달을 기다립니다. 청크를 버리지 않습니다.
     */
    var bufferCapacity: Int = 64

    internal fun build(): RecordingRequest {
        require(bufferCapacity >= 0) { "Buffer capacity must be non-negative" }
        val source = requireNotNull(input) { "Recording input is required" }
        val output = requireNotNull(output) { "Recording output is required" }
        val commands = controls.commands

        // 선언에는 자원을 열지 않는 입력 흐름을 담고, 최초 명령은 수집 시점에 읽습니다.
        val preparedInput = flow {
            val initial = commands.value
            emit(initial)

            val events = flow {
                emitAll(source)
                emit(RecordingCommand.Stop)
            }.catch { cause ->
                // 입력 자체의 취소도 합류한 흐름 밖에서 처리하도록 사건으로 전달합니다.
                emit(RecordingInputFailure(cause))
            }
            val changes = commands.dropWhile { command -> command == initial }

            emitAll(merge(events, changes))
        }

        return RecordingRequest(
            input = preparedInput,
            output = output,
            config = config,
            bufferCapacity = bufferCapacity,
        )
    }
}

internal data class RecordingRequest(
    val input: Flow<RecordingInput>,
    val output: RecordingOutput,
    val config: RecordingConfig,
    val bufferCapacity: Int,
)

/**
 * 수집 범위에서 PCM을 한 번 끝까지 기록하고, 진행량과 마감 후 결과를 전달합니다.
 */
interface RecordingOutput {
    fun write(source: Flow<PcmChunk>, config: RecordingConfig): Flow<OutputEvent>
}

sealed interface OutputEvent {
    data class Progress(val progress: RecordingProgress) : OutputEvent
    data class Finished(val result: RecordingResult) : OutputEvent
}

/**
 * 앱 조립부에서 플랫폼 입력을 선택하며, 녹음 실행은 입력의 출처를 알지 못합니다.
 */
interface RecordingInputs {
    fun microphone(config: RecordingConfig): Flow<PcmChunk>
    fun file(file: File, config: RecordingConfig): Flow<PcmChunk>
}

fun interface RecordingOutputs {
    fun file(target: RecordingTarget): RecordingOutput
}

/**
 * PCM과 명령을 같은 입력 흐름에서 순서대로 처리합니다.
 */
sealed interface RecordingInput

internal data class RecordingInputFailure(val cause: Throwable) : RecordingInput

enum class RecordingCommand : RecordingInput { Record, Pause, Stop }
enum class RecordingState { Starting, Active, Paused, Stopping }

/**
 * 사용자 요청을 명령으로 보관하며, 실행 상태를 판단하지 않습니다. 중지한 요청은 되돌리지 않습니다.
 */
class RecordingControls {
    private val desired = MutableStateFlow(RecordingCommand.Record)
    internal val commands: StateFlow<RecordingCommand> = desired.asStateFlow()

    fun pause() { changeTo(RecordingCommand.Pause) }
    fun resume() { changeTo(RecordingCommand.Record) }
    fun stop() { changeTo(RecordingCommand.Stop) }

    private fun changeTo(next: RecordingCommand) {
        desired.update { current -> if (current == RecordingCommand.Stop) current else next }
    }
}

enum class RecordingFormat { Wav, Flac, Auto }

data class RecordingTarget(val file: File, val format: RecordingFormat) {
    companion object {
        fun wav(file: File): RecordingTarget = RecordingTarget(file, RecordingFormat.Wav)
        fun flac(file: File): RecordingTarget = RecordingTarget(file, RecordingFormat.Flac)
        fun auto(file: File): RecordingTarget = RecordingTarget(file, RecordingFormat.Auto)
    }
}

data class RecordingProgress(val samples: Long, val sampleRateHz: Int) {
    val duration: Duration get() = (samples.toDouble() / sampleRateHz).seconds
}

sealed interface RecordingResult {
    val config: RecordingConfig
    val progress: RecordingProgress
}

data class RecordedAudio(
    val file: File,
    val format: RecordingFormat,
    override val config: RecordingConfig,
    override val progress: RecordingProgress,
) : RecordingResult

data class MemoryRecording(
    val chunks: List<PcmChunk>,
    override val config: RecordingConfig,
    override val progress: RecordingProgress,
) : RecordingResult

/**
 * 이벤트 합류 정책은 수집자가 정하며, 완료는 출력 마감과 입력 정리가 성공한 뒤 전달합니다.
 */
sealed interface RecordingEvent {
    data class State(val state: RecordingState, val progress: RecordingProgress) : RecordingEvent
    data class Progress(val progress: RecordingProgress) : RecordingEvent
    data class Completed(val result: RecordingResult) : RecordingEvent
}
