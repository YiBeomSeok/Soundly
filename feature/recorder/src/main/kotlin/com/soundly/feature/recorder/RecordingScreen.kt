package com.soundly.feature.recorder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soundly.sdk.v0.record.api.PcmRecorder
import com.soundly.sdk.v0.record.api.RecordingConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecordingRoute {
    @Composable
    operator fun invoke() {
        RecordingScreen()
    }
}

private fun Context.findActivity(): Activity {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    error("No activity found")
}

@Composable
internal fun RecordingScreen(
    viewModel: RecordingViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val recorder: PcmRecorder = remember { PcmRecorder() }

    var micGranted by remember { mutableStateOf(isRecordAudioGranted(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        if (!granted) asked = true
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
            activity,
            Manifest.permission.RECORD_AUDIO
        )
    }
    val permanentlyDenied = !micGranted && !shouldShowRationale && asked

    fun openAppSettings() {
        val intent = Intent(
            ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        settingsLauncher.launch(intent)
    }

    LaunchedEffect(uiState.recordingState, micGranted) {
        when (uiState.recordingState) {
            is RecordingState.Idle -> Unit
            is RecordingState.Recording -> {
                if (!micGranted) {
                    // 영구 거절이면 시스템 다이얼로그 대신 설정으로
                    if (permanentlyDenied) {
                        openAppSettings()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    return@LaunchedEffect
                }
                val output = createOutputFile(context)
                @SuppressLint("MissingPermission")
                recorder.record(output = output, config = RecordingConfig())
            }

            is RecordingState.Stopping -> viewModel.setIdle()
            is RecordingState.Error -> Unit
        }
    }

    // ---- UI 렌더링 ----
    RecordingScreen(
        modifier = Modifier.fillMaxSize(),
        state = uiState,
        micGranted = micGranted,
        onRequestMicPermission = {
            when {
                micGranted -> Unit
                permanentlyDenied -> openAppSettings()
                else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onStartClick = {
            when {
                micGranted -> viewModel.setRecording()
                permanentlyDenied -> openAppSettings()
                else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onStopClick = { viewModel.setStopping() },
        onResetError = { viewModel.setIdle() },
        permanentlyDenied = permanentlyDenied, // 아래 UI에서 사용
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
    permanentlyDenied: Boolean, // 추가
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (val s = state.recordingState) {
            is RecordingState.Idle -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("준비됨", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))

                    if (!micGranted) {
                        if (permanentlyDenied) {
                            Text(
                                "마이크 권한이 영구적으로 거부되었습니다.\n설정에서 권한을 허용해 주세요.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = onRequestMicPermission) {
                                Text("설정 열기")
                            }
                        } else {
                            Text("마이크 권한이 필요합니다.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = onRequestMicPermission) {
                                Text("권한 요청")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Button(onClick = onStartClick, enabled = micGranted) {
                        Text("녹음 시작")
                    }
                }
            }

            is RecordingState.Recording -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("녹음 중...", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onStopClick) { Text("정지") }
                }
            }

            is RecordingState.Stopping -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("정지 중...", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator()
                }
            }

            is RecordingState.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "오류가 발생했습니다.",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    s.t?.message?.let { msg ->
                        Text(msg, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(onClick = onResetError) { Text("확인") }
                }
            }
        }
    }
}

private fun isRecordAudioGranted(context: Context): Boolean {
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    return granted
}

// 출력 파일 경로: /Android/data/<pkg>/files/Music/recordings/rec_yyyyMMdd_HHmmss.wav
private fun createOutputFile(context: Context): File {
    val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        ?: context.filesDir // 폴백: 내부 저장소
    val recordingsDir = File(baseDir, "recordings").apply { mkdirs() }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return File(recordingsDir, "rec_$stamp.wav")
}