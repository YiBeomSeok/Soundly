# Compose UI ↔ ViewModel 아키텍처 가이드

## 개요

Compose UI와 ViewModel 간의 관계를 상태 지향(State-driven) 아키텍처로 설계하는 방법을 정리합니다. 이 패턴은 UI의 복잡성을 줄이고, 테스트 가능성을 높이며, 사이드이펙트를 예측 가능하게 만듭니다.

## 핵심 원칙

### 1. 단방향 데이터 흐름 (Unidirectional Data Flow)
```
User Action → ViewModel State Change → UI Update → Side Effect → State Update
```

### 2. 상태 중심 설계 (State-Driven Design)
- 모든 UI 상태를 명시적으로 모델링
- 상태 변화가 사이드이펙트를 트리거
- UI는 상태를 관찰하고 반응

### 3. 책임 분리 (Separation of Concerns)
- **ViewModel**: 순수 상태 관리만 담당
- **Composable**: 상태 관찰 + 사이드이펙트 오케스트레이션
- **UI Components**: 순수 UI 렌더링

---

## 상태 모델링 패턴

### 기본 상태 구조
```kotlin
data class UiState(
    // 주요 상태
    val mainState: MainState,
    val permission: PermissionState,
    val loading: LoadingState,
    
    // 내부 플래그 (사이드이펙트 제어용)
    val hasTriggeredAction: Boolean,
    val userHasInteracted: Boolean,
    
    // 데이터
    val items: List<Item>,
    val error: String? = null
)

sealed interface MainState {
    data object Idle : MainState
    data object Processing : MainState
    data object Completed : MainState
    data class Error(val cause: Throwable?) : MainState
}

sealed interface PermissionState {
    data object Unknown : PermissionState
    data object Granted : PermissionState
    data object Denied : PermissionState
    data object PermanentlyDenied : PermissionState
    data object Requesting : PermissionState
    data object OpenSettings : PermissionState
}
```

### 상태 설계 가이드라인

1. **명시적 상태**: 모든 가능한 UI 상태를 sealed interface로 표현
2. **내부 플래그**: 사이드이펙트 중복 실행 방지용 불리언 플래그
3. **불변성**: 상태는 불변 객체로 관리
4. **기본값**: Companion object에 Default 값 제공

---

## ViewModel 설계 패턴

### 메서드 네이밍 컨벤션

```kotlin
class MyViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState.Default)
    val uiState = _uiState.asStateFlow()
    
    // ✅ 상태 전이 메서드 (set- 접두사)
    fun setMainState(state: MainState) {
        _uiState.update { it.copy(mainState = state) }
    }
    
    fun setPermissionState(state: PermissionState) {
        _uiState.update { it.copy(permission = state) }
    }
    
    fun setItems(items: List<Item>) {
        _uiState.update { it.copy(items = items) }
    }
    
    fun setError(message: String?) {
        _uiState.update { it.copy(error = message) }
    }
    
    fun setActionTriggered(triggered: Boolean) {
        _uiState.update { it.copy(hasTriggeredAction = triggered) }
    }
    
    // ✅ 토글 대신 명시적 set 사용
    fun setListVisible(visible: Boolean) {
        _uiState.update { it.copy(showList = visible) }
    }
    
    // ❌ 피해야 할 패턴
    // fun toggleList() { ... }  // 명시적이지 않음
    // fun startProcess() { ... }  // 사이드이펙트 포함
}
```

### ViewModel 규칙

1. **순수성**: 외부 의존성 없이 상태만 관리
2. **set- 컨벤션**: 모든 공개 메서드는 `set` 접두사 사용
3. **단일 책임**: 상태 전이만 담당, 비즈니스 로직은 별도 클래스
4. **불변성**: 상태 업데이트는 `copy()` 사용

---

## Composable 설계 패턴

### 단일 LaunchedEffect 오케스트레이션

