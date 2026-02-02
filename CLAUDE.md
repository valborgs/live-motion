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
    → feature:studio   → core:tracking → domain
                       → core:live2d   → live2d:framework → Live2DCubismCore.aar
                       → core:storage
                       → core:ui
                       → core:navigation
    → core:navigation
    → domain
    → data
```

Dependency rules:
- `domain` — pure Kotlin, no Android dependencies, no module dependencies
- `core:*` — may depend on `domain` only
- `feature:*` — may depend on `core:*` and `domain`
- `app` — may depend on any module; contains only `MainActivity` and navigation setup

### Key Modules

| Module | Purpose | Language |
|--------|---------|----------|
| `domain` | Data classes (`FacePose`, `ModelSource`) — pure Kotlin, no Android deps | Kotlin |
| `data` | Repository implementations, Hilt modules for UseCases | Kotlin |
| `core:tracking` | `FaceTracker` (CameraX + MediaPipe) — face detection and landmark extraction | Kotlin |
| `core:live2d` | Live2D rendering wrapper — `Live2DScreen` composable, `Live2DGLSurfaceView`, `LAppMinimum*` Java classes | Kotlin + Java |
| `core:navigation` | `NavKey` sealed interface for type-safe navigation, `Navigator` interface | Kotlin |
| `core:storage` | SAF permission management, model caching, external model metadata | Kotlin |
| `core:ui` | Shared Compose theme (colors, typography) | Kotlin |
| `feature:home` | `TitleScreen` (app entry), `IntroScreen` (Cubism splash) | Kotlin |
| `feature:settings` | `SettingsScreen` (app settings) | Kotlin |
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
- `NavKey.Settings` → `SettingsScreen`
- `NavKey.ModelSelect` → `ModelSelectScreen` (scans assets for folders containing `{name}.model3.json`)
- `NavKey.Studio(modelId)` → `StudioScreen` (face tracking + Live2D rendering)

### Live2D Model Assets

Models are placed in `app/src/main/assets/{modelId}/` with a `{modelId}.model3.json` file. Optional `expressions/` and `motions/` subfolders (case-insensitive matching) enable expression/motion buttons in the studio UI. The native SDK AAR is at `app/libs/Live2DCubismCore.aar`.

### MediaPipe Setup

The `face_landmarker.task` model file must be in `app/src/main/assets/`. FaceTracker uses 478 landmarks with blendshapes enabled for eye blink, eye wide, jaw open, mouth smile detection, and iris tracking (landmarks 468, 473).

## Code Placement Rules

- New data classes → `domain/model/`
- New UseCases → `domain/usecase/`
- New screens → `feature:*` module (home, settings, or studio)
- Reusable UI components → `core:ui`
- Live2D logic → `core:live2d`
- Face tracking logic → `core:tracking`
- Storage/caching logic → `core:storage`
- Do not add business logic to the `app` module (navigation setup only)

## Package Naming

| Module | Package |
|--------|---------|
| domain | `org.comon.domain.*` |
| data | `org.comon.data.*` |
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
- **Compose BOM**: 2026.01.00
- **Kotlin**: 2.3.0
- **DI**: Hilt with `@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`
- **Navigation**: Jetpack Navigation Compose with type-safe routes via kotlinx.serialization
- **Camera**: CameraX with front camera; preview and analysis are decoupled (tracking works without visible preview)
- **Mirror mode**: Yaw, Roll, and EyeBallX are sign-inverted for front camera
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
