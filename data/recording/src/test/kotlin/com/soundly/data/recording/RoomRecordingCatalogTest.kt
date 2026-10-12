package com.soundly.data.recording

import com.soundly.data.recording.db.RecordingDao
import com.soundly.data.recording.db.RecordingEntity
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RoomRecordingCatalogTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val dao = FakeRecordingDao()

    @Test fun `DB 등록 실패 후 완성 파일을 다음 복구에서 등록한다`() = runTest {
        val directory = temporaryFolder.newFolder("recordings")
        val file = completedFile(directory)
        val catalog = catalog(directory)
        dao.insertFailure = IllegalStateException("DB unavailable")

        try {
            catalog.registerCompletedFile(file, 3.seconds)
            fail("등록 실패가 전달되어야 합니다")
        } catch (_: IllegalStateException) {
            assertTrue(file.exists())
            assertTrue(dao.rows.value.isEmpty())
        }

        dao.insertFailure = null
        catalog.reconcileWithFiles()
        assertEquals(file.absolutePath, dao.rows.value.single().id)
        assertEquals(3_000L, dao.rows.value.single().durationMs)
    }

    @Test fun `복구를 반복해도 기존 제목과 한 개의 DB 항목을 유지한다`() = runTest {
        val directory = temporaryFolder.newFolder("recordings")
        val file = completedFile(directory)
        val catalog = catalog(directory)
        catalog.registerCompletedFile(file, 3.seconds)
        dao.rows.value = listOf(dao.rows.value.single().copy(title = "사용자가 바꾼 제목"))

        catalog.reconcileWithFiles()
        catalog.reconcileWithFiles()
        catalog.registerCompletedFile(file, 9.seconds)

        assertEquals(1, dao.rows.value.size)
        assertEquals("사용자가 바꾼 제목", dao.rows.value.single().title)
        assertEquals(3_000L, dao.rows.value.single().durationMs)
    }

    @Test fun `완성되지 않은 파일과 관리 범위 밖의 파일은 복구하지 않는다`() = runTest {
        val directory = temporaryFolder.newFolder("recordings")
        File(directory, ".soundly-123.part").writeText("partial")
        File(directory, "notes.wav").writeText("other")
        completedFile(temporaryFolder.newFolder("elsewhere"))
        catalog(directory).reconcileWithFiles()
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test fun `파일 삭제 후 DB 삭제가 실패하면 다음 복구에서 DB 항목을 정리한다`() = runTest {
        val directory = temporaryFolder.newFolder("recordings")
        val file = completedFile(directory)
        val catalog = catalog(directory)
        catalog.registerCompletedFile(file, 3.seconds)
        dao.deleteFailure = IllegalStateException("DB unavailable")

        try {
            catalog.deleteRecording(file.absolutePath)
            fail("삭제 실패가 전달되어야 합니다")
        } catch (_: IllegalStateException) {
            assertFalse(file.exists())
            assertEquals(1, dao.rows.value.size)
        }

        dao.deleteFailure = null
        catalog.reconcileWithFiles()
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test fun `폴더 접근 실패 시 등록된 항목을 삭제하지 않는다`() = runTest {
        val directory = temporaryFolder.newFile("recordings") // 디렉터리가 아닌 경로
        val path = File(directory, "rec_20261012_120000_abcdef12.wav").absolutePath
        dao.rows.value = listOf(RecordingEntity(path, "기존 녹음", 0, 1_000))

        try {
            catalog(directory).reconcileWithFiles()
            fail("폴더 접근 오류가 전달되어야 합니다")
        } catch (_: Exception) {
            assertEquals(1, dao.rows.value.size)
        }
    }

    @Test fun `복구와 삭제가 겹쳐도 삭제된 녹음을 다시 등록하지 않는다`() = runTest {
        val directory = temporaryFolder.newFolder("recordings")
        val file = completedFile(directory)
        val catalog = catalog(directory)
        dao.insertEntered = CompletableDeferred()
        dao.insertGate = CompletableDeferred()

        val recovery = async { catalog.reconcileWithFiles() }
        dao.insertEntered!!.await()
        val deletion = async { catalog.deleteRecording(file.absolutePath) }
        runCurrent()
        assertFalse(deletion.isCompleted)

        dao.insertGate!!.complete(Unit)
        recovery.await()
        deletion.await()
        assertFalse(file.exists())
        assertTrue(dao.rows.value.isEmpty())
    }

    private fun catalog(directory: File): RoomRecordingCatalog =
        RoomRecordingCatalog(dao, listOf(directory), { 3.seconds }, kotlinx.coroutines.Dispatchers.IO)

    private fun completedFile(directory: File): File =
        File(directory, "rec_20261012_120000_abcdef12.wav").apply { writeText("completed") }
}

private class FakeRecordingDao : RecordingDao {
    val rows = MutableStateFlow<List<RecordingEntity>>(emptyList())
    var insertFailure: Exception? = null
    var deleteFailure: Exception? = null
    var insertEntered: CompletableDeferred<Unit>? = null
    var insertGate: CompletableDeferred<Unit>? = null

    override suspend fun insertIfAbsent(entity: RecordingEntity) {
        insertEntered?.complete(Unit)
        insertGate?.await()
        insertFailure?.let { throw it }
        if (rows.value.none { it.id == entity.id }) rows.value = rows.value + entity
    }

    override suspend fun deleteById(id: String) {
        deleteFailure?.let { throw it }
        rows.value = rows.value.filterNot { it.id == id }
    }

    override fun observeAll(): Flow<List<RecordingEntity>> = rows
    override suspend fun getAll(): List<RecordingEntity> = rows.value
}
