# LiveMotion

> **실시간 얼굴 추적 기반 Live2D 캐릭터 애니메이션 Android 앱**

전면 카메라의 얼굴 인식 데이터를 실시간으로 Live2D 캐릭터에 매핑하여,
사용자의 표정과 머리 움직임이 그대로 캐릭터에 반영되는 Android 애플리케이션입니다.

<br>

## 📱 스크린샷

<table>
  <tr>
    <th>스플래시 1</th>
    <th>스플래시 2</th>
    <th>타이틀</th>
    <th>이용약관</th>
  </tr>
  <tr>
    <td><img src="https://github.com/user-attachments/assets/9918e18e-50a3-487e-b599-f100c89378f0" width="200" /></td>
    <td><img src="https://github.com/user-attachments/assets/594e6a24-39e0-48bf-950d-420c0901e0a5" width="200" /></td>
    <td><img src="https://github.com/user-attachments/assets/3a75518e-5062-4711-94e8-360b5c1ee35d" width="200" /></td>
    <td><img src="https://github.com/user-attachments/assets/6e5b4193-7362-462c-ba7b-9e64cdaf6b42" width="200" /></td>
  </tr>
  <tr>
    <th>설정</th>
    <th>모델 선택</th>
    <th>배경 선택</th>
    <th>스튜디오</th>
  </tr>
  <tr>
    <td><img src="https://github.com/user-attachments/assets/7aeb6a15-81ac-4e71-af03-8b7ac8ce4978" width="200" /></td>
    <td><img src="https://github.com/user-attachments/assets/01099845-04c0-4e5a-8e35-9a84f3ede561" width="200" /></td>
    <td><img src="https://github.com/user-attachments/assets/dd3c4a8d-b749-4c97-83a8-5b6db5d6cbe6" width="200" /></td>
    <td><img src="https://github.com/user-attachments/assets/158956f5-7bfd-4136-9494-e233e7f906a8" width="200" /></td>
  </tr>
  <tr>
    <th>녹화</th>
    <th colspan="2">가로 모드</th>
  </tr>
  <tr>
    <td><img src="https://github.com/user-attachments/assets/efdf511b-2e65-49a5-8731-678cf464719b" width="200" /></td>
    <td colspan="2"><img src="https://github.com/user-attachments/assets/9925c7b2-8f30-4f9a-b127-6c3218a0b5f7" width="412" /></td>
  </tr>
</table>

<br>

## ✨ 주요 기능

| 기능 | 설명 |
|------|------|
| **실시간 얼굴 추적** | MediaPipe Face Landmarker(478 랜드마크)로 얼굴 움직임을 실시간 감지 |
| **Live2D 캐릭터 렌더링** | Live2D Cubism SDK를 활용한 2D 캐릭터 실시간 렌더링 |
| **표정 매핑** | 눈 깜빡임, 입 벌림, 머리 회전(Yaw/Pitch/Roll), 홍채 추적 등을 Live2D 파라미터에 매핑 |
| **모델/배경 선택** | 내장 모델 및 SAF를 통한 외부 모델 불러오기, 다양한 배경 선택 |
| **감정 & 모션 재생** | 모델별 표정(.exp3.json) 및 모션(.motion3.json) 파일 재생 |
| **화면 녹화** | 스튜디오 화면을 영상으로 녹화 |
| **가로/세로 모드** | 세로 및 가로 모드 모두 지원하는 반응형 UI |
| **설정** | 테마(시스템/라이트/다크), 트래킹 감도, EMA 스무딩 강도 조절 |
| **GPU 가속** | MediaPipe GPU Delegate를 통한 하드웨어 가속 (자동 폴백) |

<br>

## 🏗️ 아키텍처

**클린 아키텍처** 기반의 **멀티모듈** 구조와 **MVI 패턴**을 적용하여 설계하였습니다.

### 모듈 의존성 그래프

