package com.soundly.feature.recording.recorder

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.soundly.sdk.v0.record.AudioRecorderWav
import com.soundly.domain.recording.Recording
import com.soundly.domain.recording.usecase.IndexRecordingUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@AndroidEntryPoint
internal class RecordingForegroundService : LifecycleService() {

    @Inject
    lateinit var indexRecordingUseCase: IndexRecordingUseCase

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "Recording"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.soundly.action.STOP_RECORDING"
        private const val ACTION_PAUSE = "com.soundly.action.PAUSE_RECORDING"
        private const val ACTION_RESUME = "com.soundly.action.RESUME_RECORDING"
    }

    private val binder = LocalBinder()
    private val _uiState = MutableStateFlow(RecorderUiState())
    val uiState = _uiState.asStateFlow()

    private var audioRecorder: AudioRecorderWav? = null
    private var timerJob: Job? = null
    private var recordingStartTime: Long = 0
    private var pausedDuration: Duration = 0.seconds
    private var currentOutputFile: File? = null

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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> stopRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
    }

    @SuppressLint("MissingPermission")
    fun startRecording(outputFile: File) {
        if (_uiState.value.recordingState !is RecordingState.Idle) return

        try {
            // 포그라운드 서비스 시작
            startForeground(NOTIFICATION_ID, buildNotification("Starting recording..."))

            currentOutputFile = outputFile
            audioRecorder = AudioRecorderWav().apply {
                record(outputFile)
            }

            recordingStartTime = System.currentTimeMillis()
            pausedDuration = 0.seconds
            _uiState.update {
                it.copy(
                    recordingState = RecordingState.Recording,
                    elapsed = 0.seconds,
                    isPaused = false
                )
            }

            startTimer()
            updateNotification("Recording...")

        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    recordingState = RecordingState.Error(e)
                )
            }
            updateNotification("Error: ${e.message}")
        }
    }

    fun pauseRecording() {
        val currentState = _uiState.value
        if (currentState.recordingState is RecordingState.Recording) {
            audioRecorder?.pause()
            timerJob?.cancel()
            _uiState.update {
                it.copy(
                    recordingState = RecordingState.Paused,
                    isPaused = true
                )
            }
            updateNotification("Paused")
        }
    }

    fun resumeRecording() {
        val currentState = _uiState.value
        if (currentState.recordingState !is RecordingState.Paused) return

        audioRecorder?.resume()
        pausedDuration = currentState.elapsed
        recordingStartTime = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                recordingState = RecordingState.Recording,
                isPaused = false
            )
        }
        startTimer()
        updateNotification("Recording...")
    }

    fun stopRecording() {
        timerJob?.cancel()

        val currentState = _uiState.value
        if (currentState.recordingState !is RecordingState.Recording && currentState.recordingState !is RecordingState.Paused) return

        val finalDuration = currentState.elapsed
        _uiState.update { it.copy(recordingState = RecordingState.Stopping) }
        updateNotification("Stopping...")

        audioRecorder?.stop()

        val file = currentOutputFile ?: error("currentOutputFile is null")
        if (!file.exists() && file.length() <= 0) error("File is empty or does not exist")

        Log.e("AudioRecorder", "File saved at: ${file.absolutePath}")
        Log.e("AudioRecorder", "File exists: ${file.exists()}")
        Log.e("AudioRecorder", "File length: ${file.length()}")
        Log.e("AudioRecorder", "Final duration: $finalDuration")

        lifecycleScope.launch {
            val recording = Recording(
                uri = file.absolutePath,
                title = file.nameWithoutExtension,
                createdAt = Instant.ofEpochMilli(file.lastModified()),
                duration = finalDuration
            )
            indexRecordingUseCase(recording)
            Log.d("AudioRecorder", "Recording saved to database successfully!")

            updateNotification("Recording saved")

            _uiState.update {
                it.copy(
                    recordingState = RecordingState.Idle,
                    elapsed = 0.seconds,
                    isPaused = false
                )
            }

            // 서비스 종료
            audioRecorder = null
            currentOutputFile = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startTimer() {
        timerJob = lifecycleScope.launch {
            while (isActive && _uiState.value.recordingState is RecordingState.Recording) {
                val d = (System.currentTimeMillis() - recordingStartTime).milliseconds
                val elapsed = pausedDuration + d
                _uiState.update { currentState ->
                    if (currentState.recordingState is RecordingState.Recording) {
                        currentState.copy(elapsed = elapsed)
                    } else {
                        currentState
                    }
                }
                delay(1000)
            }
        }
    }

    private fun ensureNotificationChannel() {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Recording notifications"
            setSound(null, null)
        }
        mgr.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Sound Recording")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(contentIntent)

        // 현재 상태에 따른 액션 버튼 추가
        when (_uiState.value.recordingState) {
            is RecordingState.Recording -> {
                builder.addAction(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    createActionPendingIntent(ACTION_PAUSE)
                )
                builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    createActionPendingIntent(ACTION_STOP)
                )
            }

            is RecordingState.Paused -> {
                builder.addAction(
                    android.R.drawable.ic_media_play,
                    "Resume",
                    createActionPendingIntent(ACTION_RESUME)
                )
                builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    createActionPendingIntent(ACTION_STOP)
                )
            }

            else -> {
                builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    createActionPendingIntent(ACTION_STOP)
                )
            }
        }

        return builder.build()
    }

    private fun updateNotification(text: String) {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIFICATION_ID, buildNotification(text))
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
