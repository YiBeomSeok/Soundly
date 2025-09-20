package com.soundly.feature.recording.recordings

import kotlin.time.Duration

internal sealed interface RecordingsIntent {
    // UI side-effect requests
    data object RefreshFiles : RecordingsIntent
    data class StartPlayback(val id: String) : RecordingsIntent
    data object StopPlayback : RecordingsIntent
    data class DeleteFile(val id: String) : RecordingsIntent
    data object PendingCommandHandled : RecordingsIntent

    // State updates
    data class SetFiles(val files: List<RecordingItem>) : RecordingsIntent
    data class SetSelectedId(val id: String?) : RecordingsIntent
    data object Buffering : RecordingsIntent
    data class Playing(val duration: Duration) : RecordingsIntent
    data object Paused : RecordingsIntent
    data object Idle : RecordingsIntent
    data class Position(val position: Duration) : RecordingsIntent
    data class PlaybackError(val message: String) : RecordingsIntent
}