```
app → feature:home     → core:ui
    → feature:settings → core:ui
                       → core:storage
    → feature:studio   → core:tracking → domain
                       → core:live2d   → live2d:framework → Live2DCubismCore.aar
                       → core:storage
                       → core:ui
                       → core:navigation
    → core:common      → core:tracking, core:storage (DI wiring)
    → core:navigation
    → domain
    → data             → core:storage, domain
```

### 모듈별 역할

| 모듈 | 역할 | 언어 |
|------|------|------|
| `app` | MainActivity, Navigation Host 설정 | Kotlin |
| `domain` | 순수 도메인 모델, Repository 인터페이스, UseCase | Kotlin |
| `data` | Repository 구현체, Hilt DI 모듈, Firebase Firestore 연동 | Kotlin |
| `core:tracking` | CameraX + MediaPipe 얼굴 추적 | Kotlin |
| `core:live2d` | Live2D SDK 래핑, GLSurfaceView 기반 렌더링 | Kotlin + Java |
| `core:storage` | SAF 권한 관리, 모델 캐싱, DataStore 기반 로컬 저장소 | Kotlin |
| `core:ui` | 공통 Compose Theme, 재사용 UI 컴포넌트 | Kotlin |
| `core:navigation` | NavKey(Type-safe Navigation), Navigator 인터페이스 | Kotlin |
| `core:common` | App 레벨 DI 모듈 (ModelAssetReader, FaceTrackerFactory) | Kotlin |
| `feature:home` | 스플래시, 타이틀, 이용약관 화면 | Kotlin |
| `feature:settings` | 설정 화면 (테마, 트래킹 감도, 스무딩) | Kotlin |
| `feature:studio` | 스튜디오 (트래킹 + 렌더링), 모델/배경 선택 화면 | Kotlin |
| `live2d:framework` | Live2D Cubism SDK Framework (벤더 코드) | Java |

### 데이터 흐름

```
카메라 프레임
    ↓
FaceTracker (CameraX + MediaPipe FaceLandmarker)
    ↓  StateFlow<FacePose>
MapFacePoseUseCase (EMA 스무딩 → Live2DParams 변환)
    ↓  Map<String, Float>
Live2DScreen (Compose ↔ GLSurfaceView)
    ↓  queueEvent
LAppMinimumLive2DManager (Live2D SDK 파라미터 적용)
    ↓
Live2D 캐릭터 렌더링
```

### MVI 패턴

```kotlin
// UiState — 단일 상태 클래스
data class StudioUiState(
    val isModelLoading: Boolean = true,
    val isZoomMode: Boolean = false, ...
)

// UiIntent — 사용자 액션 정의
sealed interface StudioUiIntent {
    data object ToggleZoom : StudioUiIntent
    data object ToggleRecord : StudioUiIntent
    ...
}

// UiEffect — 일회성 이벤트 (Snackbar, Navigation)
sealed class StudioUiEffect {
    data class ShowSnackbar(val message: String) : StudioUiEffect()
    ...
}
```

### Screen / Content 분리 패턴

```
StudioScreen (public)          — ViewModel 연결, Effect 수집, DI 의존
    └─ StudioScreenContent (private)  — 순수 UI, @Preview 지원
         └─ modelViewContent slot     — Live2DScreen을 슬롯으로 주입
```

- **Screen**: `hiltViewModel()`, `collectAsStateWithLifecycle()` 등 플랫폼 의존 로직
- **ScreenContent**: 파라미터(state, callback)만으로 동작하는 순수 Composable, `@Preview` 가능
- 네이티브 의존 컴포넌트는 `@Composable () -> Unit` 슬롯 파라미터로 주입하여 Preview에서 대체

<br>

## 🛠️ 기술 스택

### Android & UI
- **Language**: Kotlin 2.2.0
- **Min SDK**: 26 / **Target SDK**: 36
- **UI Framework**: Jetpack Compose (BOM 2026.01.01)
- **Navigation**: Jetpack Navigation Compose + kotlinx.serialization (Type-safe Routes)
- **DI**: Hilt (Dagger)
- **비동기 처리**: Kotlin Coroutines + StateFlow

