package com.soundly.feature.recording.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.soundly.feature.recording.api.RecordingNavigation
import com.soundly.feature.recording.api.RecordingRoute
import com.soundly.feature.recording.recorder.RecorderRoute
import com.soundly.feature.recording.recordings.RecordingsRoute
import javax.inject.Inject

internal class RecordingNavigationImpl @Inject constructor() : RecordingNavigation {
    override fun createEntryProvider(
        onRecordClick: () -> Unit,
        onBackClick: () -> Unit,
    ): (NavKey) -> NavEntry<NavKey> = entryProvider {
        entry<RecordingRoute.Recordings> {
            RecordingsRoute(onRecordClick = onRecordClick)
        }
        entry<RecordingRoute.Recorder> {
            RecorderRoute(onBackClick = onBackClick)
        }
    }
}
