# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

LiveMotion is an Android app that renders Live2D characters driven by real-time face tracking via the front camera. Users select a Live2D model, and the app uses MediaPipe Face Landmarker to capture facial movements (head rotation, eye blinks, iris tracking, mouth movements) and maps them to Live2D parameters in real-time.

## Build Commands
Don't use build commands.
User will do it manually.

## Architecture

### Module Dependency Graph

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
    → data             → core:storage, domain (repository impls)
```

Dependency rules:
- `domain` — pure Kotlin, no Android dependencies, no module dependencies
- `core:*` — may depend on `domain` only (except `core:common` which wires DI across core modules)
- `feature:*` — may depend on `core:*` and `domain`
- `data` — may depend on `core:storage` and `domain`
- `app` — may depend on any module; contains only `MainActivity` and navigation setup

### Key Modules

| Module | Purpose | Language |
|--------|---------|----------|
| `domain` | Data classes (`FacePose`, `ExternalModel`, `UserConsent`, `TrackingSensitivity`, `ThemeMode`), repository interfaces, UseCases — pure Kotlin, no Android deps | Kotlin |
| `data` | Repository implementations (`ModelRepositoryImpl`, `ExternalModelRepositoryImpl`, `ConsentRepositoryImpl`), Hilt modules for UseCases and Repositories, Firebase Firestore integration | Kotlin |
| `core:common` | App-level DI wiring (`AppModule` — provides `ModelAssetReader`, `FaceTrackerFactory`), shared utilities (`ModelAssetReader`) | Kotlin |
| `core:tracking` | `FaceTracker` (CameraX + MediaPipe) — face detection and landmark extraction, `FaceTrackerFactory` | Kotlin |
| `core:live2d` | Live2D rendering wrapper — `Live2DScreen` composable, `Live2DGLSurfaceView`, `LAppMinimum*` Java classes | Kotlin + Java |
| `core:navigation` | `NavKey` sealed interface for type-safe navigation, `Navigator` interface | Kotlin |
| `core:storage` | SAF permission management, model caching, external model metadata, DataStore-based local data sources (`ConsentLocalDataSource`, `ThemeLocalDataSource`, `TrackingSettingsLocalDataSource`) | Kotlin |
| `core:ui` | Shared Compose theme (colors, typography), reusable UI components (snackbar) | Kotlin |
| `feature:home` | `IntroScreen` (Cubism splash), `TitleScreen` (app entry), `TermsOfServiceScreen` (이용약관 동의) | Kotlin |
| `feature:settings` | `SettingsScreen` (테마 모드 전환, 트래킹 감도/스무딩 조절) | Kotlin |
| `feature:studio` | `StudioScreen` (tracking+rendering), `ModelSelectScreen` (model picker) | Kotlin |
| `live2d:framework` | Live2D Cubism SDK Framework — vendored Java library, do not modify | Java |

### Data Flow: Face Tracking to Live2D Rendering

1. **`FaceTracker`** (`core:tracking`) — starts CameraX, runs MediaPipe `FaceLandmarker` in `LIVE_STREAM` mode on each camera frame. Extracts `FacePose` (yaw/pitch/roll from landmarks, eye/mouth/iris from blendshapes). Emits via `StateFlow<FacePose>`. Handles auto-calibration (5s neutral pose), GPU/CPU delegate switching with automatic fallback, and camera/preview separation.

2. **`MapFacePoseUseCase`** (`domain`) — pure function that applies EMA smoothing to `FacePose` fields, then converts to `Live2DParams` map (e.g., `ParamAngleX`, `ParamEyeLOpen`, `ParamMouthOpenY`). Smoothing state managed externally via `FacePoseSmoothingState`.

3. **`Live2DScreen`** (`core:live2d`) — Compose wrapper that hosts `Live2DGLSurfaceView`. Receives `faceParams` map and queues parameter application to the GL thread via `queueEvent`. Also handles model loading and gesture modes (pinch zoom, drag).

4. **`LAppMinimumLive2DManager`** (`core:live2d`) — singleton Java class that bridges Compose/Kotlin code to the Live2D SDK. Manages model loading from assets, applies face parameters, controls projection matrix for zoom/pan, and triggers expressions/motions from files.

### Navigation Flow

`MainActivity` → `NavHost` with routes:
- `NavKey.Intro` → `IntroScreen` (Cubism logo splash, 1s timeout)
- `NavKey.Title` → `TitleScreen` (Studio/Settings buttons)
- `NavKey.TermsOfService(viewOnly)` → `TermsOfServiceScreen` (이용약관 동의, viewOnly=true면 조회 전용)
- `NavKey.Settings` → `SettingsScreen`
- `NavKey.ModelSelect` → `ModelSelectScreen` (scans assets + external models)
- `NavKey.Studio(modelId, isExternal, cachePath?, modelJsonName?)` → `StudioScreen` (face tracking + Live2D rendering, Asset/External 모델 모두 지원)

### Live2D Model Assets

Models are placed in `app/src/main/assets/{modelId}/` with a `{modelId}.model3.json` file. Optional `expressions/` and `motions/` subfolders (case-insensitive matching) enable expression/motion buttons in the studio UI. The native SDK AAR is at `app/libs/Live2DCubismCore.aar`.

### MediaPipe Setup

The `face_landmarker.task` model file must be in `app/src/main/assets/`. FaceTracker uses 478 landmarks with blendshapes enabled for eye blink, eye wide, jaw open, mouth smile detection, and iris tracking (landmarks 468, 473).

## Code Placement Rules

- New data classes → `domain/model/`
- New repository interfaces → `domain/repository/`
- New UseCases → `domain/usecase/`
- Repository implementations → `data/repository/`
- DI modules → `data/di/` (UseCases, Repositories) or `core:common/di/` (app-level singletons)
- New screens → `feature:*` module (home, settings, or studio)
- Reusable UI components → `core:ui`
- Live2D logic → `core:live2d`
- Face tracking logic → `core:tracking`
- Storage/caching/DataStore logic → `core:storage`
- Shared utilities (asset reading etc.) → `core:common`
- Do not add business logic to the `app` module (navigation setup only)

## Package Naming

| Module | Package |
|--------|---------|
| domain | `org.comon.domain.*` |
| data | `org.comon.data.*` |
| core:common | `org.comon.common.*` |
| core:tracking | `org.comon.tracking.*` |
| core:live2d | `org.comon.live2d.*` |
| core:ui | `org.comon.ui.*` |
| core:navigation | `org.comon.navigation.*` |
| core:storage | `org.comon.storage.*` |
| feature:home | `org.comon.home.*` |
| feature:settings | `org.comon.settings.*` |
| feature:studio | `org.comon.studio.*` |

## Technical Details

- **Min SDK**: 26, **Target/Compile SDK**: 36
- **JVM Target**: 11 (all modules except `live2d:framework` which uses Java toolchain 17)
- **Compose BOM**: 2026.01.01
- **Kotlin**: 2.2.0
- **DI**: Hilt with `@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`
- **Navigation**: Jetpack Navigation Compose with type-safe routes via kotlinx.serialization
- **Camera**: CameraX 1.5.3 with front camera; preview and analysis are decoupled (tracking works without visible preview)
- **Mirror mode**: Yaw, Roll, and EyeBallX are sign-inverted for front camera
- **Firebase**: Firebase BOM 34.9.0, Firestore for user consent tracking
- **DataStore**: Preferences DataStore for local persistence (theme, tracking settings, consent)
- **`live2d:framework`**: Vendored SDK code — avoid modifying files in this module

## MVI Pattern

ViewModels follow MVI (Model-View-Intent) pattern:
- **UiState**: Single state class holding all UI state
- **UiIntent**: Sealed interface for user actions (e.g., `StudioUiIntent.ToggleZoom`)
- **UiEffect**: Sealed class for one-time events (snackbar, navigation)
- **onIntent()**: Single entry point for handling user actions

Example:
```kotlin
@HiltViewModel
class StudioViewModel @Inject constructor(...) : ViewModel() {
    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    fun onIntent(intent: StudioUiIntent) {
        when (intent) {
            is StudioUiIntent.ToggleZoom -> toggleZoom()
            // ...
        }
    }
}
```

## Screen/Content 분리 패턴

Screen composable은 두 계층으로 분리:

- **`XxxScreen`** (public) — ViewModel 연결 전용. `hiltViewModel()`, `collectAsStateWithLifecycle()`, `LaunchedEffect`로 effect 수집, `LAppMinimumLive2DManager` 호출 등 플랫폼/DI 의존 로직을 담당
- **`XxxScreenContent`** (private) — 순수 UI. 파라미터(state, 콜백)만으로 동작하여 `@Preview` 지원

네이티브 라이브러리에 의존하는 컴포넌트(`Live2DScreen` 등)는 `@Composable () -> Unit` 슬롯 파라미터로 전달하여 Preview에서 placeholder로 대체:

```kotlin
// Screen (wrapper)
@Composable
fun StudioScreen(modelSource: ModelSource, ..., viewModel: StudioViewModel = hiltViewModel()) {
    // state collect, effect 수집 ...
    StudioScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        modelViewContent = { Live2DScreen(...) },  // 실제 네이티브 뷰
    )
}