### 카메라 & AI
- **Camera**: CameraX 1.5.3 (전면 카메라)
- **Face Tracking**: MediaPipe Face Landmarker (478 랜드마크 + Blendshapes)
- **GPU 가속**: MediaPipe GPU Delegate (자동 CPU 폴백)

### 렌더링
- **Live2D**: Cubism SDK for Native (Android AAR)
- **OpenGL ES**: GLSurfaceView 기반 실시간 렌더링

### 로컬 저장소 & 백엔드
- **DataStore**: Preferences DataStore (테마, 트래킹 설정, 동의 상태)
- **Firebase**: Firestore (이용약관 동의 추적)
- **SAF**: Storage Access Framework (외부 모델 파일 접근)

### 빌드
- **Build System**: Gradle Kotlin DSL
- **Code Generation**: KSP (Kotlin Symbol Processing)
- **Serialization**: kotlinx.serialization

<br>

## 📂 프로젝트 구조

```
LiveMotion/
├── app/                          # MainActivity, Navigation 설정
├── domain/                       # 순수 Kotlin 도메인 레이어
│   ├── model/                    #   FacePose, Live2DParams, ExternalModel 등
│   ├── repository/               #   Repository 인터페이스
│   └── usecase/                  #   MapFacePoseUseCase 등
├── data/                         # 데이터 레이어
│   ├── repository/               #   Repository 구현체
│   └── di/                       #   Hilt DI 모듈
├── core/
│   ├── tracking/                 #   FaceTracker, FaceTrackerFactory
│   ├── live2d/                   #   Live2DScreen, LAppMinimum* 래퍼
│   ├── storage/                  #   DataStore, SAF, 모델 캐싱
│   ├── ui/                       #   Compose Theme, 공통 컴포넌트
│   ├── navigation/               #   NavKey, Navigator
│   └── common/                   #   AppModule (DI), ModelAssetReader
├── feature/
│   ├── home/                     #   IntroScreen, TitleScreen, TermsOfServiceScreen
│   ├── settings/                 #   SettingsScreen
│   └── studio/                   #   StudioScreen, ModelSelectScreen
│       └── components/           #     FileListDialog, StudioIconButton 등
└── live2d/framework/             # Live2D Cubism SDK Framework (벤더)
```

<br>

## 🖥️ 화면 구성

| 화면 | 설명 | 스크린샷 |
|------|------|----------|
| **스플래시** | 앱 아이콘 → Live2D Cubism 로고 순서로 표시 | 스크린샷 1~2 |
| **타이틀** | Studio / Settings 진입점, 이용약관 링크 | 스크린샷 3 |
| **이용약관** | Live2D SDK 라이선스 관련 이용약관 동의 | 스크린샷 4 |
| **설정** | 테마 모드, 언어, 트래킹 감도(Yaw/Pitch/Roll), 스무딩 강도 조절 | 스크린샷 5 |
| **모델 선택** | 내장/외부 모델 그리드, SAF로 외부 모델 추가 | 스크린샷 6 |
| **배경 선택** | 다양한 배경 이미지 그리드, 외부 배경 추가 | 스크린샷 7 |
| **스튜디오** | Live2D 렌더링 + 얼굴 추적 미리보기, 감정/모션/녹화 컨트롤 | 스크린샷 8~9 |
| **가로 모드** | 가로 방향 반응형 레이아웃 (모델 뷰 + 컨트롤 패널) | 스크린샷 10 |

<br>

## 🔑 핵심 기술 포인트

### 1. 실시간 얼굴 추적 파이프라인
- MediaPipe FaceLandmarker의 478개 랜드마크와 Blendshapes를 활용
- `LIVE_STREAM` 모드로 카메라 프레임마다 얼굴 포즈 추출
- 5초간 자동 캘리브레이션으로 중립 포즈 기준점 설정
- GPU/CPU Delegate 자동 전환 및 폴백 처리

