# Soundly 인수인계 — 2026-10-12

집에서 이어서 작업할 때 이 파일부터 확인한다. 맥북에어의 Soundly 저장소에도 같은 파일을 둔다. 녹음 복구 변경은 `901f17f`, 임시 파일 정리는 `85db9be`, Catalog Flow 구조 변경은 `6e99ce7`, 목록 오류 수정은 `a5a8261`, 기기 검증 테스트 보강은 `496bb9e`, 버전 값 통일은 `60e9127`, Controller 분리는 `e935181`, 알림 액션 기기 테스트는 `49f8b1d`, 스키마·백업 정책은 `823cc9b`, 복원 경로 보정은 `2845522`로 커밋됐다. 현재 작업 브랜치는 `develop`이다.

## 현재까지 작업한 내용

- 기준 브랜치 `main`, 시작 HEAD `c965c55`. 기존 작업 트리는 깨끗했으며 이후 변경은 이번 작업에서 만들었다.
- 파일 완성 후 Room 등록이 실패하거나 그 사이 프로세스가 종료되면 목록에서 누락될 수 있어 `RoomRecordingCatalog`를 만들었다. 완성 파일 등록, 목록 관찰, 삭제, 목록 Flow 첫 구독 시 파일·DB 대조를 맡는다. 등록·삭제·복구는 같은 Mutex를 쓴다.
- 파일 경로는 Room의 기본 키다. `insertIfAbsent`는 기존 항목을 덮어쓰지 않아 사용자가 바꾼 제목을 보존한다. DB 등록 실패 시 완성 파일은 남겨 다음 실행의 복구 대상으로 삼는다.
- `RecordingForegroundService`는 SDK 완료 이벤트를 받으면 `RoomRecordingCatalog.registerCompletedFile()`을 호출한다. `RecordingsViewModel`은 Catalog의 목록을 관찰하고 삭제를 요청한다. 기존 `RecordingRepository`, `LocalRecordingRepository`, DI 바인딩은 제거했다. 별도 domain 모듈이나 전달만 하는 UseCase는 만들지 않았다.
- `SoundlyApp.onCreate()`에는 `roomRecordingCatalog.deleteAbandonedParts()` 한 줄만 남겼다. Catalog가 녹음 폴더를 알고 SDK의 `.part` 정리를 동기적으로 호출한다. `applicationScope`는 제거했고 목록 Flow의 `onStart`에서 `reconcileWithFiles()`를 실행한다. 따라서 목록 첫 결과 전에 복구가 끝나며, 화면을 다시 구독하면 재대조한다. 별도 `RecordingStartup` 객체나 서비스 대기는 없다. 정리 대상은 외부·내부 `recordings` 폴더의 `.soundly-*.part` 일반 파일뿐이다.
- 완성 파일은 `rec_YYYYMMDD_HHMMSS_<8자리 hex>.wav/.flac`만 복구한다. 오디오 길이를 읽지 못하는 파일은 보존하고 해당 파일의 복구만 건너뛴다.
- 목록 Flow 오류와 삭제 오류의 메시지는 `RecordingsViewModel.mutableState` 한 곳에서 관리한다. `dismissError()`가 이를 지우면 이미 종료된 목록 Flow의 오류가 다시 나타나지 않는다. 재구독 후 복구가 다시 실패하면 새 오류로 표시된다.

## 검증 결과

