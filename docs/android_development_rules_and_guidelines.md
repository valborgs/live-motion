# 📱 Android Development Rules & Guidelines (2026 Edition)

이 문서는 프로젝트의 일관성, 유지보수성, 확장성을 극대화하기 위한 코드 생성 및 리뷰 가이드라인입니다. 모든 코드 생성 시 아래의 **8대 원칙**을 엄격히 준수하세요.

---

## 1. Project Architecture & Modularization
* **Single Activity Architecture (SAA):** 모든 화면 흐름은 `MainActivity` 하나에서 제어하며, Compose 기반의 내비게이션을 활용한다.
* **Feature-based Multi-Module:**
    * `:app`: 모든 모듈의 결합점. 의존성 주입(Hilt)의 본체이자 전역 설정을 담당.
    * `:feature:[name]`: 개별 기능 단위. UI(Compose)와 ViewModel을 포함하며, 타 feature 모듈을 직접 참조하지 않음.
    * `:domain:[name]`: 비즈니스 로직(UseCase, Model, Repository Interface). Pure Kotlin 모듈 지향.
    * `:data:[name]`: 데이터 소스(API, DB) 관리 및 Repository 구현체.
    * `:core:[ui|navigation|network|common]`: 앱 전역에서 재사용되는 횡단 관심사 모듈.

## 2. Clean Architecture Principles
* **Dependency Rule:** 의존성은 항상 `Outer(Data/UI) -> Inner(Domain)` 방향으로만 흐른다.
* **UseCase Centric:** 하나의 UseCase는 하나의 비즈니스 행위만 책임진다. 클래스명은 `DoSomethingUseCase` 형식을 사용하며 `invoke` 연산자를 활용한다.
* **Interface Segregation:** Domain 레이어는 Interface만 정의하고, 실질적인 구현은 Data 레이어에서 담당하여 결합도를 낮춘다.

## 3. UI Layer (Jetpack Compose & MVI)
* **Unidirectional Data Flow (UDF):** 모든 상태 관리 및 이벤트 처리는 MVI 패턴을 따른다.
    * `UiState`: 화면의 모든 상태를 나타내는 단일 불변(Immutable) 데이터 클래스.
    * `UiIntent` (or `Action`): 사용자의 액션을 정의하는 Sealed Interface.
    * `UiEffect` (or `SideEffect`): 내비게이션, 스낵바 등 1회성 이벤트를 위한 `Channel` 기반 흐름.
* **State Hoisting:** Composable은 최대한 Stateless하게 유지하며, 상태는 ViewModel에서 Hoisting한다.
* **Preview Definition:** 모든 UI 컴포넌트는 다크 모드와 라이트 모드를 포함한 `Preview`를 필수 작성한다.



## 4. Navigation (Navigation3 Implementation)
* **Navigation3 Standard:** 최신 `Navigation3` 라이브러리를 사용하며, 모든 경로는 Kotlin `Serializable` 객체로 정의한다.
* **Decoupled Routing:** * `:core:navigation` 모듈에 Route 객체와 Navigator 인터페이스를 정의한다.
    * Feature 모듈은 인터페이스만 호출하고, 실제 구현은 `:app` 모듈의 `AppNavigatorImpl`에서 처리한다.
    * `NavGraphBuilder` 확장 함수를 통해 각 모듈의 화면 진입점을 노출한다.

## 5. Dependency Injection (Hilt)
* **Standard:** 모든 의존성 주입은 `Hilt`를 사용한다.
* **Interface Binding:** `@Binds`를 사용하여 인터페이스와 구현체를 분리 주입한다.
* **ViewModel Injection:** `@HiltViewModel`을 사용하여 ViewModel의 생명주기를 관리한다.

## 6. Clean Code & Flow Control
* **Naming Convention:** * UseCase: `GetUserInfoUseCase`
    * Repository: `UserRepository`, `UserRepositoryImpl`
    * UI State: `HomeUiState`
* **Coroutines & Flow:** 비동기 작업은 `Coroutines`를 사용하며, 데이터 스트림은 `StateFlow` 및 `SharedFlow`를 활용한다.
* **Immutability:** 모든 데이터 클래스는 `val`을 사용하며, 상태 변경 시 `copy()`를 활용한다.

## 7. Error Handling & Data Management
* **Result Wrapper:** 모든 Repository와 UseCase의 반환값은 `Result<T>` 혹은 커스텀 `Resource<T>` 클래스로 래핑한다.
* **Single Source of Truth:** 데이터는 항상 로컬 DB(Room)나 메모리 캐시를 거쳐 UI로 전달되는 것을 지향한다.
* **Kotlinx Serialization:** 모든 JSON 직렬화는 `kotlinx-serialization`을 사용한다.

## 8. Development Etiquette
* **No Hardcoding:** 모든 문자열은 `strings.xml` 혹은 Compose 전용 `String Resources`를 사용한다.
* **SOLID:** 특히 단일 책임 원칙(SRP)과 의존성 역전 원칙(DIP)을 위배하는 코드를 작성하지 않는다.
* **Documentation:** 복잡한 비즈니스 로직이나 UseCase에는 반드시 KDoc 주석을 추가한다.