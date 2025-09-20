package com.soundly.domain.recording.usecase

import com.soundly.domain.recording.Recording
import com.soundly.domain.recording.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface GetRecordingsUseCase {
    operator fun invoke(): Flow<List<Recording>>
}

internal class GetRecordingsUseCaseImpl @Inject constructor(
    private val repository: RecordingRepository
) : GetRecordingsUseCase {
    override fun invoke(): Flow<List<Recording>> {
        return repository.getRecordings()
    }
}