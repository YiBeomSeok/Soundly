package com.soundly.data.recording

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import com.soundly.sdk.recording.storage.FileRecordingOutput
import com.soundly.data.recording.db.RecordingDao
import com.soundly.data.recording.db.RecordingEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** 완성된 녹음 파일의 Room 목록과 파일·DB 일관성을 관리합니다. */
@Singleton
class RoomRecordingCatalog internal constructor(
    private val dao: RecordingDao,
    directories: List<File>,
    private val readDuration: (File) -> Duration = ::readAudioDuration,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    @Inject
    internal constructor(
        dao: RecordingDao,
        @ApplicationContext context: Context,
    ) : this(
        dao = dao,
        directories = listOfNotNull(
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { File(it, "recordings") },
            File(context.filesDir, "recordings"),
        ),
    )

    private val directories = directories.map { it.absoluteFile.normalize() }.distinct()
    private val mutex = Mutex()

    fun deleteAbandonedParts() {
        for (directory in directories) {
            try {
                FileRecordingOutput.deleteAbandonedTemporaryFiles(directory)
            } catch (failure: Exception) {
                Logger.getLogger(RoomRecordingCatalog::class.java.name)
                    .log(Level.WARNING, "남은 녹음 임시 파일 정리 실패: ${directory.absolutePath}", failure)
            }
        }
    }

    val recordings: Flow<List<Recording>> = dao.observeAll().onStart {
        reconcileWithFiles()
    }.map { rows ->
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
    }.flowOn(ioDispatcher)

    suspend fun registerCompletedFile(file: File, duration: Duration): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val completedFile = file.absoluteFile.normalize()
            require(isManagedRecording(completedFile)) { "앱이 관리하는 완성된 녹음 파일이 아닙니다." }
            check(readAttributesOrNull(completedFile)?.isRegularFile == true) { "녹음 파일을 찾을 수 없습니다." }
            dao.insertIfAbsent(completedFile.toEntity(duration))
        }
    }

    suspend fun deleteRecording(uri: String): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val file = File(uri).absoluteFile.normalize()
            require(isManagedRecording(file)) { "앱이 관리하는 녹음 파일이 아닙니다." }
            // 파일 삭제 실패 시 DB 항목을 유지합니다. DB 삭제만 실패하면 다음 복구에서 정리합니다.
            Files.deleteIfExists(file.toPath())
            dao.deleteById(file.absolutePath)
        }
    }

    suspend fun reconcileWithFiles(): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            // 모든 폴더를 먼저 읽습니다. 접근 오류를 빈 폴더로 취급하지 않습니다.
            val completedFiles = directories.flatMap { directory ->
                try {
                    Files.newDirectoryStream(directory.toPath()).use { entries ->
                        entries.map { it.toFile() }
                            .filter { isManagedRecording(it) && Files.isRegularFile(it.toPath()) }
                            .sortedBy { it.name }
                    }
                } catch (_: NoSuchFileException) {
                    emptyList()
                }
            }
            // 이전 백업은 절대 경로가 담긴 DB까지 복원할 수 있습니다. 현재 폴더의
            // 같은 녹음 파일을 찾으면 메타데이터를 옮기고, 찾지 못한 오래된 행은 지웁니다.
            for (row in dao.getAll()) {
                val oldFile = File(row.id)
                if (isManagedRecording(oldFile)) continue

                try {
                    val restoredFile = completedFiles.singleOrNull { it.name == oldFile.name }
                    if (restoredFile != null && COMPLETED_FILE_NAME.matches(oldFile.name)) {
                        dao.insertIfAbsent(row.copy(id = restoredFile.absolutePath))
                    }
                    dao.deleteById(row.id)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    logRecoveryFailure(oldFile, failure)
                }
            }

            val rows = dao.getAll()
            val registeredPaths = rows.map { it.id }.toSet()

            for (file in completedFiles) {
                if (file.absolutePath in registeredPaths) continue
                try {
                    dao.insertIfAbsent(file.toEntity(readDuration(file)))
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    logRecoveryFailure(file, failure)
                }
            }

            for (row in rows) {
                val file = File(row.id)
                if (!isManagedRecording(file)) continue
                try {
                    // 폴더 목록에 없다는 이유만으로 삭제하지 않고 실제 부재를 확인합니다.
                    if (readAttributesOrNull(file) == null) dao.deleteById(row.id)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    logRecoveryFailure(file, failure)
                }
            }
        }
    }

    private fun isManagedRecording(file: File): Boolean =
        file.absoluteFile.normalize().parentFile?.let { it in directories } == true &&
            COMPLETED_FILE_NAME.matches(file.name)

    private fun File.toEntity(duration: Duration): RecordingEntity {
        require(duration >= Duration.ZERO && duration.isFinite()) { "녹음 길이가 올바르지 않습니다." }
        val createdAt = Instant.ofEpochMilli(lastModified())
        val title = TITLE_TIME.format(createdAt.atZone(ZoneId.systemDefault()))
        return RecordingEntity(
            id = absolutePath,
            title = "녹음 · $title",
            createdAtEpochMs = createdAt.toEpochMilli(),
            durationMs = duration.inWholeMilliseconds,
        )
    }

    private fun readAttributesOrNull(file: File): BasicFileAttributes? = try {
        Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
    } catch (_: NoSuchFileException) {
        null
    }

    private fun logRecoveryFailure(file: File, failure: Exception) {
        Logger.getLogger(RoomRecordingCatalog::class.java.name)
            .log(Level.WARNING, "녹음 목록 복구 실패: ${file.name}", failure)
    }

    private companion object {
        val COMPLETED_FILE_NAME = Regex("rec_[0-9]{8}_[0-9]{6}_[0-9a-f]{8}\\.(wav|flac)")
        val TITLE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 HH:mm")
    }
}

private fun readAudioDuration(file: File): Duration {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        check(durationMs != null && durationMs >= 0) { "녹음 길이를 읽지 못했습니다." }
        return durationMs.milliseconds
    } finally {
        retriever.release()
    }
}
