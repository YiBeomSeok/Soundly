package com.soundly.feature.recording.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.soundly.feature.recording.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * 포그라운드 서비스 수명과 알림을 관리하고 녹음 세션은 Controller에 맡깁니다.
 */
@AndroidEntryPoint
internal class RecordingForegroundService : LifecycleService() {
    @Inject lateinit var controller: RecordingController

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "Soundly 녹음"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.soundly.action.STOP_RECORDING"
        private const val ACTION_PAUSE = "com.soundly.action.PAUSE_RECORDING"
        private const val ACTION_RESUME = "com.soundly.action.RESUME_RECORDING"
    }

    private val binder = LocalBinder()
    val uiState get() = controller.uiState
    private var foregroundStarted = false

    inner class LocalBinder : Binder() {
        fun getService(): RecordingForegroundService = this@RecordingForegroundService
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        lifecycleScope.launch {
            controller.uiState
                .distinctUntilChangedBy { state -> state.recordingState to state.elapsed.inWholeSeconds }
                .collect { state ->
                    when (state.recordingState) {
                        RecordingState.Stopped, is RecordingState.Error -> {
                            if (foregroundStarted) stopForeground(STOP_FOREGROUND_REMOVE)
                            foregroundStarted = false
                            stopSelf()
                        }
                        else -> if (foregroundStarted) updateNotification(state)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> stopRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
        }
        if (!controller.isRunning) stopSelf(startId)
        return START_NOT_STICKY
    }

    fun startRecording(request: RecordingRequest) {
        if (controller.isRunning) return
        controller.start(request, lifecycleScope) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, RecordingForegroundService::class.java),
            )
            startForeground(
                NOTIFICATION_ID,
                buildNotification(RecorderUiState(recordingState = RecordingState.Starting)),
            )
            foregroundStarted = true
        }
    }

    fun pauseRecording() = controller.pause()
    fun resumeRecording() = controller.resume()
    fun stopRecording() = controller.stop()
    fun resetError() = controller.resetError()

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "녹음 상태와 일시정지·저장 제어"
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun updateNotification(state: RecorderUiState) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: RecorderUiState): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val text = when (state.recordingState) {
            RecordingState.Starting -> "녹음 준비 중"
            RecordingState.Recording -> "녹음 중"
            RecordingState.Paused -> "일시정지"
            RecordingState.Stopping -> "음성 저장 중"
            RecordingState.Stopped -> "녹음 저장 완료"
            else -> "Soundly 녹음"
        }
        val elapsed = state.elapsed.inWholeSeconds
        val duration = "%02d:%02d".format(elapsed / 60, elapsed % 60)
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_record_wave_24)
            .setContentTitle(text)
            .setContentText("$duration · Soundly")
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        when (state.recordingState) {
            RecordingState.Recording -> builder.addAction(R.drawable.ic_pause_24, "일시정지", createActionPendingIntent(ACTION_PAUSE))
            RecordingState.Paused -> builder.addAction(R.drawable.ic_resume_24, "녹음 재개", createActionPendingIntent(ACTION_RESUME))
            else -> Unit
        }
        if (state.recordingState == RecordingState.Recording || state.recordingState == RecordingState.Paused) {
            builder.addAction(R.drawable.ic_stop_24, "저장하고 종료", createActionPendingIntent(ACTION_STOP))
        }
        return builder.build()
    }

    private fun createActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, RecordingForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
