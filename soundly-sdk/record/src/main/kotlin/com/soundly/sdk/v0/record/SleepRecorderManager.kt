package com.soundly.sdk.v0.record

import android.content.Context
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manager class for sleep recording operations.
 * Provides high-level API for recording sleep audio data.
 */
class SleepRecorderManager(
    private val context: Context,
    private val config: RecordingConfig = RecordingConfig()
) {
    
    private val recorder: SleepRecorder = SleepRecorderImpl(config)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    /**
     * Starts recording sleep audio data.
     * @param fileName Optional custom filename. If null, generates timestamp-based name.
     * @return RecordingResult indicating success or failure
     */
    suspend fun startSleepRecording(fileName: String? = null): RecordingResult {
        return try {
            val outputFile = createOutputFile(fileName)
            val success = recorder.startRecording(outputFile)
            
            if (success) {
                RecordingResult.Success
            } else {
                RecordingResult.Error("Failed to start recording")
            }
        } catch (e: Exception) {
            RecordingResult.Error("Failed to start recording", e)
        }
    }
    
    /**
     * Stops the current recording session.
     * @return RecordingResult indicating success or failure
     */
    suspend fun stopSleepRecording(): RecordingResult {
        return try {
            val success = recorder.stopRecording()
            
            if (success) {
                RecordingResult.Success
            } else {
                RecordingResult.Error("Failed to stop recording")
            }
        } catch (e: Exception) {
            RecordingResult.Error("Failed to stop recording", e)
        }
    }
    
    /**
     * Checks if recording is currently active.
     * @return true if recording is in progress
     */
    fun isRecording(): Boolean = recorder.isRecording()
    
    /**
     * Gets the current recording state.
     * @return RecordingState indicating current status
     */
    fun getRecordingState(): RecordingState = recorder.getRecordingState()
    
    /**
     * Gets the list of recorded files.
     * @return List of recorded audio files
     */
    fun getRecordedFiles(): List<File> {
        val recordingsDir = getRecordingsDirectory()
        return recordingsDir.listFiles()?.filter { it.isFile && it.extension == "pcm" } ?: emptyList()
    }
    
    /**
     * Deletes a recorded file.
     * @param file The file to delete
     * @return true if deletion was successful
     */
    fun deleteRecording(file: File): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Clears all recorded files.
     * @return Number of files deleted
     */
    fun clearAllRecordings(): Int {
        val files = getRecordedFiles()
        var deletedCount = 0
        
        files.forEach { file ->
            if (deleteRecording(file)) {
                deletedCount++
            }
        }
        
        return deletedCount
    }
    
    /**
     * Releases all resources and stops any ongoing recording.
     */
    fun release() {
        scope.launch {
            recorder.stopRecording()
        }
        recorder.release()
        scope.cancel()
    }
    
    private fun createOutputFile(fileName: String?): File {
        val recordingsDir = getRecordingsDirectory()
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
        }
        
        val actualFileName = fileName ?: generateFileName()
        return File(recordingsDir, actualFileName)
    }
    
    private fun getRecordingsDirectory(): File {
        return File(context.getExternalFilesDir(null), "sleep_recordings")
    }
    
    private fun generateFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "sleep_recording_$timestamp.pcm"
    }
}
