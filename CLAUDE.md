# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

LiveMotion is an Android app that renders Live2D characters driven by real-time face tracking via the front camera. Users select a Live2D model, and the app uses MediaPipe Face Landmarker to capture facial movements (head rotation, eye blinks, iris tracking, mouth movements) and maps them to Live2D parameters in real-time.

## Build Commands

```bash
# Build the project
./gradlew assembleDebug

# Run unit tests (all modules)
./gradlew test

# Run unit tests for a specific module
./gradlew :app:testDebugUnitTest
./gradlew :core:tracking:testDebugUnitTest
./gradlew :feature:studio:testDebugUnitTest

# Run a single test class
./gradlew :app:testDebugUnitTest --tests "org.comon.livemotion.ExampleUnitTest"

# Run instrumented tests
./gradlew connectedAndroidTest

# Clean
./gradlew clean
```

## Architecture

### Module Dependency Graph

```
app → feature:studio → core:tracking → domain
                      → core:live2d   → live2d:framework → Live2DCubismCore.aar
    → core:ui
    → core:navigation
```

Dependency rules:
- `domain` — pure Kotlin, no Android dependencies, no module dependencies
- `core:*` — may depend on `domain` only
- `feature:*` — may depend on `core:*` and `domain`
- `app` — may depend on any module; contains only `MainActivity` and navigation setup

### Key Modules

| Module | Purpose | Language |
|--------|---------|----------|
| `domain` | Data classes (`FacePose`) — pure Kotlin, no Android deps | Kotlin |
| `core:tracking` | `FaceTracker` (CameraX + MediaPipe) and `FaceToLive2DMapper` (EMA smoothing + parameter mapping) | Kotlin |
| `core:live2d` | Live2D rendering wrapper — `Live2DScreen` composable, `Live2DGLSurfaceView`, `LAppMinimum*` Java classes | Kotlin + Java |
| `core:navigation` | `NavKey` sealed interface for type-safe navigation (kotlinx.serialization) | Kotlin |
| `core:ui` | Shared Compose theme (colors, typography) | Kotlin |
| `feature:studio` | `StudioScreen` (main tracking+rendering UI) and `ModelSelectScreen` (model picker) | Kotlin |
| `live2d:framework` | Live2D Cubism SDK Framework — vendored Java library, do not modify | Java |

### Data Flow: Face Tracking to Live2D Rendering

1. **`FaceTracker`** (`core:tracking`) — starts CameraX, runs MediaPipe `FaceLandmarker` in `LIVE_STREAM` mode on each camera frame. Extracts `FacePose` (yaw/pitch/roll from landmarks, eye/mouth/iris from blendshapes). Emits via `StateFlow<FacePose>`. Handles auto-calibration (5s neutral pose), GPU/CPU delegate switching with automatic fallback, and camera/preview separation.

2. **`FaceToLive2DMapper`** (`core:tracking`) — applies EMA smoothing (alpha=0.4) to all `FacePose` fields, then converts to a `Map<String, Float>` of Live2D parameter names (e.g., `ParamAngleX`, `ParamEyeLOpen`, `ParamMouthOpenY`). Supports VTube Studio-compatible extended ranges (eye open up to 2.0, mouth open up to 2.1).

3. **`Live2DScreen`** (`core:live2d`) — Compose wrapper that hosts `Live2DGLSurfaceView`. Receives `faceParams` map and queues parameter application to the GL thread via `queueEvent`. Also handles model loading and gesture modes (pinch zoom, drag).

4. **`LAppMinimumLive2DManager`** (`core:live2d`) — singleton Java class that bridges Compose/Kotlin code to the Live2D SDK. Manages model loading from assets, applies face parameters, controls projection matrix for zoom/pan, and triggers expressions/motions from files.

### Navigation Flow

`MainActivity` → `NavHost` with two routes:
- `NavKey.ModelSelect` → `ModelSelectScreen` (scans assets for folders containing `{name}.model3.json`)
- `NavKey.Studio(modelId)` → `StudioScreen` (face tracking + Live2D rendering)

### Live2D Model Assets

Models are placed in `app/src/main/assets/{modelId}/` with a `{modelId}.model3.json` file. Optional `expressions/` and `motions/` subfolders (case-insensitive matching) enable expression/motion buttons in the studio UI. The native SDK AAR is at `app/libs/Live2DCubismCore.aar`.

### MediaPipe Setup

The `face_landmarker.task` model file must be in `app/src/main/assets/`. FaceTracker uses 478 landmarks with blendshapes enabled for eye blink, eye wide, jaw open, mouth smile detection, and iris tracking (landmarks 468, 473).

## Code Placement Rules

- New data classes → `domain/model/`
- New screens → `feature:*` module
- Reusable UI components → `core:ui`
- Live2D logic → `core:live2d`
- Face tracking logic → `core:tracking`
- Do not add business logic to the `app` module (navigation setup only)

## Package Naming

| Module | Package |
|--------|---------|
| domain | `org.comon.domain.*` |
| core:tracking | `org.comon.tracking.*` |
| core:live2d | `org.comon.live2d.*` |
| core:ui | `org.comon.ui.*` |
| core:navigation | `org.comon.navigation.*` |
| feature:studio | `org.comon.studio.*` |

## Technical Details

- **Min SDK**: 26, **Target/Compile SDK**: 36
- **JVM Target**: 11 (all modules except `live2d:framework` which uses Java toolchain 17)
- **Compose BOM**: 2026.01.00
- **Kotlin**: 2.3.0
- **Navigation**: Jetpack Navigation Compose with type-safe routes via kotlinx.serialization
- **Camera**: CameraX with front camera; preview and analysis are decoupled (tracking works without visible preview)
- **Mirror mode**: Yaw, Roll, and EyeBallX are sign-inverted for front camera
- **`live2d:framework`**: Vendored SDK code — avoid modifying files in this module
