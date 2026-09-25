package com.soundly.sdk.recording.api

/**
 * 녹음 설정에 따른 리틀 엔디언 PCM16 청크이며, 전달한 배열은 생산자가 재사용하거나 소비자가 수정하지 않습니다.
 */
data class PcmChunk(val bytes: ByteArray) : RecordingInput
