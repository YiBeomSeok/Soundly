package com.soundly.sdk.recording.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * 선언 확정 → 수집별 실행 생성 → 이벤트 전달만 맡으며, 녹음 상태나 입출력 자원을 보관하지 않습니다.
 */
object Soundly : Recorder {
    override fun record(configure: RecordingBuilder.() -> Unit): Flow<RecordingEvent> {
        val request = RecordingBuilder().apply(configure).build()

        return flow {
            val recorder = FlowRecorder(request.config)

            emitAll(recorder.record(request.input, request.output, request.bufferCapacity))
        }
    }
}
