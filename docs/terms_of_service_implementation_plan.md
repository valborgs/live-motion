# 이용약관 동의 기능 작업계획서

## 1. 기능 개요

앱 최초 실행 시 이용약관 화면을 표시하고, 사용자 동의를 받아 로컬(DataStore)과 원격(Firebase Firestore)에 저장하는 기능.

### 사용자 플로우

```
앱 실행 → Splash(Intro) → [최초 실행?] → 이용약관 화면 → 동의 → Title 화면
                              ↓ (이미 동의함)
                         Title 화면 (바로 이동)
```

### 저장 데이터

| 필드 | 설명 |
|------|------|
| `userId` | UUID v4 (앱 최초 실행 시 생성) |
| `agreedAt` | 동의 시각 (epoch millis) |
| `tosVersion` | 이용약관 버전 (향후 약관 변경 대응) |

---

## 2. 영향받는 모듈 및 변경 범위

### 신규 의존성 추가

| 라이브러리 | 용도 | 추가 위치 |
|-----------|------|----------|
| `androidx.datastore:datastore-preferences` | 로컬 동의 상태 저장 | `core:storage`, `libs.versions.toml` |
| `com.google.firebase:firebase-bom` | Firebase BOM | `app`, `libs.versions.toml` |
| `com.google.firebase:firebase-firestore-ktx` | Firestore | `data`, `libs.versions.toml` |
| `com.google.gms:google-services` (plugin) | Firebase 연동 | 루트 `build.gradle.kts`, `app` |

### 모듈별 변경 요약

| 모듈 | 변경 내용 | 신규/수정 |
|------|----------|----------|
| `gradle/libs.versions.toml` | DataStore, Firebase 의존성 선언 | 수정 |
| `build.gradle.kts` (루트) | google-services 플러그인 추가 | 수정 |
| `domain` | `UserConsent` 데이터 클래스, `IConsentRepository` 인터페이스 추가 | 수정 |
| `core:storage` | `ConsentLocalDataSource` (DataStore 기반) 추가 | 수정 |
| `data` | `ConsentRepositoryImpl` 추가, Hilt 모듈 수정 | 수정 |
| `core:navigation` | `NavKey.TermsOfService` 추가 | 수정 |
| `feature:home` | `TermsOfServiceScreen` 추가, Hilt/ViewModel 의존성 추가 | 수정 |
| `app` | NavHost에 이용약관 라우트 추가, `google-services.json` 배치 | 수정 |

---

## 3. 상세 구현 계획

### Phase 1: 의존성 및 인프라 설정

#### 1-1. `gradle/libs.versions.toml` — 의존성 선언 추가

```toml
[versions]
datastore = "1.1.4"
firebaseBom = "33.9.0"
googleServices = "4.4.2"

[libraries]
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-firestore = { group = "com.google.firebase", name = "firebase-firestore-ktx" }

[plugins]
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

#### 1-2. 루트 `build.gradle.kts` — google-services 플러그인 등록

```kotlin
alias(libs.plugins.google.services) apply false
```

#### 1-3. `app/build.gradle.kts` — google-services 플러그인 적용 및 Firebase 의존성

```kotlin
plugins {
    // ... 기존 플러그인
    alias(libs.plugins.google.services)
}

dependencies {
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
}
```

#### 1-4. Firebase 프로젝트 설정

- Firebase Console에서 프로젝트 생성 (수동 작업)
- `google-services.json`을 `app/` 디렉토리에 배치 (수동 작업)
- `.gitignore`에 `google-services.json` 추가 여부 결정

---

### Phase 2: Domain 레이어

#### 2-1. `domain/model/UserConsent.kt` — 데이터 클래스

```kotlin
package org.comon.domain.model

data class UserConsent(
    val userId: String,        // UUID v4
    val tosVersion: String,    // 이용약관 버전 (예: "1.0")
    val agreedAt: Long         // epoch millis
)
```

#### 2-2. `domain/repository/IConsentRepository.kt` — 리포지토리 인터페이스

```kotlin
package org.comon.domain.repository

import org.comon.domain.model.UserConsent

interface IConsentRepository {
    /** 로컬에 저장된 동의 정보 조회 (없으면 null) */
    suspend fun getLocalConsent(): UserConsent?

