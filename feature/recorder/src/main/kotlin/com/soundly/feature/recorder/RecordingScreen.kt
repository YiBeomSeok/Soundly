package com.soundly.feature.recorder

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soundly.sdk.v0.record.api.PcmRecorder
import com.soundly.sdk.v0.record.api.RecordingConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun RecordingScreen(
    viewModel: RecordingViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val recorder = remember { PcmRecorder() }
    val playbackManager = remember(context) {
        PlaybackManager(
            context = context,
            callbacks = object : PlaybackManager.Callbacks {
                override fun onBuffering() = viewModel.onBuffering()
                override fun onPlaying(durationMs: Long) = viewModel.onPlaying(durationMs)
                override fun onPaused() = viewModel.onPaused()
                override fun onStopped() = viewModel.onStopped()
                override fun onPosition(positionMs: Long) = viewModel.onPosition(positionMs)
                override fun onError(message: String) = viewModel.onPlaybackError(message)
            }
        )
    }
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

    // 영구 거절 판단: 권한 미보유 && (설명 필요=false) && (한번 이상 요청해봄)
    val shouldShowRationale = remember(micGranted, asked) {
        ActivityCompat.shouldShowRequestPermissionRationale(
            context.findActivity(),
            Manifest.permission.RECORD_AUDIO
        )
    }
    val permanentlyDenied = !micGranted && !shouldShowRationale && asked

    LaunchedEffect(uiState.recordingState, micGranted) {
        when (uiState.recordingState) {
            is RecordingState.Idle -> {
                val dir =
                    context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
                val recordingsDir = File(dir, "recordings").apply { mkdirs() }
                val list =
                    recordingsDir.listFiles()?.sortedByDescending { it.lastModified() }?.map { f ->
                        RecordingItem(
                            id = f.absolutePath,
                            file = f,
                            name = f.name,
                            sizeBytes = f.length(),
                            lastModified = f.lastModified(),
                            durationMs = null,
                        )
                    } ?: emptyList()
                viewModel.loadFiles(list)
            }

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

            is RecordingState.Stopping -> viewModel.setIdle()
            is RecordingState.Error -> Unit
        }
    }

    // Execute pending one-shot UI commands (permission, open settings, playback, delete)
    LaunchedEffect(uiState.pendingCommand) {
        when (val cmd = uiState.pendingCommand) {
            is UiCommand.RequestMicPermission -> {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }

            is UiCommand.OpenAppSettings -> {
                val intent = Intent(
                    ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
                settingsLauncher.launch(intent)
            }

            is UiCommand.StartPlayback -> {
                val item = uiState.files.firstOrNull { it.id == cmd.id }
                if (item != null) playbackManager.play(item.file)
            }

            is UiCommand.TogglePlayback -> {
                playbackManager.pauseOrResume()
            }

            is UiCommand.SeekPlayback -> {
                playbackManager.seekTo(cmd.positionMs)
            }

            is UiCommand.StopPlayback -> {
                playbackManager.stop()
            }

            is UiCommand.DeleteFile -> {
                val item = uiState.files.firstOrNull { it.id == cmd.id }
                if (item != null) {
                    runCatching { item.file.delete() }
                    val dir = item.file.parentFile
                    val list =
                        dir?.listFiles()?.sortedByDescending { it.lastModified() }?.map { f ->
                            RecordingItem(
                                id = f.absolutePath,
                                file = f,
                                name = f.name,
                                sizeBytes = f.length(),
                                lastModified = f.lastModified(),
                                durationMs = null,
                            )
                        } ?: emptyList()
                    viewModel.loadFiles(list)
                    if (uiState.playback.selectedId == item.id) viewModel.select(null)
                }
            }

            null -> Unit
        }
        if (uiState.pendingCommand != null) {
            viewModel.clearPendingCommand()
        }
    }

    RecordingScreen(
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

    RecordingsPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        showList = uiState.showList,
        files = uiState.files,
        playback = uiState.playback,
        onToggleList = { viewModel.toggleListVisibility() },
        onPlayPause = { item ->
            if (uiState.playback.selectedId != item.id) {
                viewModel.select(item.id)
                viewModel.intentStartPlayback(item.id)
            } else {
                viewModel.intentTogglePlayback(item.id)
            }
        },
        onDelete = { item -> viewModel.intentDeleteFile(item.id) },
        onSeek = { pos -> viewModel.intentSeek(pos) },
        onClose = { viewModel.intentStopPlayback() },
    )
}

@Composable
private fun RecordingScreen(
    modifier: Modifier,
    state: RecordingUiState,
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
private fun createOutputFile(context: Context): File {
    val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        ?: context.filesDir // 폴백: 내부 저장소
    val recordingsDir = File(baseDir, "recordings").apply { mkdirs() }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return File(recordingsDir, "rec_$stamp.wav")
}