package com.soundly.feature.recorder

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.soundly.sdk.v0.record.PermissionUtils
import com.soundly.sdk.v0.record.RecordingState
import com.soundly.sdk.v0.record.SleepRecorder
import com.soundly.sdk.v0.record.SleepRecorderImpl
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun RecordingScreen() {
    val context = LocalContext.current
    val recorder: SleepRecorder = remember { SleepRecorderImpl() }
    val recordingState by recorder.state.collectAsState()
    val scope = rememberCoroutineScope()
    var recordingJob by remember { mutableStateOf<Job?>(null) }

    var hasPermissions by remember {
        mutableStateOf(PermissionUtils.hasAllPermissions(context))
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
    }

    DisposableEffect(Unit) {
        onDispose {
            recordingJob?.cancel()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Recording State: $recordingState")

        Spacer(modifier = Modifier.height(16.dp))

        if (!hasPermissions) {
            Button(onClick = { permissionsLauncher.launch(PermissionUtils.REQUIRED_PERMISSIONS) }) {
                Text("Request Permissions")
            }
        } else {
            Button(
                onClick = {
                    recordingJob = scope.launch {
                        val outputFile = createRecordingFile(context)
                        try {
                            recorder.record(outputFile)
                        } catch (e: Exception) {
                            // Handle exception, e.g., show a toast
                            e.printStackTrace()
                        }
                    }
                },
                enabled = recordingState == RecordingState.IDLE && hasPermissions
            ) {
                Text("Start Recording")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    recordingJob?.cancel()
                },
                enabled = recordingState == RecordingState.RECORDING
            ) {
                Text("Stop Recording")
            }
        }
    }
}

private fun createRecordingFile(context: Context): File {
    val timestamp = System.currentTimeMillis()
    val fileName = "sleep_recording_$timestamp.wav"
    // Use app's external cache directory which doesn't require storage permissions on API 29+
    val storageDir = context.externalCacheDir ?: context.cacheDir
    return File(storageDir, fileName)
}