    /** 동의 정보를 로컬 + 원격에 저장 */
    suspend fun saveConsent(consent: UserConsent)
}
```

> domain 모듈은 pure Kotlin이므로 Android/Firebase 의존성 없이 인터페이스만 정의.

---

### Phase 3: Storage 레이어 (core:storage)

#### 3-1. `core/storage/build.gradle.kts` — DataStore 의존성 추가

```kotlin
dependencies {
    implementation(libs.androidx.datastore.preferences)
}
```

#### 3-2. `core/storage/.../ConsentLocalDataSource.kt` — DataStore 기반 로컬 저장소

```kotlin
package org.comon.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.consentDataStore by preferencesDataStore(name = "user_consent")

class ConsentLocalDataSource(private val context: Context) {

    companion object {
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_TOS_VERSION = stringPreferencesKey("tos_version")
        private val KEY_AGREED_AT = longPreferencesKey("agreed_at")
        private val KEY_HAS_CONSENTED = booleanPreferencesKey("has_consented")
    }

    /** 동의 여부 확인 */
    suspend fun hasConsented(): Boolean {
        return context.consentDataStore.data.map { prefs ->
            prefs[KEY_HAS_CONSENTED] ?: false
        }.first()
    }

    /** 저장된 userId 조회 (없으면 null) */
    suspend fun getUserId(): String? {
        return context.consentDataStore.data.map { prefs ->
            prefs[KEY_USER_ID]
        }.first()
    }

    /** 동의 정보 저장 */
    suspend fun saveConsent(userId: String, tosVersion: String, agreedAt: Long) {
        context.consentDataStore.edit { prefs ->
            prefs[KEY_USER_ID] = userId
            prefs[KEY_TOS_VERSION] = tosVersion
            prefs[KEY_AGREED_AT] = agreedAt
            prefs[KEY_HAS_CONSENTED] = true
        }
    }
}
```

#### 3-3. Hilt 모듈 (`core/storage` 내 기존 DI 구조에 추가)

`ConsentLocalDataSource`를 Hilt `@Provides`로 제공.

---

### Phase 4: Data 레이어

#### 4-1. `data/build.gradle.kts` — Firebase Firestore 의존성 추가

```kotlin
dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
}
```

#### 4-2. `data/repository/ConsentRepositoryImpl.kt` — 리포지토리 구현체

```kotlin
package org.comon.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.comon.domain.model.UserConsent
import org.comon.domain.repository.IConsentRepository
import org.comon.storage.ConsentLocalDataSource
import javax.inject.Inject

class ConsentRepositoryImpl @Inject constructor(
    private val localDataSource: ConsentLocalDataSource,
    private val firestore: FirebaseFirestore
) : IConsentRepository {

    override suspend fun getLocalConsent(): UserConsent? {
        if (!localDataSource.hasConsented()) return null
        val userId = localDataSource.getUserId() ?: return null
        // 로컬에서 복원 (상세 데이터는 필요 시 확장)
        return UserConsent(userId = userId, tosVersion = "", agreedAt = 0L)
    }

    override suspend fun saveConsent(consent: UserConsent) {
        // 1. 로컬 저장 (DataStore)
        localDataSource.saveConsent(
            userId = consent.userId,
            tosVersion = consent.tosVersion,
            agreedAt = consent.agreedAt
        )

        // 2. 원격 저장 (Firestore)
        val data = mapOf(
            "userId" to consent.userId,
            "tosVersion" to consent.tosVersion,
            "agreedAt" to consent.agreedAt
        )
        firestore.collection("consents")
            .document(consent.userId)
            .set(data)
            .await()
    }
}
```

#### 4-3. `data/di/RepositoryModule.kt` — Hilt 바인딩 추가

```kotlin
@Provides
@Singleton
fun provideFirebaseFirestore(): FirebaseFirestore {
    return FirebaseFirestore.getInstance()
}

