# Soundly Recording

음성 수집의 입력과 출력을 선택하고 cold Flow를 한 번 수집한다. SDK는 생산자, 소비자 버퍼, 장치와 출력의 수명, 정상 마감과 실패 정리를 담당한다.

```kotlin
val config = RecordingConfig()
val controls = RecordingControls()

val job = scope.launch {
    Soundly.record {
        input = audioInputs.microphone(config)
        output = audioOutputs.file(RecordingTarget.flac(file))
        this.config = config
        this.controls = controls
        bufferCapacity = 64
    }.catch { error -> showError(error) }
        .collect { event ->
            when (event) {
                is RecordingEvent.State -> showState(event.state)
                is RecordingEvent.Progress -> showDuration(event.progress.duration)
                is RecordingEvent.Completed -> showResult(event.result)
            }
        }
}

controls.pause()
controls.resume()
controls.stop()
job.join()
```

녹음 입력은 `Flow<RecordingInput>`이며, 각 사건은 `PcmChunk` 또는 `RecordingCommand.Record, Pause, Stop`이다. `PcmChunk`도 RecordingInput이므로 `AudioInputs.microphone(config)`, `AudioInputs.file(file, config)`, 사용자 PCM Flow를 그대로 사용할 수 있다. 사용자 조작은 controls에 전달하며, RecordingBuilder가 PCM과 명령을 하나의 입력 흐름으로 조합한다. 파일 입력은 설정과 일치하는 PCM16으로 읽으며 리샘플링은 수행하지 않는다.

출력은 `RecordingOutput`이다. 파일 출력은 `FileRecordingOutput(target)`, 메모리 출력은 `MemoryOutput(maxBytes)`를 사용한다. 앱 조립부에서 RecordingInputs, RecordingOutputs 팩토리를 주입할 수 있다.

출력의 `write(source, config)`도 cold Flow다. 수집 중에는 `OutputEvent.Progress`를, 마감과 자원 정리 후에는 `OutputEvent.Finished`를 전달한다. 파일 결과는 RecordedAudio, 메모리 결과는 MemoryRecording이다. 사용자 출력은 source를 한 번 끝까지 수집하고 마감 결과를 한 번 전달한다.

보관한 PCM은 나중에 다음과 같이 저장할 수 있다.

```kotlin
FileRecordingOutput(target)
    .write(memory.chunks.asFlow(), memory.config)
    .collect { event ->
        when (event) {
            is OutputEvent.Progress -> showDuration(event.progress.duration)
            is OutputEvent.Finished -> showResult(event.result)
        }
    }
```

Soundly는 Recorder를 구현하므로 앱에서는 Recorder로 주입해 사용할 수 있다. 내부 FlowRecorder는 수집마다 생성되며 record 안에서 명령에 따른 상태 전환과 출력 연결을 처리한다.

bufferCapacity는 표준 Flow.buffer의 대기 청크 수이며 기본값은 64다. 0이면 대기열 없이 전달한다. 가득 차면 전달을 기다리며 청크를 버리지 않는다. 바이트나 시간 단위의 제한은 아니다. 상태, 진행 이벤트의 합류 여부는 수집자가 conflate()나 buffer()로 선택한다. SDK는 이벤트를 생략하지 않는다.

명령은 같은 입력에 들어온 PCM과 순서대로 처리한다. 일시정지 명령 뒤의 PCM은 제외하고, 재개 명령 뒤부터 다시 기록한다. 정지 명령 앞의 PCM은 보존하며, 정지 뒤의 입력은 수집하지 않는다. 출력이 느려 앞선 PCM 전달이 대기 중이면 명령 처리도 기다릴 수 있다. 정상 중지는 입력 정리 후 버퍼를 모두 처리하고 출력 마감까지 기다린다. 입력의 자연스러운 종료도 정상 완료다. 취소는 `job.cancelAndJoin()`으로 정리 완료를 기다리며 정상 완료 이벤트를 만들지 않는다.

파일 확정 전 실패, 취소는 임시 파일을 폐기한다. 기존 파일 덮어쓰기와 같은 경로의 동시 기록은 거부한다. Auto의 WAV 전환은 입력 수집 전 FLAC 초기화 실패에만 적용한다.

RecorderException.reason은 Capture, InvalidPcm, MemoryLimit, OutputContract, Encoding, Storage를 구분한다. 취소와 수집자 오류는 녹음 오류로 재분류하지 않는다.

상세 구조: [녹음과 앱 설계](../../docs/recording-architecture.md). FLAC 원리: [학습 노트](FLAC.md).