<details>
<summary>📄 참고 코드</summary>

**FaceLandmarker 초기화 및 GPU 자동 폴백** — `core/tracking/FaceTracker.kt`

```kotlin
fun setupFaceLandmarker(useGpu: Boolean = true) {
    val requestedDelegate = if (useGpu) Delegate.GPU else Delegate.CPU
    val baseOptionsBuilder = BaseOptions.builder()
        .setDelegate(requestedDelegate)
        .setModelAssetPath("face_landmarker.task")
    try {
        val optionsBuilder = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ -> processResult(result) }
            .setNumFaces(1)
            .setOutputFaceBlendshapes(true)
        faceLandmarker = FaceLandmarker.createFromOptions(context, optionsBuilder.build())
    } catch (e: Exception) {
        // GPU 초기화 실패 시 CPU로 자동 폴백
        if (requestedDelegate == Delegate.GPU) {
            val cpuBaseOptions = BaseOptions.builder()
                .setDelegate(Delegate.CPU)
                .setModelAssetPath("face_landmarker.task")
            // ... CPU로 재초기화
        }
    }
}
```

**Blendshapes 기반 표정 추출** — `core/tracking/FaceTracker.kt`

```kotlin
private fun calculatePose(landmarks: List<NormalizedLandmark>, scores: Map<String, Float>): FacePose {
    val nose = landmarks[4]; val leftEye = landmarks[33]; val rightEye = landmarks[263]
    // Yaw: 거울 모드 반전, Z좌표 깊이 차이 기반
    val yawNorm = -(rightEye.z() - leftEye.z()) * 15f
    // Pitch: 코끝-코 다리 Z좌표 차이 (원근법 왜곡 회피)
    val pitchZ = nose.z() - landmarks[6].z()
    // Blendshapes에서 눈·입 상태 추출
    val eyeL = scores["eyeBlinkRight"] ?: 0f          // 감은 정도
    val eyeWideL = scores["eyeWideRight"] ?: 0f       // 크게 뜬 정도
    val openL = (1f - eyeL) + (eyeWideL * 0.8f)       // 최종 눈 뜬 값
    val mouthRaw = scores["jawOpen"] ?: 0f
    val mouth = ((mouthRaw - 0.15f) / 0.85f).coerceIn(0f, 1f) // 노이즈 제거
    // Iris 랜드마크(468, 473)로 시선 추적
    val (eyeBallX, eyeBallY) = calculateIrisPosition(landmarks)
    return FacePose(yaw = yawNorm, pitch = pitchZ * 15f, eyeLOpen = openL, mouthOpen = mouth, ...)
}
```

**5초 자동 캘리브레이션** — `core/tracking/FaceTracker.kt`

```kotlin
private fun processResult(result: FaceLandmarkerResult) {
    // ...
    if (!isCalibrated) {
        if (currentTime - calibrationStartTime < 5000) {
            // 중립 포즈 데이터 수집
            sumYaw += pose.yaw; sumPitch += pose.pitch; sumRoll += pose.roll
            calibrationSampleCount++
        } else {
            // 오프셋 확정
            offsetYaw = sumYaw / calibrationSampleCount
            offsetPitch = sumPitch / calibrationSampleCount
            offsetRoll = sumRoll / calibrationSampleCount
            isCalibrated = true
        }
    }
    // 보정 적용
    if (isCalibrated) {
        pose = pose.copy(
            yaw = pose.yaw - offsetYaw,
            pitch = pose.pitch - offsetPitch,
            roll = pose.roll - offsetRoll
        )
    }
}
```

</details>

