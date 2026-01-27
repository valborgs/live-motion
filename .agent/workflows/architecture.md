---
description: LiveMotion 프로젝트 클린 아키텍처 구조 안내
---

# LiveMotion 프로젝트 구조

## 모듈 구조

```
LiveMotion/
├── app/                    # MainActivity만 포함, 네비게이션 담당
├── domain/                 # 도메인 모델 (순수 Kotlin)
│   └── model/             # FacePose 등 데이터 클래스
├── core/
│   ├── tracking/          # 얼굴 추적 (FaceTracker, FaceToLive2DMapper)
│   ├── live2d/            # Live2D 렌더링 (SDK 래퍼 포함)
│   ├── ui/                # 공통 테마/컴포넌트
│   └── navigation/        # NavKey 등 네비게이션 관련
├── feature/
│   └── studio/            # 스튜디오 화면 (StudioScreen, ModelSelectScreen)
└── live2d/framework/      # Live2D SDK Framework (외부 라이브러리)
```

---

## 의존성 규칙

```
app → feature:* → core:* → domain
         ↓           ↓
      core:*      domain
```

- `domain`: 의존성 없음 (순수 Kotlin)
- `core:*`: domain만 의존 가능
- `feature:*`: core:*, domain 의존 가능
- `app`: 모든 모듈 의존 가능

---

## 코드 작성 규칙

1. **새로운 데이터 클래스** → `domain/model/` 패키지에 작성
2. **새로운 화면 (Screen)** → `feature:*` 모듈에 작성
3. **공통 유틸리티/헬퍼** → `core:common` 또는 해당 `core:*` 모듈에 작성
4. **UI 컴포넌트 (재사용)** → `core:ui/component/` 패키지에 작성
5. **Live2D 관련 코드** → `core:live2d` 모듈에 작성
6. **얼굴 추적 관련 코드** → `core:tracking` 모듈에 작성
7. **app 모듈에는 코드 추가 금지** (네비게이션 설정만)

---

## 패키지 네이밍 규칙

| 모듈 | 패키지 |
|------|--------|
| domain | `org.comon.domain.*` |
| core:tracking | `org.comon.tracking.*` |
| core:live2d | `org.comon.live2d.*` |
| core:ui | `org.comon.ui.*` |
| core:navigation | `org.comon.navigation.*` |
| feature:studio | `org.comon.studio.*` |

---

## 새 기능 추가 시 체크리스트

1. [ ] 해당 기능이 어느 모듈에 속하는지 확인
2. [ ] 올바른 패키지에 파일 생성
3. [ ] build.gradle.kts에 필요한 의존성 추가
4. [ ] 모듈 간 의존성 규칙 준수 확인
