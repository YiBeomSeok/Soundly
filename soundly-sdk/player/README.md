# Soundly Playback

파일 재생을 cold Flow로 제공합니다. Flow를 만들 때에는 플레이어를 생성하지 않습니다. 수집을 시작하면 Media3 플레이어를 만들고, 완료나 정지, 실패, 취소 시 같은 수집 범위에서 해제합니다.

## 사용하기

```kotlin
val player: Playback = PlaybackManager(context = context)
val controls = PlaybackControls()

player.play(
    file = file,
    controls = controls,
).collect { status ->
    showPlayback(status)
}
```

한 번의 재생에는 하나의 `PlaybackControls`를 사용합니다. 화면의 사용자 입력에서 다음 명령을 전달합니다.

```kotlin
controls.pause()
controls.seekTo(position = 30.seconds)
controls.resume()
controls.stop()
```

명령은 수집 시작 전의 요청과 같은 위치의 반복 탐색까지 순서대로 보관합니다. 명령 큐는 한 재생 수집에서 사용하며, 동시에 여러 수집의 제어를 공유하지 않습니다.

## 상태와 종료

| 상태 | 의미 |
| --- | --- |
| `Buffering` | 재생 준비 또는 버퍼 대기 |
| `Playing` | 재생 중이며 위치를 100ms 간격으로 관찰 |
| `Paused` | 재생을 멈춘 채 플레이어 유지 |
| `Ended` | 자연스러운 재생 완료로 수집 반환 |
| `Error` | 준비 또는 재생 실패로 수집 반환 |
| `Idle` | 정지 명령 처리 후 수집 반환 |

`Ended`를 받은 화면은 마지막 상태를 유지할 수 있습니다. 다시 재생할 때에는 새 수집과 새 제어 객체를 사용합니다. 탐색 위치는 음수와 알려진 파일 길이의 범위를 벗어나지 않도록 보정합니다.

수집 취소와 수집자의 예외는 재생 오류 상태로 바꾸지 않습니다. 취소 후 같은 Flow를 다시 수집하면 새 플레이어를 생성합니다. 이미 사용한 제어 객체에는 소비하지 않은 명령이 남을 수 있으므로 새 재생에는 새 객체를 권장합니다.

## 내부 구조

```text
수집 시작
 → 플레이어 생성과 재생 준비
 → 명령 Flow와 상태 Flow 연결
 → 명령 실행 또는 현재 상태 읽기
 → 완료, 정지, 오류 판단
 → 리스너 제거와 플레이어 해제
 → 수집 반환
```

| 구성 | 책임 |
| --- | --- |
| `Playback` | 파일과 제어를 받아 상태 Flow 제공 |
| `PlaybackManager` | 수집 수명과 명령 처리, 위치 관찰, 종료 판단 |
| `PlaybackControls` | 사용자 명령의 순서 보존 |
| `PlaybackEngine` | 플랫폼 명령과 상태 조회를 위한 내부 계약 |
| `Media3PlaybackEngine` | Media3 조작과 리스너의 Flow 변환 |

Media3의 `onEvents`를 `callbackFlow`로 변환하고 `awaitClose`에서 리스너를 제거합니다. 상태가 바뀌면 `transformLatest`가 이전 위치 관찰을 취소하므로 별도의 ticker Job이나 감시 코루틴을 보관하지 않습니다. 플레이어 접근과 해제는 Main dispatcher에서 수행합니다.

모킹 라이브러리 대신 Fake 엔진과 가상 시간으로 명령 순서, 일시정지 중 관찰 중단, 완료, 오류, 취소, 재수집을 검증합니다. 장치에서는 실제 WAV 재생, 탐색과 정지, 조기 수집 종료 후 재수집, 없는 파일의 실패를 검증합니다.

## 참고

- [Media3 플레이어 사건](https://developer.android.com/media/media3/exoplayer/listening-to-player-events)
- [callbackFlow와 정리](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/callback-flow.html)
- [transformLatest의 취소](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/transform-latest.html)