- 맥북에어에서 `:app:assembleDebug` 성공.
- `:data:recording:testDebugUnitTest` 6개와 `:feature:recording:testDebugUnitTest` 7개 통과. DB 등록 실패 후 복구, 중복·제목 보존, 임시 파일 제외, 삭제 후 DB 실패 복구, 폴더 접근 오류, 복구·삭제 동시 실행을 확인했다.
- 에뮬레이터 `pvc-api35`에서 `:data:recording:connectedDebugAndroidTest` 2개 통과. WAV와 **실제 SDK가 생성한 FLAC**를 Room 등록 없이 남겨 두고, Catalog가 길이와 경로를 복구하며 FLAC 재복구 시 중복이 없음을 확인했다. FLAC STREAMINFO 대체 파서는 필요하지 않아 추가하지 않았다.
- `:app:connectedDebugAndroidTest` 기존 앱 테스트 2개 통과. 녹음 시작·일시정지·재개·저장 및 화면 이동을 포함한다.
- `git diff --check` 통과. 테스트 후 에뮬레이터는 종료했다.
- 2026-10-13: `.part` 정리 변경 후 `:soundly-sdk:recording-android:testDebugUnitTest`와 `:app:assembleDebug` 통과. SDK 형식의 임시 파일만 삭제하고 완성 파일과 다른 형식 파일을 보존하는 테스트를 추가했다. 실제 강제 종료 후 앱 재시작 시나리오는 기기에서 아직 확인하지 않았다.
- 2026-10-13: Catalog Flow 구조 변경 후 `:data:recording:testDebugUnitTest`, `:feature:recording:testDebugUnitTest`, `:app:assembleDebug` 통과. 목록 첫 결과 전에 누락된 파일이 복구되는 테스트와 Catalog를 통한 `.part` 정리 테스트를 추가했다. 변경 이후 기기 테스트는 아직 실행하지 않았다.
- 2026-10-13: 목록 오류 수정 후 `:feature:recording:testDebugUnitTest`, `:app:assembleDebug` 통과. 목록 오류를 닫아도 다시 나타나지 않는 회귀 테스트를 추가했다.
- 2026-10-13: `pvc-api35`에서 `:data:recording:connectedDebugAndroidTest` 2개와 `:app:connectedDebugAndroidTest` 2개 통과. WAV·SDK 생성 FLAC를 Room 등록 없이 남겨 Catalog Flow 첫 구독에서 복구하고, 재구독 시 FLAC가 중복되지 않음을 확인했다. 앱 테스트는 저장 후 목록 항목 표시도 확인한다. 별도로 실제 녹음 중 `am force-stop` 후 `.soundly-*.part`가 남는 것을 확인하고, 앱 재시작 시 삭제되는 것을 확인했다. 완성된 FLAC와 다른 이름의 `keep.part`는 재시작 뒤 유지됐고 목록에 FLAC가 표시됐다. 테스트용 파일은 정리하고 에뮬레이터를 종료한다.
- 2026-10-13: 앱 Gradle의 중복 버전 리터럴을 `Configuration.versionCode`/`Configuration.versionName` 참조로 교체했다. `:app:assembleDebug`와 `git diff --check` 통과. 생성된 APK를 `aapt dump badging`으로 확인한 결과 `versionCode=10`, `versionName=1.3.0`이다.
- 2026-10-13: 서비스가 녹음 세션 상태와 SDK Flow를 직접 관리하던 코드를 `RecordingController`로 옮겼다. 서비스는 포그라운드 실행·알림·Binder를 맡는다. Controller는 Flow의 `transform`/`onStart`/`onCompletion`/`catch`로 이벤트·완료 등록·정리·오류를 처리하고, 완료 상태는 세션 정리 후 공개한다. `!!`와 시작 명령의 Boolean 반환은 사용하지 않는다. `:feature:recording:testDebugUnitTest`, `:app:assembleDebug` 통과.
- 2026-10-13: `:app:connectedDebugAndroidTest` 3개 통과. 기존 화면 이동·일시정지·재개·저장 테스트 2개와 새 알림 `PendingIntent` 일시정지·재개·저장 테스트 1개를 확인했다.
- 2026-10-14: Room 버전 1 스키마를 내보내고 JSON을 저장소에 포함했다. Android 11 이하와 12 이상의 백업 규칙을 명시해 클라우드 백업에서는 녹음 파일·Room·권한 요청 이력을 제외하고, 기기 간 전송에서는 내부·외부 녹음 파일만 옮긴다. 복원된 파일은 Catalog 첫 목록 구독에서 Room에 다시 등록한다. 이전 백업의 다른 기기 절대 경로 행은 같은 이름의 현재 파일로 옮기며 제목과 길이를 보존하고, 파일이 없으면 오래된 행을 제거한다.
- 2026-10-14 검증: `:data:recording:testDebugUnitTest`, `:app:assembleDebug`, 에뮬레이터 `:data:recording:connectedDebugAndroidTest` 2개와 `:app:connectedDebugAndroidTest` 3개 통과. 이전 경로의 제목 보존·누락 행 제거 단위 테스트를 추가했고, APK에 두 백업 XML의 규칙이 반영된 것을 `aapt dump xmltree`로 확인했다. 실제 기기 간 전송 및 클라우드 복원은 수행하지 않았다.

