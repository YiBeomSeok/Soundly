package com.soundly.domain.recording.usecase

import com.soundly.domain.recording.repository.RecordingRepository
import javax.inject.Inject

interface DeleteRecordingUseCase {
    suspend operator fun invoke(uri: String): Boolean
}

internal class DeleteRecordingUseCaseImpl @Inject constructor(
    private val repository: RecordingRepository
) : DeleteRecordingUseCase {
    override suspend fun invoke(uri: String): Boolean {
        return repository.deleteRecording(uri)
    }
}


