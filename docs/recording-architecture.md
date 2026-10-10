# Soundly 녹음과 앱의 실행 구조

## 녹음의 의미

녹음은 음성 입력을 수집하는 실행이다. 입력의 출처와 수집한 PCM의 보관 방법은 독립적으로 선택한다.

`Recorder.record`는 입력, 생산자, 소비자 사이의 버퍼, 출력의 수명을 소유한다. 구성 시에는 자원을 열지 않으며, 최종 Flow를 수집할 때 실행한다. 수집 호출이 정상 반환하면 입력 정리와 출력 마감도 끝난 상태다.

```kotlin
val config = RecordingConfig()
val controls = RecordingControls()

val recording = Soundly.record {
    input = audioInputs.microphone(config)
    output = audioOutputs.file(RecordingTarget.flac(file))
    this.config = config
    this.controls = controls
    bufferCapacity = 64
}

recording.collect { event ->
    when (event) {
        is RecordingEvent.State -> showState(event.state)
        is RecordingEvent.Progress -> showDuration(event.progress.duration)
        is RecordingEvent.Completed -> showResult(event.result)
    }
}
```

플랫폼 입력은 `(RecordingConfig) -> Flow<PcmChunk>`로 생성한다. 녹음기의 입력 계약은 `Flow<RecordingInput>`이다. PcmChunk와 RecordingCommand가 RecordingInput을 구현하므로 PCM만 전달하거나 Record, Pause, Stop 명령을 함께 전달할 수 있다. RecordingBuilder는 input, output, controls 선언을 확정하고 PCM과 명령을 하나의 입력 흐름으로 조합한다. 사용자가 직접 만든 PCM Flow도 사용할 수 있다. 파일 입력은 WAV, FLAC 등 장치에서 지원하는 형식을 PCM16으로 읽는다. 샘플레이트, 채널 수가 설정과 다르면 거부하며 리샘플링은 제공하지 않는다.

출력 계약은 `RecordingOutput.write(source, config): Flow<OutputEvent>`다. 반환된 Flow를 수집하면 입력을 한 번 끝까지 기록한다. 처리한 진행량은 Progress로, 자원 정리와 마감이 끝난 결과는 Finished로 한 번 전달한다. 사용자 출력도 이 계약을 구현한다. 파일 출력은 수집하는 동안 계속 기록하므로 전체 녹음을 메모리에 보관하지 않는다. `MemoryOutput(maxBytes)`는 제한된 용량 안에서 PCM을 복사해 보관한다. 메모리 결과는 이후 다른 출력의 write에 chunks.asFlow()로 전달하고, 반환된 출력 Flow를 수집해 저장할 수 있다.

## 생산자와 소비자

```text
Flow<RecordingInput>
 → 명령에 따른 기록 여부 결정
 → 활성 상태의 비어 있지 않은 PCM 전달
 → Flow.buffer(bufferCapacity)
 → 출력.write
```

기준은 위에서 아래로 한 번 읽으면 실행 순서와 책임이 드러나는 것이다. 단계마다 클래스를 추가하지 않고, 상태는 필요한 실행 범위에만 둔다. 각 실행 클래스의 주석에 이 기준과 맡은 책임을 적는다.

Soundly는 Recorder의 공개 구현이다. 선언을 확정하고, collect마다 독립된 FlowRecorder를 만들어 실행한다. FlowRecorder는 한 번의 녹음에 필요한 상태를 필드로 보관한다. 자기 자신을 생성하거나 수집 사이에 상태를 공유하지 않으므로 뮤텍스가 필요 없다.

| 구성 | 책임과 유지 범위 |
| --- | --- |
| Soundly.record | 선언 확정 → 수집별 실행 생성 → 이벤트 전달 |
| RecordingBuilder.build | 설정 확인 → 입력과 명령의 Flow 조합 → 요청 확정 |
| FlowRecorder.record | 명령에 따른 상태 전환 → PCM 버퍼 연결 → 출력 관찰 → 완료 전달 |
| RecordingOutput.write | 입력 수집 → 기록, 진행량 계산 → 마감, 정리 → 결과 전달 |

FlowRecorder 안에는 combine, capture, write 함수를 두지 않는다. 장치를 직접 열거나 파일에 쓰지 않으며, 입력의 수집 횟수를 별도 플래그로 감시하지 않는다. 기본 출력 구현은 자신의 한 번의 collect에서 모든 PCM을 처리한다. 녹음기는 출력이 마감 결과 하나를 전달하고 정상 종료했는지를 확인한다.

