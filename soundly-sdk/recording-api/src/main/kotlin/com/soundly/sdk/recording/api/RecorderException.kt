package com.soundly.sdk.recording.api

/**
 * 녹음 실패의 구분과 원인을 전달하며, 코루틴 취소는 이 예외로 변환하지 않습니다.
 */
class RecorderException(val reason: Reason, cause: Throwable? = null) : Exception(reason.message, cause) {
    enum class Reason(internal val message: String) {
        Capture("음성 입력을 처리하지 못했습니다."),
        InvalidPcm("입력이 녹음 설정의 PCM16 프레임과 맞지 않습니다."),
        MemoryLimit("메모리 출력의 보관 용량을 초과했습니다."),
        OutputContract("출력은 기록을 마친 뒤 마감 결과를 한 번 전달해야 합니다."),
        Encoding("FLAC 인코딩을 처리하지 못했습니다."),
        Storage("녹음 파일을 저장하지 못했습니다."),
    }
}
