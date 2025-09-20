package com.soundly.feature.recording.api

import com.soundly.feature.nav.graph.SoundlyNavGraph

interface RecordingNavGraph : SoundlyNavGraph<RecordingNavGraphInfo>

data class RecordingNavGraphInfo(
    val onRecordClick: () -> Unit
)