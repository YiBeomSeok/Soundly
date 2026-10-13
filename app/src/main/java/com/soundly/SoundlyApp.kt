package com.soundly

import android.app.Application
import com.soundly.data.recording.RoomRecordingCatalog
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
internal class SoundlyApp : Application() {
    @Inject lateinit var roomRecordingCatalog: RoomRecordingCatalog

    override fun onCreate() {
        super.onCreate()
        roomRecordingCatalog.deleteAbandonedParts()
    }
}
