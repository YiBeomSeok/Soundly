package com.soundly.feature.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.soundly.core.designsystem.theme.SoundlyTheme
import com.soundly.feature.recording.RecorderRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SoundlyTheme {
                RecorderRoute.Recordings()
            }
        }
    }
}