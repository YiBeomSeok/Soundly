package com.soundly.domain.recording

import java.time.Instant
import kotlin.time.Duration

data class Recording(
    val uri: String,
    val title: String,
    val createdAt: Instant,
    val duration: Duration,
)