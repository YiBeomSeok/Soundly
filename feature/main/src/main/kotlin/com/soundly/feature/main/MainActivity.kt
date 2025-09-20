package com.soundly.feature.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.soundly.core.designsystem.theme.SoundlyTheme
import com.soundly.feature.recording.api.RecordingNavController
import com.soundly.feature.recording.api.RecordingNavControllerInfo
import com.soundly.feature.recording.api.RecordingNavGraph
import com.soundly.feature.recording.api.RecordingNavGraphInfo
import com.soundly.feature.recording.api.RecordingRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var recordingNavGraph: RecordingNavGraph

    @Inject
    lateinit var recordingNavController: RecordingNavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val navController = rememberNavController()
            SoundlyTheme {
                Surface {
                    NavHost(
                        navController = navController,
                        startDestination = RecordingRoute.Recordings
                    ) {
                        recordingNavGraph.buildNavGraph(
                            navGraphBuilder = this,
                            navInfo = RecordingNavGraphInfo(
                                onRecordClick = {
                                    recordingNavController.navigate(
                                        navController,
                                        RecordingNavControllerInfo(type = RecordingRoute.Recorder)
                                    )
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}