package com.soundly.feature.recording.recorder

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soundly.sdk.v0.record.api.PcmRecorder
import com.soundly.sdk.v0.record.api.RecordingConfig
import com.soundly.feature.recording.findActivity
import com.soundly.feature.recording.isRecordAudioGranted
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun RecorderScreen(
    viewModel: RecorderViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val recorder = remember { PcmRecorder() }
    var micGranted by remember { mutableStateOf(isRecordAudioGranted(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        if (!granted) {
            asked = true
        }
    }

    // 앱 설정 화면 런처 (설정 다녀오면 권한 다시 체크)
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        micGranted = isRecordAudioGranted(context)
    }

    val shouldShowRationale = remember(micGranted, asked) {
        ActivityCompat.shouldShowRequestPermissionRationale(
            context.findActivity(),
            Manifest.permission.RECORD_AUDIO
        )
    }
    val permanentlyDenied = !micGranted && !shouldShowRationale && asked

    LaunchedEffect(uiState.recordingState, micGranted) {
        when (uiState.recordingState) {
            is RecordingState.Idle -> Unit

            is RecordingState.Recording -> {
                if (!micGranted) {
                    if (permanentlyDenied) {
                        viewModel.intentOpenAppSettings()
                    } else {
                        viewModel.intentRequestMicPermission()
                    }
                    return@LaunchedEffect
                }
                val output = createOutputFile(context)
                @SuppressLint("MissingPermission")
                recorder.record(output = output, config = RecordingConfig())

                viewModel.onFileRecorded(output)
            }

            is RecordingState.Stopping -> {
                viewModel.setIdle()
            }

            is RecordingState.Error -> Unit
        }
    }

    LaunchedEffect(uiState.pendingCommand) {
        when (uiState.pendingCommand) {
            is RecorderUiCommand.RequestMicPermission -> {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }

            is RecorderUiCommand.OpenAppSettings -> {
                val intent = Intent(
                    ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
                settingsLauncher.launch(intent)
            }

            null -> Unit
        }

        if (uiState.pendingCommand != null) {
            viewModel.handlePendingCommand()
        }
    }

    Surface {
        Box(modifier = Modifier.fillMaxSize()) {
            RecorderScreen(
                modifier = Modifier.fillMaxSize(),
                state = uiState,
                micGranted = micGranted,
                onRequestMicPermission = {
                    if (permanentlyDenied) {
                        viewModel.intentOpenAppSettings()
                    } else {
                        viewModel.intentRequestMicPermission()
                    }
                },
                onStartClick = { viewModel.intentStartRecording() },
                onStopClick = { viewModel.intentStopRecording() },
                onResetError = { viewModel.setIdle() },
                permanentlyDenied = permanentlyDenied,
            )
        }
    }

}

@Composable
private fun RecorderScreen(
    modifier: Modifier,
    state: RecorderUiState,
    micGranted: Boolean,
    onRequestMicPermission: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onResetError: () -> Unit,
    permanentlyDenied: Boolean,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (val s = state.recordingState) {
            is RecordingState.Idle -> {
                RecordingIdleContent(
                    modifier = Modifier.fillMaxSize(),
                    micGranted = micGranted,
                    permanentlyDenied = permanentlyDenied,
                    onRequestMicPermission = onRequestMicPermission,
                    onStartClick = onStartClick,
                )
            }

            is RecordingState.Recording -> {
                RecordingActiveContent(
                    modifier = Modifier.fillMaxSize(),
                    onStopClick = onStopClick,
                )
            }

            is RecordingState.Stopping -> {
                RecordingStoppingContent(
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is RecordingState.Error -> {
                RecordingErrorContent(
                    modifier = Modifier.fillMaxSize(),
                    message = s.t?.message,
                    onResetError = onResetError,
                )
            }
        }
    }
}

// 출력 파일 경로: /Android/data/<pkg>/files/Music/recordings/rec_yyyyMMdd_HHmmss.wav
internal fun createOutputFile(context: Context): File {
    val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        ?: context.filesDir // 폴백: 내부 저장소
    val recordingsDir = File(baseDir, "recordings").apply { mkdirs() }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return File(recordingsDir, "rec_$stamp.wav")
}