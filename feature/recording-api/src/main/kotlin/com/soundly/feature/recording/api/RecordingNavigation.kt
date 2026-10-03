package com.soundly.feature.recording.api

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey

interface RecordingNavigation {
    fun createEntryProvider(
        onRecordClick: () -> Unit,
        onBackClick: () -> Unit,
    ): (NavKey) -> NavEntry<NavKey>
}