### 2. 얼굴 → Live2D 파라미터 매핑
- `FacePose` → `Live2DParams` 변환 UseCase (순수 함수)
- EMA(지수이동평균) 스무딩으로 자연스러운 애니메이션 구현
- 거울 모드: 전면 카메라의 Yaw, Roll, EyeBallX 부호 반전 처리
- 홍채 추적(Landmarks 468, 473)으로 시선 방향 반영

<details>
<summary>📄 참고 코드</summary>

**도메인 모델** — `domain/model/FacePose.kt`

```kotlin
data class FacePose(
    val yaw: Float = 0f,     // 좌우 회전 (-1.0 ~ 1.0)
    val pitch: Float = 0f,   // 상하 회전
    val roll: Float = 0f,    // 기울기
    val mouthOpen: Float = 0f,
    val mouthForm: Float = 0f,
    val eyeLOpen: Float = 1f,
    val eyeROpen: Float = 1f,
    val eyeBallX: Float = 0f, // 눈동자 좌우 (-1 ~ 1)
    val eyeBallY: Float = 0f  // 눈동자 상하 (-1 ~ 1)
)
```

**EMA 스무딩 + Live2D 파라미터 변환** — `domain/usecase/MapFacePoseUseCase.kt`

```kotlin
class MapFacePoseUseCase {
    // EMA: smoothed = lastValue + alpha * (newValue - lastValue)
    private fun smooth(last: Float, current: Float, alpha: Float): Float {
        return last + alpha * (current - last)
    }

    private fun map(newPose: FacePose, state: FacePoseSmoothingState, sensitivity: TrackingSensitivity)
        : Pair<Live2DParams, FacePoseSmoothingState> {
        val alpha = sensitivity.smoothing  // 기본값 0.4f, 설정에서 조절 가능
        val smoothed = FacePose(
            yaw = smooth(state.lastPose.yaw, newPose.yaw, alpha),
            pitch = smooth(state.lastPose.pitch, newPose.pitch, alpha),
            // ... 모든 필드에 EMA 적용
        )
        val params = buildParams(smoothed, sensitivity)
        return Pair(Live2DParams(params), FacePoseSmoothingState(lastPose = smoothed))
    }

    private fun buildParams(smoothed: FacePose, sensitivity: TrackingSensitivity): Map<String, Float> {
        val params = mutableMapOf<String, Float>()
        // 머리 회전 → Live2D 표준 범위 (-30 ~ 30)
        params["ParamAngleX"] = (smoothed.yaw * 30f * sensitivity.yaw).coerceIn(-30f, 30f)
        params["ParamAngleY"] = (-smoothed.pitch * 40f * sensitivity.pitch).coerceIn(-30f, 30f)
        // 눈, 입, 시선 파라미터
        params["ParamEyeLOpen"] = smoothed.eyeLOpen.coerceIn(0f, 2f)
        params["ParamMouthOpenY"] = (smoothed.mouthOpen * 2.1f).coerceIn(0f, 2.1f)
        params["ParamEyeBallX"] = smoothed.eyeBallX.coerceIn(-1f, 1f)
        // ...
        return params
    }
}
```

</details>

### 3. Live2D 렌더링 아키텍처
- Compose ↔ GLSurfaceView 브릿지 (`queueEvent`로 스레드 안전 통신)
- 핀치 줌 / 드래그 제스처를 통한 모델 확대·이동
- 모델별 표정·모션 에셋 동적 감지 및 재생

<details>
<summary>📄 참고 코드</summary>

**Compose-GL 브릿지** — `core/live2d/Live2DScreen.kt`