```kotlin
@Composable
fun MyScreen(
    viewModel: MyViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // 외부 의존성 (런처, 매니저 등)
    val permissionLauncher = rememberLauncherForActivityResult(...)
    val myManager = remember { MyManager() }
    
    // OS 상태 관찰
    var osPermission by remember { mutableStateOf(checkPermission()) }
    var userHasAsked by rememberSaveable { mutableStateOf(false) }
    
    // 🎯 핵심: 단일 LaunchedEffect로 모든 사이드이펙트 오케스트레이션
    LaunchedEffect(uiState) {
        // 1. OS 상태 → ViewModel 상태 반영
        val targetPermission = when {
            osPermission -> PermissionState.Granted
            !shouldShowRationale && userHasAsked -> PermissionState.PermanentlyDenied
            else -> PermissionState.Denied
        }
        
        if (uiState.permission != targetPermission && 
            uiState.permission != PermissionState.Requesting &&
            uiState.permission != PermissionState.OpenSettings) {
            viewModel.setPermissionState(targetPermission)
        }
        
        // 2. 상태별 사이드이펙트 실행
        when (uiState.permission) {
            PermissionState.Requesting -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
            PermissionState.OpenSettings -> {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ...)
                settingsLauncher.launch(intent)
            }
            else -> Unit
        }
        
        // 3. 메인 액션 실행 (멱등성 보장)
        if (uiState.mainState == MainState.Processing && 
            uiState.permission == PermissionState.Granted &&
            !uiState.hasTriggeredAction) {
            
            viewModel.setActionTriggered(true)
            val result = myManager.performAction()
            viewModel.setItems(result.items)
            viewModel.setMainState(MainState.Completed)
        }
        
        // 4. 에러 처리
        if (uiState.mainState is MainState.Error) {
            // 에러 로깅, 사용자 알림 등
        }
    }
    
    // UI 렌더링
    MyScreenContent(
        state = uiState,
        onButtonClick = { viewModel.setMainState(MainState.Processing) },
        onPermissionRequest = { 
            if (uiState.permission == PermissionState.PermanentlyDenied) {
                viewModel.setPermissionState(PermissionState.OpenSettings)
            } else {
                viewModel.setPermissionState(PermissionState.Requesting)
            }
        }
    )
}
```

### Composable 규칙

1. **단일 LaunchedEffect**: 모든 사이드이펙트를 하나의 `LaunchedEffect(uiState)`에서 관리
2. **상태 기반 실행**: 조건문으로 사이드이펙트 실행 여부 결정
3. **멱등성 보장**: 플래그로 중복 실행 방지
4. **순서 보장**: 상태 반영 → 사이드이펙트 실행 순서 유지

---

## 실제 예시: 카메라 권한 + 사진 촬영

### 상태 모델
```kotlin
data class CameraUiState(
    val cameraState: CameraState,
    val permission: PermissionState,
    val photos: List<Photo>,
    val permissionAsked: Boolean,
    val photoTaken: Boolean,
    val error: String?
) {
    companion object {
        val Default = CameraUiState(
            cameraState = CameraState.Idle,
            permission = PermissionState.Unknown,
            photos = emptyList(),
            permissionAsked = false,
            photoTaken = false,
            error = null
        )
    }
}

sealed interface CameraState {
    data object Idle : CameraState
    data object Taking : CameraState
    data object Processing : CameraState
    data class Error(val cause: Throwable?) : CameraState
}
```

### ViewModel
```kotlin
class CameraViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CameraUiState.Default)
    val uiState = _uiState.asStateFlow()
    
    fun setCameraState(state: CameraState) {
        _uiState.update { it.copy(cameraState = state) }
    }
    
    fun setPermissionState(state: PermissionState) {
        _uiState.update { it.copy(permission = state) }
    }
    
    fun setPermissionAsked(asked: Boolean) {
        _uiState.update { it.copy(permissionAsked = asked) }
    }
    
    fun setPhotoTaken(taken: Boolean) {
        _uiState.update { it.copy(photoTaken = taken) }
    }
    
    fun addPhoto(photo: Photo) {
        _uiState.update { it.copy(photos = listOf(photo) + it.photos) }
    }
    
    fun setError(message: String?) {
        _uiState.update { it.copy(error = message) }
    }
}
```

