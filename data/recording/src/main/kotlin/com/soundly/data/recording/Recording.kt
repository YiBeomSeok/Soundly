package com.soundly.data.recording

import java.time.Instant
import kotlin.time.Duration

data class Recording(
    val uri: String,
    val title: String,
    val createdAt: Instant,
    val duration: Duration,
    val sizeBytes: Long = 0,
    val lastModified: Long = createdAt.toEpochMilli(),
)
