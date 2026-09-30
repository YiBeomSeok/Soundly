package com.soundly.sdk.player

import android.content.Context
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.isActive

/**
 * 수집별 플레이어 생성 → 명령·상태 처리 → 해제를 한 실행 범위에서 읽도록 구성합니다.
 */
class PlaybackManager internal constructor(
    private val createEngine: () -> PlaybackEngine,
    private val dispatcher: CoroutineDispatcher,
) : Playback {
    constructor(context: Context) : this(
        createEngine = { Media3PlaybackEngine(context = context.applicationContext) },
        dispatcher = Dispatchers.Main.immediate,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun play(
        file: File,
        controls: PlaybackControls,
    ): Flow<PlaybackStatus> {
        return flow {
            emit(PlaybackStatus(selectedFile = file, state = PlaybackState.Buffering))

            createEngine().use { engine ->
                engine.play(file = file)

                // 상태가 바뀌면 이전 위치 갱신을 취소하고, 재생 중인 구간에서만 다시 갱신합니다.
                val observations = engine.observe()
                    .transformLatest { status ->
                        emit(Event.Refreshed)
                        if (status.state == PlaybackState.Playing) {
                            while (currentCoroutineContext().isActive) {
                                delay(timeMillis = 100)
                                emit(Event.Refreshed)
                            }
                        }
                    }
                val commands = controls.commands.map { command -> Event.Commanded(command = command) }

                // 명령과 관찰 결과를 한곳에서 처리하며, 정지나 재생 실패는 수집 범위를 끝냅니다.
                merge(commands, observations)
                    .transformWhile { event ->
                        val status = when (event) {
                            Event.Refreshed -> engine.readStatus()
                            is Event.Commanded -> when (val command = event.command) {
                                PlaybackCommand.Pause -> {
                                    engine.pause()
                                    engine.readStatus()
                                }
                                PlaybackCommand.Resume -> {
                                    engine.resume()
                                    engine.readStatus()
                                }
                                is PlaybackCommand.Seek -> {
                                    engine.seekTo(position = command.position)
                                    engine.readStatus()
                                }
                                PlaybackCommand.Stop -> {
                                    emit(PlaybackStatus.Idle)
                                    return@transformWhile false
                                }
                            }
                        }
                        emit(status.copy(selectedFile = file))
                        status.state != PlaybackState.Error && status.state != PlaybackState.Ended
                    }
                    .collect { status -> emit(status) }
            }
        }.catch { failure ->
            if (failure is CancellationException) throw failure

            emit(
                PlaybackStatus(
                    selectedFile = file,
                    state = PlaybackState.Error,
                    errorMessage = failure.message ?: "재생하지 못했습니다.",
                ),
            )
        }.flowOn(context = dispatcher)
    }

    private sealed interface Event {
        data object Refreshed : Event
        data class Commanded(val command: PlaybackCommand) : Event
    }
}