@Provides
@Singleton
fun provideConsentRepository(
    localDataSource: ConsentLocalDataSource,
    firestore: FirebaseFirestore
): IConsentRepository {
    return ConsentRepositoryImpl(localDataSource, firestore)
}
```

---

### Phase 5: Navigation 레이어

#### 5-1. `core/navigation/NavKey.kt` — 라우트 추가

```kotlin
@Serializable
data object TermsOfService : NavKey
```

#### 5-2. `core/navigation/Navigator.kt` — 메서드 추가

```kotlin
fun navigateToTermsOfService()
```

#### 5-3. `app/.../AppNavigatorImpl.kt` — 구현

```kotlin
override fun navigateToTermsOfService() {
    navController.navigate(NavKey.TermsOfService) {
        popUpTo(NavKey.Intro) { inclusive = true }
    }
}
```

---

### Phase 6: Feature 레이어 (feature:home)

#### 6-1. `feature/home/build.gradle.kts` — 의존성 추가

```kotlin
plugins {
    // ... 기존
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(project(":domain"))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Lifecycle (collectAsStateWithLifecycle)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
```

#### 6-2. `feature/home/.../TermsOfServiceViewModel.kt` — MVI ViewModel

```kotlin
package org.comon.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.comon.domain.model.UserConsent
import org.comon.domain.repository.IConsentRepository
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TermsOfServiceViewModel @Inject constructor(
    private val consentRepository: IConsentRepository
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
        val scrolledToBottom: Boolean = false // 약관 끝까지 스크롤 여부
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<TermsOfServiceUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    fun onIntent(intent: TermsOfServiceUiIntent) {
        when (intent) {
            is TermsOfServiceUiIntent.Agree -> agree()
            is TermsOfServiceUiIntent.ScrolledToBottom -> {
                _uiState.update { it.copy(scrolledToBottom = true) }
            }
        }
    }

    private fun agree() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val consent = UserConsent(
                    userId = UUID.randomUUID().toString(),
                    tosVersion = TOS_VERSION,
                    agreedAt = System.currentTimeMillis()
                )
                consentRepository.saveConsent(consent)
                _uiEffect.send(TermsOfServiceUiEffect.NavigateToTitle)
            } catch (e: Exception) {
                // Firestore 실패 시에도 로컬 저장은 완료 → 진행 허용
                _uiEffect.send(TermsOfServiceUiEffect.NavigateToTitle)
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    companion object {
        const val TOS_VERSION = "1.0"
    }
}
```

#### 6-3. `feature/home/.../TermsOfServiceUiIntent.kt`

```kotlin
package org.comon.home

sealed interface TermsOfServiceUiIntent {
    data object Agree : TermsOfServiceUiIntent
    data object ScrolledToBottom : TermsOfServiceUiIntent
}
```

#### 6-4. `feature/home/.../TermsOfServiceUiEffect.kt`

```kotlin
package org.comon.home

sealed class TermsOfServiceUiEffect {
    data object NavigateToTitle : TermsOfServiceUiEffect()
}
```

#### 6-5. `feature/home/.../TermsOfServiceScreen.kt` — UI

Screen/Content 분리 패턴을 따름:

```kotlin
// TermsOfServiceScreen (public) — ViewModel 연결
@Composable
fun TermsOfServiceScreen(
    onAgreed: () -> Unit,
    viewModel: TermsOfServiceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is TermsOfServiceUiEffect.NavigateToTitle -> onAgreed()
            }
        }
    }

    TermsOfServiceScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent
    )
}

// TermsOfServiceScreenContent (private) — 순수 UI
@Composable
private fun TermsOfServiceScreenContent(
    uiState: TermsOfServiceViewModel.UiState,
    onIntent: (TermsOfServiceUiIntent) -> Unit
) {
    // 스크롤 가능한 약관 텍스트 + 하단 동의 버튼
}
```

**UI 구성 요소:**
- 상단: "이용약관" 타이틀
- 중앙: 스크롤 가능한 약관 본문 (LazyColumn 또는 verticalScroll)
- 하단: "동의하고 시작하기" 버튼 (로딩 중 CircularProgressIndicator)
- 선택사항: 끝까지 스크롤해야 버튼 활성화

---

### Phase 7: App 모듈 — 네비게이션 통합

#### 7-1. `MainActivity.kt` — 분기 로직

Intro 화면의 `onTimeout`에서 동의 여부를 확인하여 분기:

```kotlin
composable<NavKey.Intro> {
    val consentRepository: IConsentRepository = ... // Hilt 주입
    IntroScreen(
        onTimeout = {
            // 동의 여부에 따라 분기
            if (hasConsented) {
                navigator.navigateToTitle()
            } else {
                navigator.navigateToTermsOfService()
            }
        }
    )
}

