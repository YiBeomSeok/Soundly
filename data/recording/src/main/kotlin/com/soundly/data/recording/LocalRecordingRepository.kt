package com.soundly.data.recording

import com.soundly.data.recording.db.RecordingDao
import com.soundly.data.recording.db.RecordingEntity
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * 녹음 목록과 파일 삭제를 소유하며, DB·파일 접근은 IO에서 실행합니다.
 */
@Singleton
internal class LocalRecordingRepository @Inject constructor(
    private val dao: RecordingDao,
) : RecordingRepository {
    override fun observe(): Flow<List<Recording>> {
        return dao.observeAll().map { rows ->
            rows.map { row ->
                val file = File(row.id)
                Recording(
                    uri = row.id,
                    title = row.title,
                    createdAt = Instant.ofEpochMilli(row.createdAtEpochMs),
                    duration = row.durationMs.milliseconds,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun save(recording: Recording) {
        dao.upsert(
            entity = RecordingEntity(
                id = recording.uri,
                title = recording.title,
                createdAtEpochMs = recording.createdAt.toEpochMilli(),
                durationMs = recording.duration.inWholeMilliseconds,
            )
        )
    }

    override suspend fun delete(uri: String): Boolean {
        return withContext(Dispatchers.IO) {
            val exists = dao.getById(uri) != null
            val file = File(uri)
            val fileExists = file.exists()
            check(!fileExists || file.delete()) { "녹음 파일을 삭제하지 못했습니다." }

            dao.deleteById(uri)
            exists || fileExists
        }
    }
}
