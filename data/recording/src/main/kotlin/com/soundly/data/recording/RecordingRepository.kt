package com.soundly.data.recording

import kotlinx.coroutines.flow.Flow

/**
 * 화면과 서비스가 사용하는 녹음 저장소 계약이며, 저장 방식은 구현체에 맡깁니다.
 */
interface RecordingRepository {
    fun observe(): Flow<List<Recording>>

    suspend fun save(recording: Recording)

    suspend fun delete(uri: String): Boolean
}
