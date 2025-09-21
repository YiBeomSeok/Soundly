package com.soundly.feature.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material3.Surface
import androidx.lifecycle.Lifecycle
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
                        startDestination = RecordingRoute.Recordings,
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None },
                    ) {
                        recordingNavGraph.buildNavGraph(
                            navGraphBuilder = this,
                            navInfo = RecordingNavGraphInfo(
                                RecordingNavGraphInfo.Recorder(
                                    onBackClick = {
                                        if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                                            navController.popBackStack()
                                        }
                                    }
                                ),
                                RecordingNavGraphInfo.Recordings(
                                    onRecordClick = {
                                        recordingNavController.navigate(
                                            navController,
                                            RecordingNavControllerInfo(type = RecordingRoute.Recorder)
                                        )
                                    }
                                )

                            )
                        )
                    }
                }
            }
        }
    }
}