Record 명령은 Active, Pause는 Paused, Stop은 Stopping으로 상태를 직접 변경한다. 상태 대입만 하는 함수를 따로 두지 않아 명령 처리와 변경 결과를 같은 위치에서 읽을 수 있다. 같은 상태의 반복 요청은 이벤트를 추가하지 않는다. transformWhile는 활성 구간의 PCM을 전달하고 정지 상태에서 입력 수집을 끝낸다.

일시정지와 재개는 입력, 출력의 수집 범위를 끝내지 않는다. 입력을 계속 읽으면서 일시정지 구간의 PCM만 제외한다. 누적 샘플 수와 출력 자원은 출력의 write Flow가 끝날 때까지 유지한다. 정지 시 이미 출력 버퍼에 들어온 PCM은 마저 기록한다.

진행량은 출력이 계산한다. FlowRecorder는 OutputEvent.Progress를 공개 진행 이벤트로 전달하며, 최신 진행량만 상태 이벤트에 첨부한다. Finished를 받은 즉시 완료시키지 않고 출력 Flow가 정상 종료할 때까지 기다린 뒤 RecordingEvent.Completed를 전달한다. 마감 결과가 없거나 여러 개면 OutputContract 실패다.

입력 실패는 빌더가 내부 사건으로 전달하고 record가 원래 취소 또는 Capture 실패로 전파한다. 합류한 입력 생산자만 취소되어 제어 수집이 남는 상황을 막기 위한 경계다. 세션 범위를 별도로 취소하는 호출이나 독립 제어 감시 코루틴은 두지 않는다.

파일 입출력과 마이크는 각 구현의 IO 실행 범위에서 처리한다. 녹음 상태 연결은 호출자의 코루틴 문맥을 따른다. 동시 수집 시 입력과 출력 구현도 독립된 자원을 제공해야 하며, 같은 파일 경로의 중복 기록은 파일 출력이 거부한다.

버퍼는 코루틴의 표준 Flow.buffer를 사용한다. bufferCapacity는 대기 청크 수이며 기본값은 64다. 0이면 대기열 없이 전달한다. 기본 SUSPEND 정책으로 버퍼가 가득 차면 전달을 기다리며, 청크를 생략하거나 순서를 바꾸지 않는다. 처리 중인 청크와 수집 측에서 아직 전송하지 못한 청크는 대기열 용량에 포함되지 않는다.

청크 크기는 입력 구현마다 다르므로 이 설정을 시간이나 바이트 제한으로 해석하지 않는다. 버퍼는 일시적인 지연을 흡수한다. 출력이 지속적으로 느리면 역압력이 입력까지 전달되며, 마이크 장치, 운영체제 내부 버퍼의 넘침이나 손실을 SDK의 Flow 버퍼가 검출하지는 않는다.

PCM을 생산자가 전달한 후 배열을 재사용하지 않고 소비자도 수정하지 않는다. PcmChunk는 리틀 엔디언 PCM16이다. 출력 구현은 샘플 프레임 정렬과 자체 용량 제한을 확인한다.

## 제어와 종료

명령과 PCM은 입력 흐름의 순서대로 처리한다. 일시정지 명령 이후에는 입력을 계속 읽고 새 PCM을 출력으로 보내지 않는다. 이미 버퍼에 들어온 PCM은 그대로 처리한다. 재개해도 같은 입력, 출력을 유지한다. RecordingControls의 stop은 되돌릴 수 없다. 출력이 느려 앞선 PCM을 전달하지 못하면 뒤의 명령 처리도 대기한다. 정지 명령이 도착하기 전에 입력 흐름에 들어온 PCM을 건너뛰지 않는다.

```text
Stop 명령까지 순서대로 처리
 → 입력 수집 종료 및 입력 자원 정리
 → PCM 채널 정상 종료
 → 소비자가 남은 PCM 처리
 → 입력 EOS 제출 및 출력 EOS 회수
 → 파일 스트림, 코덱 정리
 → 파일 헤더 보정 및 최종 경로 확정
 → Completed
 → collect 반환
```

파일 입력의 자연스러운 종료도 정상 종료다. 빈 입력 역시 출력 정책에 맞는 빈 결과로 마감한다.

실패나 취소 시 정상 마감과 완료 이벤트를 만들지 않는다. 파일 확정 전에는 임시 파일을 삭제한다. 파일이 확정된 후의 수집자 오류나 DB 등록 실패는 파일을 삭제하지 않는다. CancellationException은 RecorderException으로 바꾸지 않는다.

