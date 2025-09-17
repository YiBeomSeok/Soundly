package com.soundly.sdk.v0.record

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Implementation of SleepRecorder using Android's AudioRecord API.
 */
class SleepRecorderImpl(
    private val config: RecordingConfig = RecordingConfig()
) : SleepRecorder {
    
    private val recordingState = AtomicReference(RecordingState.IDLE)
    private val recordingInProgress = AtomicBoolean(false)
    private val mutex = Mutex()
    
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var outputFile: File? = null
    
    private val bufferSize = AudioRecord.getMinBufferSize(
        config.sampleRate,
        config.channelConfig,
        config.audioFormat
    ) * config.bufferSizeFactor
    
    override suspend fun startRecording(outputFile: File): Boolean = mutex.withLock {
        if (recordingInProgress.get()) {
            return false
        }
        
        try {
            this.outputFile = outputFile
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                config.sampleRate,
                config.channelConfig,
                config.audioFormat,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                recordingState.set(RecordingState.ERROR)
                return false
            }
            
            audioRecord?.startRecording()
            recordingInProgress.set(true)
            recordingState.set(RecordingState.RECORDING)
            
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                performRecording()
            }
            
            true
        } catch (e: Exception) {
            recordingState.set(RecordingState.ERROR)
            false
        }
    }
    
    override suspend fun stopRecording(): Boolean = mutex.withLock {
        if (!recordingInProgress.get()) {
            return true
        }
        
        try {
            recordingState.set(RecordingState.STOPPING)
            recordingInProgress.set(false)
            
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            recordingJob?.cancel()
            recordingJob = null
            
            recordingState.set(RecordingState.IDLE)
            true
        } catch (e: Exception) {
            recordingState.set(RecordingState.ERROR)
            false
        }
    }
    
    override fun isRecording(): Boolean = recordingInProgress.get()
    
    override fun getRecordingState(): RecordingState = recordingState.get()
    
    override fun release() {
        runBlocking {
            stopRecording()
        }
    }
    
    private suspend fun performRecording() {
        val file = outputFile ?: return
        val buffer = ByteBuffer.allocateDirect(bufferSize)
        
        try {
            FileOutputStream(file).use { outputStream ->
                while (recordingInProgress.get()) {
                    val result = audioRecord?.read(buffer, bufferSize) ?: -1
                    
                    if (result < 0) {
                        throw RuntimeException("Reading of audio buffer failed: ${getBufferReadFailureReason(result)}")
                    }
                    
                    outputStream.write(buffer.array(), 0, bufferSize)
                    buffer.clear()
                }
            }
        } catch (e: IOException) {
            recordingState.set(RecordingState.ERROR)
            throw RuntimeException("Writing of recorded audio failed", e)
        }
    }
    
    private fun getBufferReadFailureReason(errorCode: Int): String {
        return when (errorCode) {
            AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
            AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
            AudioRecord.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT"
            AudioRecord.ERROR -> "ERROR"
            else -> "Unknown ($errorCode)"
        }
    }
}
