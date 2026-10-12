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
import com.soundly.sdk.recording.api.RecordingInputs
import com.soundly.sdk.recording.api.RecordingOutputs
import com.soundly.sdk.recording.api.RecordingState as SdkRecordingState
import com.soundly.sdk.recording.api.Recorder
import com.soundly.sdk.recording.api.RecordingControls
import com.soundly.sdk.recording.api.RecordingEvent
import com.soundly.sdk.recording.api.RecordedAudio
import com.soundly.sdk.recording.api.RecordingTarget.Companion.auto
import com.soundly.sdk.recording.api.RecordingTarget.Companion.flac
import com.soundly.sdk.recording.api.RecordingTarget.Companion.wav
import com.soundly.data.recording.RoomRecordingCatalog
import com.soundly.feature.recording.R
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * 녹음 수집 → 화면 상태 반영 → 알림 갱신 → 종료를 소유하며, PCM 처리는 SDK에 맡깁니다.
 */
@AndroidEntryPoint
internal class RecordingForegroundService : LifecycleService() {
    @Inject lateinit var recorder: Recorder
    @Inject lateinit var inputs: RecordingInputs
    @Inject lateinit var outputs: RecordingOutputs
    @Inject lateinit var roomRecordingCatalog: RoomRecordingCatalog

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "Soundly 녹음"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.soundly.action.STOP_RECORDING"
        private const val ACTION_PAUSE = "com.soundly.action.PAUSE_RECORDING"
        private const val ACTION_RESUME = "com.soundly.action.RESUME_RECORDING"
    }

    private val binder = LocalBinder()
    private val mutableState = MutableStateFlow(RecorderUiState())
    val uiState = mutableState.asStateFlow()
    private var recordingJob: Job? = null
    private var controls: RecordingControls? = null

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
        return START_NOT_STICKY
    }

    fun startRecording(request: RecordingRequest) {
        if (recordingJob != null) return

        val sessionControls = RecordingControls()
        controls = sessionControls

        // 작업을 필드에 보관한 뒤 시작하여, 빠른 종료나 연속 요청도 같은 실행 범위에서 처리합니다.
        recordingJob = lifecycleScope.launch(start = CoroutineStart.LAZY) {
            var pendingRegistration: RecordedAudio? = null

            flow {
                mutableState.value = RecorderUiState(recordingState = RecordingState.Starting)
                ContextCompat.startForegroundService(
                    this@RecordingForegroundService,
                    Intent(this@RecordingForegroundService, RecordingForegroundService::class.java),
                )
                startForeground(NOTIFICATION_ID, buildNotification(mutableState.value))

                val target = when (request.prefer) {
                    RecordingFormat.Wav -> wav(File(request.dir, "${request.baseName}.wav"))
                    RecordingFormat.Flac -> flac(File(request.dir, "${request.baseName}.flac"))
                    RecordingFormat.Auto -> auto(File(request.dir, "${request.baseName}.flac"))
                }

                emitAll(recorder.record {
                    input = inputs.microphone(config)
                    output = outputs.file(target)
                    controls = sessionControls
                })
            }.onEach { event ->
                // 녹음 사건을 화면 상태로 반영하고, 파일이 확정된 경우에만 목록에 등록합니다.
                val current = mutableState.value
                mutableState.value = when (event) {
                    is RecordingEvent.State -> RecorderUiState(
                        recordingState = when (event.state) {
                            SdkRecordingState.Starting -> RecordingState.Starting
                            SdkRecordingState.Active -> RecordingState.Recording
                            SdkRecordingState.Paused -> RecordingState.Paused
                            SdkRecordingState.Stopping -> RecordingState.Stopping
                        },
                        elapsed = event.progress.duration,
                    )
                    is RecordingEvent.Progress -> current.copy(elapsed = event.progress.duration)
                    is RecordingEvent.Completed -> {
                        val audio = checkNotNull(event.result as? RecordedAudio) { "File output is required" }
                        pendingRegistration = audio
                        roomRecordingCatalog.registerCompletedFile(audio.file, audio.progress.duration)
                        pendingRegistration = null
                        RecorderUiState(
                            recordingState = RecordingState.Stopped,
                            elapsed = audio.progress.duration,
                        )
                    }
                }
            }.map { mutableState.value }
                // 알림은 상태나 표시할 초가 바뀐 경우에만 갱신합니다.
                .distinctUntilChangedBy { state -> state.recordingState to state.elapsed.inWholeSeconds }
                .onEach { state -> updateNotification(state) }
                .catch { failure ->
                    if (failure is CancellationException) throw failure

                    val error = pendingRegistration?.let { audio ->
                        IllegalStateException(
                            "녹음 파일은 저장됐지만 목록 등록에 실패했습니다: ${audio.file.name}",
                            failure,
                        )
                    } ?: failure
                    mutableState.value = mutableState.value.copy(recordingState = RecordingState.Error(error))
                }
                .onCompletion {
                    // 정상 종료·실패·취소 모두 이곳에서 작업과 포그라운드 알림을 정리합니다.
                    controls = null
                    recordingJob = null
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                .collect()
        }
        recordingJob?.start()
    }

    fun pauseRecording() { controls?.pause() }
    fun resumeRecording() { controls?.resume() }
    fun stopRecording() { controls?.stop() }

    fun resetError() {
        if (recordingJob == null && mutableState.value.recordingState is RecordingState.Error) {
            mutableState.value = RecorderUiState()
        }
    }

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
