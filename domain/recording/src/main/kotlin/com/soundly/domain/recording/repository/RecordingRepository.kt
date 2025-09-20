package com.soundly.domain.recording.repository

import com.soundly.domain.recording.Recording
import kotlinx.coroutines.flow.Flow

interface RecordingRepository {
    suspend fun saveRecording(recording: Recording)
    suspend fun deleteRecording(uri: String): Boolean
    fun getRecordings(): Flow<List<Recording>>
}