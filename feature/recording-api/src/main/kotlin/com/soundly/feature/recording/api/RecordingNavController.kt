package com.soundly.feature.recording.api

import com.soundly.feature.nav.graph.SoundlyNavController

interface RecordingNavController : SoundlyNavController<RecordingNavControllerInfo>

data class RecordingNavControllerInfo(
    val type: RecordingRoute
)