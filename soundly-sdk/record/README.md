# Sleep Recorder SDK v0

A Kotlin-based audio recording library for sleep analysis applications, built on top of Android's AudioRecord API.

## Features

- **High-quality audio recording** using Android's AudioRecord API
- **Coroutine-based** asynchronous recording operations
- **File management** with automatic timestamp-based naming
- **Permission handling** utilities for audio recording
- **State management** with comprehensive recording states
- **Error handling** with detailed error reporting

## Setup

Add the module to your project:

```kotlin
dependencies {
    implementation(project(":soundly-sdk:record"))
}
```

## Permissions

Add the following permissions to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

## Usage

### Basic Recording

```kotlin
class SleepRecordingActivity : AppCompatActivity() {
    private lateinit var recorderManager: SleepRecorderManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize the recorder manager
        recorderManager = SleepRecorderManager(this)
        
        // Check permissions before starting
        if (PermissionUtils.hasAllPermissions(this)) {
            startRecording()
        } else {
            // Request permissions
            requestPermissions()
        }
    }
    
    private fun startRecording() {
        lifecycleScope.launch {
            val result = recorderManager.startSleepRecording()
            when (result) {
                is RecordingResult.Success -> {
                    // Recording started successfully
                    updateUI(RecordingState.RECORDING)
                }
                is RecordingResult.Error -> {
                    // Handle error
                    showError(result.message)
                }
            }
        }
    }
    
    private fun stopRecording() {
        lifecycleScope.launch {
            val result = recorderManager.stopSleepRecording()
            when (result) {
                is RecordingResult.Success -> {
                    // Recording stopped successfully
                    updateUI(RecordingState.IDLE)
                }
                is RecordingResult.Error -> {
                    // Handle error
                    showError(result.message)
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        recorderManager.release()
    }
}
```

### Custom Configuration

```kotlin
val customConfig = RecordingConfig(
    sampleRate = 48000,
    channelConfig = AudioFormat.CHANNEL_IN_STEREO,
    audioFormat = AudioFormat.ENCODING_PCM_16BIT,
    bufferSizeFactor = 3
)

val recorderManager = SleepRecorderManager(context, customConfig)
```

### File Management

```kotlin
// Get list of recorded files
val recordedFiles = recorderManager.getRecordedFiles()

// Delete a specific recording
recorderManager.deleteRecording(recordedFile)

// Clear all recordings
val deletedCount = recorderManager.clearAllRecordings()
```

### Permission Handling

```kotlin
// Check if all permissions are granted
if (PermissionUtils.hasAllPermissions(context)) {
    // Start recording
} else {
    // Request missing permissions
    val missingPermissions = PermissionUtils.getMissingPermissions(context)
    // Request permissions using ActivityCompat.requestPermissions
}

// Check specific permissions
if (PermissionUtils.hasRecordAudioPermission(context)) {
    // Audio recording permission is granted
}
```

## API Reference

### SleepRecorderManager

- `startSleepRecording(fileName: String? = null): RecordingResult`
- `stopSleepRecording(): RecordingResult`
- `isRecording(): Boolean`
- `getRecordingState(): RecordingState`
- `getRecordedFiles(): List<File>`
- `deleteRecording(file: File): Boolean`
- `clearAllRecordings(): Int`
- `release()`

### RecordingState

- `IDLE` - No recording in progress
- `RECORDING` - Currently recording
- `STOPPING` - Recording is being stopped
- `ERROR` - An error occurred

### RecordingResult

- `Success` - Operation completed successfully
- `Error(message: String, cause: Throwable?)` - Operation failed with error details

## Technical Details

- **Sample Rate**: 44.1kHz (configurable)
- **Audio Format**: PCM 16-bit
- **Channel**: Mono (configurable to stereo)
- **File Format**: Raw PCM data (.pcm extension)
- **Buffer Size**: 2x minimum buffer size (configurable)

## Thread Safety

All public methods are thread-safe and can be called from any thread. The library uses coroutines for asynchronous operations and proper synchronization mechanisms.

## Error Handling

The library provides comprehensive error handling with detailed error messages and optional exception causes. Always check the `RecordingResult` for operation success/failure.
