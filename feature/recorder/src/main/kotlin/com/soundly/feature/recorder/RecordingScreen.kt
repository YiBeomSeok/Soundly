package com.soundly.feature.recorder

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soundly.sdk.v0.record.api.PcmRecorder
import com.soundly.sdk.v0.record.pcm.AndroidPcmRecorder

@Composable
fun RecordingRoute(
    viewModel: RecordingViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Own the Android recorder lifecycle inside the Composable
    val permission = rememberRecordAudioPermission()
    val context = LocalContext.current
    val recorder: PcmRecorder = remember { AndroidPcmRecorder() }
    val manager = remember { RecorderManager(recorder) }
    DisposableEffect(Unit) {
        onDispose { manager.release() }
    }

    RecordingScreen(
        modifier = Modifier.fillMaxSize(),
        state = state,
        onStartClick = {
            // Always re-check permission just before starting
            // Re-evaluate OS-level permission in addition to the cached PermissionState
            if (!permission.isGranted || !isRecordAudioGranted(context)) {
                permission.request()
                // Defensive re-evaluation in case permission changed synchronously
                return@RecordingScreen
            }
            @SuppressLint("MissingPermission")
            manager.start(
                targetDir = context.externalCacheDir!!,
                onSuccess = { path -> viewModel.markStartRecording(path) },
                onError = { msg -> viewModel.markStartFailed(msg) },
            )
        },
        onStopClick = {
            manager.stop(
                onStopped = { viewModel.markStopRecording() },
                onError = { msg -> viewModel.markStartFailed(msg) }
            )
        },
    )
}

// State hoisting: caller owns UI state and event handlers.
@Composable
fun RecordingScreen(
    state: RecordingUiState,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (state.isRecording) formatElapsed(state.elapsedMs) else "Ready",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onStartClick,
                enabled = !state.isRecording && state.canRecord,
                modifier = Modifier.weight(1f)
            ) { Text("Start") }

            Button(
                onClick = onStopClick,
                enabled = state.isRecording,
                modifier = Modifier.weight(1f)
            ) { Text("Stop") }
        }

        Spacer(Modifier.height(12.dp))
        state.currentFilePath?.let { path ->
            Text(text = path, style = MaterialTheme.typography.bodySmall)
        }
        state.errorMessage?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(text = msg, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}