composable<NavKey.TermsOfService> {
    TermsOfServiceScreen(
        onAgreed = { navigator.navigateToTitle() }
    )
}
```

> 동의 여부 확인은 `IntroScreen`의 1초 대기 동안 비동기로 수행하여 추가 지연 없이 처리.

**구현 방식 선택지:**

- **Option A**: `MainActivity`에서 `ConsentLocalDataSource`를 직접 주입받아 확인
- **Option B (권장)**: `IntroViewModel`을 새로 만들어 동의 여부를 체크하고 적절한 navigation effect를 발행

---

## 4. 파일 생성/수정 목록

### 신규 파일

| 파일 경로 | 설명 |
|----------|------|
| `domain/src/main/java/org/comon/domain/model/UserConsent.kt` | 동의 데이터 클래스 |
| `domain/src/main/java/org/comon/domain/repository/IConsentRepository.kt` | 리포지토리 인터페이스 |
| `core/storage/src/main/java/org/comon/storage/ConsentLocalDataSource.kt` | DataStore 기반 로컬 저장소 |
| `data/src/main/java/org/comon/data/repository/ConsentRepositoryImpl.kt` | 리포지토리 구현체 |
| `feature/home/src/main/java/org/comon/home/TermsOfServiceScreen.kt` | 이용약관 화면 |
| `feature/home/src/main/java/org/comon/home/TermsOfServiceViewModel.kt` | ViewModel (MVI) |
| `feature/home/src/main/java/org/comon/home/TermsOfServiceUiIntent.kt` | Intent 정의 |
| `feature/home/src/main/java/org/comon/home/TermsOfServiceUiEffect.kt` | Effect 정의 |

### 수정 파일

| 파일 경로 | 변경 내용 |
|----------|----------|
| `gradle/libs.versions.toml` | DataStore, Firebase 의존성 선언 |
| `build.gradle.kts` (루트) | google-services 플러그인 등록 |
| `app/build.gradle.kts` | google-services 적용, Firebase BOM 추가 |
| `data/build.gradle.kts` | Firebase Firestore 의존성 추가 |
| `core/storage/build.gradle.kts` | DataStore 의존성 추가 |
| `feature/home/build.gradle.kts` | Hilt, Lifecycle, domain 의존성 추가 |
| `core/navigation/.../NavKey.kt` | `TermsOfService` 라우트 추가 |
| `core/navigation/.../Navigator.kt` | `navigateToTermsOfService()` 추가 |
| `app/.../AppNavigatorImpl.kt` | `navigateToTermsOfService()` 구현 |
| `app/.../MainActivity.kt` | NavHost에 TermsOfService 라우트 추가, Intro 분기 로직 |
| `data/di/RepositoryModule.kt` | `IConsentRepository` 바인딩 추가 |
| `core/storage` Hilt 모듈 | `ConsentLocalDataSource` 제공 |

---

## 5. Firestore 컬렉션 구조

```
consents/
  └── {userId}/
        ├── userId: string
        ├── tosVersion: string
        └── agreedAt: number (epoch millis)
```

**Firestore 보안 규칙 (Firebase Console에서 설정):**

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /consents/{userId} {
      // 쓰기만 허용 (읽기 불필요)
      allow create: if true;
      allow read, update, delete: if false;
    }
  }
}
```

---

## 6. 에러 핸들링 전략

| 시나리오 | 처리 방법 |
|---------|----------|
| Firestore 저장 실패 (네트워크 오류) | 로컬 저장은 성공 → Title 화면으로 진행. Firestore는 SDK의 오프라인 캐시가 연결 복구 시 자동 동기화 |
| DataStore 저장 실패 | 극히 드문 경우. 다음 실행 시 다시 이용약관 표시 |
| 이미 동의한 사용자 | Intro 화면에서 DataStore 확인 후 바로 Title로 이동 |

---

## 7. 구현 순서 (권장)

1. **의존성 설정** — `libs.versions.toml`, 각 모듈 `build.gradle.kts`
2. **Firebase 프로젝트 설정** — Console에서 생성, `google-services.json` 배치
3. **Domain 레이어** — `UserConsent`, `IConsentRepository`
4. **Storage 레이어** — `ConsentLocalDataSource`
5. **Data 레이어** — `ConsentRepositoryImpl`, Hilt 모듈
6. **Navigation** — `NavKey.TermsOfService`, Navigator 메서드
7. **Feature UI** — TermsOfServiceScreen, ViewModel, Intent, Effect
8. **App 통합** — MainActivity 네비게이션 분기
9. **테스트** — 빌드 확인 및 수동 테스트

---

## 8. 미결 사항 (사용자 확인 필요)

- [ ] 이용약관 본문 텍스트 내용
- [ ] Firebase 프로젝트 생성 및 `google-services.json` 준비 여부
- [ ] 약관을 끝까지 스크롤해야 동의 버튼 활성화할지 여부
- [ ] "동의하지 않음" 버튼 필요 여부 (동의 거부 시 앱 종료?)
- [ ] Firestore 보안 규칙 설정 방식
