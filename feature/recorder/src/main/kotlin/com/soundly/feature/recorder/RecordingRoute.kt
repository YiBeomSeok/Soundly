package com.soundly.feature.recorder

import androidx.compose.runtime.Composable

object RecordingRoute {
    @Composable
    operator fun invoke() {
        RecordingScreen()
    }
}