SDK는 발생한 상태, 진행 이벤트를 합류하거나 생략하지 않는다. 이벤트 소비자가 conflate()나 buffer()로 관찰 정책을 정한다. 느린 수집자는 기본 Flow 버퍼가 찬 뒤 실행에 역압력을 전달할 수 있다. 완료 결과는 정상 실행의 마지막 이벤트이며 PCM에는 합류 정책을 적용하지 않는다. 제어 입력은 StateFlow이므로 빠른 연속 요청의 모든 중간 값을 보존하는 이벤트 로그는 아니다.

## 계약과 구현

| 구성 | 책임 |
| --- | --- |
| Recorder / Soundly | 공개 선언과 수집별 실행 생성 |
| RecordingBuilder | 선언 확정과 입력, 명령 연결 |
| FlowRecorder | 녹음 상태 전이, 표준 버퍼와 출력 연결, 완료 전달 |
| RecordingInput / RecordingCommand | PCM과 기록, 일시정지, 정지 명령의 단일 입력 계약 |
| RecordingControls | 사용자 조작에 따른 명령 값 보관 |
| RecordingOutput / OutputEvent | PCM 기록의 진행량과 정상 마감 결과를 Flow로 전달 |
| RecordingInputs / RecordingOutputs | 앱 조립부에서 제공하는 선택적 입력, 출력 팩토리 |
| AudioInputs / Microphone | 플랫폼 파일 입력과 장치 입력, 수집 범위의 장치 정리 |
| FileRecordingOutput | 경로 예약, WAV, FLAC 기록, 헤더 보정, 파일 확정, 폐기 |
| MemoryOutput | 제한된 용량의 독립된 PCM 보관 |
| FlacEncoderMediaCodec | PCM Flow를 압축 패킷 Flow로 변환, EOS와 대기 제한 |
| FlacCodec / MediaCodecFlac | 안드로이드 버퍼의 획득, 복사, 반환 |

별도 RecordingSession, FlacEncodingSession, FlacFinalizer, PcmWriter 계층은 두지 않는다. 인코딩 실행 상태는 Flow의 지역 변수이며, STREAMINFO 보정은 파일 출력 안에 둔다. 콜백은 하위 수집이나 인코딩 함수가 사건, 패킷을 전달하는 경우에만 사용하며 공개 이벤트 전달 계약은 Flow다.

recording-android도 실행 순서를 위에서 아래로 읽는 기준을 따른다. Microphone은 장치 생성과 시작, PCM 읽기를 하나의 Flow에 두고 중지, 해제를 같은 use 범위에 묶는다. AudioInputs.file은 트랙 선택과 읽기 방식 결정만 담당하며, 비압축 PCM 읽기와 디코딩은 각각 실제 처리를 수행하는 하위 함수로 둔다.

FlacEncoderMediaCodec는 입력을 collect한 다음 EOS를 제출하고 남은 출력을 회수한다. 정상 입력 종료 뒤에 마감 코드가 이어지므로 onCompletion에서 종료 원인을 다시 판단하지 않는다. 실패나 취소로 수집이 끝나면 use가 코덱을 해제한다. 블로킹 장치 읽기와 코덱 폴링에는 반복문과 취소 확인이 필요하며, 코덱 대기에는 시간 제한을 둔다.

FileRecordingOutput은 형식 선택과 파일 확정을 맡고, writeWav와 writeFlac은 해당 형식의 기록, 마감과 진행량 전달만 수행한다. Auto의 WAV 전환은 입력을 열기 전 FLAC 초기화가 실패한 경우에만 retryWhen으로 한 번 수행한다. WAV는 같은 파일 핸들에서 임시 헤더, PCM, 확정 헤더를 순서대로 기록한다.

코덱 입력은 PCM sample frame 경계로 분할한다. PTS는 제출한 채널당 sample 수를 기준으로 계산한다. 마감은 입력 EOS를 실제 제출한 후 출력 EOS까지 회수한다. 일시적인 출력 없음은 종료가 아니다. 입력 공간 대기 중 회수한 출력도 보존하며, 코덱 대기는 단조 시계의 유한한 제한을 둔다.

FLAC codec-config 출력에는 파일 헤더가 있으므로 보존한다. 헤더와 빈 EOS를 제외한 압축 패킷 크기의 최소, 최대값을 집계하고, 스트림과 코덱을 닫은 뒤 STREAMINFO의 sample 수와 함께 기록한다. 일부 장치의 MediaExtractor는 최대 프레임 크기가 0인 파일의 큰 프레임을 반환하지 않아 실제 프레임 크기를 보정한다.

