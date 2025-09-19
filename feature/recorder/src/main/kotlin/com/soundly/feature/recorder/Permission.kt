package com.soundly.feature.recorder

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun rememberRecordAudioPermission(): PermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(isRecordAudioGranted(context)) }
    var requestedOnce by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted = it }
    )

    LaunchedEffect(Unit) {
        granted = isRecordAudioGranted(context)
    }

    return object : PermissionState {
        override val isGranted: Boolean get() = granted
        override val hasRequested: Boolean get() = requestedOnce
        override fun request() {
            requestedOnce = true
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

interface PermissionState {
    val isGranted: Boolean
    val hasRequested: Boolean
    fun request()
}

internal fun isRecordAudioGranted(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED


