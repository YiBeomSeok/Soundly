# Media Recorder SDK

오디오 녹음 라이브러리입니다. WAV와 FLAC 포맷을 지원하며, 실시간 오디오 캡처와 효율적인 파일 저장을 제공합니다.

## 주요 기능

- **다중 포맷 지원**: WAV (무압축), FLAC (무손실 압축)
- **실시간 녹음**: Android AudioRecord 기반의 안정적인 오디오 캡처
- **상태 관리**: 녹음 상태를 Flow로 관찰 가능
- **일시정지/재개**: 녹음 중 일시정지 및 재개 기능


## 기본 사용법

### 1. WAV 녹음

```kotlin
import com.soundly.sdk.v0.media.recorder.factory.RecorderFactory
import java.io.File

class AudioRecorder {
    private var recorder: WavRecorder? = null
    
    suspend fun startRecording(outputFile: File) {
        recorder = RecorderFactory.WavRecorder(
            outputFile = outputFile,
            sampleRate = 44100,
            channelCount = 1,
            bitsPerSample = 16
        )
        
        recorder?.prepare()
        recorder?.start()
    }
    
    suspend fun stopRecording() {
        recorder?.stop()
        recorder?.release()
        recorder = null
    }
}
```

### 2. FLAC 녹음

```kotlin
suspend fun startFlacRecording(outputFile: File) {
    recorder = RecorderFactory.FlacRecorder(
        outputFile = outputFile,
        sampleRate = 44100,
        channelCount = 1
    )
    
    recorder?.prepare()
    recorder?.start()
}
```

### 3. 상태 모니터링

```kotlin
recorder.state.collect { state ->
    when (state.simple) {
        RecorderSimpleState.Idle -> updateUi("준비됨")
        RecorderSimpleState.Recording -> updateUi("녹음 중...")
        RecorderSimpleState.Paused -> updateUi("일시정지")
        RecorderSimpleState.Stopped -> updateUi("녹음 완료")
        RecorderSimpleState.Error -> {
            updateUi("오류: ${state.errorMessage}")
        }
    }
}
```

### 4. 일시정지/재개

```kotlin
suspend fun pauseRecording() {
    if (recorder.state.value.simple == RecorderSimpleState.Recording) {
        recorder.pause()
    }
}

suspend fun resumeRecording() {
    if (recorder.state.value.simple == RecorderSimpleState.Paused) {
        recorder.resume()
    }
}
```

## 커스텀 설정

```kotlin
val customRecorder = RecorderFactory.WavRecorder(
    outputFile = File(context.filesDir, "custom_recording.wav"),
    sampleRate = 16000,  // 16kHz
    channelCount = 1,    // 모노
    bitsPerSample = 16   // 16-bit
)
```

## 권한 설정

AndroidManifest.xml에 마이크 권한을 추가

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

런타임 권한 요청:

```kotlin
if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
    != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(this, 
        arrayOf(Manifest.permission.RECORD_AUDIO), 
        REQUEST_RECORD_AUDIO)
}
```

## 성능 최적화

### 버퍼 크기 조정

```kotlin
// 더 큰 버퍼로 지연 시간 감소
val recorder = RecorderFactory.WavRecorder(
    outputFile = outputFile,
    sampleRate = 44100,
    channelCount = 1,
    bitsPerSample = 16
)
```

### 메모리 관리

```kotlin
override fun onDestroy() {
    super.onDestroy()
    recorder?.release()  // 리소스 해제
    recorder = null
}
```

## 지원되는 오디오 포맷

| 포맷 | 샘플레이트 | 채널 | 비트 깊이 | 압축 |
|------|------------|------|-----------|------|
| WAV  | 8-48 kHz   | 1-2  | 16-bit    | 무압축 |
| FLAC | 8-48 kHz   | 1-2  | 16-bit    | 무손실 |
