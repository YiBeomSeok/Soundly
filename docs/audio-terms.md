# 오디오 용어

Soundly의 녹음, 재생 코드와 [FLAC 학습 노트](../soundly-sdk/recording-android/FLAC.md)를 읽을 때 사용하는 용어를 정리합니다.

## PCM

PCM(Pulse Code Modulation)은 소리의 진폭을 일정한 시간 간격으로 측정하고 수치로 표현하는 방식입니다. 디지털 오디오에서는 이 수치의 나열을 입력 데이터로 다룹니다.

마이크의 아날로그 신호를 디지털 값으로 바꾸는 과정에는 샘플링과 양자화가 포함됩니다. FLAC의 무손실 복원은 이 과정을 거쳐 만들어진 PCM을 기준으로 합니다.

## Sample과 sample frame

sample은 한 채널에서 한 시점에 측정한 진폭 값입니다. sample frame은 같은 시점의 모든 채널 sample을 묶은 단위입니다.

mono의 sample frame에는 값 하나, stereo의 sample frame에는 왼쪽, 오른쪽 값 두 개가 들어갑니다. Soundly의 녹음 길이와 PTS 계산에서 sample 수는 채널당 sample 수, 즉 sample frame 수를 의미합니다.

## Sample rate

sample rate는 채널마다 1초에 표현하는 sample의 수입니다. 단위는 Hz이며, 44.1kHz는 한 채널에 초당 44,100개의 sample이 있다는 뜻입니다.

채널이 두 개라고 sample rate가 두 배가 되지는 않습니다. 같은 시간 동안 저장하는 값의 개수가 두 배가 됩니다.

## Bit depth와 PCM16

bit depth는 sample 하나를 표현하는 데 사용하는 비트 수입니다. PCM16은 sample 하나를 16비트, 즉 2바이트로 표현합니다.

signed PCM16의 정수 범위는 −32,768부터 32,767까지입니다. Soundly는 이 형식의 입력을 사용합니다. 32비트 부동소수점 PCM과는 값의 표현 방식이 다릅니다.

## 채널과 interleaved PCM

채널은 독립적인 오디오 신호의 흐름입니다. mono는 한 채널, stereo는 왼쪽, 오른쪽 두 채널을 사용합니다.

interleaved PCM은 같은 시점의 채널 값을 나란히 배치합니다. stereo라면 `L0, R0, L1, R1, …` 순서입니다. Soundly는 PCM을 자를 때 이 묶음의 중간에서 끊지 않습니다.

## Endianness

endianness는 여러 바이트로 표현하는 값의 바이트 순서입니다. little endian은 낮은 자리의 바이트부터, big endian은 높은 자리의 바이트부터 저장합니다.

예를 들어 16비트 값 `0x1234`는 little endian에서 `34 12`, big endian에서 `12 34`로 저장합니다. Soundly의 입력 PCM16은 little endian입니다. 이 규칙을 FLAC 파일의 모든 필드에 그대로 적용해서는 안 됩니다.

## Chunk, block, frame, subframe

chunk는 API가 한 번 전달하는 데이터 묶음을 가리키는 일반적인 표현입니다. PCM chunk 크기는 압축 frame 크기와 일치할 필요가 없습니다.

FLAC의 block은 인코딩을 위해 묶은 채널별 sample들의 구간입니다. frame은 해당 구간의 압축 데이터에 헤더와 검사값을 붙인 단위이며, subframe은 frame 안에서 채널 하나를 표현합니다.

PCM의 sample frame과 FLAC의 압축 frame은 서로 다른 단위입니다. 문맥에 따라 구분해서 읽어야 합니다.

## Codec, encoder, decoder

codec은 오디오나 영상을 부호화하고 복원하는 방식 또는 구현을 가리킵니다. encoder는 입력을 압축된 표현으로 바꾸고, decoder는 그 표현을 풀어냅니다.

Soundly의 FLAC encoder는 Android MediaCodec을 사용합니다. 재생에는 Media3와 libFLAC을 사용합니다. 같은 FLAC 형식을 다루더라도 각 구현의 지원 범위와 동작은 별도로 검증합니다.

## 파일 형식과 container

파일 형식은 데이터와 부가 정보를 파일에 배치하는 규칙입니다. container는 압축된 데이터 등의 스트림을 메타데이터와 함께 담는 구조입니다.

