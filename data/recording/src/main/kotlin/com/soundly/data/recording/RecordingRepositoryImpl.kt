package com.soundly.data.recording

import com.soundly.data.recording.db.RecordingDao
import com.soundly.domain.recording.Recording
import com.soundly.domain.recording.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject

internal class RecordingRepositoryImpl @Inject constructor(
    private val dao: RecordingDao,
) : RecordingRepository {

    override suspend fun saveRecording(recording: Recording) {
        // Persist metadata to Room. The audio file is already written by the recorder.
        dao.upsert(recording.toEntity())
    }

    override suspend fun deleteRecording(uri: String): Boolean {
        val wasInDb = dao.getById(uri) != null
        // Try to delete the backing file as best-effort.
        val fileDeleted = runCatching {
            File(uri).delete()
        }.getOrElse { false }
        dao.deleteById(uri)
        return wasInDb || fileDeleted
    }

    override fun getRecordings(): Flow<List<Recording>> {
        return dao.observeAll().map { list ->
            list.map { it.toDomain() }
        }
    }
}

