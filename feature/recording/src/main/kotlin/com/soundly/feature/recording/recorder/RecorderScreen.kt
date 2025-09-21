package com.soundly.feature.recording.recorder

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun RecorderScreen(
    onBackClick: () -> Unit = {},
) {
    // --- Environment & service binding ---
    val context = LocalContext.current
    var recordingService by remember { mutableStateOf<RecordingForegroundService?>(null) }
    var isBound by remember { mutableStateOf(false) }

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as RecordingForegroundService.LocalBinder
                recordingService = binder.getService()
                isBound = true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                recordingService = null
                isBound = false
            }
        }
    }

    DisposableEffect(Unit) {
        val serviceIntent = Intent(context, RecordingForegroundService::class.java)
        context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        onDispose {
            if (isBound) context.unbindService(serviceConnection)
        }
    }

    // --- UI state from service (fallback to default) ---
    val uiState by recordingService?.uiState?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(RecorderUiState()) }

    // --- Permission & prefs ---
    var micPermissionGranted by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var isInitialized by remember { mutableStateOf(false) }

    val prefs = remember(context) {
        context.getSharedPreferences("recorder_prefs", Context.MODE_PRIVATE)
    }

    // --- Launchers ---
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        micPermissionGranted = isGranted
        if (!isGranted) {
            permanentlyDenied = true
            prefs.edit { putBoolean("mic_denied_once", true) }
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        micPermissionGranted = granted
        if (granted) {
            permanentlyDenied = false
            prefs.edit { remove("mic_denied_once") }
        } else {
            permanentlyDenied = true
        }
    }

    // --- Effects ---
    LaunchedEffect(Unit) {
        // 권한 초기 상태 반영
        micPermissionGranted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        permanentlyDenied = prefs.getBoolean("mic_denied_once", false)
        isInitialized = true
    }

    BackHandler {
        when (uiState.recordingState) {
            is RecordingState.Idle,
            is RecordingState.Error -> onBackClick()

            is RecordingState.Recording,
            is RecordingState.Paused -> recordingService?.stopRecording()

            is RecordingState.Stopping,
            is RecordingState.Stopped -> Unit
        }
    }

    LaunchedEffect(uiState) {
        if (uiState.recordingState is RecordingState.Stopped) onBackClick()
    }

    // --- Content ---
    if (!isInitialized) {
        RecordingLoadingScreen()
        return
    }

    RecorderScreen(
        modifier = Modifier.fillMaxSize(),
        state = uiState,
        micGranted = micPermissionGranted,
        onRequestMicPermission = {
            if (permanentlyDenied) {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
                settingsLauncher.launch(intent)
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onStartClick = {
            recordingService?.let { service ->
                val outputFile = createOutputFile(context)
                service.startRecording(outputFile)
            } ?: Log.e("RecorderScreen", "Service not bound, cannot start recording")
        },
        onPauseClick = { recordingService?.pauseRecording() },
        onResumeClick = { recordingService?.resumeRecording() },
        onStopClick = { recordingService?.stopRecording() },
        onResetError = {},
        permanentlyDenied = permanentlyDenied,
    )
}

private fun createOutputFile(context: Context): File {
    val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        ?: context.filesDir // 폴백: 내부 저장소
    val recordingsDir = File(baseDir, "recordings").apply { mkdirs() }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return File(recordingsDir, "rec_$stamp.wav")
}

@Composable
private fun RecorderScreen(
    modifier: Modifier,
    state: RecorderUiState,
    micGranted: Boolean,
    onRequestMicPermission: () -> Unit,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopClick: () -> Unit,
    onResetError: () -> Unit,
    permanentlyDenied: Boolean,
) {
    Surface(
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
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
                        elapsed = state.elapsed,
                        isPaused = false,
                        onPauseClick = onPauseClick,
                        onStopClick = onStopClick,
                    )
                }

                is RecordingState.Paused -> {
                    RecordingActiveContent(
                        modifier = Modifier.fillMaxSize(),
                        elapsed = state.elapsed,
                        isPaused = true,
                        onPauseClick = onResumeClick,
                        onStopClick = onStopClick,
                    )
                }

                is RecordingState.Stopping, RecordingState.Stopped -> {
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
}

@Composable
private fun RecordingLoadingScreen() {
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {}
}
