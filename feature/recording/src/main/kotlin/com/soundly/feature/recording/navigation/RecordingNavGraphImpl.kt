package com.soundly.feature.recording.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.soundly.feature.recording.api.RecordingNavGraph
import com.soundly.feature.recording.api.RecordingRoute
import com.soundly.feature.recording.recorder.RecorderScreen
import com.soundly.feature.recording.recordings.RecordingsScreen
import javax.inject.Inject

internal class RecordingNavGraphImpl @Inject constructor() : RecordingNavGraph {
    override fun buildNavGraph(
        navGraphBuilder: NavGraphBuilder,
        navInfo: Unit
    ) {
        navGraphBuilder.composable<RecordingRoute.Recorder> {
            RecorderScreen()
        }

        navGraphBuilder.composable<RecordingRoute.Recordings> {
            RecordingsScreen()
        }
    }
}