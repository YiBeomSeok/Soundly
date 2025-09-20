package com.soundly.domain.recording.usecase

import com.soundly.domain.recording.Recording
import com.soundly.domain.recording.repository.RecordingRepository
import javax.inject.Inject

interface IndexRecordingUseCase {
    suspend operator fun invoke(recording: Recording)
}

class IndexRecordingUseCaseImpl @Inject constructor(
    private val repository: RecordingRepository,
) : IndexRecordingUseCase {

    override suspend fun invoke(recording: Recording) {
        repository.saveRecording(recording)
    }
}