### Composable
```kotlin
@Composable
fun CameraScreen(
    viewModel: CameraViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    val cameraManager = remember { CameraManager(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            viewModel.setPermissionAsked(true)
        }
    }
    
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 설정에서 돌아왔을 때 권한 재확인
    }
    
    var osPermission by remember { 
        mutableStateOf(isCameraPermissionGranted(context)) 
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    
    LaunchedEffect(uiState) {
        // 1. OS 권한 상태 반영
        val targetPermission = when {
            osPermission -> PermissionState.Granted
            !shouldShowRationale && asked -> PermissionState.PermanentlyDenied
            else -> PermissionState.Denied
        }
        
        if (uiState.permission != targetPermission &&
            uiState.permission != PermissionState.Requesting &&
            uiState.permission != PermissionState.OpenSettings) {
            viewModel.setPermissionState(targetPermission)
        }
        
        // 2. 권한 요청
        when (uiState.permission) {
            PermissionState.Requesting -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
            PermissionState.OpenSettings -> {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ...)
                settingsLauncher.launch(intent)
            }
            else -> Unit
        }
        
        // 3. 사진 촬영
        if (uiState.cameraState == CameraState.Taking &&
            uiState.permission == PermissionState.Granted &&
            !uiState.photoTaken) {
            
            viewModel.setPhotoTaken(true)
            try {
                val photo = cameraManager.takePhoto()
                viewModel.addPhoto(photo)
                viewModel.setCameraState(CameraState.Processing)
            } catch (e: Exception) {
                viewModel.setError("사진 촬영 실패: ${e.message}")
                viewModel.setCameraState(CameraState.Error(e))
            }
        }
        
        // 4. 처리 완료
        if (uiState.cameraState == CameraState.Processing) {
            viewModel.setCameraState(CameraState.Idle)
        }
    }
    
    CameraScreenContent(
        state = uiState,
        onTakePhotoClick = { viewModel.setCameraState(CameraState.Taking) },
        onPermissionRequest = {
            if (uiState.permission == PermissionState.PermanentlyDenied) {
                viewModel.setPermissionState(PermissionState.OpenSettings)
            } else {
                viewModel.setPermissionState(PermissionState.Requesting)
            }
        }
    )
}
```

---

## 안티패턴과 해결책

### ❌ 잘못된 패턴

```kotlin
// 1. Composable에서 직접 사이드이펙트 실행
@Composable
fun BadScreen() {
    val viewModel = viewModel()
    
    Button(onClick = {
        // ❌ 직접 권한 요청
        permissionLauncher.launch(Manifest.permission.CAMERA)
        // ❌ 직접 카메라 호출
        cameraManager.takePhoto()
    }) {
        Text("Take Photo")
    }
}

// 2. 다중 LaunchedEffect
@Composable
fun BadScreen() {
    LaunchedEffect(permission) {
        // 권한 처리
    }
    
    LaunchedEffect(cameraState) {
        // 카메라 처리
    }
    
    LaunchedEffect(photos) {
        // 사진 처리
    }
}

// 3. ViewModel에서 사이드이펙트
class BadViewModel : ViewModel() {
    fun takePhoto() {
        // ❌ ViewModel에서 직접 카메라 호출
        cameraManager.takePhoto()
    }
}
```

### ✅ 올바른 패턴

```kotlin
// 1. 상태 기반 사이드이펙트
@Composable
fun GoodScreen() {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    LaunchedEffect(uiState) {
        if (uiState.cameraState == CameraState.Taking && 
            uiState.permission == PermissionState.Granted) {
            // 사이드이펙트 실행
        }
    }
    
    Button(onClick = { 
        viewModel.setCameraState(CameraState.Taking) 
    }) {
        Text("Take Photo")
    }
}

// 2. 단일 LaunchedEffect
@Composable
fun GoodScreen() {
    LaunchedEffect(uiState) {
        // 모든 사이드이펙트를 순서대로 처리
    }
}

// 3. ViewModel은 순수 상태만
class GoodViewModel : ViewModel() {
    fun setCameraState(state: CameraState) {
        _uiState.update { it.copy(cameraState = state) }
    }
}
```

---

## 장점과 이점

### 1. 예측 가능성
- 상태 변화가 사이드이펙트를 트리거하므로 동작이 예측 가능
- 디버깅이 쉬움

### 2. 테스트 용이성
- ViewModel은 순수 함수로 테스트 가능
- UI 컴포넌트는 상태와 콜백만 받아 테스트 가능

### 3. 유지보수성
- 단일 LaunchedEffect로 사이드이펙트 관리
- 상태 중심으로 로직 파악 용이

### 4. 재사용성
- UI 컴포넌트는 상태와 콜백만 받아 재사용 가능
- ViewModel 로직을 다른 UI에서도 활용 가능

---

## 요약

이 아키텍처 패턴의 핵심은 **상태 중심 설계**입니다:

1. **ViewModel**: 순수 상태 관리 (`set` 메서드)
2. **Composable**: 단일 `LaunchedEffect(uiState)`로 사이드이펙트 오케스트레이션
3. **UI Components**: 상태와 콜백만 받는 순수 컴포넌트

이를 통해 복잡한 UI 플로우를 예측 가능하고 테스트 가능한 코드로 만들 수 있습니다.