Soundly는 WAV 파일에 PCM을 저장하고, FLAC 파일에는 FLAC으로 부호화한 오디오를 저장합니다. 확장자를 바꾸는 것만으로 내부 데이터 형식이 변하지는 않습니다.

## 무손실 압축

무손실 압축은 디코딩 결과가 인코딩에 넣은 데이터와 정확히 같은 압축입니다. FLAC에서는 입력 PCM sample을 정확히 복원해야 합니다.

원본 FLAC 파일과 재인코딩한 FLAC 파일의 바이트가 같아야 한다는 뜻은 아닙니다. 압축 방식이나 메타데이터가 달라도 복원한 PCM이 같을 수 있습니다.

## 예측과 residual

예측은 이미 알고 있는 sample로 다음 sample 값을 추정하는 과정입니다. residual, 또는 잔차는 실제 값에서 예측값을 뺀 차이입니다.

디코더는 동일한 방법으로 예측값을 계산하고 잔차를 더합니다. 예측이 틀려도 그 차이를 저장하므로 원본을 복원할 수 있습니다.

## LPC

LPC(Linear Predictive Coding)는 이전 sample들의 가중합으로 현재 sample을 예측하는 방식입니다.

FLAC은 예측 차수, 초기 sample, 정수 계수, shift와 잔차를 저장해 디코더가 같은 계산을 수행하게 합니다. 자세한 계산 예시는 [FLAC의 예측과 잔차](../soundly-sdk/recording-android/FLAC.md#3-예측과-잔차)에 있습니다.

## Rice coding

Rice coding은 음이 아닌 정수를 `2^k`로 나눈 몫과 나머지로 표현하는 부호화 방식입니다. 작은 정수가 자주 나타나는 데이터에 유리합니다.

FLAC은 signed 잔차를 음이 아닌 정수로 변환한 뒤 부호화합니다. 실제 비트 계산은 [Rice 잔차 부호화](../soundly-sdk/recording-android/FLAC.md#4-rice-잔차-부호화)를 참고합니다.

## Metadata와 STREAMINFO

metadata는 오디오 값 이외에 해석이나 탐색을 돕는 정보입니다. FLAC의 STREAMINFO는 sample rate, 채널 수, bit depth와 같은 스트림 정보를 담는 필수 metadata block입니다.

전체 sample 수가 알려져 있으면 길이를 계산하는 데 사용할 수 있습니다. 라이브 인코딩처럼 처음에 알 수 없는 일부 값은 파일 마감 단계에서 채울 수 있습니다.

## CRC와 MD5

CRC와 MD5는 데이터를 이용해 계산하는 검사값입니다. FLAC의 frame CRC는 해당 frame의 손상을 검출하는 데 사용하며, STREAMINFO의 PCM MD5는 복원한 전체 오디오를 검증하는 데 사용합니다.

Soundly의 현재 마감 코드는 MD5를 새로 계산하지 않습니다. 테스트에서는 입력 PCM과 디코딩한 PCM을 바이트 단위로 직접 비교합니다.

## Buffer

buffer는 데이터를 주고받거나 처리하기 위해 잠시 보관하는 메모리 영역입니다. AudioRecord의 장치 buffer, 앱이 읽는 PCM 배열, MediaCodec의 입력, 출력 buffer는 각각 별도로 관리됩니다.

MediaCodec에서 꺼낸 출력 buffer는 데이터를 복사한 뒤 반환해야 합니다. buffer의 수명과 파일에 기록한 데이터의 수명을 구분해야 합니다.

## PTS

PTS(Presentation Timestamp)는 데이터가 재생되어야 하는 시점을 나타냅니다. Soundly는 인코더에 제출한 채널당 sample 수로 입력 PTS를 계산합니다.

```text
PTS(μs) = 제출한 sample frame 수 × 1,000,000 / sample rate
```

벽시계 경과 시간을 그대로 사용하는 것과 달리, 실제 제출한 오디오의 길이를 기준으로 합니다.

## EOS

EOS(End Of Stream)는 스트림의 끝을 알리는 신호입니다. MediaCodec의 입력 EOS는 더 이상 PCM을 제출하지 않는다는 뜻이며, 출력 EOS는 인코더가 마지막 출력까지 내보냈다는 뜻입니다.

두 시점은 일치하지 않을 수 있습니다. 입력 EOS 이후에도 출력 EOS를 확인할 때까지 데이터를 회수해야 합니다.