## 녹음 제어 구조 검토 — 2026-10-13

- 현재 녹음 실행, 진행 상태, 알림 액션은 `RecordingForegroundService` 한 인스턴스가 관리한다. 알림의 중지·일시정지·재개 명령도 `onStartCommand()`에서 같은 함수로 들어간다. 저장된 파일 목록과 복구·삭제는 별도 `RoomRecordingCatalog`가 담당한다.
- `RecorderRoute`는 서비스 바인딩, 마이크·알림 권한, 상태 관찰, 버튼 명령을 모두 갖는다. 화면이 사라지면 바인딩만 해제하며 녹음은 시작된 서비스에서 계속된다. 현재 직접 바인딩하는 UI 클라이언트는 이 화면 하나뿐이다.
- 서비스가 포그라운드 생명주기와 알림을 맡고, 서비스 소유 Controller가 SDK 세션·명령·상태를 맡도록 실제 책임을 옮겼다. 상태 원천은 Controller의 `StateFlow` 한 곳이다. 화면의 바인딩은 현재 사용 화면이 하나라 그대로 두었다.
- `recordingJob`/`controls`, SDK 이벤트 처리, 완료 파일 Catalog 등록은 Controller로 이동했다. 서비스는 Controller 상태 관찰 → 알림 갱신, 시작/종료 시 포그라운드 전환, 알림 Intent 전달, Binder 제공을 맡는다. 새 `RecordingStateStore`는 없다.

## 남은 작업 — 우선순위

1. **녹음 파일의 별도 내보내기·동기화 검토.** 현재 Android 클라우드 백업에는 녹음 파일을 포함하지 않는다. 기기 분실에도 녹음을 보존하려면 사용자가 선택한 위치로 내보내거나 별도 동기화를 제공해야 한다.

## 이어서 볼 파일

- `data/recording/src/main/kotlin/com/soundly/data/recording/RoomRecordingCatalog.kt`
- `data/recording/src/androidTest/kotlin/com/soundly/data/recording/RoomRecordingCatalogDeviceTest.kt`
- `data/recording/src/test/kotlin/com/soundly/data/recording/RoomRecordingCatalogTest.kt`
- `feature/recording/src/main/kotlin/com/soundly/feature/recording/recorder/RecordingForegroundService.kt`
- `feature/recording/src/main/kotlin/com/soundly/feature/recording/recorder/RecordingController.kt`
- `feature/recording/src/main/kotlin/com/soundly/feature/recording/recordings/RecordingsViewModel.kt`
- `soundly-sdk/recording-android/src/main/kotlin/com/soundly/sdk/recording/storage/FileRecordingOutput.kt`
- `app/src/main/java/com/soundly/SoundlyApp.kt`

작업을 재개할 때 `git status --short`로 상태를 먼저 확인한다. `develop`에는 `main`에서 진행한 작업 6개와 녹음 제어 구조 변경·알림 액션 테스트 커밋이 포함돼 있다. 이 문서는 그 작업과 검증 결과를 반영했다.
