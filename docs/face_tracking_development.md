# 얼굴 추적 및 Live2D 연동 개발 문서

## 개요

이 문서는 MediaPipe Face Landmarker와 Live2D Cubism SDK를 연동하여 실시간 얼굴 추적 기능을 구현하면서 발생한 문제점과 해결 방법을 정리한 것입니다.

---

## 목차

1. [시선 추적(Iris Tracking) 구현](#1-시선-추적iris-tracking-구현)
2. [거울 모드 반전 처리](#2-거울-모드-반전-처리)
3. [Pitch 계산 왜곡 문제](#3-pitch-계산-왜곡-문제)
4. [입 벌림 임계값 처리](#4-입-벌림-임계값-처리)
5. [EMA 스무딩 적용](#5-ema-스무딩-적용)
6. [GPU 가속 및 Delegate 전환](#6-gpu-가속-및-delegate-전환)
7. [캐릭터 확대 및 이동 기능](#7-캐릭터-확대-및-이동-기능)
8. [카메라와 프리뷰의 구조적 분리](#8-카메라와-프리뷰의-구조적-분리)
9. [렌더링 엔진 생명주기 안정화](#9-렌더링-엔진-생명주기-안정화)
10. [얼굴 추적 파라미터 정교화 및 명시적 초기화](#10-얼굴-추적-파라미터-정교화-및-명시적-초기화)
11. [VTube Studio 호환 매핑 및 eyeWide 지원](#11-vtube-studio-호환-매핑-및-eyewide-지원)
12. [감정(Expressions) 및 모션(Motions) UI 추가](#12-감정expressions-및-모션motions-ui-추가)
13. [ViewModel 도입 및 상태 관리 개선](#13-viewmodel-도입-및-상태-관리-개선-2026-01-28-업데이트)
14. [에러 처리 시스템 구축](#14-에러-처리-시스템-구축-2026-01-28-업데이트)
15. [수동 의존성 주입(Manual DI) 리팩토링](#15-수동-의존성-주입manual-di-리팩토링-2026-01-28-업데이트)
16. [Clean Architecture 리팩터링](#16-clean-architecture-리팩터링-2026-01-29-업데이트)
17. [외부 모델 가져오기 기능](#17-외부-모델-가져오기-기능-2026-01-30-업데이트)
18. [외부 모델 삭제 기능](#18-외부-모델-삭제-기능-2026-01-31-업데이트)
19. [감정/모션 초기화 기능](#19-감정모션-초기화-기능-2026-01-31-업데이트)
20. [스플래시/인트로 화면 구현](#20-스플래시인트로-화면-구현-2026-01-31-업데이트)
21. [라이트/다크 모드 테마 적용](#21-라이트다크-모드-테마-적용-2026-01-31-업데이트)
22. [Hilt 의존성 주입 마이그레이션](#22-hilt-의존성-주입-마이그레이션-2026-02-02-업데이트)
23. [MVI 패턴 완성 (UiIntent/UiEffect)](#23-mvi-패턴-완성-uiintentuieffect-2026-02-02-업데이트)
24. [Feature 모듈 분리](#24-feature-모듈-분리-2026-02-02-업데이트)
25. [MapFacePoseUseCase 순수 함수화](#25-mapfaceposeusecase-순수-함수화-2026-02-02-업데이트)
26. [스낵바 로직 리팩토링 및 SnackbarStateHolder](#26-스낵바-로직-리팩토링-및-snackbarstateholder-2026-02-02-업데이트)
27. [Screen/Content 분리 및 Preview 지원](#27-screencontent-분리-및-preview-지원-2026-02-04-업데이트)
28. [Predictive Back 제스처 취소 시 모델 소실 문제](#28-predictive-back-제스처-취소-시-모델-소실-문제-2026-02-05-업데이트)
29. [Live2DUiEffect를 통한 렌더링 이벤트 캡슐화](#29-live2duieffect를-통한-렌더링-이벤트-캡슐화-2026-02-05-업데이트)
30. [핀치 줌에서 싱글 터치 전환 시 위치 튀는 현상 수정](#30-핀치-줌에서-싱글-터치-전환-시-위치-튀는-현상-수정-2026-02-05-업데이트)

---

## 1. 시선 추적(Iris Tracking) 구현

### 문제
- 기존 구현에서는 눈동자(ParamEyeBallX/Y)가 고개 방향(Yaw)에 연동되어 있었음
- 사용자가 고개를 고정한 채 눈동자만 움직여도 캐릭터 눈동자가 반응하지 않음

### 해결 방법
MediaPipe Face Landmarker의 Iris 랜드마크를 활용하여 실제 눈동자 위치를 추적:

```kotlin
// 눈동자 랜드마크 인덱스
// 왼쪽 눈동자 중심: 468, 오른쪽 눈동자 중심: 473
val irisL = landmarks[468]
val irisR = landmarks[473]

// 눈 너비 대비 상대적 위치 계산
val eyeLWidth = eyeLInner.x() - eyeLOuter.x()
val irisLRelX = (irisL.x() - eyeLOuter.x()) / eyeLWidth

// 정규화 (-1 ~ 1)
val eyeBallX = -((avgRelX - 0.5f) * 2f).coerceIn(-1f, 1f)
```

### 관련 파일
- `FaceTracker.kt`: `calculateIrisPosition()` 함수 추가
- `FacePose.kt`: `eyeBallX`, `eyeBallY` 필드 추가
- `FaceToLive2DMapper.kt`: Iris 데이터를 ParamEyeBallX/Y에 매핑

---

## 2. 거울 모드 반전 처리

### 문제
- 전면 카메라 사용 시 사용자가 왼쪽으로 기울이면 캐릭터는 오른쪽으로 기울어짐
- Yaw(좌우)에는 반전이 적용되어 있었으나 Roll(기울기)에는 적용 안 됨

### 해결 방법
Roll 계산에 `-` 부호를 적용하여 거울 모드 반전:

```kotlin
// 수정 전
val rollNorm = rollDeg / 20f

// 수정 후 (- 부호 추가)
val rollNorm = -rollDeg / 20f
```

### 거울 모드 적용 파라미터
| 파라미터 | 반전 적용 | 비고 |
|---------|----------|------|
| Yaw (ParamAngleX) | ✅ `-` 적용 | 좌우 회전 |
| Roll (ParamAngleZ) | ✅ `-` 적용 | 기울기 |
| EyeBallX | ✅ `-` 적용 | 시선 좌우 |

---

## 3. Pitch 계산 왜곡 문제

### 문제
고개를 옆으로 돌린 상태에서 위/아래를 바라보면 Pitch가 정확하게 인식되지 않음:
- 옆을 보면서 위를 바라보면 → 캐릭터는 아래를 바라봄
- 고개를 숙이면 → 캐릭터가 반응하지 않음

### 원인
Y좌표 기반 Pitch 계산은 Yaw 회전 시 원근법 왜곡의 영향을 받음

### 해결 방법
**Z좌표(깊이)** 기반으로 Pitch 계산 변경:

```kotlin
// 수정 전: Y좌표 기반 (Yaw에 영향받음)
val pitchPoint = eyeYCenter - nose.y()
val pitchNorm = pitchPoint * 6f * normFactor

// 수정 후: Z좌표 기반 (Yaw와 독립)
val noseBridge = landmarks[6]  // 코 다리 (미간 근처)
val pitchZ = nose.z() - noseBridge.z()
val pitchNorm = pitchZ * 15f
```

### 추가 수정
Live2D ParamAngleY의 부호 규칙에 맞게 Mapper에서 반전:

```kotlin
// Live2D: 양수=위, 음수=아래
params["ParamAngleY"] = (-smoothed.pitch * 40f).coerceIn(-30f, 30f)
```

---

## 4. 입 벌림 임계값 처리

### 문제
입을 다물고 있어도 캐릭터 입이 약간 열려 있음

### 원인
MediaPipe의 `jawOpen` Blendshape 값이 완전히 0이 되지 않고 작은 값(노이즈)이 발생

### 해결 방법
**임계값(Dead Zone)** 적용:

```kotlin
val mouthRaw = scores["jawOpen"] ?: 0f

// 0.15 이하는 닫힌 입으로 간주하고, 이후 값을 0~1로 재정규화
val mouthThreshold = 0.15f
val mouth = ((mouthRaw - mouthThreshold) / (1f - mouthThreshold)).coerceIn(0f, 1f)
```

### 효과
- `jawOpen < 0.15`: `mouth = 0` (입 닫힘)
- `jawOpen = 0.15 ~ 1.0`: `mouth = 0 ~ 1` (비례 열림)

---

## 5. EMA 스무딩 적용

### 문제
카메라 데이터의 미세한 떨림으로 캐릭터가 덜덜 떨림

### 해결 방법
**지수 이동 평균(EMA)** 필터 적용:

```kotlin
/**
 * EMA 스무딩 계수 (alpha)
 * 
 * 공식: smoothed = lastValue + alpha * (newValue - lastValue)
 * 
 * - 0.1~0.3: 부드럽지만 반응 느림 (떨림 억제)
 * - 0.3~0.5: 부드러움과 반응성 균형
 * - 0.5~0.8: 빠른 반응, 떨림 가능성
 */
private val alpha = 0.4f

private fun smooth(last: Float, current: Float): Float {
    return last + alpha * (current - last)
}
```

### 적용 대상
모든 FacePose 필드에 EMA 적용:
- yaw, pitch, roll
- eyeLOpen, eyeROpen
- mouthOpen, mouthForm
- eyeBallX, eyeBallY

---

## 좌표계 참고

### MediaPipe 정규화 좌표
- X: 0.0 (왼쪽) ~ 1.0 (오른쪽)
- Y: 0.0 (위) ~ 1.0 (아래)
- Z: 깊이 (카메라에 가까울수록 음수)

### Live2D 파라미터 범위
| 파라미터 | 범위 | 설명 |
|---------|------|------|
| ParamAngleX | -30 ~ 30 | 좌우 회전 |
| ParamAngleY | -30 ~ 30 | 상하 회전 (양수=위) |
| ParamAngleZ | -30 ~ 30 | 기울기 |
| ParamEyeBallX/Y | -1 ~ 1 | 시선 |
| ParamMouthOpenY | 0 ~ 1 | 입 벌림 |
| ParamEyeLOpen/ROpen | 0 ~ 1 | 눈 뜸 |

---

## 수정된 파일 목록

| 파일 | 변경 내용 |
|------|----------|
| `FacePose.kt` | eyeBallX, eyeBallY, mouthForm 필드 추가 |
| `FaceTracker.kt` | Iris 추적, Z기반 Pitch, 입 임계값, 거울 모드 |
| `FaceToLive2DMapper.kt` | EMA 확장, ParamAngleY 반전, 시선 매핑 |
| `MainActivity.kt` | ParamMouthForm 리셋 추가 |

---

6. [GPU 가속 및 Delegate 전환](#6-gpu-가속-및-delegate-전환)
7. [캐릭터 확대 및 이동 기능](#7-캐릭터-확대-및-이동-기능)
8. [카메라와 프리뷰의 구조적 분리](#8-카메라와-프리뷰의-구조적-분리)

---

## 6. GPU 가속 및 Delegate 전환

### 문제
- 실시간 얼굴 추적 시 CPU 부하가 높고 지연시간이 발생할 수 있음
- 기기 사양에 따라 GPU 가속 사용 여부를 선택할 필요가 있음

### 해결 방법
MediaPipe Face Landmarker에 GPU Delegate를 적용하고, 런타임에 CPU/GPU를 전환할 수 있는 기능을 구현:

- **자동 폴백**: GPU 초기화 실패 시 자동으로 CPU로 폴백하여 안정성 확보
- **런타임 전환**: FaceLandmarker를 닫고 새 Delegate로 재초기화하는 `setGpuEnabled()` 메서드 구현
- **로그 강화**: 초기화에 소요된 시간과 활성화된 Delegate를 확인하기 위한 상세 로깅 추가

---

## 7. 캐릭터 확대 및 이동 기능

### 문제
- 캐릭터가 화면 중앙에 고정되어 있어 세밀한 연출이나 구도 조정이 불가능함

### 해결 방법
Live2D 모델의 Projection 매트릭스에 개별적인 스케일과 오프셋을 적용:

- **확대 (Pinch Zoom)**: `ScaleGestureDetector`를 사용하여 캐릭터 크기를 0.5배 ~ 10배까지 조정
- **이동 (Drag)**: 화면 드래그를 통해 캐릭터 위치를 자유롭게 이동 (-10.0 ~ 10.0 범위)
- **비율 유지**: `scaleRelative`를 사용하여 화면 크기에 관계없이 캐릭터 고유 비율 유지

### 관련 파일
- `LAppMinimumLive2DManager.java`: 스케일/오프셋 API 및 렌더링 로직 추가
- `Live2DGLSurfaceView.kt`: 제스처 감지 및 모드 토글 로직 구현

---

## 8. 카메라와 프리뷰의 구조적 분리

### 문제
- 기존에는 `PreviewView`가 생성될 때만 카메라가 시작되어, 프리뷰를 끄면 얼굴 추적도 멈춤
- 프리뷰 표시 여부와 관계없이 얼굴 추적은 계속 동작해야 함

### 해결 방법
카메라 시작(`startCamera`)과 프리뷰 연결(`attachPreview`)을 물리적으로 분리:

- **독립적 카메라 실행**: `LaunchedEffect(Unit)`에서 카메라를 먼저 시작하여 `ImageAnalysis` 레이어 활성화
- **조건부 프리뷰**: 프리뷰 토글이 ON일 때만 `PreviewView`를 렌더링하고 `attachPreview()`로 연결
- **자원 관리**: `onRelease`에서 `detachPreview()`를 호출하여 메모리 누수 방지

---

## 9. 렌더링 엔진 생명주기 안정화

### 문제
- 특정 상황에서 Live2D 렌더링 엔진이 정상적으로 초기화되지 않거나, 화면 전환 후 모델이 로드되지 않는 현상 발생

### 해결 방법
- `Live2DGLSurfaceView`의 초기화 시점에 `LAppMinimumDelegate`의 `onStart(context)`를 명시적으로 호출하여 네이티브 라이브러리와 렌더링 리소스를 안정적으로 확보
- `LAppMinimumLive2DManager`에서 뷰포트 크기 계산 및 프로젝션 매트릭스 업데이트 로직을 보강하여 모델이 정확하게 화면에 표시되도록 함

### 관련 파일
- [Live2DGLSurfaceView.kt](file:///d:/comon/LiveMotion/app/src/main/java/org/comon/livemotion/Live2DGLSurfaceView.kt)
- [LAppMinimumLive2DManager.java](file:///d:/comon/LiveMotion/app/src/main/java/org/comon/livemotion/demo/minimum/LAppMinimumLive2DManager.java)

---

## 10. 얼굴 추적 파라미터 정교화 및 명시적 초기화

### 문제
- 눈 깜빡임 등 일부 얼굴 파라미터가 0으로 고정되거나 노이즈로 인해 부자연스럽게 동작함
- `FaceLandmarker` 초기화 시점을 앱 생명주기 또는 사용자 상태에 따라 세밀하게 제어할 필요가 있음

### 해결 방법
1. **명시적 초기화 메서드 분리**: `setupFaceLandmarker(useGpu: Boolean)`를 통해 GPU 사용 여부를 런타임에 결정하고 초기화를 명시적으로 수행할 수 있도록 개선
2. **눈 깜빡임 로직 개선**: `eyeBlinkLeft/Right` 점수가 누락되거나 노이즈가 섞일 경우를 대비하여 기본값 처리 및 재정규화 로직 최적화

### 관련 파일
- [FaceTracker.kt](file:///d:/comon/LiveMotion/app/src/main/java/org/comon/livemotion/tracking/FaceTracker.kt)

---

## 좌표계 참고

### MediaPipe 정규화 좌표
- X: 0.0 (왼쪽) ~ 1.0 (오른쪽)
- Y: 0.0 (위) ~ 1.0 (아래)
- Z: 깊이 (카메라에 가까울수록 음수)

### Live2D 파라미터 범위
| 파라미터 | 범위 | 설명 |
|---------|------|------|
| ParamAngleX | -30 ~ 30 | 좌우 회전 |
| ParamAngleY | -30 ~ 30 | 상하 회전 (양수=위) |
| ParamAngleZ | -30 ~ 30 | 기울기 |
| ParamEyeBallX/Y | -1 ~ 1 | 시선 |
| ParamMouthOpenY | 0 ~ 2.1 | 입 벌림 (VTube Studio 호환 확장) |
| ParamEyeLOpen/ROpen | 0 ~ 2.0 | 눈 뜸 (eyeWide 지원) |

---

## 수정된 파일 목록

| 파일 | 변경 내용 |
|------|----------|
| `FacePose.kt` | eyeBallX, eyeBallY, mouthForm 필드 추가 |
| `FaceTracker.kt` | 명시적 초기화 API, 얼굴 파라미터(눈동자/입/깜빡임) 추출 정교화 |
| `FaceToLive2DMapper.kt` | EMA 스무딩 고도화 및 파라미터 매핑 브리지 |
| `MainActivity.kt` | CPU/GPU, 확대, 이동, 프리뷰 토글 UI 및 상태 관리 |
| `Live2DScreen.kt` | 줌/이동 모드 파라미터 연동 |
| `Live2DGLSurfaceView.kt` | 핀치 줌, 드래그 제스처 처리 및 생명주기(`onStart`) 연동 |
| `LAppMinimumLive2DManager.java` | 렌더링 매트릭스 제어 및 뷰포트 업데이트 로직 보강 |

---

---

## 11. VTube Studio 호환 매핑 및 eyeWide 지원 (2026-01-27 업데이트)

### 배경
- VTube Studio용 모델(gyana3 등)은 Live2D 표준 파라미터 범위를 초과하여 더 역동적인 표정을 구현함
- 기존 구현에서는 파라미터가 0~1 표준 범위로 제한되어 있어, 눈을 크게 뜨거나 입을 크게 벌려도 모델이 반응하지 않거나 표현이 제한적이었음

### 구현 변경 사항

#### 1. 눈동자 확장 (eyeWide) 지원
기존에는 눈 깜빡임(`eyeBlink`)만 사용하여 눈 뜬 정도를 0~1로 제한했으나, `eyeWide` Blendshape를 도입하여 1.0 이상의 값(눈 크게 뜸)을 표현:

```kotlin
// 수정 전: 최대 1.0
val eyeLOpen = 1f - eyeBlinkL

// 수정 후: 1.0 + 확장값 (최대 약 1.8~2.0)
val eyeLOpen = (1f - eyeBlinkL) + (eyeWideL * 0.8f)
```

#### 2. 파라미터 출력 범위 확장
FaceToLive2DMapper에서 0~1로 강제하던 제한을 VTube Studio 설정(`gyana3.vtube.json`)을 참고하여 확장:

| 파라미터 | 기존 범위 | 변경된 범위 | 효과 |
|----------|----------|------------|------|
| **ParamEyeLOpen/ROpen** | 0.0 ~ 1.0 | **0.0 ~ 2.0** | 눈을 크게 떴을 때 모델 눈이 커짐 |
| **ParamMouthOpenY** | 0.0 ~ 1.0 | **0.0 ~ 2.1** | 입을 더 크고 역동적으로 벌림 |

#### 3. 눈웃음 자동 연동
미소를 지을 때(`ParamMouthForm`) 눈이 함께 웃도록 새로운 파라미터 매핑 추가 (모델 지원 시 작동):
```kotlin
// 미소(MouthForm) 값을 눈웃음(EyeSmile)에도 전달
params["ParamEyeLSmile"] = smoothed.mouthForm.coerceIn(0f, 1f)
params["ParamEyeRSmile"] = smoothed.mouthForm.coerceIn(0f, 1f)
```

---

## 추후 개선 사항

1. **FaceGeometry 활용**: MediaPipe FaceGeometry를 사용하면 Euler Angles를 직접 얻을 수 있어 더 정확한 회전 계산 가능
2. **개인별 보정**: 사용자마다 다른 얼굴 비율을 고려한 동적 보정
3. **배경 합성**: 크로마키 보드 또는 이미지 배경 추가 기능

---

## 12. 감정(Expressions) 및 모션(Motions) UI 추가 (2026-01-27 업데이트)

### 배경
- 사용자가 버튼 클릭을 통해 모델의 특정 표정이나 동작을 즉시 실행해볼 수 있는 기능 필요
- 모델 에셋 구조(대소문자 차이 등)에 유연하게 대응해야 함

### 구현 내용

#### 1. 에셋 폴더 자동 스캔 (대소문자 무관)
모델마다 `expressions` / `Expressions` 등 폴더명이 다를 수 있어, `StudioScreen` 진입 시 `AssetManager.list()`를 통해 실제 폴더명을 동적으로 찾도록 구현:

```kotlin
private fun findAssetFolder(assetManager: AssetManager, modelId: String, targetFolder: String): String? {
    return try {
        // 대소문자 구분 없이 "expressions" 또는 "motions" 폴더가 있는지 확인하고 실제 이름 반환
        val files = assetManager.list(modelId) ?: return null
        files.firstOrNull { it.equals(targetFolder, ignoreCase = true) }
    } catch (e: IOException) {
        null
    }
}
```

#### 2. 파일 직접 실행 지원
기존 `LAppMinimumModel`은 `model3.json`에 정의된 모션 그룹만 실행 가능했으나, 파일 경로를 통해 임의의 모션/표정 파일을 로드하고 실행하는 메서드를 추가:

- `startMotionFromFile(String fileName)`: `.motion3.json` 파일을 직접 로드하여 실행
- `startExpressionFromFile(String fileName)`: `.exp3.json` 파일을 직접 로드하여 실행

#### 3. UI 구성
- **상단 버튼 바**: 해당 모델에 `expressions` 또는 `motions` 폴더가 존재할 경우에만 "감정", "모션" 버튼 표시
- **파일 목록 다이얼로그**: 버튼 클릭 시 폴더 내의 파일들을 리스트로 표시
- **실행**: 리스트 아이템 선택 시 `LAppMinimumLive2DManager`를 통해 즉시 해당 파일 재생

### 관련 파일
- `StudioScreen.kt`: 에셋 스캔 및 버튼/다이얼로그 UI
- `LAppMinimumModel.java`: 파일 경로 기반 모션/표정 로드 및 재생 로직
- `LAppMinimumLive2DManager.java`: UI와 Model 간 브리지 메서드 (`startMotion`, `startExpression`)

---

## 13. ViewModel 도입 및 상태 관리 개선 (2026-01-28 업데이트)

### 배경
- 기존 `StudioScreen`에서 `remember`로 `FaceTracker`를 생성하여 화면 회전 시 상태 손실 발생
- 상태 변수가 분산되어 관리 어려움
- 에러 발생 시 UI 피드백 부재

### 구현 내용

#### 1. StudioViewModel 도입
`FaceTracker`와 상태를 ViewModel 스코프에서 관리하여 configuration change에서 생존:

```kotlin
class StudioViewModel(private val context: Context) : ViewModel() {
    private var faceTracker: FaceTracker? = null

    // 실시간 트래킹 데이터 (30fps) - 별도 Flow
    val facePose: StateFlow<FacePose>
    val faceLandmarks: StateFlow<List<NormalizedLandmark>>

    // UI 상태 - 단일 State 객체
    val uiState: StateFlow<StudioUiState>
}
```

#### 2. 상태 분리 전략
실시간 데이터(30fps)와 UI 상태를 분리하여 성능 최적화:

| 구분 | 타입 | 업데이트 빈도 |
|------|------|-------------|
| `facePose`, `faceLandmarks` | 별도 StateFlow | 30fps |
| `StudioUiState` | 단일 State 객체 | 사용자 액션 시 |

#### 3. StudioUiState 구조
```kotlin
data class StudioUiState(
    val isModelLoading: Boolean = true,
    val isCalibrating: Boolean = false,
    val isGpuEnabled: Boolean = false,
    val trackingError: TrackingError? = null,
    val isZoomEnabled: Boolean = false,
    val isMoveEnabled: Boolean = false,
    val isPreviewVisible: Boolean = true,
    val dialogState: DialogState = DialogState.None,
    val expressionsFolder: String? = null,
    val motionsFolder: String? = null,
    val expressionFiles: List<String> = emptyList(),
    val motionFiles: List<String> = emptyList()
)
```

### 관련 파일
| 파일 | 변경 내용 |
|------|----------|
| `StudioViewModel.kt` | 신규 생성 - ViewModel 및 UiState 정의 |
| `StudioScreen.kt` | ViewModel 사용으로 리팩토링 |
| `feature/studio/build.gradle.kts` | lifecycle-viewmodel-compose 의존성 추가 |

---

## 14. 에러 처리 시스템 구축 (2026-01-28 업데이트)

### 배경
- 모델 로딩 실패 시 앱이 강제 종료됨 (`Activity.finish()` 호출)
- FaceTracker 에러가 로그로만 출력되어 사용자에게 피드백 없음
- 카메라 권한 거부 시 재요청 방법 없음

### 구현 내용

#### 1. 모델 로딩 에러 처리
기존에는 모델 로딩 실패 시 `Activity.finish()`를 호출하여 앱이 강제 종료되었으나, 이를 예외 기반 처리로 변경:

- `LAppMinimumModel`에서 `Activity.finish()` 대신 `IllegalStateException` throw
- `LAppMinimumLive2DManager`에서 에러 리스너 콜백 추가
- 에러 발생 시 ModelSelectScreen으로 복귀 + 스낵바 표시
- "자세히" 버튼으로 에러 상세 다이얼로그 표시

```java
// 수정 전
if (this.modelSetting.getJson() == null) {
    LAppMinimumDelegate.getInstance().getActivity().finish();
}

// 수정 후
if (this.modelSetting.getJson() == null) {
    throw new IllegalStateException("Failed to load model3.json: " + model3JsonPath);
}
```

#### 2. TrackingError Flow 추가
FaceTracker에 에러 상태 Flow를 추가하여 UI로 에러를 전파:

```kotlin
sealed class TrackingError {
    data class FaceLandmarkerInitError(val message: String) : TrackingError()
    data class CameraError(val message: String) : TrackingError()
    data class MediaPipeRuntimeError(val message: String) : TrackingError()
}

class FaceTracker(...) {
    private val _error = MutableStateFlow<TrackingError?>(null)
    val error: StateFlow<TrackingError?> = _error

    fun clearError() { _error.value = null }
}
```

에러 발생 시점:
| 에러 타입 | 발생 상황 |
|----------|----------|
| `FaceLandmarkerInitError` | MediaPipe 초기화 실패 (GPU/CPU 모두) |
| `CameraError` | 카메라 시작 실패, 전면 카메라 없음 |
| `MediaPipeRuntimeError` | MediaPipe 런타임 에러 |

#### 3. 카메라 권한 UX 개선
기존에는 권한 거부 시 텍스트만 표시되었으나, 사용자 친화적인 UI로 개선:

```kotlin
@Composable
private fun CameraPermissionScreen(
    isPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
)
```

- 일반 거부: "권한 허용하기" 버튼으로 재요청
- 영구 거부 (`shouldShowRequestPermissionRationale = false`): "설정으로 이동" 버튼
- 설정에서 복귀 시 권한 상태 자동 재확인

#### 4. 전면 카메라 존재 확인
전면 카메라가 없는 기기에서 명확한 에러 메시지 제공:

```kotlin
if (!cameraProvider!!.hasCamera(cameraSelector)) {
    _error.value = TrackingError.CameraError(
        "전면 카메라를 찾을 수 없습니다. 이 앱은 전면 카메라가 필요합니다."
    )
    return@addListener
}
```

### 에러 처리 흐름도

```
┌─────────────────────────────────────────────────────────────┐
│                     에러 발생 지점                           │
├─────────────────────────────────────────────────────────────┤
│  FaceTracker          │  LAppMinimumModel                   │
│  - 초기화 실패        │  - model3.json 로드 실패            │
│  - 카메라 실패        │  - 모델 파일 없음                   │
│  - MediaPipe 에러     │                                     │
└──────────┬────────────┴──────────────┬──────────────────────┘
           │                           │
           ▼                           ▼
┌─────────────────────┐    ┌─────────────────────────────────┐
│ _error.value = ...  │    │ onModelLoadError 콜백 호출      │
└──────────┬──────────┘    └──────────────┬──────────────────┘
           │                              │
           ▼                              ▼
┌─────────────────────────────────────────────────────────────┐
│              StudioViewModel / StudioScreen                 │
│         uiState.trackingError 또는 onError 콜백             │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                      스낵바 표시                             │
│         "트래킹 오류가 발생했습니다" [자세히]               │
│         "모델 로딩에 실패했습니다" [자세히]                 │
└──────────────────────────┬──────────────────────────────────┘
                           │ 자세히 클릭
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                   에러 상세 다이얼로그                       │
│              (에러 메시지 전문 표시)                        │
└─────────────────────────────────────────────────────────────┘
```

### 관련 파일
| 파일 | 변경 내용 |
|------|----------|
| `LAppMinimumModel.java` | `Activity.finish()` → 예외 throw |
| `LAppMinimumLive2DManager.java` | `OnModelLoadListener` 에러 콜백 추가 |
| `FaceTracker.kt` | `TrackingError` sealed class, `error` StateFlow 추가 |
| `StudioViewModel.kt` | `trackingError` 상태 관리, `clearTrackingError()` |
| `StudioScreen.kt` | 트래킹 에러 스낵바/다이얼로그 |
| `ModelSelectScreen.kt` | 모델 로딩 에러 스낵바/다이얼로그 |
| `MainActivity.kt` | `CameraPermissionScreen`, 권한 상태 관리 |
| `Live2DScreen.kt` | `onModelLoadError` 콜백 추가 |

---

## 15. 수동 의존성 주입(Manual DI) 리팩토링 (2026-01-28 업데이트)

### 배경
- `StudioViewModel`에서 `Application`/`Context`를 직접 참조하여 메모리 누수 경고 및 테스트 어려움 발생
- Android 프레임워크에 대한 강한 결합으로 인해 단위 테스트 작성이 어려움
- Hilt 등 DI 프레임워크 없이 수동 의존성 주입 패턴 적용 필요

### 구현 내용

#### 1. DI 인프라 구축

**core:common 모듈**에 추상화 계층 생성:

```kotlin
// AppContainer 인터페이스 (core:common)
interface AppContainer {
    val modelAssetReader: ModelAssetReader
    val faceTrackerFactory: FaceTrackerFactory
}

// CompositionLocal 정의 (core:common)
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer has not been provided")
}
```

**app 모듈**에 구현체 생성:

```kotlin
// AppContainerImpl (app)
class AppContainerImpl(application: Application) : AppContainer {
    override val faceTrackerFactory by lazy { FaceTrackerFactory(application) }
    override val modelAssetReader by lazy { ModelAssetReader(application.assets) }
}

// 커스텀 Application (app)
class LiveMotionApp : Application() {
    lateinit var container: AppContainerImpl
    override fun onCreate() {
        super.onCreate()
        container = AppContainerImpl(this)
    }
}
```

#### 2. Factory 패턴 적용

**FaceTrackerFactory**: ViewModel이 Context 없이 FaceTracker를 생성할 수 있도록 함:

```kotlin
class FaceTrackerFactory(private val context: Context) {
    fun create(lifecycleOwner: LifecycleOwner): FaceTracker {
        return FaceTracker(context, lifecycleOwner)
    }
}
```

**ModelAssetReader**: Asset 접근을 캡슐화:

```kotlin
class ModelAssetReader(private val assets: AssetManager) {
    fun findAssetFolder(modelId: String, targetFolder: String): String?
    fun listFiles(path: String): List<String>
    fun listLive2DModels(): List<String>
}
```

#### 3. ViewModel 리팩토링

기존 `Application` 직접 참조를 Factory 주입으로 변경:

```kotlin
// 수정 전
class StudioViewModel(private val application: Application) : ViewModel()

// 수정 후
class StudioViewModel(
    private val faceTrackerFactory: FaceTrackerFactory,
    private val modelAssetReader: ModelAssetReader
) : ViewModel()
```

#### 4. Compose에서 DI 사용

**MainActivity**에서 `CompositionLocalProvider`로 컨테이너 제공:

```kotlin
setContent {
    val container = (application as LiveMotionApp).container
    CompositionLocalProvider(LocalAppContainer provides container) {
        LiveMotionTheme { MainContent() }
    }
}
```

**Screen**에서 `LocalAppContainer.current`로 의존성 획득:

```kotlin
@Composable
fun StudioScreen(...) {
    val container = LocalAppContainer.current
    val viewModel: StudioViewModel = viewModel(
        factory = StudioViewModel.Factory(
            container.faceTrackerFactory,
            container.modelAssetReader
        )
    )
}
```

### 모듈 의존성 구조

```mermaid
graph TD
    A[app] --> B[core:common]
    A --> C[core:tracking]
    A --> D[feature:studio]
    B --> C
    D --> B
    D --> C
```

순환 의존성을 방지하기 위해:
- `AppContainer` **인터페이스**는 `core:common`에 정의
- `AppContainerImpl` **구현체**는 `app`에 위치
- `feature:studio`는 인터페이스만 참조

### 관련 파일

**신규 생성**
| 파일 | 위치 | 설명 |
|------|------|------|
| `AppContainer.kt` | core:common/di | 의존성 컨테이너 인터페이스 |
| `LocalAppContainer.kt` | core:common/di | Compose CompositionLocal |
| `ModelAssetReader.kt` | core:common/asset | Asset 읽기 클래스 |
| `FaceTrackerFactory.kt` | core:tracking | FaceTracker 생성 Factory |
| `AppContainerImpl.kt` | app/di | AppContainer 구현체 |
| `LiveMotionApp.kt` | app | 커스텀 Application |

**수정됨**
| 파일 | 변경 내용 |
|------|----------|
| `StudioViewModel.kt` | Application → Factory 주입 |
| `StudioScreen.kt` | LocalAppContainer 사용 |
| `ModelSelectScreen.kt` | LocalAppContainer 사용 |
| `MainActivity.kt` | CompositionLocalProvider 추가 |
| `AndroidManifest.xml` | LiveMotionApp 등록 |
| `libs.versions.toml` | compose-runtime 추가 |
| `app/build.gradle.kts` | core:common, core:tracking 의존성 추가 |
| `feature/studio/build.gradle.kts` | core:common 의존성 추가 |
| `core/common/build.gradle.kts` | core:tracking, compose-runtime 의존성 추가 |

---

## 16. Clean Architecture 리팩터링 (2026-01-29 업데이트)

### 배경
- ViewModel이 `ModelAssetReader`, `FaceToLive2DMapper` 등 인프라 클래스를 직접 참조
- 비즈니스 로직과 데이터 접근 로직이 혼재되어 단위 테스트 어려움
- 에러 처리가 각 레이어에 분산되어 일관성 부족

### 목표
- **Repository 패턴**: 데이터 접근 로직 추상화
- **UseCase 패턴**: 비즈니스 로직 캡슐화
- **Result<T> 패턴**: 일관된 예외 처리

### 아키텍처 변경

#### 변경 전
```
StudioViewModel
├─ FaceTrackerFactory.create() 직접 호출
├─ ModelAssetReader 직접 사용
└─ FaceToLive2DMapper 직접 사용
```

#### 변경 후
```
StudioViewModel
├─ GetModelMetadataUseCase → IModelRepository → ModelRepositoryImpl
└─ MapFacePoseUseCase (EMA 스무딩 포함)
```

### 구현 내용

#### 1. Domain Layer 기반 구축

**Result 래퍼 및 DomainException** (`domain/common/Result.kt`):

```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: DomainException) : Result<Nothing>()

    inline fun onSuccess(action: (T) -> Unit): Result<T>
    inline fun onError(action: (DomainException) -> Unit): Result<T>
    inline fun <R> map(transform: (T) -> R): Result<R>
}

sealed class DomainException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class FaceTrackingInitError(message: String) : DomainException(message)
    class CameraError(message: String) : DomainException(message)
    class MediaPipeRuntimeError(message: String) : DomainException(message)
    class ModelNotFoundError(modelId: String) : DomainException("Model not found: $modelId")
    class AssetReadError(path: String, cause: Throwable?) : DomainException("Failed to read: $path", cause)
}
```

**Domain 모델** (`domain/model/`):

```kotlin
// 모델 메타데이터
data class Live2DModelInfo(
    val modelId: String,
    val expressionsFolder: String?,
    val motionsFolder: String?,
    val expressionFiles: List<String>,
    val motionFiles: List<String>
)

// Live2D 파라미터 래퍼
data class Live2DParams(val params: Map<String, Float>) {
    companion object {
        val DEFAULT = Live2DParams(mapOf(
            "ParamAngleX" to 0f, "ParamAngleY" to 0f, "ParamAngleZ" to 0f,
            "ParamEyeLOpen" to 1f, "ParamEyeROpen" to 1f, ...
        ))
    }
}
```

**Repository 인터페이스** (`domain/repository/IModelRepository.kt`):

```kotlin
interface IModelRepository {
    fun listLive2DModels(): Result<List<String>>
    fun getModelMetadata(modelId: String): Result<Live2DModelInfo>
    fun modelExists(modelId: String): Boolean
}
```

#### 2. UseCase 클래스

**GetLive2DModelsUseCase**: 모델 목록 조회
```kotlin
class GetLive2DModelsUseCase(private val modelRepository: IModelRepository) {
    operator fun invoke(): Result<List<String>> = modelRepository.listLive2DModels()
}
```

**GetModelMetadataUseCase**: 모델 메타데이터 조회
```kotlin
class GetModelMetadataUseCase(private val modelRepository: IModelRepository) {
    operator fun invoke(modelId: String): Result<Live2DModelInfo> =
        modelRepository.getModelMetadata(modelId)
}
```

**MapFacePoseUseCase**: FacePose → Live2D 파라미터 변환 (EMA 스무딩 포함)
```kotlin
class MapFacePoseUseCase {
    private var lastPose = FacePose()
    private val alpha = 0.4f

    fun reset()
    operator fun invoke(facePose: FacePose, hasLandmarks: Boolean): Live2DParams
}
```

> **Note**: `MapFacePoseUseCase`는 EMA 스무딩을 위한 상태를 가지므로, DI 컨테이너에서 매번 새 인스턴스를 생성합니다.

#### 3. Data Layer 구현

**ModelRepositoryImpl** (`data/repository/ModelRepositoryImpl.kt`):

```kotlin
class ModelRepositoryImpl(
    private val modelAssetReader: ModelAssetReader
) : IModelRepository {

    override fun listLive2DModels(): Result<List<String>> {
        return try {
            Result.success(modelAssetReader.listLive2DModels())
        } catch (e: Exception) {
            Result.error(DomainException.AssetReadError("assets root", e))
        }
    }

    override fun getModelMetadata(modelId: String): Result<Live2DModelInfo> {
        return try {
            val expressionsFolder = modelAssetReader.findAssetFolder(modelId, "expressions")
            val motionsFolder = modelAssetReader.findAssetFolder(modelId, "motions")
            // ... 파일 목록 조회
            Result.success(Live2DModelInfo(...))
        } catch (e: Exception) {
            Result.error(DomainException.AssetReadError(modelId, e))
        }
    }
}
```

#### 4. DI Container 확장

**AppContainer 인터페이스** (`core/common/di/AppContainer.kt`):

```kotlin
interface AppContainer {
    // 기존 (하위 호환성)
    val modelAssetReader: ModelAssetReader
    val faceTrackerFactory: FaceTrackerFactory

    // 신규 - Repository
    val modelRepository: IModelRepository

    // 신규 - UseCases
    val getLive2DModelsUseCase: GetLive2DModelsUseCase
    val getModelMetadataUseCase: GetModelMetadataUseCase
    fun createMapFacePoseUseCase(): MapFacePoseUseCase  // Stateful - 매번 새 인스턴스
}
```

**AppContainerImpl 구현** (`app/di/AppContainer.kt`):

```kotlin
class AppContainerImpl(application: Application) : AppContainer {
    // 의존성 그래프:
    // modelAssetReader
    //     └─ ModelRepositoryImpl
    //         ├─ GetLive2DModelsUseCase
    //         └─ GetModelMetadataUseCase

    override val modelRepository: IModelRepository by lazy {
        ModelRepositoryImpl(modelAssetReader)
    }

    override val getLive2DModelsUseCase by lazy {
        GetLive2DModelsUseCase(modelRepository)
    }

    override val getModelMetadataUseCase by lazy {
        GetModelMetadataUseCase(modelRepository)
    }

    override fun createMapFacePoseUseCase() = MapFacePoseUseCase()
}
```

#### 5. ViewModel 리팩터링

**StudioViewModel** 변경:

```kotlin
// 수정 전
class StudioViewModel(
    private val faceTrackerFactory: FaceTrackerFactory,
    private val modelAssetReader: ModelAssetReader
) : ViewModel() {
    private val mapper = FaceToLive2DMapper()

    private fun loadModelMetadata(modelId: String) {
        val expressionsFolder = modelAssetReader.findAssetFolder(modelId, "expressions")
        // ... 직접 호출
    }
}

// 수정 후
class StudioViewModel(
    private val faceTrackerFactory: FaceTrackerFactory,
    private val getModelMetadataUseCase: GetModelMetadataUseCase,
    private val mapFacePoseUseCase: MapFacePoseUseCase
) : ViewModel() {

    private fun loadModelMetadata(modelId: String) {
        getModelMetadataUseCase(modelId)
            .onSuccess { metadata ->
                _uiState.update { it.copy(
                    expressionsFolder = metadata.expressionsFolder,
                    motionsFolder = metadata.motionsFolder,
                    expressionFiles = metadata.expressionFiles,
                    motionFiles = metadata.motionFiles
                )}
            }
            .onError { error ->
                _uiState.update { it.copy(domainError = error) }
            }
    }

    fun mapFaceParams(facePose: FacePose, hasLandmarks: Boolean): Map<String, Float> {
        return mapFacePoseUseCase(facePose, hasLandmarks).params
    }
}
```

### 최종 아키텍처 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                        │
│  ┌─────────────────┐    ┌──────────────────────────────┐   │
│  │ ModelSelectScreen│    │         StudioScreen         │   │
│  └────────┬────────┘    └──────────────┬───────────────┘   │
│           │                            │                    │
│           ▼                            ▼                    │
│  ┌────────────────────────────────────────────────────┐    │
│  │                  StudioViewModel                    │    │
│  │  - uiState: StateFlow<UiState>                     │    │
│  │  - facePose: StateFlow<FacePose>                   │    │
│  └────────────────────────┬───────────────────────────┘    │
└───────────────────────────┼─────────────────────────────────┘
                            │
┌───────────────────────────┼─────────────────────────────────┐
│                    Domain Layer                              │
│           ┌───────────────┴───────────────┐                 │
│           ▼                               ▼                 │
│  ┌─────────────────────┐    ┌─────────────────────────┐    │
│  │GetModelMetadataUseCase│   │   MapFacePoseUseCase    │    │
│  └──────────┬──────────┘    └─────────────────────────┘    │
│             │                                               │
│             ▼                                               │
│  ┌─────────────────────┐                                   │
│  │   IModelRepository   │ ◄── Interface                    │
│  └──────────┬──────────┘                                   │
└─────────────┼───────────────────────────────────────────────┘
              │
┌─────────────┼───────────────────────────────────────────────┐
│             │           Data Layer                           │
│             ▼                                                │
│  ┌─────────────────────┐    ┌─────────────────────────┐    │
│  │ ModelRepositoryImpl  │───►│   ModelAssetReader      │    │
│  └─────────────────────┘    └─────────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
```

### 모듈 의존성 변경

```mermaid
graph TD
    A[app] --> B[domain]
    A --> C[data]
    A --> D[core:common]
    C --> B
    C --> D
    D --> B
    E[feature:studio] --> B
    E --> D
```

**build.gradle.kts 변경 사항**:

| 모듈 | 추가된 의존성 |
|------|--------------|
| `domain` | (변경 없음 - 순수 Kotlin) |
| `data` | `domain`, `core:common` |
| `core:common` | `domain` |
| `app` | `domain`, `data` |

### 관련 파일

**신규 생성 (9개)**

| 파일 | 위치 | 설명 |
|------|------|------|
| `Result.kt` | domain/common | Result 래퍼 + DomainException |
| `Live2DModelInfo.kt` | domain/model | 모델 메타데이터 DTO |
| `Live2DParams.kt` | domain/model | Live2D 파라미터 래퍼 |
| `IModelRepository.kt` | domain/repository | Repository 인터페이스 |
| `GetLive2DModelsUseCase.kt` | domain/usecase | 모델 목록 조회 |
| `GetModelMetadataUseCase.kt` | domain/usecase | 메타데이터 조회 |
| `MapFacePoseUseCase.kt` | domain/usecase | 파라미터 매핑 + EMA |
| `ModelRepositoryImpl.kt` | data/repository | Repository 구현체 |

**수정됨 (7개)**

| 파일 | 변경 내용 |
|------|----------|
| `data/build.gradle.kts` | domain, core:common 의존성 추가 |
| `core/common/build.gradle.kts` | domain 의존성 추가 |
| `app/build.gradle.kts` | domain, data 의존성 추가 |
| `AppContainer.kt` (interface) | Repository, UseCase 프로퍼티 추가 |
| `AppContainerImpl.kt` | 의존성 그래프 구성 |
| `StudioViewModel.kt` | UseCase 주입 및 사용 |
| `StudioScreen.kt` | ViewModel Factory 수정 |
| `ModelSelectScreen.kt` | GetLive2DModelsUseCase 사용 |

### 이점

1. **테스트 용이성**: Repository를 Mock으로 대체하여 UseCase 단위 테스트 가능
2. **관심사 분리**: 데이터 접근, 비즈니스 로직, UI가 명확히 분리
3. **일관된 에러 처리**: `Result<T>` 패턴으로 모든 레이어에서 동일한 방식의 에러 처리
4. **유지보수성**: 데이터 소스 변경 시 Repository 구현체만 수정

---

## 17. 외부 모델 가져오기 기능 (2026-01-30 업데이트)

### 배경
- 앱에 번들된 Asset 모델 외에도 사용자가 직접 Live2D 모델을 가져와서 사용하고 싶은 요구사항
- Android의 Scoped Storage 정책으로 인해 SAF(Storage Access Framework)를 통한 파일 접근 필요

### 구현 내용

#### 1. 외부 모델 저장소 구조

**ModelCacheManager**: SAF로 선택한 폴더를 내부 캐시로 복사
```kotlin
class ModelCacheManager(private val context: Context) {
    // 캐시 구조: /cache/external_models/{model_id}/
    fun copyToCache(folderUri: Uri, modelId: String, onProgress: (Float) -> Unit): Long
    fun validateModelFolder(folderUri: Uri): ModelValidationResult
    fun deleteCache(modelId: String): Boolean
}
```

**ExternalModelMetadataStore**: 가져온 모델의 메타데이터 관리 (SharedPreferences + JSON)
```kotlin
data class ModelMetadata(
    val id: String,
    val name: String,
    val originalUri: String,
    val modelJsonName: String,
    val sizeBytes: Long,
    val cachedAt: Long,
    val lastAccessedAt: Long
)
```

#### 2. Domain Layer 확장

**ModelSource sealed class**: Asset과 External 모델을 구분
```kotlin
sealed class ModelSource {
    abstract val id: String
    abstract val displayName: String

    data class Asset(val modelId: String) : ModelSource()
    data class External(val model: ExternalModel) : ModelSource()
}
```

**IExternalModelRepository**: 외부 모델 관리 인터페이스
```kotlin
interface IExternalModelRepository {
    suspend fun listExternalModels(): Result<List<ExternalModel>>
    suspend fun validateModel(folderUri: String): Result<ModelValidationResult>
    suspend fun importModel(folderUri: String, onProgress: (Float) -> Unit): Result<ExternalModel>
    suspend fun deleteModel(modelId: String): Result<Unit>
    suspend fun getModelMetadata(modelId: String): Result<Live2DModelInfo>
}
```

#### 3. UseCase 추가

**GetAllModelsUseCase**: Asset + External 모델 통합 조회
```kotlin
class GetAllModelsUseCase(
    private val modelRepository: IModelRepository,
    private val externalModelRepository: IExternalModelRepository
) {
    suspend operator fun invoke(): Result<List<ModelSource>>
}
```

**ImportExternalModelUseCase**: 외부 모델 검증 후 캐시로 복사
```kotlin
class ImportExternalModelUseCase(
    private val externalModelRepository: IExternalModelRepository
) {
    suspend operator fun invoke(folderUri: String, onProgress: (Float) -> Unit): Result<ExternalModel>
}
```

#### 4. UI 구현

**ModelSelectScreen 개선**:
- FAB 버튼으로 폴더 선택기 실행
- 가져오기 진행률 다이얼로그 표시
- 외부 모델은 "외부 모델" 라벨 표시

**Live2D 로더 확장**:
- `LAppMinimumPal`: Asset/External 파일 로딩 분기 처리
- `LAppMinimumLive2DManager.loadExternalModel()`: 캐시 경로에서 모델 로드

### 관련 파일

**신규 생성**
| 파일 | 위치 | 설명 |
|------|------|------|
| `ModelCacheManager.kt` | core:storage | 외부 모델 캐시 관리 |
| `ExternalModelMetadataStore.kt` | core:storage | 메타데이터 저장소 |
| `SAFPermissionManager.kt` | core:storage | SAF 권한 관리 |
| `ExternalModel.kt` | domain/model | 외부 모델 데이터 클래스 |
| `ModelSource.kt` | domain/model | 모델 소스 sealed class |
| `ModelValidationResult.kt` | domain/model | 검증 결과 |
| `IExternalModelRepository.kt` | domain/repository | Repository 인터페이스 |
| `ExternalModelRepositoryImpl.kt` | data/repository | Repository 구현체 |
| `GetAllModelsUseCase.kt` | domain/usecase | 통합 모델 조회 |
| `ImportExternalModelUseCase.kt` | domain/usecase | 모델 가져오기 |

---

## 18. 외부 모델 삭제 기능 (2026-01-31 업데이트)

### 배경
- 가져온 외부 모델을 삭제하는 기능 필요
- Asset 모델은 앱에 번들되어 있으므로 삭제 불가
- 다중 선택 삭제 지원 필요

### 구현 내용

#### 1. 삭제 모드 UI

**길게 눌러서 삭제 모드 진입**:
```kotlin
ModelCard(
    modifier = Modifier.combinedClickable(
        onClick = { /* 일반 클릭 */ },
        onLongClick = {
            if (modelSource is ModelSource.External) {
                viewModel.enterDeleteMode(modelSource.id)
            }
        }
    )
)
```

**삭제 모드 앱바**:
- 왼쪽: 뒤로가기 버튼 (삭제 모드 종료)
- 중앙: "N개 선택됨" 타이틀
- 오른쪽: 빨간색 휴지통 버튼

**선택 UI**:
- 외부 모델만 체크박스 표시
- Asset 모델은 비활성화 스타일로 표시 (선택 불가)

#### 2. 삭제 확인 다이얼로그

```kotlin
@Composable
private fun DeleteConfirmDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        title = { Text("모델 삭제") },
        text = { Text("${count}개의 모델을 삭제하시겠습니까?") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        dismissButton = { TextButton(onClick = onConfirm) { Text("삭제", color = Red) } }
    )
}
```

#### 3. 삭제 진행 다이얼로그

```kotlin
@Composable
private fun DeletingProgressDialog() {
    Dialog(onDismissRequest = { /* 취소 불가 */ }) {
        Row {
            CircularProgressIndicator()
            Text("삭제 중...")
        }
    }
}
```

#### 4. ViewModel 상태 관리

```kotlin
data class UiState(
    // ... 기존 필드
    val isDeleteMode: Boolean = false,
    val selectedModelIds: Set<String> = emptySet(),
    val isDeleting: Boolean = false
)

// 삭제 모드 관련 메서드
fun enterDeleteMode(initialModelId: String)
fun exitDeleteMode()
fun toggleModelSelection(modelId: String)
fun deleteSelectedModels()
```

#### 5. GetModelMetadataUseCase 통합

기존에 Asset 모델과 External 모델의 메타데이터 조회가 분리되어 있었으나, 하나의 UseCase로 통합:

```kotlin
class GetModelMetadataUseCase(
    private val modelRepository: IModelRepository,
    private val externalModelRepository: IExternalModelRepository
) {
    suspend operator fun invoke(modelSource: ModelSource): Result<Live2DModelInfo> {
        return when (modelSource) {
            is ModelSource.Asset -> modelRepository.getModelMetadata(modelSource.modelId)
            is ModelSource.External -> externalModelRepository.getModelMetadata(modelSource.model.id)
        }
    }
}
```

**통합 이유**:
- Asset 모델: `AssetManager`로 assets 폴더 접근
- External 모델: `File` API로 캐시 디렉토리 접근
- 저장소 접근 방식이 다르므로 Repository는 분리하되, UseCase에서 `ModelSource`로 분기 처리

#### 6. 외부 모델 expressions/motions 폴더 인식

**ExternalModelRepositoryImpl.getModelMetadata()**:
```kotlin
override suspend fun getModelMetadata(modelId: String): Result<Live2DModelInfo> {
    val cacheDir = cacheManager.getModelCacheDir(modelId)

    // 대소문자 무시하고 폴더 탐색
    val expressionsFolder = cacheDir.listFiles()
        ?.firstOrNull { it.isDirectory && it.name.equals("expressions", ignoreCase = true) }

    val motionsFolder = cacheDir.listFiles()
        ?.firstOrNull { it.isDirectory && it.name.equals("motions", ignoreCase = true) }

    // 파일 목록 조회
    val expressionFiles = expressionsFolder?.listFiles()
        ?.filter { it.name.endsWith(".exp3.json", ignoreCase = true) }
        ?.map { it.name } ?: emptyList()

    val motionFiles = motionsFolder?.listFiles()
        ?.filter { it.name.endsWith(".motion3.json", ignoreCase = true) }
        ?.map { it.name } ?: emptyList()

    return Result.success(Live2DModelInfo(...))
}
```

### 에러 처리

**Repository 레벨**:
```kotlin
override suspend fun deleteModel(modelId: String): Result<Unit> {
    return try {
        cacheManager.deleteCache(modelId)
        metadataStore.deleteModel(modelId)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.error(DomainException.ExternalModelException.CacheOperationFailed(...))
    }
}
```

**UseCase 레벨**: 첫 번째 에러 발생 시 중단하고 에러 반환

**ViewModel 레벨**: `isDeleting = false` 설정 후 에러 메시지 표시

### 관련 파일

**신규 생성**
| 파일 | 위치 | 설명 |
|------|------|------|
| `DeleteExternalModelsUseCase.kt` | domain/usecase | 다중 모델 삭제 |

**수정됨**
| 파일 | 변경 내용 |
|------|----------|
| `IExternalModelRepository.kt` | `getModelMetadata()` 메서드 추가 |
| `ExternalModelRepositoryImpl.kt` | `getModelMetadata()` 구현 |
| `GetModelMetadataUseCase.kt` | Asset/External 통합, `ModelSource` 파라미터 |
| `ModelSelectViewModel.kt` | 삭제 모드 상태 및 메서드 추가 |
| `ModelSelectScreen.kt` | 삭제 모드 UI, 다이얼로그 추가 |
| `StudioViewModel.kt` | `initialize(modelSource)` 시그니처 변경 |
| `StudioScreen.kt` | ViewModel 초기화 수정 |
| `AppContainer.kt` | `deleteExternalModelsUseCase` 추가 |
| `AppContainerImpl.kt` | UseCase 구현 추가 |

---

## 19. 감정/모션 초기화 기능 (2026-01-31 업데이트)

### 배경
- 감정(Expression) 또는 모션(Motion)을 선택하여 적용한 후, 원래 상태로 되돌리는 기능이 없었음
- 사용자가 다양한 표정/동작을 테스트할 때 초기화 기능 필요

### 구현 내용

#### 1. Expression 초기화

**LAppMinimumModel.java**에 `clearExpression()` 메서드 추가:

```java
/**
 * Clear all active expressions, returning the model to its default state.
 */
public void clearExpression() {
    if (expressionManager != null) {
        expressionManager.stopAllMotions();
    }
}
```

Expression은 `CubismMotionManager`의 `stopAllMotions()`만 호출하면 기본 상태로 복귀됨.

#### 2. Motion 초기화

Motion 초기화는 Expression보다 복잡한 처리가 필요:

**문제점**: `stopAllMotions()`만 호출하면 모션이 현재 프레임에서 멈추고, 기본 포즈로 돌아가지 않음

**원인 분석** (`LAppMinimumModel.update()` 흐름):
1. `model.loadParameters()` - 이전에 저장된 파라미터 로드
2. `motionManager.updateMotion()` - 모션이 파라미터 업데이트
3. `model.saveParameters()` - 현재 파라미터 저장

모션 중지 후에도 `loadParameters()`가 저장된 값을 다시 로드하므로, 파라미터가 모션 상태로 유지됨.

**해결 방법**: 파라미터를 기본값으로 리셋 후 `saveParameters()` 호출

```java
/**
 * Clear all active motions, returning the model to its idle state.
 * Resets all parameters to their default values.
 */
public void clearMotion() {
    if (motionManager != null) {
        motionManager.stopAllMotions();
    }
    // 모든 파라미터를 기본값으로 리셋
    if (model != null) {
        int parameterCount = model.getParameterCount();
        for (int i = 0; i < parameterCount; i++) {
            float defaultValue = model.getParameterDefaultValue(i);
            model.setParameterValue(i, defaultValue);
        }
        // 리셋된 상태를 저장하여 다음 프레임의 loadParameters()에서 유지되도록 함
        model.saveParameters();
    }
}
```

#### 3. Manager Wrapper 메서드

**LAppMinimumLive2DManager.java**에 UI에서 호출할 수 있는 wrapper 추가:

```java
public void clearExpression() {
    if (model != null) {
        model.clearExpression();
    }
}

public void clearMotion() {
    if (model != null) {
        model.clearMotion();
    }
}
```

#### 4. UI 통합

**StudioScreen.kt**의 `FileListDialog` 호출 시 "초기화" 아이템을 목록 맨 앞에 추가:

```kotlin
// Expression 다이얼로그
is StudioViewModel.DialogState.Expression -> {
    FileListDialog(
        title = "감정 목록",
        files = listOf("초기화") + uiState.expressionFiles,
        onFileSelected = { fileName ->
            if (fileName == "초기화") {
                LAppMinimumLive2DManager.getInstance().clearExpression()
            } else {
                LAppMinimumLive2DManager.getInstance()
                    .startExpression("${uiState.expressionsFolder}/$fileName")
            }
            viewModel.dismissDialog()
        }
    )
}

// Motion 다이얼로그
is StudioViewModel.DialogState.Motion -> {
    FileListDialog(
        title = "모션 목록",
        files = listOf("초기화") + uiState.motionFiles,
        onFileSelected = { fileName ->
            if (fileName == "초기화") {
                LAppMinimumLive2DManager.getInstance().clearMotion()
            } else {
                LAppMinimumLive2DManager.getInstance()
                    .startMotion("${uiState.motionsFolder}/$fileName")
            }
            viewModel.dismissDialog()
        }
    )
}
```

### Live2D SDK 참고

`expressionManager`와 `motionManager`는 모두 `CubismMotionManager` 타입:

| 메서드 | 설명 |
|--------|------|
| `startMotionPriority()` | 모션/표정 시작 |
| `updateMotion()` | 프레임별 업데이트 |
| `stopAllMotions()` | 모든 모션 중지 |
| `isFinished()` | 모션 완료 여부 |

### 관련 파일

**수정됨**
| 파일 | 변경 내용 |
|------|----------|
| `LAppMinimumModel.java` | `clearExpression()`, `clearMotion()` 메서드 추가 |
| `LAppMinimumLive2DManager.java` | `clearExpression()`, `clearMotion()` wrapper 추가 |
| `StudioScreen.kt` | 다이얼로그에 "초기화" 아이템 추가 |

---

## 20. 스플래시/인트로 화면 구현 (2026-01-31 업데이트)

### 배경
- 앱 시작 시 브랜딩을 위한 Cubism 로고 표시 필요
- 사용자에게 앱이 로딩 중임을 시각적으로 알려주는 인트로 화면 필요

### 초기 시도: Android Splash Screen API

Android 12+의 Splash Screen API를 사용하여 구현 시도:

```kotlin
// MainActivity.kt
override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    // ...
}
```

```xml
<!-- themes.xml -->
<style name="Theme.LiveMotion.Splash" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">@android:color/white</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/splash_icon</item>
    <item name="postSplashScreenTheme">@style/Theme.LiveMotion</item>
</style>
```

**문제점**: Splash Screen API는 아이콘을 원형으로 마스킹하여 표시하기 때문에, 가로로 긴 Cubism 로고가 잘려서 제대로 보이지 않음.

### 최종 구현: CubismIntroScreen Composable

Splash Screen API 대신 별도의 Compose 화면으로 인트로 구현:

#### 1. NavKey.Intro 추가

```kotlin
// NavKey.kt
@Serializable
sealed interface NavKey {
    @Serializable
    data object Intro : NavKey  // 신규 추가

    @Serializable
    data object ModelSelect : NavKey
    // ...
}
```

#### 2. CubismIntroScreen 구현

```kotlin
// CubismIntroScreen.kt
@Composable
fun CubismIntroScreen(
    onTimeout: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(1000L)  // 1초 대기
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.cubism_logo_orange),
            contentDescription = "Cubism Logo",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
        )
    }
}
```

#### 3. Navigation 설정

```kotlin
// MainActivity.kt
NavHost(
    navController = navController,
    startDestination = NavKey.Intro  // Intro로 시작
) {
    composable<NavKey.Intro> {
        CubismIntroScreen(
            onTimeout = {
                navController.navigate(NavKey.ModelSelect) {
                    popUpTo(NavKey.Intro) { inclusive = true }  // 뒤로가기 스택에서 제거
                }
            }
        )
    }
    composable<NavKey.ModelSelect> { /* ... */ }
    composable<NavKey.Studio> { /* ... */ }
}
```

#### 4. 시스템 스플래시 테마 간소화

시스템 스플래시는 아이콘 없이 흰색 배경만 표시:

```xml
<!-- themes.xml -->
<style name="Theme.LiveMotion.Splash" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">@android:color/white</item>
    <item name="postSplashScreenTheme">@style/Theme.LiveMotion</item>
    <!-- 아이콘 제거 -->
</style>
```

### 화면 전환 흐름

```
앱 시작
    │
    ▼
┌─────────────────────────┐
│   시스템 스플래시        │  (흰색 배경, 아이콘 없음)
│   (매우 짧게 표시)       │
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│   CubismIntroScreen     │  (1초간 로고 표시)
│   - 흰색 배경           │
│   - Cubism 로고 중앙    │
└───────────┬─────────────┘
            │ 1초 후 자동 전환
            ▼
┌─────────────────────────┐
│   ModelSelectScreen     │  (뒤로가기 시 앱 종료)
└─────────────────────────┘
```

### 관련 파일

**신규 생성**
| 파일 | 위치 | 설명 |
|------|------|------|
| `CubismIntroScreen.kt` | feature:studio | 인트로 화면 Composable |
| `cubism_logo_orange.png` | feature:studio/res/drawable | 로고 이미지 (복사) |

**수정됨**
| 파일 | 변경 내용 |
|------|----------|
| `libs.versions.toml` | `splashscreen = "1.0.1"` 버전 추가 |
| `app/build.gradle.kts` | `androidx-core-splashscreen` 의존성 추가 |
| `themes.xml` | `Theme.LiveMotion.Splash` 스타일 추가 |
| `AndroidManifest.xml` | MainActivity 테마를 `Theme.LiveMotion.Splash`로 변경 |
| `MainActivity.kt` | `installSplashScreen()` 호출, `NavKey.Intro` composable 추가 |
| `NavKey.kt` | `NavKey.Intro` data object 추가 |

### 이점

1. **로고 전체 표시**: 원형 마스킹 없이 가로로 긴 로고를 온전히 표시
2. **유연한 커스터마이징**: Compose로 구현하여 애니메이션, 레이아웃 자유롭게 변경 가능
3. **일관된 UX**: 시스템 스플래시 → 인트로 화면 → 메인 화면으로 자연스러운 전환
4. **뒤로가기 처리**: `popUpTo(inclusive = true)`로 인트로 화면을 스택에서 제거하여 뒤로가기 시 앱 종료

---

## 21. 라이트/다크 모드 테마 적용 (2026-01-31 업데이트)

### 배경
- 기존 `StudioScreen`의 컨트롤 패널과 버튼들이 하드코딩된 다크 테마 색상만 사용
- 시스템 라이트/다크 모드 전환 시 UI가 일관되지 않음
- `ModelSelectScreen`의 모델 카드 색상도 Asset/External 모델에 따라 다르게 적용되어 있었음

### 구현 내용

#### 1. ModelSelectScreen 모델 카드 색상 통일

기존에는 Asset 모델과 External 모델의 카드 배경색이 달랐으나, 모두 `primaryContainer`(연한 민트)로 통일:

```kotlin
// 변경 전
colors = if (isDeleteMode && !isExternal) {
    CardDefaults.cardColors(containerColor = surfaceVariant.copy(alpha = 0.5f))
} else {
    CardDefaults.cardColors()
}

// 변경 후
colors = when {
    isDeleteMode && !isExternal -> CardDefaults.cardColors(containerColor = surfaceVariant.copy(alpha = 0.5f))
    else -> CardDefaults.cardColors(containerColor = primaryContainer)
}
```

#### 2. StudioScreen MaterialTheme 적용

하드코딩된 색상들을 `MaterialTheme.colorScheme`으로 교체:

| 기존 하드코딩 색상 | MaterialTheme 색상 | 용도 |
|------------------|-------------------|------|
| `ControlPanelBackground` (#1A1A2E) | `surfaceContainer` | 컨트롤 패널 배경 |
| `ButtonDefaultColor` (#2D2D44) | `surfaceVariant` | 비활성 버튼 배경 |
| `AccentBlue` (#4A9FF5) | `primary` | 감정/GPU/이동 버튼 |
| `AccentPurple` (#7C4DFF) | `secondary` | 모션/확대 버튼 |
| `AccentCyan` (#00BCD4) | `tertiary` | 프리뷰 버튼 |
| `TextPrimary` (#FFFFFF) | `onSurface`, `onPrimary` | 주요 텍스트 |
| `TextSecondary` (#B0B0C3) | `onSurfaceVariant` | 보조 텍스트 |

#### 3. 다이얼로그 색상 적용

`FileListDialog`와 `TrackingErrorDetailDialog`도 MaterialTheme 색상 사용:

```kotlin
@Composable
fun FileListDialog(...) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        // 제목: onSurface
        // 버튼 배경: surfaceVariant
        // 버튼 텍스트: onSurfaceVariant
        // 닫기 버튼: primary / onPrimary
    }
}
```

### 테마 색상 매핑 (Theme.kt)

| 색상 | 라이트 모드 | 다크 모드 |
|------|------------|----------|
| `primary` | Mint70 (#5BBFAA) | Mint80 (#7DD3C0) |
| `primaryContainer` | Mint90 (#B2DFDB) | Mint30 (#005A4D) |
| `secondary` | Teal40 (#007F8F) | Teal80 (#4DD0E1) |
| `tertiary` | Coral40 (#994D4D) | Coral80 (#FFB4AB) |
| `surface` | Neutral99 (#FBFDFA) | Neutral10 (#191C1B) |
| `surfaceVariant` | NeutralVariant90 (#DBE5E0) | NeutralVariant30 (#3F4945) |
| `surfaceContainer` | (자동 계산) | (자동 계산) |

### 이점

1. **코드 간소화**: 하드코딩된 색상 상수 제거, `MaterialTheme.colorScheme` 직접 사용
2. **자동 테마 전환**: 시스템 라이트/다크 모드에 따라 자동으로 색상 변경
3. **일관된 디자인**: 앱 전체에서 동일한 색상 시스템 사용
4. **유지보수 용이**: 색상 변경 시 `Theme.kt`만 수정하면 전체 반영

### 관련 파일

**수정됨**
| 파일 | 변경 내용 |
|------|----------|
| `ModelSelectScreen.kt` | 모델 카드 배경색 `primaryContainer`로 통일 |
| `StudioScreen.kt` | 하드코딩 색상 제거, `MaterialTheme.colorScheme` 사용 |

---

## 22. Hilt 의존성 주입 마이그레이션 (2026-02-02 업데이트)

### 배경
- 기존에는 수동 DI 컨테이너(`AppContainer`, `LocalAppContainer`)를 사용
- ViewModel Factory를 수동으로 작성해야 하는 번거로움
- 테스트 시 의존성 교체가 복잡

### 구현 내용

#### 1. Application 클래스 설정

```kotlin
// app/src/main/java/org/comon/livemotion/LiveMotionApp.kt
@HiltAndroidApp
class LiveMotionApp : Application()
```

#### 2. Activity 설정

```kotlin
// MainActivity.kt
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // CompositionLocalProvider 제거됨
}
```

#### 3. Hilt Module 생성

**AppModule** (`core/common/di/AppModule.kt`):
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideModelAssetReader(@ApplicationContext context: Context): ModelAssetReader {
        return ModelAssetReader(context.assets)
    }

    @Provides
    @Singleton
    fun provideFaceTrackerFactory(@ApplicationContext context: Context): FaceTrackerFactory {
        return FaceTrackerFactory(context)
    }
}
```

**StorageModule** (`core/storage/di/StorageModule.kt`):
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideModelCacheManager(@ApplicationContext context: Context): ModelCacheManager

    @Provides
    @Singleton
    fun provideExternalModelMetadataStore(@ApplicationContext context: Context): ExternalModelMetadataStore

    @Provides
    @Singleton
    fun provideSAFPermissionManager(@ApplicationContext context: Context): SAFPermissionManager
}
```

**RepositoryModule** (`data/di/RepositoryModule.kt`):
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindModelRepository(impl: ModelRepositoryImpl): IModelRepository

    @Binds
    @Singleton
    abstract fun bindExternalModelRepository(impl: ExternalModelRepositoryImpl): IExternalModelRepository
}
```

**UseCaseModule** (`data/di/UseCaseModule.kt`):
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides
    fun provideGetAllModelsUseCase(...): GetAllModelsUseCase

    @Provides
    fun provideGetModelMetadataUseCase(...): GetModelMetadataUseCase

    @Provides
    fun provideImportExternalModelUseCase(...): ImportExternalModelUseCase

    @Provides
    fun provideDeleteExternalModelsUseCase(...): DeleteExternalModelsUseCase
}
```

#### 4. ViewModel 수정

```kotlin
// 수정 전 (수동 DI)
class StudioViewModel(
    private val faceTrackerFactory: FaceTrackerFactory,
    private val getModelMetadataUseCase: GetModelMetadataUseCase
) : ViewModel() {
    class Factory(...) : ViewModelProvider.Factory { ... }
}

// 수정 후 (Hilt)
@HiltViewModel
class StudioViewModel @Inject constructor(
    private val faceTrackerFactory: FaceTrackerFactory,
    private val getModelMetadataUseCase: GetModelMetadataUseCase
) : ViewModel()
```

#### 5. Screen에서 ViewModel 사용

```kotlin
// 수정 전
val container = LocalAppContainer.current
val viewModel: StudioViewModel = viewModel(
    factory = StudioViewModel.Factory(container.faceTrackerFactory, ...)
)

// 수정 후
val viewModel: StudioViewModel = hiltViewModel()
```

### 삭제된 파일

| 파일 | 이유 |
|------|------|
| `core/common/di/AppContainer.kt` | Hilt Module로 대체 |
| `core/common/di/LocalAppContainer.kt` | hiltViewModel()로 대체 |
| `app/di/AppContainerImpl.kt` | Hilt Module로 대체 |

### 관련 파일

**신규 생성**
| 파일 | 위치 | 설명 |
|------|------|------|
| `AppModule.kt` | core/common/di | 공통 의존성 제공 |
| `StorageModule.kt` | core/storage/di | 저장소 의존성 제공 |
| `RepositoryModule.kt` | data/di | Repository 바인딩 |
| `UseCaseModule.kt` | data/di | UseCase 제공 |

**수정됨**
| 파일 | 변경 내용 |
|------|----------|
| `LiveMotionApp.kt` | `@HiltAndroidApp` 추가 |
| `MainActivity.kt` | `@AndroidEntryPoint` 추가, CompositionLocalProvider 제거 |
| `StudioViewModel.kt` | `@HiltViewModel`, `@Inject constructor` |
| `ModelSelectViewModel.kt` | `@HiltViewModel`, `@Inject constructor` |
| `StudioScreen.kt` | `hiltViewModel()` 사용 |
| `ModelSelectScreen.kt` | `hiltViewModel()` 사용 |
| `build.gradle.kts` (여러 모듈) | Hilt, KSP 의존성 추가 |

---

## 23. MVI 패턴 완성 (UiIntent/UiEffect) (2026-02-02 업데이트)

### 배경
- 기존에는 ViewModel에 개별 함수들이 분산되어 있어 사용자 액션 추적이 어려움
- 일회성 이벤트(스낵바, 네비게이션) 처리가 UiState에 혼재
- MVI 패턴의 Intent/Effect 계층이 없어 불완전한 구조

### 구현 내용

#### 1. UiIntent 정의

사용자 액션을 sealed interface로 정의:

```kotlin
// feature/studio/src/main/java/org/comon/studio/StudioUiIntent.kt
sealed interface StudioUiIntent {
    data object ToggleZoom : StudioUiIntent
    data object ToggleMove : StudioUiIntent
    data object TogglePreview : StudioUiIntent
    data class SetGpuEnabled(val enabled: Boolean) : StudioUiIntent
    data object ShowExpressionDialog : StudioUiIntent
    data object ShowMotionDialog : StudioUiIntent
    data object DismissDialog : StudioUiIntent
    data object ClearTrackingError : StudioUiIntent
    data object ClearDomainError : StudioUiIntent
    data object OnModelLoaded : StudioUiIntent
}
```

```kotlin
// feature/studio/src/main/java/org/comon/studio/ModelSelectUiIntent.kt
sealed interface ModelSelectUiIntent {
    data object LoadModels : ModelSelectUiIntent
    data class ImportModel(val folderUri: String) : ModelSelectUiIntent
    data class EnterDeleteMode(val initialModelId: String) : ModelSelectUiIntent
    data object ExitDeleteMode : ModelSelectUiIntent
    data class ToggleModelSelection(val modelId: String) : ModelSelectUiIntent
    data object DeleteSelectedModels : ModelSelectUiIntent
    data object ClearError : ModelSelectUiIntent
}
```

#### 2. UiEffect 정의

일회성 이벤트를 sealed class로 정의:

```kotlin
// feature/studio/src/main/java/org/comon/studio/StudioUiEffect.kt
sealed class StudioUiEffect {
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null
    ) : StudioUiEffect()

    data object NavigateBack : StudioUiEffect()
}
```

```kotlin
// feature/studio/src/main/java/org/comon/studio/ModelSelectUiEffect.kt
sealed class ModelSelectUiEffect {
    data class ShowSnackbar(val message: String) : ModelSelectUiEffect()
    data class NavigateToStudio(val modelSource: ModelSource) : ModelSelectUiEffect()
}
```

#### 3. ViewModel에서 onIntent() 단일 진입점

```kotlin
@HiltViewModel
class StudioViewModel @Inject constructor(...) : ViewModel() {

    private val _uiEffect = Channel<StudioUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    fun onIntent(intent: StudioUiIntent) {
        when (intent) {
            is StudioUiIntent.ToggleZoom -> toggleZoom()
            is StudioUiIntent.ToggleMove -> toggleMove()
            is StudioUiIntent.TogglePreview -> togglePreview()
            is StudioUiIntent.SetGpuEnabled -> setGpuEnabled(intent.enabled)
            is StudioUiIntent.ShowExpressionDialog -> showExpressionDialog()
            is StudioUiIntent.ShowMotionDialog -> showMotionDialog()
            is StudioUiIntent.DismissDialog -> dismissDialog()
            is StudioUiIntent.ClearTrackingError -> clearTrackingError()
            is StudioUiIntent.ClearDomainError -> clearDomainError()
            is StudioUiIntent.OnModelLoaded -> onModelLoaded()
        }
    }

    private fun toggleZoom() {
        _uiState.update { it.copy(isZoomEnabled = !it.isZoomEnabled) }
    }
    // ...
}
```

#### 4. Screen에서 Intent 사용

```kotlin
@Composable
fun StudioScreen(
    viewModel: StudioViewModel = hiltViewModel()
) {
    // Effect 수집
    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is StudioUiEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is StudioUiEffect.NavigateBack -> onBack()
            }
        }
    }

    // Intent 전달
    Button(onClick = { viewModel.onIntent(StudioUiIntent.ToggleZoom) }) {
        Text("확대")
    }
}
```

### MVI 아키텍처 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│                         Screen                              │
│  ┌─────────────────┐              ┌─────────────────────┐  │
│  │   User Action   │──────────────│   UI Rendering      │  │
│  └────────┬────────┘              └──────────▲──────────┘  │
│           │                                   │             │
│           │ onIntent(Intent)                  │ uiState     │
│           │                                   │             │
└───────────┼───────────────────────────────────┼─────────────┘
            │                                   │
            ▼                                   │
┌─────────────────────────────────────────────────────────────┐
│                       ViewModel                             │
│  ┌─────────────────┐              ┌─────────────────────┐  │
│  │    onIntent()   │──────────────│   _uiState.update   │  │
│  │   when(intent)  │              │                     │  │
│  └─────────────────┘              └─────────────────────┘  │
│           │                                                 │
│           │ Channel.send(Effect)                           │
│           ▼                                                 │
│  ┌─────────────────┐                                       │
│  │    uiEffect     │────────────────────────────────────►  │
│  │    (Channel)    │              Side Effects (Snackbar)  │
│  └─────────────────┘                                       │
└─────────────────────────────────────────────────────────────┘
```

### 관련 파일

**신규 생성**
| 파일 | 위치 | 설명 |
|------|------|------|
| `StudioUiIntent.kt` | feature/studio | Studio 화면 Intent |
| `StudioUiEffect.kt` | feature/studio | Studio 화면 Effect |
| `ModelSelectUiIntent.kt` | feature/studio | ModelSelect 화면 Intent |
| `ModelSelectUiEffect.kt` | feature/studio | ModelSelect 화면 Effect |

**수정됨**
| 파일 | 변경 내용 |
|------|----------|
| `StudioViewModel.kt` | `onIntent()` 단일 진입점, `uiEffect` Channel |
| `ModelSelectViewModel.kt` | `onIntent()` 단일 진입점, `uiEffect` Channel |
| `StudioScreen.kt` | `viewModel.onIntent()` 호출로 변경 |
| `ModelSelectScreen.kt` | `viewModel.onIntent()` 호출로 변경 |

---

## 24. Feature 모듈 분리 (2026-02-02 업데이트)

### 배경
- 기존에는 `feature:studio` 모듈에 Studio, ModelSelect, Title, Settings, Intro 화면이 모두 포함
- 단일 책임 원칙 위반 (하나의 모듈이 여러 기능 담당)
- 빈 `feature:settings` 모듈이 존재했으나 미사용

### 구현 내용

#### 변경 전 구조

```
feature/
├── studio/              # 모든 화면 포함
│   ├── StudioScreen.kt
│   ├── ModelSelectScreen.kt
│   ├── TitleScreen.kt           # ❌ Studio와 무관
│   ├── SettingsScreen.kt        # ❌ Studio와 무관
│   └── CubismIntroScreen.kt     # ❌ Studio와 무관
│
├── settings/            # 빈 모듈
└── home/                # 미사용
```

#### 변경 후 구조

```
feature/
├── home/                        # 앱 진입점
│   ├── TitleScreen.kt
│   ├── IntroScreen.kt           # CubismIntroScreen → IntroScreen 리네임
│   └── res/
│       ├── drawable/
│       │   ├── title_image.png
│       │   └── cubism_logo_orange.png
│       └── values/strings.xml
│
├── settings/                    # 설정 화면
│   ├── SettingsScreen.kt
│   └── res/values/strings.xml
│
└── studio/                      # Live2D 스튜디오
    ├── StudioScreen.kt
    ├── StudioViewModel.kt
    ├── StudioUiIntent.kt
    ├── StudioUiEffect.kt
    ├── ModelSelectScreen.kt
    ├── ModelSelectViewModel.kt
    ├── ModelSelectUiIntent.kt
    ├── ModelSelectUiEffect.kt
    └── res/values/strings.xml
```

#### 모듈별 책임

| 모듈 | 책임 | 화면 |
|------|------|------|
| `feature:home` | 앱 진입점, 메뉴 | IntroScreen, TitleScreen |
| `feature:settings` | 앱 설정 | SettingsScreen |
| `feature:studio` | Live2D 렌더링, 모델 선택 | StudioScreen, ModelSelectScreen |

#### build.gradle.kts 변경

**feature:home**:
```kotlin
plugins {
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    implementation(project(":core:ui"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
}
```

**feature:settings**:
```kotlin
plugins {
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    implementation(project(":core:ui"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
}
```

**app**:
```kotlin
dependencies {
    implementation(project(":feature:studio"))
    implementation(project(":feature:home"))      // 추가
    implementation(project(":feature:settings"))  // 추가
}
```

#### MainActivity.kt import 변경

```kotlin
// 변경 전
import org.comon.studio.CubismIntroScreen
import org.comon.studio.TitleScreen
import org.comon.studio.SettingsScreen

// 변경 후
import org.comon.home.IntroScreen
import org.comon.home.TitleScreen
import org.comon.settings.SettingsScreen
```

#### 문자열 리소스 분리

**feature:home/res/values/strings.xml**:
```xml
<resources>
    <string name="title_studio">Studio</string>
    <string name="title_settings">Settings</string>
    <string name="title_background_description">Title Background</string>
    <string name="intro_logo_description">Cubism Logo</string>
</resources>
```

**feature:settings/res/values/strings.xml**:
```xml
<resources>
    <string name="settings_title">Settings</string>
    <string name="settings_back">Back</string>
    <string name="settings_coming_soon">Coming Soon</string>
</resources>
```

### 이점

1. **단일 책임**: 각 모듈이 하나의 기능만 담당
2. **독립적 개발**: 모듈별로 독립적인 빌드/테스트 가능
3. **의존성 최소화**: 각 모듈은 필요한 의존성만 포함
4. **빌드 시간 단축**: 변경된 모듈만 재빌드

### 관련 파일

**신규 생성**
| 파일 | 위치 | 설명 |
|------|------|------|
| `TitleScreen.kt` | feature/home | Title 화면 (이동) |
| `IntroScreen.kt` | feature/home | Intro 화면 (이동 + 리네임) |
| `strings.xml` | feature/home/res/values | 문자열 리소스 |
| `SettingsScreen.kt` | feature/settings | Settings 화면 (이동) |
| `strings.xml` | feature/settings/res/values | 문자열 리소스 |

**수정됨**
| 파일 | 변경 내용 |
|------|----------|
| `feature/home/build.gradle.kts` | Compose 의존성 추가 |
| `feature/settings/build.gradle.kts` | Compose 의존성 추가 |
| `feature/studio/res/values/strings.xml` | 이동된 문자열 제거 |
| `app/build.gradle.kts` | feature:home, feature:settings 의존성 추가 |
| `MainActivity.kt` | import 경로 변경 |

**삭제됨**
| 파일 | 이유 |
|------|------|
| `feature/studio/TitleScreen.kt` | feature:home으로 이동 |
| `feature/studio/SettingsScreen.kt` | feature:settings로 이동 |
| `feature/studio/CubismIntroScreen.kt` | feature:home으로 이동 (IntroScreen으로 리네임) |
| `feature/studio/res/drawable/title_image.png` | feature:home으로 이동 |
| `feature/studio/res/drawable/cubism_logo_orange.png` | feature:home으로 이동 |

---

## 25. MapFacePoseUseCase 순수 함수화 (2026-02-02 업데이트)

### 배경
- 기존 `MapFacePoseUseCase`는 EMA 스무딩을 위해 내부에 `lastPose` 상태를 보유
- UseCase가 상태를 가지면 테스트가 어렵고, 여러 ViewModel에서 공유 시 문제 발생
- Clean Architecture 원칙: UseCase는 순수 함수여야 함

### 구현 내용

#### 1. 스무딩 상태 분리

```kotlin
// domain/src/main/java/org/comon/domain/model/FacePoseSmoothing.kt
/**
 * FacePose EMA 스무딩 상태를 저장하는 데이터 클래스.
 *
 * MapFacePoseUseCase가 순수 함수가 되도록 상태를 분리했습니다.
 */
data class FacePoseSmoothingState(
    val lastPose: FacePose = FacePose()
)
```

#### 2. UseCase 순수 함수화

```kotlin
// 변경 전 (상태 보유)
class MapFacePoseUseCase {
    private var lastPose = FacePose()  // ❌ 상태
    private val alpha = 0.4f

    fun reset() { lastPose = FacePose() }

    operator fun invoke(facePose: FacePose, hasLandmarks: Boolean): Live2DParams {
        // lastPose 사용 및 업데이트
    }
}

// 변경 후 (순수 함수)
class MapFacePoseUseCase {
    private val alpha = 0.4f

    /**
     * FacePose를 Live2D 파라미터로 변환합니다.
     *
     * @param facePose 얼굴 포즈 데이터
     * @param state 이전 스무딩 상태
     * @param hasLandmarks 얼굴 랜드마크 감지 여부
     * @return Pair<Live2DParams, FacePoseSmoothingState> - 변환된 파라미터와 새로운 상태
     */
    operator fun invoke(
        facePose: FacePose,
        state: FacePoseSmoothingState,
        hasLandmarks: Boolean
    ): Pair<Live2DParams, FacePoseSmoothingState> {
        if (!hasLandmarks) {
            return Pair(Live2DParams.DEFAULT, FacePoseSmoothingState())
        }
        return map(facePose, state)
    }
}
```

#### 3. ViewModel에서 상태 관리

```kotlin
@HiltViewModel
class StudioViewModel @Inject constructor(
    private val faceTrackerFactory: FaceTrackerFactory,
    private val getModelMetadataUseCase: GetModelMetadataUseCase
) : ViewModel() {

    // MapFacePoseUseCase는 순수 함수, 상태는 ViewModel에서 관리
    private val mapFacePoseUseCase = MapFacePoseUseCase()
    private var smoothingState = FacePoseSmoothingState()

    /**
     * 얼굴 포즈 데이터를 Live2D 파라미터 맵으로 변환합니다.
     */
    fun mapFaceParams(facePose: FacePose, hasLandmarks: Boolean): Map<String, Float> {
        val (params, newState) = mapFacePoseUseCase(facePose, smoothingState, hasLandmarks)
        smoothingState = newState
        return params.params
    }
}
```

### 순수 함수의 특징

| 특징 | 설명 |
|------|------|
| **입력 결정성** | 동일한 입력에 항상 동일한 출력 |
| **부수 효과 없음** | 외부 상태 변경 없음 |
| **테스트 용이** | Mock 없이 단순 입출력 테스트 |
| **스레드 안전** | 상태가 없으므로 동시성 문제 없음 |

### 이점

1. **테스트 용이성**: 순수 함수이므로 단위 테스트가 간단
```kotlin
@Test
fun `mapFacePose returns default when no landmarks`() {
    val useCase = MapFacePoseUseCase()
    val (params, _) = useCase(FacePose(), FacePoseSmoothingState(), hasLandmarks = false)
    assertEquals(Live2DParams.DEFAULT, params)
}
```

2. **재사용성**: 상태가 없으므로 싱글톤으로 공유 가능 (Hilt @Singleton 불필요)

3. **예측 가능성**: 이전 호출이 다음 호출에 영향 없음

### 관련 파일

**신규 생성**
| 파일 | 위치 | 설명 |
|------|------|------|
| `FacePoseSmoothing.kt` | domain/model | 스무딩 상태 데이터 클래스 |

**수정됨**
| 파일 | 변경 내용 |
|------|----------|
| `MapFacePoseUseCase.kt` | 순수 함수로 변환, `Pair<Live2DParams, State>` 반환 |
| `StudioViewModel.kt` | `smoothingState` 필드 추가, UseCase 호출 방식 변경 |

---

## 26. 스낵바 로직 리팩토링 및 SnackbarStateHolder (2026-02-02 업데이트)

### 배경
- MVI 패턴에서 `UiState.error`와 `UiEffect.ShowSnackbar`가 중복으로 에러를 처리하고 있었음
- Screen에서 스낵바 상태(`snackbarHostState`, `scope`, `showErrorDetailDialog`, `currentErrorDetail`)를 수동으로 관리
- 동일한 패턴의 코드가 여러 화면에 중복

### 구현 내용

#### 1. MVI 에러 처리 통합
`UiState.error`를 제거하고 모든 에러를 `UiEffect` 기반으로 통합:

```kotlin
// 변경 전: State와 Effect 중복
data class UiState(
    val error: String? = null  // 제거됨
)

// 변경 후: Effect만 사용
sealed class ModelSelectUiEffect {
    data class ShowSnackbar(val message: String) : ModelSelectUiEffect()
    data class ShowErrorWithDetail(
        val displayMessage: String,
        val detailMessage: String
    ) : ModelSelectUiEffect()
}
```

#### 2. SnackbarStateHolder 상태 홀더
스낵바와 에러 다이얼로그 상태를 통합 관리하는 컨테이너 클래스:

```kotlin
class SnackbarStateHolder(
    val snackbarHostState: SnackbarHostState,
    private val scope: CoroutineScope
) {
    var showErrorDialog by mutableStateOf(false)
    var currentErrorDetail by mutableStateOf<String?>(null)

    // 일반 스낵바 표시
    fun showSnackbar(message: String, actionLabel: String?, duration: SnackbarDuration)

    // 에러 스낵바 + 상세보기 다이얼로그
    fun showErrorWithDetail(displayMessage: String, detailMessage: String, actionLabel: String)

    // 다이얼로그 닫기
    fun dismissErrorDialog()
}

@Composable
fun rememberSnackbarStateHolder(): SnackbarStateHolder
```

#### 3. Screen 코드 단순화

```kotlin
// 변경 전: 5줄 이상의 수동 상태 관리
val snackbarHostState = remember { SnackbarHostState() }
val scope = rememberCoroutineScope()
var showErrorDetailDialog by remember { mutableStateOf(false) }
var currentErrorDetail by remember { mutableStateOf<String?>(null) }

// 변경 후: 1줄
val snackbarState = rememberSnackbarStateHolder()
```

Effect 처리도 단순화:
```kotlin
when (effect) {
    is ShowSnackbar -> snackbarState.showSnackbar(...)
    is ShowErrorWithDetail -> snackbarState.showErrorWithDetail(...)
}
```

### 이점

| 구분 | 변경 전 | 변경 후 |
|------|---------|----------|
| 에러 처리 | State + Effect 중복 | Effect 단일 |
| 상태 관리 | 수동 (5개 변수) | 상태 홀더 (1개) |
| 코드 중복 | 여러 Screen에 동일 패턴 | 공통 컴포넌트 재사용 |
| Effect 블로킹 | `scope.launch` 수동 호출 | 상태 홀더 내부 처리 |

### 관련 파일

**신규/수정**
| 파일 | 변경 내용 |
|------|----------|
| `SnackbarComponents.kt` | `SnackbarStateHolder` 추가, 미사용 클래스 제거 |
| `StudioUiEffect.kt` | `ShowErrorWithDetail` 추가 |
| `ModelSelectUiEffect.kt` | `ShowErrorWithDetail` 추가 |
| `StudioViewModel.kt` | `trackingError`, `domainError` State 제거, Effect 발송 |
| `ModelSelectViewModel.kt` | `error` State 제거, Effect 발송 |
| `StudioUiIntent.kt` | `ClearTrackingError`, `ClearDomainError` 제거 |
| `ModelSelectUiIntent.kt` | `ClearError` 제거 |
| `StudioScreen.kt` | `rememberSnackbarStateHolder()` 사용 |
| `ModelSelectScreen.kt` | `rememberSnackbarStateHolder()` 사용 |

---

## 27. Screen/Content 분리 및 Preview 지원 (2026-02-04 업데이트)

### 배경
- `StudioScreen`, `ModelSelectScreen`이 ViewModel에 직접 의존하여 Android Studio Preview에서 렌더링 불가
- `Live2DScreen`은 네이티브 JNI 라이브러리(`Live2DCubismCore`)를 로드하므로 Preview 환경에서 `UnsatisfiedLinkError` 발생
- ViewModel 로직과 순수 UI를 분리하여 테스트 용이성과 Preview 지원을 확보할 필요

### 구현 내용

#### 1. Screen(wrapper) / ScreenContent(순수 UI) 패턴

각 Screen 함수를 두 계층으로 분리:

```
Screen (public)         → ViewModel 연결, Effect 수집, 상태 collect
  └─ ScreenContent (private) → 순수 UI, 파라미터만으로 동작
```

#### 2. StudioScreen 분리

**StudioScreen (wrapper)** — ViewModel 연결 담당:
- `rememberSnackbarStateHolder()` 생성
- `LaunchedEffect`로 초기화, `uiEffect` 수집
- `facePose`, `landmarks`, `uiState` collect
- `faceParams` 계산
- `LAppMinimumLive2DManager` 호출을 콜백으로 전달
- `Live2DScreen`을 `modelViewContent` 슬롯으로 전달

**StudioScreenContent (순수 UI)** — 파라미터:
```kotlin
@Composable
private fun StudioScreenContent(
    uiState: StudioViewModel.StudioUiState,
    landmarks: List<NormalizedLandmark>,
    snackbarState: SnackbarStateHolder,
    onBack: () -> Unit,
    onIntent: (StudioUiIntent) -> Unit,
    onExpressionFileSelected: (String) -> Unit,
    onMotionFileSelected: (String) -> Unit,
    onExpressionReset: () -> Unit = {},
    onMotionReset: () -> Unit = {},
    modelViewContent: @Composable () -> Unit = {},
)
```

#### 3. ModelSelectScreen 분리

**ModelSelectScreen (wrapper)** — ViewModel 연결 담당:
- `rememberSnackbarStateHolder()` 생성
- `LaunchedEffect`로 `uiEffect` 수집, `errorMessage` 처리
- `SAFPermissionManager`, `folderPickerLauncher` 관리
- `uiState` collect

**ModelSelectScreenContent (순수 UI)** — 파라미터:
```kotlin
@Composable
private fun ModelSelectScreenContent(
    uiState: ModelSelectViewModel.UiState,
    snackbarState: SnackbarStateHolder,
    onModelSelected: (ModelSource) -> Unit,
    onImportClick: () -> Unit,
    onIntent: (ModelSelectUiIntent) -> Unit,
)
```

#### 4. Live2D 네이티브 라이브러리 문제 해결

`Live2DScreen`은 초기화 시 `LAppMinimumDelegate.getInstance()` → `CubismFramework.startUp()` → `Live2DCubismCoreJNI` 네이티브 라이브러리를 로드하므로 Preview 환경에서 사용 불가.

**해결**: `modelViewContent: @Composable () -> Unit` 슬롯 파라미터로 분리:

```kotlin
// wrapper에서 실제 Live2DScreen 전달
StudioScreenContent(
    modelViewContent = {
        Live2DScreen(
            modifier = Modifier.fillMaxSize(),
            modelSource = modelSource,
            faceParams = faceParams,
            ...
        )
    },
)

// Preview에서 placeholder 전달
StudioScreenContent(
    modelViewContent = {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.DarkGray),
            contentAlignment = Alignment.Center,
        ) {
            Text("Live2D Preview", color = Color.White)
        }
    },
)
```

#### 5. Preview 함수 추가

```kotlin
@Preview
@Composable
private fun StudioScreenPreview() {
    LiveMotionTheme {
        StudioScreenContent(
            uiState = StudioViewModel.StudioUiState(isModelLoading = false),
            landmarks = emptyList(),
            snackbarState = rememberSnackbarStateHolder(),
            onBack = {}, onIntent = {},
            onExpressionFileSelected = {}, onMotionFileSelected = {},
            modelViewContent = { /* placeholder */ },
        )
    }
}

@Preview
@Composable
private fun ModelSelectScreenPreview() {
    LiveMotionTheme {
        ModelSelectScreenContent(
            uiState = ModelSelectViewModel.UiState(
                models = listOf(ModelSource.Asset("Haru"), ModelSource.Asset("Mark")),
            ),
            snackbarState = rememberSnackbarStateHolder(),
            onModelSelected = {}, onImportClick = {}, onIntent = {},
        )
    }
}
```

### 설계 결정

| 항목 | 결정 | 이유 |
|------|------|------|
| Content 가시성 | `private` | Screen 외부에서 직접 사용할 필요 없음 |
| Live2D 영역 | 슬롯(`@Composable () -> Unit`) | 네이티브 JNI 의존성을 Preview에서 격리 |
| `BackHandler`, `showDeleteConfirmDialog` | Content 내부 유지 | 순수 UI 상호작용으로 ViewModel 불필요 |
| `SAFPermissionManager`, `folderPickerLauncher` | wrapper에 유지 | `Context`, `ActivityResultLauncher` 등 플랫폼 의존성 |

### 관련 파일

| 파일 | 변경 내용 |
|------|----------|
| `StudioScreen.kt` | `StudioScreen`/`StudioScreenContent` 분리, `modelViewContent` 슬롯 추가, `StudioScreenPreview` 추가 |
| `ModelSelectScreen.kt` | `ModelSelectScreen`/`ModelSelectScreenContent` 분리, `onImportClick` 콜백 추가, `ModelSelectScreenPreview` 추가 |

---

## 28. Predictive Back 제스처 취소 시 모델 소실 문제 (2026-02-05 업데이트)

### 문제

스튜디오 화면에서 Android 13+ predictive back 제스처를 시작했다가 취소하면(드래그 후 놓지 않고 원위치로 돌아가면) 로드된 Live2D 모델이 사라지는 현상이 발생.

### 원인 분석

#### 1. EGL 컨텍스트 손실 (핵심 원인)

`GLSurfaceView`는 기본적으로 pause 시 EGL 컨텍스트를 파괴함. Predictive back 제스처 흐름:

```
1. 제스처 시작 → NavBackStackEntry lifecycle: RESUMED → STARTED
2. Live2DScreen의 LifecycleEventObserver가 ON_PAUSE 수신
3. glView.onPause() 호출
4. GLSurfaceView가 EGL 컨텍스트 파괴 (setPreserveEGLContextOnPause 미설정)
5. 모든 GL 텍스처/버퍼 무효화
6. 제스처 취소 → glView.onResume() → 새 EGL 컨텍스트 생성
7. 모델의 GL 리소스는 이전 컨텍스트에서 생성된 것이라 무효
8. 모델 렌더링 실패 → 화면에 모델 안 보임
```

#### 2. DisposableEffect.onDispose에서의 리소스 정리

기존에는 `Live2DScreen.kt`의 `DisposableEffect.onDispose`에서 `LAppMinimumDelegate.getInstance().onStop()`을 호출하여 Live2D 리소스를 정리했음. Compose의 exit transition이 시작되면 onDispose가 트리거될 수 있어 제스처 취소 시에도 리소스가 파괴되는 문제가 있었음.

### 해결 방법

#### 1. EGL 컨텍스트 보존 설정

`Live2DGLSurfaceView.kt`에 `preserveEGLContextOnPause = true` 추가:

```kotlin
init {
    setEGLContextClientVersion(2)

    // Predictive back 제스처 등으로 GLSurfaceView가 pause/resume될 때
    // EGL 컨텍스트를 유지하여 GL 리소스(텍스처 등)가 무효화되지 않도록 함
    preserveEGLContextOnPause = true

    LAppMinimumDelegate.getInstance().onStart(context as android.app.Activity)
    setRenderer(GLRendererMinimum())
    renderMode = RENDERMODE_CONTINUOUSLY
}
```

#### 2. 리소스 정리 시점 이동

`Live2DScreen.kt`의 `DisposableEffect.onDispose`에서 `onStop()` 호출을 제거하고, `StudioViewModel.onCleared()`로 이동:

**Live2DScreen.kt (변경 후):**
```kotlin
DisposableEffect(lifecycleOwner, glView) {
    val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> glView.onResume()
            Lifecycle.Event.ON_PAUSE -> glView.onPause()
            else -> Unit
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
        // Live2D 리소스 정리는 StudioViewModel.onCleared()에서 수행
        // DisposableEffect.onDispose에서 정리하면 predictive back 제스처 취소 시
        // 리소스가 파괴되어 모델이 사라지는 문제 발생
    }
}
```

**StudioViewModel.kt (변경 후):**
```kotlin
override fun onCleared() {
    super.onCleared()
    faceTracker?.stop()
    faceTracker = null
    // Live2D 리소스 정리 (view, textureManager, model, CubismFramework.dispose)
    // ViewModel은 NavBackStackEntry에 바인딩되므로 predictive back 제스처 중에는
    // cleared되지 않고, 실제 네비게이션 완료 시에만 호출됨
    LAppMinimumDelegate.getInstance().onStop()
}
```

### 기술적 배경

#### NavBackStackEntry Lifecycle vs Compose Lifecycle

Navigation Compose에서 `LocalLifecycleOwner`는 **NavBackStackEntry의 lifecycle**을 반환함:

| 상황 | NavBackStackEntry Lifecycle | ViewModel |
|------|---------------------------|-----------|
| 제스처 시작 | RESUMED → STARTED | 유지 |
| 제스처 취소 | STARTED → RESUMED | 유지 |
| 제스처 완료 (뒤로가기) | DESTROYED | onCleared() 호출 |

#### preserveEGLContextOnPause의 역할

| 설정 | pause 시 동작 | resume 시 동작 |
|-----|--------------|---------------|
| `false` (기본값) | EGL 컨텍스트 파괴, GL 리소스 무효화 | 새 컨텍스트 생성, `onSurfaceCreated()` 호출 |
| `true` | EGL 컨텍스트 유지, GL 리소스 보존 | 기존 컨텍스트 재사용 |

### 설계 결정

| 항목 | 결정 | 이유 |
|------|------|------|
| EGL 컨텍스트 보존 | `preserveEGLContextOnPause = true` | 제스처 중 GL 리소스 보존 필수 |
| 리소스 정리 시점 | `ViewModel.onCleared()` | NavBackStackEntry와 동일한 생명주기, 제스처 중 호출 안 됨 |
| Lifecycle Observer 유지 | `DisposableEffect`에서 `onPause`/`onResume` 계속 호출 | GLSurfaceView의 렌더링 일시정지/재개는 여전히 필요 |

### 관련 파일

| 파일 | 변경 내용 |
|------|----------|
| `Live2DGLSurfaceView.kt` | `preserveEGLContextOnPause = true` 추가 |
| `Live2DScreen.kt` | `onDispose`에서 `onStop()` 호출 제거 |
| `StudioViewModel.kt` | `onCleared()`에 `LAppMinimumDelegate.onStop()` 추가, `LAppMinimumDelegate` import 추가 |

---

## 29. Live2DUiEffect를 통한 렌더링 이벤트 캡슐화 (2026-02-05 업데이트)

### 문제

기존에는 `StudioScreen`에서 `LAppMinimumLive2DManager` 싱글톤을 직접 호출하여 Live2D 조작을 수행했음:

```kotlin
// StudioScreen.kt (기존)
onExpressionFileSelected = { fileName ->
    LAppMinimumLive2DManager.getInstance()
        .startExpression("${uiState.expressionsFolder}/$fileName")
},
onMotionFileSelected = { fileName ->
    LAppMinimumLive2DManager.getInstance()
        .startMotion("${uiState.motionsFolder}/$fileName")
},
onExpressionReset = {
    LAppMinimumLive2DManager.getInstance().clearExpression()
},
onMotionReset = {
    LAppMinimumLive2DManager.getInstance().clearMotion()
},
```

**문제점:**
1. **feature 모듈에서 싱글톤 직접 접근** - 결합도가 높고 테스트 어려움
2. **GL 스레드 안전성 미보장** - `queueEvent` 없이 호출하여 잠재적 동시성 문제
3. **관심사 분리 위반** - UI 레이어에서 렌더링 구현 세부사항을 알고 있음

### 해결 방법

#### 1. Live2DUiEffect 정의 (core:live2d)

Live2D 렌더링 관련 일회성 이벤트를 sealed interface로 정의:

```kotlin
// core/live2d/Live2DUiEffect.kt
sealed interface Live2DUiEffect {
    data class StartExpression(val path: String) : Live2DUiEffect
    data object ClearExpression : Live2DUiEffect
    data class StartMotion(val path: String) : Live2DUiEffect
    data object ClearMotion : Live2DUiEffect
    data object ResetTransform : Live2DUiEffect
}
```

#### 2. Live2DScreen에서 Effect 처리

`effectFlow` 파라미터를 추가하고, GL 스레드에서 안전하게 처리:

```kotlin
// core/live2d/Live2DScreen.kt
@Composable
fun Live2DScreen(
    // ...
    effectFlow: Flow<Live2DUiEffect>? = null,
    // ...
) {
    val glView = remember { Live2DGLSurfaceView(context) }

    LaunchedEffect(effectFlow) {
        effectFlow?.collect { effect ->
            glView.queueEvent {
                val manager = LAppMinimumLive2DManager.getInstance()
                when (effect) {
                    is Live2DUiEffect.StartExpression -> manager.startExpression(effect.path)
                    is Live2DUiEffect.ClearExpression -> manager.clearExpression()
                    is Live2DUiEffect.StartMotion -> manager.startMotion(effect.path)
                    is Live2DUiEffect.ClearMotion -> manager.clearMotion()
                    is Live2DUiEffect.ResetTransform -> manager.resetModelTransform()
                }
            }
        }
    }
}
```

#### 3. ViewModel에서 Channel로 Effect 전송

```kotlin
// StudioViewModel.kt
private val _live2dEffect = Channel<Live2DUiEffect>()
val live2dEffect = _live2dEffect.receiveAsFlow()

private fun startExpression(path: String) {
    _live2dEffect.trySend(Live2DUiEffect.StartExpression(path))
}

private fun clearExpression() {
    _live2dEffect.trySend(Live2DUiEffect.ClearExpression)
}

private fun startMotion(path: String) {
    _live2dEffect.trySend(Live2DUiEffect.StartMotion(path))
}

private fun clearMotion() {
    _live2dEffect.trySend(Live2DUiEffect.ClearMotion)
}

private fun resetTransform() {
    _live2dEffect.trySend(Live2DUiEffect.ResetTransform)
}
```

#### 4. StudioUiIntent 확장

```kotlin
// StudioUiIntent.kt
sealed interface StudioUiIntent {
    // ... 기존 Intent ...
    data class StartExpression(val path: String) : StudioUiIntent
    data object ClearExpression : StudioUiIntent
    data class StartMotion(val path: String) : StudioUiIntent
    data object ClearMotion : StudioUiIntent
    data object ResetTransform : StudioUiIntent
}
```

#### 5. StudioScreen에서 Intent 사용

```kotlin
// StudioScreen.kt (변경 후)
StudioScreenContent(
    // ...
    onIntent = viewModel::onIntent,
    modelViewContent = {
        Live2DScreen(
            // ...
            effectFlow = viewModel.live2dEffect,
        )
    },
)

// Dialog에서 Intent 사용
onFileSelected = { fileName ->
    if (fileName == resetLabel) {
        onIntent(StudioUiIntent.ClearExpression)
    } else {
        onIntent(StudioUiIntent.StartExpression("${uiState.expressionsFolder}/$fileName"))
    }
    onIntent(StudioUiIntent.DismissDialog)
}
```

### 아키텍처 결정

#### 일회성 이벤트 처리 방식 비교

| 방식 | 장점 | 단점 |
|------|------|------|
| State 트리거 (Int 카운터) | 간단한 구현 | 의미 불명확, LaunchedEffect 오용 |
| Boolean 토글 | 간단 | 의미 불명확 |
| **Channel/Flow** | 일회성 이벤트에 적합, 명확한 의미 | 약간의 보일러플레이트 |

#### Live2D 조작의 레이어 배치

| 레이어 | 적합성 | 이유 |
|--------|--------|------|
| UseCase (Domain) | ❌ | Domain은 순수 Kotlin, Android/OpenGL 의존성 불가 |
| ViewModel | △ | ViewModel은 뷰 조작을 직접 하면 안 됨 |
| Screen | △ | 싱글톤 직접 접근은 결합도 높음 |
| **Live2DScreen** | ✅ | 렌더링 로직 캡슐화, GL 스레드 안전성 보장 |

Live2D 조작(startExpression, startMotion 등)은 **렌더링/뷰 레벨 작업**이므로:
- ViewModel: "언제" 트리거할지 결정 → Effect 전송
- Live2DScreen: "어떻게" 처리할지 → `queueEvent`로 GL 스레드에서 실행

### 데이터 흐름

```
사용자 액션 (버튼 클릭)
    ↓
StudioUiIntent (StartExpression, ClearMotion, ResetTransform 등)
    ↓
StudioViewModel.onIntent() → _live2dEffect.trySend()
    ↓
Live2DScreen.effectFlow.collect()
    ↓
glView.queueEvent { manager.xxx() }  // GL 스레드에서 실행
```

### 관련 파일

| 파일 | 변경 내용 |
|------|----------|
| `core/live2d/Live2DUiEffect.kt` | 새 파일 - Live2D 일회성 이벤트 정의 |
| `core/live2d/Live2DScreen.kt` | `effectFlow` 파라미터 추가, Effect 처리 로직 |
| `core/live2d/Live2DGLSurfaceView.kt` | 미사용 `resetTransform()` 함수 삭제 |
| `feature/studio/StudioViewModel.kt` | `_live2dEffect` Channel 추가, Live2D 액션 함수들 |
| `feature/studio/StudioUiIntent.kt` | Expression/Motion/Reset Intent 추가 |
| `feature/studio/StudioScreen.kt` | 싱글톤 직접 접근 제거, Intent 기반으로 변경 |
| `feature/studio/res/values/strings.xml` | `studio_reset` 문자열 추가 |

---

## 30. 핀치 줌에서 싱글 터치 전환 시 위치 튀는 현상 수정 (2026-02-05 업데이트)

### 문제

스튜디오 화면에서 확대/축소(핀치 줌)와 이동 기능을 동시에 사용할 때, 두 손가락 중 하나를 먼저 떼면 캐릭터의 위치가 급격하게 튀는 현상이 발생.

### 원인 분석

기존 터치 이벤트 처리 흐름:

```
1. 두 손가락 핀치 줌 중 (pointerCount = 2)
   - scaleGestureDetector만 처리, 바로 return

2. 한 손가락을 뗌 (ACTION_POINTER_UP)
   - 처리되지 않음 ← 문제!

3. 남은 한 손가락으로 ACTION_MOVE
   - lastTouchX/Y는 핀치 시작 전 위치 그대로
   - 남은 손가락 위치와 큰 차이 발생
   - deltaX/Y가 크게 계산됨 → 위치 튀는 현상
```

**핵심 문제점:**
1. `ACTION_POINTER_UP` 이벤트가 처리되지 않음
2. 핀치 → 싱글 터치 전환 시 `lastTouchX/Y`가 갱신되지 않음

### 해결 방법

#### 1. ACTION_POINTER_UP 처리 추가

핀치 줌에서 한 손가락을 뗄 때, 남은 손가락의 위치로 `lastTouchX/Y`를 갱신:

```kotlin
MotionEvent.ACTION_POINTER_UP -> {
    // 핀치에서 한 손가락 뗄 때, 남은 손가락 위치로 갱신하여 튀는 현상 방지
    if (event.pointerCount > 1) {
        val remainingPointerIndex = if (event.actionIndex == 0) 1 else 0
        lastTouchX = event.getX(remainingPointerIndex)
        lastTouchY = event.getY(remainingPointerIndex)
    }
}
```

#### 2. 핀치 진행 중 드래그 처리 차단

`ScaleGestureDetector.isInProgress`를 체크하여 핀치 줌이 진행되는 동안 드래그 처리를 완전히 차단:

```kotlin
// 핀치 줌 감지기에 항상 이벤트 전달 (줌 활성화 시)
if (isZoomEnabled) {
    scaleGestureDetector.onTouchEvent(event)
}

// 핀치 줌 진행 중에는 드래그 처리 차단
if (scaleGestureDetector.isInProgress) {
    return true
}
```

#### 3. 두 손가락 이상일 때 이동 처리 건너뜀

`ACTION_MOVE`에서 `pointerCount >= 2`일 때는 이동 처리를 하지 않음:

```kotlin
MotionEvent.ACTION_MOVE -> {
    // 두 손가락 이상일 때는 이동 처리 안함 (핀치 줌 전용)
    if (event.pointerCount >= 2) {
        return true
    }
    // ... 이동 처리 로직
}
```

### 수정 후 터치 이벤트 흐름

```
1. 두 손가락 핀치 줌 중 (pointerCount = 2)
   - scaleGestureDetector.onTouchEvent() 호출
   - isInProgress = true → 바로 return

2. 한 손가락을 뗌 (ACTION_POINTER_UP)
   - 남은 손가락 인덱스 계산 (actionIndex가 0이면 1, 아니면 0)
   - lastTouchX/Y를 남은 손가락 위치로 갱신

3. 남은 한 손가락으로 ACTION_MOVE
   - lastTouchX/Y가 이미 현재 손가락 근처 위치
   - deltaX/Y가 작게 계산됨 → 부드러운 전환
```

### 아키텍처 결정

| 문제 | 해결 방식 | 대안 |
|------|----------|------|
| 포인터 전환 시 위치 갱신 | `ACTION_POINTER_UP`에서 남은 포인터 위치 저장 | 타이머로 무시 (비권장) |
| 핀치 중 드래그 차단 | `isInProgress` 체크 | pointerCount만 체크 (불완전) |

### 관련 파일

| 파일 | 변경 내용 |
|------|----------|
| `core/live2d/Live2DGLSurfaceView.kt` | `ACTION_POINTER_UP` 처리 추가, `isInProgress` 체크, pointerCount 조건 추가 |
