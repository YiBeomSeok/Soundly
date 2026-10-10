# FLAC 학습 노트

FLAC은 정수 [PCM](../../docs/audio-terms.md#pcm) 오디오를 무손실로 압축하는 형식입니다. 인접한 샘플 사이의 관계를 이용해 저장할 정보를 줄이고, 디코딩할 때 입력 PCM을 정확히 복원합니다.

PCM의 단위부터 시작해 압축 과정, 파일 구조, Soundly의 구현을 차례로 설명합니다. 낯선 표현은 [오디오 용어](../../docs/audio-terms.md)에서 확인할 수 있습니다.

## 1. PCM의 단위

PCM은 시간에 따라 측정한 진폭을 수치로 나타냅니다.

Soundly의 입력은 [signed PCM16](../../docs/audio-terms.md#bit-depth와-pcm16) [little endian](../../docs/audio-terms.md#endianness)이며, mono 또는 stereo를 사용합니다. PCM16은 sample 하나를 2바이트로 표현하고, little endian은 그 2바이트의 저장 순서를 정합니다.

| 용어 | 의미 | 44.1kHz stereo PCM16 예시 |
| --- | --- | --- |
| sample | 한 채널의 한 시점 진폭 | 16비트 정수 하나 |
| sample frame | 같은 시점의 모든 채널 sample | L, R 한 쌍, 4바이트 |
| sample rate | 초당 채널별 sample 수 | 채널마다 44,100개 |
| PCM chunk | 입력 API가 한 번 전달한 바이트 묶음 | 여러 sample frame |
| FLAC frame | 독립적으로 복원 가능한 압축 단위 | 헤더, 채널별 subframe, 검사값 |

```text
PCM 바이트 수 = sample frame 수 × 채널 수 × 2
재생 시간(초) = sample frame 수 / sample rate
```

stereo 48,000 sample frame은 PCM16에서 192,000바이트입니다. sample rate가 48kHz라면 길이는 1초입니다. sample 수를 모든 채널의 합으로 세면 길이가 두 배로 계산됩니다.

## 2. 압축 과정

```text
PCM block
 → 채널 간 상관관계 처리
 → 채널별 표현 방식 선택
 → 예측값과 잔차 계산
 → 잔차 부호화
 → FLAC frame
```

### 블록과 채널

인코더는 PCM을 블록으로 묶고 채널마다 subframe을 만듭니다. [chunk, block, frame, subframe](../../docs/audio-terms.md#chunk-block-frame-subframe)은 서로 다른 단위입니다.

입력 chunk와 압축 frame의 경계는 일치하지 않아도 됩니다. 마지막 블록은 앞선 블록보다 짧을 수 있습니다.

stereo는 L, R을 각각 저장하거나 L−R 같은 차이 채널을 이용합니다.

두 채널이 비슷하면 차이 채널의 값이 작아져 압축에 유리합니다. 디코더는 채널 할당 정보에 따라 원래 L, R을 복원합니다. 이 단계도 가역 변환입니다. [형식 개요](https://xiph.org/flac/documentation_format_overview.html)

### 표현 방식 선택

FLAC이 모든 블록에 LPC를 적용하지는 않습니다. 인코더는 데이터와 부가 정보의 크기를 고려해 표현을 선택합니다.

| 방식 | 저장하는 내용 | 적합한 경우 |
| --- | --- | --- |
| Constant | 반복되는 값 하나 | 일정한 진폭 |
| Verbatim | 원본 sample | 예측으로 이득을 얻기 어려운 신호 |
| Fixed predictor | 초기 sample과 잔차 | 간단한 차분으로 설명되는 신호 |
| LPC | 초기 sample, 정수 계수, shift, 잔차 | 선형 관계로 설명되는 신호 |

압축률은 신호와 인코더의 선택에 따라 달라집니다. 압축 설정이 달라도 무손실 복원이라는 조건은 유지됩니다. [Subframe 형식](https://xiph.org/flac/format.html#subframe)

## 3. 예측과 잔차

sample을 s[n], 예측값을 p[n]이라 하면 [잔차](../../docs/audio-terms.md#예측과-residual)는 다음과 같습니다.

```text
r[n] = s[n] − p[n]
복원: s[n] = p[n] + r[n]
```

예측 오차를 함께 저장하므로 예측이 완벽할 필요는 없습니다. 압축에 유리한 예측은 작은 잔차를 자주 만듭니다.

### 고정 예측의 계산 예

입력 `[10, 12, 15, 19, 24]`를 예로 들어 계산해 보겠습니다.

1차 예측은 이전 sample을 그대로 사용합니다.

```text
p[n] = s[n−1]
초기 sample: 10
잔차:        2, 3, 4, 5
```

2차 예측은 이전 두 sample의 변화량이 유지된다고 가정합니다.

```text
p[n] = 2 × s[n−1] − s[n−2]
초기 sample: 10, 12
예측값:      14, 18, 23
잔차:         1,  1,  1
```

디코더는 초기 sample을 읽고 `14+1=15`, `18+1=19`, `23+1=24`를 순서대로 계산합니다. 초기 sample도 저장 비용에 포함되므로 높은 차수가 항상 유리한 것은 아닙니다.

### LPC의 계산

[LPC](../../docs/audio-terms.md#lpc)는 이전 sample들의 가중합으로 예측합니다. 파일에는 양자화한 정수 계수와 shift를 저장합니다.

```text
p[n] = (Σ a[i] × s[n−i]) >> shift
```

계수 선택 방법은 인코더의 구현에 속합니다. 디코더는 저장된 정수 계수로 같은 예측값을 계산하고 잔차를 더합니다. 계수 근사에 따른 오차도 잔차에 포함되어 원본이 복원됩니다. [RFC 9639: Linear Predictor Subframe](https://www.rfc-editor.org/rfc/rfc9639.html#section-9.2.6)

## 4. Rice 잔차 부호화

먼저 signed 잔차를 0 이상의 정수로 대응시킵니다.

```text
r ≥ 0: u = 2r
r < 0: u = −2r − 1

잔차 r:     0, −1, 1, −2, 2, −3
변환값 u:   0,  1, 2,  3, 4,  5
```

[Rice coding](../../docs/audio-terms.md#rice-coding)의 파라미터 k를 정하면 `2^k`로 나눈 몫과 나머지를 저장합니다.

```text
q = u >> k
나머지 = u & (2^k − 1)
코드 = q개의 0 + 종료 비트 1 + k비트 나머지
```

잔차 −3은 u=5로 변환됩니다. k=1이면 q=2, 나머지=1이므로 코드는 `001|1`입니다. 디코더는 u를 되찾고 signed 대응을 역으로 적용합니다.

작은 값이 자주 나오면 코드가 짧아집니다. k가 너무 작으면 큰 잔차의 몫이 길어지고, 너무 크면 모든 잔차에 붙는 나머지가 길어집니다. FLAC은 잔차를 partition으로 나눠 파라미터를 정합니다. escape 방식으로 고정 비트 폭의 잔차도 표현합니다.

[RFC 9639: Coded Residual](https://www.rfc-editor.org/rfc/rfc9639.html#section-9.2.7)

## 5. 파일 구조와 검증값

```text
fLaC marker
 → STREAMINFO
 → 추가 metadata blocks
 → FLAC frames
```

[STREAMINFO](../../docs/audio-terms.md#metadata와-streaminfo)에는 sample rate, 채널 수, bit depth, 최소, 최대 블록 크기, 최소, 최대 압축 frame 바이트 수, 전체 sample 수와 PCM MD5가 들어갑니다.

마지막 metadata block의 플래그로 오디오 frame 시작을 구분합니다.

frame 헤더에는 [CRC-8](../../docs/audio-terms.md#crc와-md5), frame 끝에는 CRC-16이 있습니다.

STREAMINFO의 MD5는 복원한 전체 PCM을 검증하는 용도입니다. 전체 sample 수와 압축 frame 크기는 모를 때 0으로 기록할 수 있고, MD5가 모두 0이면 검증값을 제공하지 않는다는 뜻입니다. [파일 형식과 STREAMINFO](https://xiph.org/flac/format.html#metadata_block_streaminfo)

## 6. Soundly 구현과 연결

Soundly는 예측, Rice 알고리즘을 직접 구현하지 않고 MediaCodec FLAC 인코더에 PCM16을 전달합니다. 애플리케이션은 녹음 Flow를 수집합니다.

```text
AudioRecord → PCM16 → MediaCodec FLAC
 → 임시 파일 → 출력 EOS 확인
 → 자원 close, release → STREAMINFO 보정
 → 최종 경로 확정 → Completed
```

| 구현 | 책임 |
| --- | --- |
| Microphone.capture | 수집 동안 마이크 읽기와 장치 정리 |
| FlowRecorder | 입력, 버퍼, 출력 조율, 제어와 진행 관찰 |
| FlacEncoderMediaCodec / MediaCodecFlac | Flow 기반 인코딩, PTS, EOS와 Android buffer 반환 |
| FileRecordingOutput | 파일 기록, 마감, 확정, 실패 시 폐기, STREAMINFO 보정 |

인코딩 상태는 압축 Flow를 수집하는 범위의 지역 변수입니다. 입력 Flow를 정상 수집한 뒤에만 EOS를 제출하고 남은 출력을 회수합니다. Android 출력 버퍼는 `use` 범위에서 복사하고 반환합니다. 파일 출력은 헤더와 빈 EOS를 제외한 실제 압축 프레임 크기를 집계해 STREAMINFO에 기록합니다.

[PTS](../../docs/audio-terms.md#pts)와 녹음 길이는 채널당 sample 수를 기준으로 계산합니다. PCM은 sample frame 경계로 분할합니다. codec-config 출력에는 FLAC 헤더가 있으므로 파일에 기록합니다.

입력 [EOS](../../docs/audio-terms.md#eos)를 전달한 뒤에도 출력 EOS까지 계속 회수해야 마지막 부분 블록이 보존됩니다. [MediaCodec EOS 계약](https://developer.android.com/reference/android/media/MediaCodec#end-of-stream-handling)

현재 마감 코드는 [MD5](../../docs/audio-terms.md#crc와-md5)를 새로 계산하지 않습니다. 인코더가 제공한 값은 그대로 둡니다. 따라서 MD5 필드의 존재만으로 데이터 검증을 완료했다고 판단하지 않습니다.

실기기에서는 압축 frame 최대 크기가 0인 파일의 큰 frame을 시스템 추출기가 반환하지 않는 현상을 확인했습니다. 같은 파일은 호스트 FFmpeg에서 정확히 복원되었습니다.

실제 최소, 최대 frame 크기를 기록한 뒤 시스템 왕복 테스트도 통과했습니다. 유효한 형식과 특정 구현의 호환성은 각각 확인해야 합니다.

## 7. 학습 내용을 확인하는 방법

- 위 고정 예측 예제를 잔차에서 원본으로 다시 계산합니다.
- 같은 잔차에 k=0, 1, 2를 적용해 Rice 코드 길이를 비교합니다.
- 일시정지 구간을 제외한 sample 수로 녹음 길이를 계산합니다.
- PCM → FLAC → PCM 왕복에서 마지막 sample까지 바이트 단위로 비교합니다.
- mono, stereo, 빈 입력, 짧은 마지막 블록을 나눠 검증합니다.

구현의 수명 계약과 테스트 기록은 [녹음, 재생 설계](../../docs/recording-architecture.md)에 정리되어 있습니다.
