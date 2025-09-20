package com.soundly.feature.recording.navigation

import androidx.navigation.NavController
import com.soundly.feature.recording.api.RecordingNavController
import com.soundly.feature.recording.api.RecordingNavControllerInfo
import javax.inject.Inject

internal class RecordingNavControllerImpl @Inject constructor() : RecordingNavController {
    override fun navigate(navController: NavController, navInfo: RecordingNavControllerInfo) {
        navController.navigate(navInfo.type)
    }
}