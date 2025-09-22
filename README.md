## 레퍼런스

### 미디어 코덱
https://developer.android.com/reference/android/media/MediaCodec
### 오디오 레코드
https://developer.android.com/reference/android/media/AudioRecord
### 샘플
https://github.com/aahlenst/android-audiorecord-sample?utm_source=chatgpt.com
### 미디어 포맷
https://developer.android.com/reference/android/media/MediaFormat
### 미디어 먹서
https://developer.android.com/reference/android/media/MediaMuxer
### libflac
https://github.com/androidx/media/tree/release/libraries/decoder_flac

---

# Soundly 아키텍처 가이드

## 프로젝트 구조

```
Soundly/
├── app/                     
├── core/designsystem/       
├── data/recording/          
├── domain/recording/        
├── feature/                 
│   ├── main/                
│   ├── recording/           
│   ├── recording-api/       
│   └── nav-graph/           
├── soundly-sdk/           
│   ├── media-recorder/      
│   ├── decoder_flac/        
│   └── player/              
└── build-logic/             
```

## 아키텍처 패턴

### Clean Architecture

- **Domain Layer**
- **Data Layer**
- **Presentation Layer**

### MVVM + Compose

- **Model**: Domain 엔티티와 UseCase
- **View**: Compose UI 컴포넌트
- **ViewModel**: UI 상태 관리

## 주요 기술 스택

### UI

- **Jetpack Compose**
- **Material 3**
- **Navigation Compose**

### 의존성 주입

- **Hilt**

### 비동기 처리

- **Kotlin Coroutines**
- **Flow**

### 데이터베이스

- **Room**: 2.7.2

### 오디오 처리

- **Media3**
- **AudioRecord**: 실시간 녹음
- **MediaCodec**: FLAC 인코딩
- **libflac**: FLAC 디코딩

### 테스트

- **JUnit**
- **Kotest**

## 모듈별 책임

### app

- 앱의 진입점
- 의존성 주입 설정
- 전역 설정

### core/designsystem

- 공통 UI 컴포넌트
- 테마 설정
- 디자인 토큰

### data/recording

- 녹음 데이터 저장
- Room 데이터베이스

### domain/recording

- 비즈니스 로직
- UseCase 정의

### feature/recording

- 녹음 UI
- 녹음 상태 관리
- 사용자 인터랙션

### soundly-sdk/recording-android

- 오디오 녹음 엔진
- WAV/FLAC 포맷 지원
- 실시간 오디오 처리

## 테스트 전략

### Unit Tests

- UseCase 테스트
- Repository 테스트
- ViewModel 테스트

## 오디오 처리

- 스트리밍 방식
- 버퍼 최적화
- 백그라운드 처리

## 확장성

### 모듈화

- 기능별 모듈 분리
- 의존성 최소화
- 재사용 가능한 컴포넌트

---

## 과제 제출 관련 안내

본 프로젝트는 **채용 전형 과제**로 작성된 코드이며,  
지원자의 기술력 · 설계 능력 · 문제 해결 접근 방식을 보여주기 위한 목적으로만 사용됩니다.

이 코드는 별도의 **상용 라이선스**를 허용하지 않으며,  
면접 및 평가 이외의 용도로 활용하는 것을 금합니다.

## License

MIT License

Copyright (c) 2025 [yibeomseok]

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.