// ScreenContent (순수 UI)
@Composable
private fun StudioScreenContent(
    uiState: StudioViewModel.StudioUiState,
    onIntent: (StudioUiIntent) -> Unit,
    modelViewContent: @Composable () -> Unit = {},
    // ...
) { /* UI 구현 */ }

// Preview
@Preview
@Composable
private fun StudioScreenPreview() {
    LiveMotionTheme {
        StudioScreenContent(
            uiState = StudioViewModel.StudioUiState(isModelLoading = false),
            onIntent = {},
            modelViewContent = { Box(Modifier.fillMaxSize().background(Color.DarkGray)) },
        )
    }
}
```

Screen에서 사용하는 UI 컴포넌트(카드, 다이얼로그, 버튼 그룹 등)는 같은 feature 모듈의 `components/` 하위 패키지에 별도 파일로 분리:

```
feature/studio/src/main/java/org/comon/studio/
├── StudioScreen.kt              # Screen + ScreenContent + Preview
├── StudioViewModel.kt
├── StudioUiIntent.kt
├── StudioUiEffect.kt
└── components/
    ├── FileListDialog.kt        # 컴포넌트별 파일 분리
    ├── StudioIconButton.kt
    └── StudioToggleButton.kt
```

새 Screen 추가 시 이 패턴을 따르고, `@Preview` 함수를 함께 작성할 것.
