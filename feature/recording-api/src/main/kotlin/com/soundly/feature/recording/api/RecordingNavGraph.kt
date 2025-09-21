package com.soundly.feature.recording.api

import com.soundly.feature.nav.graph.SoundlyNavGraph

interface RecordingNavGraph : SoundlyNavGraph<RecordingNavGraphInfo>

data class RecordingNavGraphInfo(
    val recorder: Recorder,
    val recordings: Recordings,
) {
    data class Recorder(
        val onBackClick: () -> Unit,
    )

    data class Recordings(
        val onRecordClick: () -> Unit,
    )
}
