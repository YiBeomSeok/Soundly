package com.soundly.feature.recording.recorder

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun RecorderRoute(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence(seed = context) { current ->
            (current as? ContextWrapper)?.baseContext
        }.filterIsInstance<Activity>().firstOrNull()
    }
    val prefs = remember(context) {
        context.getSharedPreferences("recorder_prefs", Context.MODE_PRIVATE)
    }
    var service by remember { mutableStateOf<RecordingForegroundService?>(null) }
    var granted by remember {
        val permission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        mutableStateOf(permission == PackageManager.PERMISSION_GRANTED)
    }
    var deniedPermanently by remember { mutableStateOf(false) }
    var selectedFormat by rememberSaveable { mutableStateOf(RecordingFormat.Flac) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 화면은 서비스에 연결해 관찰하며, 화면 이탈은 녹음 중지가 아닌 바인딩 해제만 수행합니다.
    DisposableEffect(context) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as? RecordingForegroundService.LocalBinder)?.getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        val intent = Intent(context, RecordingForegroundService::class.java)
        val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        onDispose {
            if (bound) context.unbindService(connection)
        }
    }

    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                val requestedBefore = prefs.getBoolean("mic_requested", false)
                val canExplain = activity?.let {
                    ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
                } == true
                deniedPermanently = !granted && requestedBefore && !canExplain
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { allowed ->
            granted = allowed
            prefs.edit { putBoolean("mic_requested", true) }
            deniedPermanently = !allowed && activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
            } == true
        },
    )
    val notifications = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )

    // 화면에 표시하는 초 단위로 관찰해 PCM 청크마다 전체 화면을 다시 그리지 않습니다.
    val observation = remember(service) {
        service?.uiState
            ?.map { state -> state.copy(elapsed = state.elapsed.inWholeSeconds.seconds) }
            ?.distinctUntilChanged()
            ?: flowOf(RecorderUiState())
    }
    val state by observation.collectAsStateWithLifecycle(initialValue = RecorderUiState())
    LaunchedEffect(state.recordingState) {
        if (state.recordingState == RecordingState.Stopped) onBackClick()
    }

    RecorderScreen(
        state = state,
        micGranted = granted,
        permanentlyDenied = deniedPermanently,
        selectedFormat = selectedFormat,
        ready = service != null,
        onBackClick = onBackClick,
        onSelectFormat = { selectedFormat = it },
        onRequestMicPermission = {
            if (deniedPermanently) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            } else {
                permission.launch(input = Manifest.permission.RECORD_AUDIO)
            }
        },
        onStartClick = {
            if (Build.VERSION.SDK_INT >= 33) {
                val notificationPermission = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                if (notificationPermission != PackageManager.PERMISSION_GRANTED) {
                    notifications.launch(input = Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            service?.startRecording(
                request = RecordingRequest.create(context = context, prefer = selectedFormat),
            )
        },
        onPauseClick = {
            if (state.recordingState == RecordingState.Paused) {
                service?.resumeRecording()
            } else {
                service?.pauseRecording()
            }
        },
        onStopClick = { service?.stopRecording() },
        onResetError = { service?.resetError() },
    )
}
