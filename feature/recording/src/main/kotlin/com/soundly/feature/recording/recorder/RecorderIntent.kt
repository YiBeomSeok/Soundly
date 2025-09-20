package com.soundly.feature.recording.recorder

import java.io.File

internal sealed interface RecorderIntent {
    data object RequestMicPermission : RecorderIntent
    data object OpenAppSettings : RecorderIntent
    data object StartRecording : RecorderIntent
    data object StopRecording : RecorderIntent
    data object ResetError : RecorderIntent
    data object PendingCommandHandled : RecorderIntent
    data object SetIdle : RecorderIntent
    data class SetRecordedFile(val file: File) : RecorderIntent
}


