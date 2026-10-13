package com.soundly

import android.app.Application
import android.os.Environment
import android.util.Log
import com.soundly.data.recording.RoomRecordingCatalog
import com.soundly.sdk.recording.storage.FileRecordingOutput
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
internal class SoundlyApp : Application() {
    @Inject lateinit var roomRecordingCatalog: RoomRecordingCatalog

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val recordingDirectories = listOfNotNull(
            getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { File(it, "recordings") },
            File(filesDir, "recordings"),
        )
        for (directory in recordingDirectories) {
            try {
                FileRecordingOutput.deleteAbandonedTemporaryFiles(directory)
            } catch (failure: Exception) {
                Log.w("SoundlyApp", "남은 녹음 임시 파일을 정리하지 못했습니다: $directory", failure)
            }
        }

        applicationScope.launch {
            try {
                roomRecordingCatalog.reconcileWithFiles()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                Log.w("SoundlyApp", "녹음 목록 복구를 실행하지 못했습니다.", failure)
            }
        }
    }
}