참조: [MediaCodec EOS](https://developer.android.com/reference/android/media/MediaCodec#end-of-stream-handling), [FLAC STREAMINFO](https://www.rfc-editor.org/rfc/rfc9639.html#section-8.2).

## 앱의 책임

```text
feature:recording → data:recording
                 → soundly-sdk:recording-api
                 → soundly-sdk:player
app              → 플랫폼 입력, 파일 출력, Recorder 조립
```

단순 위임만 하던 domain 모듈과 UseCase를 제거했다. 녹음 목록, 메타데이터 저장, 파일 삭제는 data의 RecordingRepository 인터페이스에 모은다. 실제 Room과 파일 접근은 LocalRecordingRepository가 구현하며 Hilt로 연결한다. 테스트는 같은 인터페이스의 Fake 구현체를 사용한다. Room 스키마와 데이터베이스 이름은 유지한다. 삭제가 실패하면 목록만 먼저 없애지 않고 오류를 전달한다.

ViewModel은 init에서 저장소를 별도로 수집하지 않는다. 저장소 Flow와 검색, 재생, 오류를 담은 내부 MutableStateFlow를 combine으로 결합하고, WhileSubscribed(5초)의 stateIn으로 화면 상태를 제공한다. 화면 구독이 사라지면 유예 시간 이후 저장소 수집도 종료한다. 검색, 삭제 요청, 오류 표시를 관리한다. 파일 길이 조회나 DB 구현, 플레이어 자원은 소유하지 않는다. 화면은 선택한 파일의 재생 Flow를 수집한다. 파일 선택이 바뀌거나 화면이 사라지면 이전 수집을 취소한다. 재생 패널이 위치를 직접 관찰하고 목록에는 위치를 제외한 상태만 전달한다.

포그라운드 서비스는 한 번의 녹음 수집과 알림 수명을 소유한다. 화면은 바인딩해 상태를 관찰하고 제어한다. 화면을 나가도 녹음은 계속된다. 서비스는 정상 파일 결과를 저장소에 등록하고, 목록 등록 실패와 녹음 실패를 구분한다. 사건 반영, 알림 갱신, 실패 처리를 한 Flow 체인으로 연결하고 onCompletion 한 곳에서 포그라운드 실행을 정리한다. 녹음 화면은 표시할 초 단위로 상태를 관찰한다.

## 화면 탐색과 상태

MainActivity는 edge-to-edge 설정과 테마를 제공한다. SoundlyNavigation은 rememberNavBackStack으로 복원 가능한 화면 스택을 보관하고, NavDisplay에 저장 상태와 ViewModel 수명 데코레이터를 적용한다. RecordingNavigation은 화면 키에 대응하는 엔트리를 제공한다.

Route는 서비스 바인딩, 권한, 플레이어 수집, Snackbar와 삭제 대상을 소유한다. Screen은 상태와 이름 있는 콜백을 받아 UI를 그린다. 재생 패널의 드래그 상태는 패널 Route에만 유지한다.

배경은 시스템 창까지 확장하며, 화면 Scaffold와 재생 패널에서 필요한 inset을 적용한다. 버튼과 아이콘 주변은 padding으로 자연스럽게 크기를 정하고, 화면 본문에는 최대 너비를 적용한다. 녹음 화면은 스크롤을 허용하고 형식 선택은 너비가 부족하면 다음 줄로 배치한다.

## 재생의 수명

Playback은 파일과 PlaybackControls를 받아 Flow<PlaybackStatus>를 반환한다. PlaybackManager는 외부 Scope, 플레이어 필드, ticker Job을 보관하지 않는다. 각 수집에서 플레이어를 생성하고 use 범위에서 해제한다.

명령 Flow와 Media3 상태 Flow를 합류한 뒤 한 곳에서 명령을 실행하거나 현재 상태를 읽는다. 상태가 재생 중이면 transformLatest 안에서 위치를 주기적으로 관찰한다. 상태가 바뀌면 이전 관찰이 취소되므로 독립된 감시 코루틴이나 수동 ticker 취소가 필요 없다.

자연스러운 재생 완료는 Ended, 정지는 Idle, 플레이어 실패는 Error를 마지막으로 전달하고 수집을 반환한다. 화면은 마지막 Ended 상태를 보관하며, 다시 재생할 때 새 제어 객체로 새 Flow를 수집한다. 취소와 수집자 실패는 오류 상태로 바꾸지 않는다.

Media3PlaybackEngine은 플랫폼 명령, 상태 조회, 리스너 변환만 담당한다. callbackFlow의 awaitClose는 리스너를 제거하고, 상위 수집의 use는 플레이어를 해제한다. Main dispatcher에서 플랫폼을 호출한다. 내부 PlaybackEngine 인터페이스를 Fake로 구현해 코루틴의 가상 시간으로 검증한다.

사용 예제와 참조는 [재생 SDK](../soundly-sdk/player/README.md)에 정리한다.

## 검증과 작성 규칙

JDK 17로 실행한다.

```sh
./gradlew :soundly-sdk:recording-android:testDebugUnitTest
./gradlew :soundly-sdk:recording-android:connectedDebugAndroidTest
./gradlew :soundly-sdk:recording-android:lintDebug
./gradlew :feature:recording:testDebugUnitTest
./gradlew :soundly-sdk:player:testDebugUnitTest
./gradlew :soundly-sdk:player:connectedDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleDebug
```

작은 표준 버퍼와 청크 순서, 정상 중지 후 잔여 PCM 처리, 용량 대기 시 PCM, 명령 순서, 느린 관찰자, 취소, 실패 정리, 재수집과 동시 수집의 격리, 출력 마감 결과의 누락, 중복, 후속 실패 거부, 출력 진행량 전달, 입력 정리, 출력 마감 이후의 완료, 메모리 한도와 파일 확정을 검증한다. 실제 코덱은 합성 PCM의 mono, stereo 왕복과 파일 입력을 별도로 검증한다. 실기기 마이크, UI 확인은 합성 PCM 계측 테스트와 구분해 기록한다.

Unit 반환 타입은 생략하고 값 반환 타입은 명시한다. 최상위 함수는 필요한 경우에만 추가한다. 주석은 한글로 3줄 이내에 쓰며 문서 주석은 여러 줄로 작성한다. 이론, 규격 기반 로직에는 출처를 기록한다.

## 확인 기록

2026년 10월 11일, JDK 17과 Galaxy Tab S10+ 계열 SM-X820(Android 16)에서 확인했다. 앱은 설정에 선언된 targetSdk 35를 적용하며, 마이크, 알림 권한과 마이크 포그라운드 서비스로 실행한다.

FlowRecorder 개편은 JVM 테스트 58개(SDK 52개, ViewModel 6개)와 장치 계측 테스트 7개로 확인했다. 이어서 recording-android를 정리하고 SDK 테스트를 55개, 장치 계측 테스트를 10개로 보강해 모두 통과했다. SDK 린트와 앱 빌드도 통과했다. 재생 개편 뒤 Fake 기반 재생 테스트 8개와 ViewModel 테스트 7개, 실제 Media3 재생 장치 테스트 4개, 앱 탐색 및 녹음 장치 테스트 2개가 통과했다. 변경한 재생, 녹음 화면, 앱 진입 모듈의 린트도 통과했다.

장치에서는 합성 PCM의 FLAC 왕복, WAV, FLAC 일시정지, 재개, 정지, 취소 후 임시 파일 정리와 재수집을 검증했다. 파일 입력의 취소 후 재수집과 형식 불일치 거부도 확인했다. 실제 마이크는 취소 및 조기 수집 종료 후 같은 Flow를 다시 수집해 PCM을 받는 것을 확인했다. Navigation 3 전환 후 화면 스택과 선택 형식의 Activity 재생성 복원, 검색 상태 유지, 화면 이동 중 녹음 유지와 일시정지, 재개, 저장을 앱 계측 테스트로 확인했다.

이전 확인에서는 실제 마이크로 FLAC, WAV 녹음, 일시정지, 재개, 화면 이탈과 홈 이동 후 녹음 유지, 파일 저장과 목록 반영, 두 형식의 재생을 확인했다. 삭제 확인창의 취소와 실제 파일, 목록 삭제도 확인했다. 가로 화면과 다크 모드에서 녹음 화면이 잘리지 않는 것을 확인했으며, 점검 후 장치의 자동 회전과 밝은 모드를 원래 설정으로 복원했다.

장치 계측은 합성 PCM의 샘플 수, 내용과 파일 입력 변환, 마이크 수집 수명을 검증한다. 실제 마이크 계측은 PCM 수신과 취소, 재수집 확인이며, 외부 기준 신호와 비교하는 음향 손실 측정은 포함하지 않는다.
