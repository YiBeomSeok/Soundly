package com.soundly.data.recording

import com.soundly.data.recording.db.RecordingEntity
import com.soundly.domain.recording.Recording
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

internal fun RecordingEntity.toDomain(): Recording {
    return Recording(
        uri = id,
        title = title,
        createdAt = Instant.ofEpochMilli(createdAtEpochMs),
        duration = durationMs.milliseconds
    )
}

internal fun Recording.toEntity(): RecordingEntity {
    return RecordingEntity(
        id = uri,
        title = title,
        createdAtEpochMs = createdAt.toEpochMilli(),
        durationMs = duration.inWholeMilliseconds,
    )
}