```kotlin
@Composable
fun Live2DScreen(
    modelSource: ModelSource? = null,
    faceParams: Map<String, Float>? = null,
    effectFlow: Flow<Live2DUiEffect>? = null,
    // ...
) {
    val glView = remember { Live2DGLSurfaceView(context) }

    // 얼굴 파라미터 → GL Thread로 전달
    LaunchedEffect(faceParams) {
        faceParams?.let { params ->
            glView.queueEvent {
                LAppMinimumLive2DManager.getInstance().applyFacePose(params)
            }
        }
    }

    // 모델 로드 → GL Thread에서 실행, 결과는 Main Thread로 전환
    LaunchedEffect(modelSource) {
        modelSource?.let { source ->
            val mainHandler = Handler(Looper.getMainLooper())
            glView.queueEvent {
                LAppMinimumLive2DManager.getInstance().setOnModelLoadListener(
                    object : LAppMinimumLive2DManager.OnModelLoadListener {
                        override fun onModelLoaded() {
                            mainHandler.post { onModelLoaded?.invoke() }
                        }
                    }
                )
                LAppMinimumLive2DManager.getInstance().loadModel(source.modelId)
            }
        }
    }

    // Effect (표정/모션 재생) → GL Thread에서 처리
    LaunchedEffect(effectFlow) {
        effectFlow?.collect { effect ->
            glView.queueEvent {
                when (effect) {
                    is Live2DUiEffect.StartExpression -> manager.startExpression(effect.path)
                    is Live2DUiEffect.StartMotion -> manager.startMotion(effect.path)
                    // ...
                }
            }
        }
    }

    AndroidView(factory = { glView })
}
```

</details>

### 4. 모듈화 & 의존성 관리
- 계층별 엄격한 의존성 규칙 (`domain` ← `core` ← `feature` ← `app`)
- Hilt를 이용한 생성자 주입, ViewModel에서 Context 의존성 제거
- 각 모듈의 독립적 빌드 및 테스트 가능

<details>
<summary>📄 참고 코드</summary>

**MVI 패턴 적용 ViewModel** — `feature/studio/StudioViewModel.kt`

```kotlin
@HiltViewModel
class StudioViewModel @Inject constructor(
    private val faceTrackerFactory: FaceTrackerFactory,  // Factory 주입
    private val mapFacePoseUseCase: MapFacePoseUseCase,  // 순수 UseCase 주입
    private val getModelMetadataUseCase: GetModelMetadataUseCase,
    // ...
) : ViewModel() {
    // State: 단일 UI 상태 객체
    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    // Effect: 일회성 이벤트
    private val _uiEffect = Channel<StudioUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    // Intent: 사용자 액션 처리
    fun onIntent(intent: StudioUiIntent) {
        when (intent) {
            is StudioUiIntent.ToggleGesture -> toggleGesture()
            is StudioUiIntent.SetGpuEnabled -> setGpuEnabled(intent.enabled)
            is StudioUiIntent.StartExpression -> startExpression(intent.path)
            // ...
        }
    }
}
```

**Screen/Content 분리 패턴** — `feature/studio/StudioScreen.kt`

```kotlin
// Screen (public): ViewModel + 플랫폼 의존 로직
@Composable
fun StudioScreen(modelSource: ModelSource, viewModel: StudioViewModel = hiltViewModel()) {
    val facePose by viewModel.facePose.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val faceParams = remember(facePose) { viewModel.mapFaceParams(facePose, ...) }

    StudioScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        modelViewContent = {
            Live2DScreen(faceParams = faceParams, modelSource = modelSource, ...)
        },
    )
}

// ScreenContent (private): 순수 UI, @Preview 지원
@Composable
private fun StudioScreenContent(
    uiState: StudioViewModel.StudioUiState,
    onIntent: (StudioUiIntent) -> Unit,
    modelViewContent: @Composable () -> Unit = {},  // 네이티브 뷰를 슬롯으로 주입
) {
    // 순수 파라미터만으로 동작하는 UI 구성
}
```

</details>

<br>

## 👤 개발 정보

- **개발 인원**: 1인 개발
- **개발 기간**: 2025.01 ~ 진행 중
- **플랫폼**: Android

<br>

## 📄 라이선스

이 프로젝트는 [Live2D Cubism SDK](https://www.live2d.com/en/sdk/license/)의 라이선스 정책을 따릅니다.