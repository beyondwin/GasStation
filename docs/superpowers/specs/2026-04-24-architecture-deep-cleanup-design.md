# Architecture Deep Cleanup Design

**Date:** 2026-04-24

## Goal

이전 clean-architecture-boundary-refactor(2026-04-19)에서 domain:location 추출, settings use case 도입, StationQuery 정리가 완료되었다. 이번 작업은 그때 다루지 않은 **나머지 아키텍처 위반과 코드 품질 문제**를 해결한다.

핵심 목표는 다음 다섯 가지다.

1. `core:*` 모듈이 `domain:station`에 역의존하는 문제를 해소한다.
2. `domain:settings`가 `domain:station`에 의존하는 커플링을 제거한다.
3. `StationListViewModel`(522줄)의 책임을 분리한다.
4. 일시적 네트워크 실패에 대한 재시도 정책을 도입한다.
5. 에러 진단을 위한 구조화된 로깅을 추가한다.

사용자 동작은 바꾸지 않는다. 재시도 정책은 자동 1회 재시도로, 사용자에게는 성공률 향상으로만 체감된다.

## Scope

이번 설계는 다음 변경을 포함한다.

- 공유 enum 6개를 `domain:station`에서 `core:model`로 이동
- `core:datastore`, `core:network`, `core:designsystem`의 `domain:station` 의존 제거
- `domain:settings`의 `domain:station` 의존 제거
- `StationListViewModel` 책임 분리 (위치 상태 머신, 검색 오케스트레이션 추출)
- `data:station`에 1회 자동 재시도 정책 추가
- `domain:station`에 구조화된 에러 로깅 계약 추가

이번 설계는 다음을 포함하지 않는다.

- 권한/GPS UI 흐름 변경
- Room, Retrofit, DataStore 저장 포맷 변경
- 새 화면이나 기능 추가
- 새 Gradle 모듈 생성 (기존 `core:model` 확장으로 해결)
- 테스트 프레임워크 변경

---

## Part 1: 공유 Enum을 core:model로 이동

### Current Problem

`core:datastore`, `core:network`, `core:designsystem` 세 모듈이 `domain:station`에 의존한다. Clean Architecture에서 core 레이어는 domain보다 아래에 위치하므로 domain을 참조해서는 안 된다.

현재 의존 관계:

```
core:datastore   ──→ domain:settings ──→ domain:station
core:network     ──→ domain:station
core:designsystem ──→ domain:station
```

원인은 `domain:station/model/`에 있는 6개 enum이 실질적으로 **공유 어휘(shared vocabulary)** 역할을 하기 때문이다. 이 enum들은 도메인 비즈니스 로직이 아니라, 검색 조건과 설정을 표현하는 값 타입이다.

### Enums to Move

| Enum | 현재 위치 | 영향 파일 수 | 비고 |
|------|----------|------------|------|
| `Brand` | `domain:station/model/Brand.kt` | 3 | 10개 주유소 브랜드 enum |
| `BrandFilter` | `domain:station/model/BrandFilter.kt` | 25 | Brand 참조, `matches()` 메서드 포함 |
| `FuelType` | `domain:station/model/FuelType.kt` | 34 | 5개 유종 |
| `MapProvider` | `domain:station/model/MapProvider.kt` | 23 | 3개 외부 지도 앱 |
| `SearchRadius` | `domain:station/model/SearchRadius.kt` | 29 | 3개 반경, `meters` 프로퍼티 포함 |
| `SortOrder` | `domain:station/model/SortOrder.kt` | 24 | 2개 정렬 기준 |

총 138개 파일의 import 경로가 변경된다.

### Target Location

`core:model`의 새 패키지 `com.gasstation.core.model`에 둔다.

현재 `core:model`은 `Coordinates`, `DistanceMeters`, `MoneyWon` 세 가지 값 객체만 가지고 있다. 이동 대상 enum들도 본질적으로 값 타입이므로 같은 패키지에 자연스럽게 들어간다.

### Migration Strategy

**Phase 1: 파일 이동과 패키지 변경**

6개 파일을 `domain/station/src/main/kotlin/com/gasstation/domain/station/model/`에서 `core/model/src/main/kotlin/com/gasstation/core/model/`로 이동한다. 패키지 선언을 `com.gasstation.core.model`로 변경한다.

**Phase 2: import 경로 일괄 변경**

모든 소비자 파일에서 import 경로를 변경한다:
```
- import com.gasstation.domain.station.model.Brand
+ import com.gasstation.core.model.Brand
```

6개 enum에 대해 동일한 패턴으로 적용한다.

**Phase 3: build.gradle.kts 의존성 정리**

| 모듈 | 변경 |
|------|------|
| `core:datastore` | `domain:station` 의존 제거 (이미 `core:model`을 간접 참조 가능) |
| `core:network` | `domain:station` 의존 제거, `core:model` 의존 유지 (이미 있음) |
| `core:designsystem` | `domain:station` 의존 제거, `core:model` 의존 추가 |
| `domain:settings` | `domain:station` 의존 제거 (이미 `core:model` 의존 있음) |
| `domain:station` | 변경 없음 (이미 `core:model` 의존, enum은 빠지지만 참조는 `core:model`을 통해 유지) |

**Phase 4: 호환성 유지 판단**

`domain:station`에서 `core:model`을 `api()`로 노출하고 있다면 기존 소비자는 자동으로 접근 가능하다. `implementation()`이라면 명시적으로 `core:model` 의존을 추가해야 한다.

현재 `domain:station/build.gradle.kts`는 `implementation(project(":core:model"))`이므로, `domain:station`을 통해 enum에 접근하던 모듈은 직접 `core:model` 의존을 추가해야 한다. 단, 대부분의 모듈은 이미 `core:model`을 의존하고 있으므로 추가 작업은 최소화된다.

### BrandFilter.matches() 고려사항

`BrandFilter`는 단순 enum이 아니라 `matches(Brand): Boolean` 메서드를 포함한다:

```kotlin
enum class BrandFilter(val brand: Brand?) {
    ALL(brand = null), SKE(brand = Brand.SKE), ...
    fun matches(stationBrand: Brand): Boolean = brand == null || brand == stationBrand
}
```

이 메서드는 `Brand` enum만 참조하므로 `Brand`와 함께 `core:model`로 이동하면 문제없다. 비즈니스 로직이라기보다 값 객체의 비교 연산이다.

### designsystem의 Brand 의존 해소

`BrandIcon.kt`는 `Brand` enum을 받아 drawable 리소스를 매핑한다:

```kotlin
fun Brand.gasStationBrandIconResource(): Int = when (this) {
    Brand.SKE -> R.drawable.ic_brand_ske
    ...
}

@Composable
fun GasStationBrandIcon(brand: Brand, ...) { ... }
```

enum이 `core:model`로 이동하면 `core:designsystem`은 `core:model`만 의존하게 되어 문제가 해결된다. `Brand` 타입 자체를 파라미터에서 제거할 필요는 없다.

### 결과 의존성 그래프

```
Before:
  core:datastore   ──→ domain:settings ──→ domain:station ──→ core:model
  core:network     ──→ domain:station ──→ core:model
  core:designsystem ──→ domain:station ──→ core:model

After:
  core:datastore   ──→ domain:settings ──→ core:model
  core:network     ──→ core:model
  core:designsystem ──→ core:model
  domain:station   ──→ core:model
```

---

## Part 2: domain:settings에서 domain:station 의존 제거

### Current Problem

`UserPreferences`가 `domain:station`의 enum 5개를 import한다:

```kotlin
// domain/settings/model/UserPreferences.kt
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder
```

이로 인해 `domain:settings`가 `domain:station`에 의존하고, `core:datastore`가 `domain:settings`를 통해 간접적으로 `domain:station`까지 의존하게 된다.

### Solution

Part 1에서 enum들이 `core:model`로 이동하면 이 문제는 **자동으로 해결된다**.

`UserPreferences`의 import가 `core:model`을 가리키게 되고, `domain:settings/build.gradle.kts`에서 `implementation(project(":domain:station"))`을 제거할 수 있다.

변경 대상:
- `domain/settings/build.gradle.kts`: `domain:station` 의존 제거
- `domain/settings/model/UserPreferences.kt`: import 경로 변경
- `domain/settings/usecase/Update*UseCase.kt` (5개): import 경로 변경
- `domain/settings/src/test/kotlin/...`: import 경로 변경

### 검증

`domain:settings` 모듈을 단독 빌드해서 `domain:station` 없이 컴파일되는지 확인한다:

```bash
./gradlew :domain:settings:compileKotlin
```

---

## Part 3: StationListViewModel 책임 분리

### Current Problem

`StationListViewModel`(522줄)이 다음 책임을 모두 소유한다:

1. **위치 상태 머신** (권한, GPS 가용성, 좌표 획득, 주소 조회) — ~100줄
2. **검색 오케스트레이션** (query 빌드, observe, cache 판단, criteria 변경 감지) — ~150줄
3. **새로고침 사이클** (위치→주소→쿼리→원격조회→에러핸들링) — ~120줄
4. **실패 처리** (blocking failure, pending failure, snackbar) — ~50줄
5. **사용자 액션 디스패치** (sort toggle, watch toggle, 외부 지도, 이벤트 로깅) — ~70줄
6. **내부 상태 클래스** (SessionState, QueryState, PendingFailure, CachedSnapshotState) — ~60줄

### Chosen Approach: 상태 홀더 추출

ViewModel을 쪼개되, 별도 ViewModel이나 Fragment를 만들지 않는다. 대신 **순수 Kotlin 클래스**로 책임을 추출하고, ViewModel은 이들을 조합하는 역할만 한다.

### 추출 대상 1: LocationStateMachine

위치 관련 상태와 흐름을 캡슐화한다.

```kotlin
// feature/station-list/src/main/kotlin/.../stationlist/LocationStateMachine.kt

class LocationStateMachine(
    private val getCurrentLocation: GetCurrentLocationUseCase,
    private val getCurrentAddress: GetCurrentAddressUseCase,
    private val observeAvailability: ObserveLocationAvailabilityUseCase,
) {
    // 상태
    val state: StateFlow<LocationState>

    // 이벤트
    val failures: SharedFlow<LocationFailure>

    // GPS availability flow
    fun observeGpsAvailability(): Flow<Boolean>

    // 권한 상태 갱신
    fun onPermissionChanged(state: LocationPermissionState)

    // GPS 가용성 갱신
    fun onGpsAvailabilityChanged(isEnabled: Boolean)

    // 위치 + 주소 획득 (suspend)
    suspend fun acquireLocationAndAddress(): LocationAcquisitionResult
}

data class LocationState(
    val permissionState: LocationPermissionState,
    val hasDeniedLocationAccess: Boolean,
    val isGpsEnabled: Boolean,
    val isAvailabilityKnown: Boolean,
    val currentCoordinates: Coordinates?,
    val currentAddressLabel: String?,
    val needsRecoveryRefresh: Boolean,
)

sealed interface LocationAcquisitionResult {
    data class Success(val coordinates: Coordinates, val addressLabel: String?) : LocationAcquisitionResult
    data class Failed(val failure: StationListFailureReason) : LocationAcquisitionResult
}
```

**책임 경계:**
- LocationStateMachine은 "현재 위치는 어디인가"와 "위치를 얻을 수 있는 상태인가"만 소유한다.
- 주유소 검색, 캐시, 실패 처리는 소유하지 않는다.
- ViewModel의 기존 `handleLocationResult()`, permission/GPS 상태 관리, `acquireLocationAndAddress()` 로직이 여기로 이동한다.

### 추출 대상 2: StationSearchOrchestrator

검색 쿼리 구성과 결과 관찰을 캡슐화한다.

```kotlin
// feature/station-list/src/main/kotlin/.../stationlist/StationSearchOrchestrator.kt

class StationSearchOrchestrator(
    private val observeNearbyStations: ObserveNearbyStationsUseCase,
    private val refreshNearbyStations: RefreshNearbyStationsUseCase,
) {
    // 현재 활성 쿼리와 캐시 상태
    val activeQueryState: StateFlow<ActiveStationQueryState?>

    // 검색 결과 관찰 Flow 구성
    fun observeStations(query: StationQuery): Flow<StationSearchResult>

    // 원격 새로고침 수행
    suspend fun refreshStations(query: StationQuery): RefreshOutcome

    // 새 기준으로 쿼리 변경이 필요한지 판단
    fun shouldRefreshForCriteriaChange(
        current: StationQuery?,
        next: StationQuery,
    ): Boolean
}

sealed interface RefreshOutcome {
    data object Success : RefreshOutcome
    data class Failed(val reason: StationRefreshFailureReason?) : RefreshOutcome
}
```

**책임 경계:**
- 쿼리 구성, 결과 관찰, 원격 새로고침 호출을 소유한다.
- 위치 획득, UI 상태 조합, 실패의 UI 표현은 소유하지 않는다.

### ViewModel 최종 형태

```kotlin
@HiltViewModel
class StationListViewModel @Inject constructor(
    private val locationStateMachine: LocationStateMachine,
    private val searchOrchestrator: StationSearchOrchestrator,
    private val observePreferences: ObserveUserPreferencesUseCase,
    private val updateSortOrder: UpdatePreferredSortOrderUseCase,
    private val updateWatchState: UpdateWatchStateUseCase,
    private val stationEventLogger: StationEventLogger,
) : ViewModel() {
    // UI 상태 조합: location + search + preferences → StationListUiState
    // 액션 디스패치
    // 실패 → UI 매핑
    // 이벤트 로깅
}
```

예상 줄 수:
- `LocationStateMachine`: ~120줄
- `StationSearchOrchestrator`: ~80줄
- `StationListViewModel`: ~250줄 (현재 522줄에서 약 52% 감소)

### 내부 상태 클래스 배치

| 클래스 | 현재 위치 | 이동 대상 |
|--------|----------|----------|
| `StationListSessionState` | ViewModel 하단 | `LocationStateMachine` 내부로 흡수 (`LocationState`로 대체) |
| `ActiveStationQueryState` | ViewModel 하단 | `StationSearchOrchestrator` 내부 |
| `PendingBlockingFailure` | ViewModel 하단 | ViewModel에 유지 (UI 관심사) |
| `CachedSnapshotState` | ViewModel 하단 | `StationSearchOrchestrator` 내부 |

### 테스트 전략

- `LocationStateMachineTest`: 권한 변경, GPS 토글, 위치 획득 성공/실패 시나리오
- `StationSearchOrchestratorTest`: 쿼리 변경 감지, 캐시 상태 판단, 새로고침 결과 전달
- `StationListViewModelTest`: 기존 테스트를 유지하되, 내부 구현이 바뀌므로 mock 대상이 변경됨. 기존 시나리오의 외부 동작 계약은 동일하게 보장한다.

---

## Part 4: 일시적 실패 재시도 정책

### Current Problem

네트워크 실패가 발생하면 즉시 UI에 전파된다. `Timeout`이나 `Network` 실패는 일시적이므로, 1회 자동 재시도만으로도 사용자 경험이 크게 개선된다.

### Chosen Approach: data 레이어 1회 재시도

재시도 정책은 `data:station`에 둔다. Domain과 feature 레이어는 재시도 존재를 알 필요가 없다.

```kotlin
// data/station/src/main/kotlin/.../station/StationRetryPolicy.kt

class StationRetryPolicy {
    /**
     * Timeout 또는 Network 실패 시 1회 재시도한다.
     * InvalidPayload와 Unknown은 재시도하지 않는다.
     * 재시도 전 500ms 대기한다.
     */
    suspend fun <T> withRetry(
        block: suspend () -> T,
    ): T
}
```

### 재시도 대상

| 실패 유형 | 재시도 여부 | 이유 |
|----------|-----------|------|
| `Timeout` | O | 일시적 네트워크 지연 |
| `Network` | O | 일시적 연결 끊김 |
| `InvalidPayload` | X | 서버 응답 포맷 문제, 재시도해도 동일 |
| `Unknown` | X | 원인 불명, 안전하지 않음 |

### 적용 위치

`DefaultStationRepository.refreshNearbyStations()` 내부에서 원격 데이터 소스 호출을 감싼다:

```kotlin
// Before:
val remoteResult = remoteDataSource.fetchStations(query)

// After:
val remoteResult = retryPolicy.withRetry {
    remoteDataSource.fetchStations(query)
}
```

### 설계 제약

- 최대 재시도 횟수: 1회 (총 최대 2회 시도)
- 재시도 간격: 500ms 고정 (exponential backoff 불필요 — 1회이므로)
- CancellationException은 재시도하지 않고 즉시 전파한다
- 재시도 시 로그를 남긴다 (Part 5의 구조화된 로깅 사용)

### 테스트

- 재시도 가능 실패(Timeout, Network) → 첫 시도 실패, 두 번째 성공 → 성공 반환
- 재시도 가능 실패 → 두 번째도 실패 → 최종 실패 반환
- 재시도 불가 실패(InvalidPayload, Unknown) → 즉시 실패 반환
- CancellationException → 재시도 없이 전파
- 재시도 시 500ms 대기 확인 (TestDispatcher 사용)

---

## Part 5: 구조화된 에러 로깅

### Current Problem

`StationEventLogger`는 분석 이벤트(검색, watch, 비교, 외부 지도)만 기록한다. 에러 자체를 구조화해서 기록하는 계약이 없어, 프로덕션에서 실패 원인을 진단하기 어렵다.

### Chosen Approach: 기존 StationEventLogger 확장

새 로거를 만들지 않고, 기존 `StationEventLogger`와 `StationEvent`를 확장한다. 이벤트 기반 분석과 에러 로깅을 하나의 채널로 통합하면 구현이 단순해지고, 에러도 분석 이벤트로 추적할 수 있다.

### 새 이벤트 타입

`StationEvent`에 다음 variant를 추가한다:

```kotlin
sealed interface StationEvent {
    // 기존 이벤트 유지
    data class SearchRefreshed(...) : StationEvent
    data class WatchToggled(...) : StationEvent
    data class CompareViewed(...) : StationEvent
    data class ExternalMapOpened(...) : StationEvent

    // 새 에러 이벤트
    data class RefreshFailed(
        val reason: StationRefreshFailureReason,
        val wasRetried: Boolean,
        val query: StationQuery,
    ) : StationEvent

    data class LocationFailed(
        val resultType: String, // "PermissionDenied", "TimedOut", "Unavailable", "Error"
    ) : StationEvent

    data class RetryAttempted(
        val originalReason: StationRefreshFailureReason,
        val succeeded: Boolean,
    ) : StationEvent
}
```

### 로깅 위치

| 이벤트 | 발생 위치 |
|--------|----------|
| `RefreshFailed` | `StationListViewModel.handleRefreshFailure()` |
| `LocationFailed` | `LocationStateMachine.acquireLocationAndAddress()` (추출 후) |
| `RetryAttempted` | `StationRetryPolicy.withRetry()` |

### LogcatStationEventLogger 확장

기존 구현체에 새 이벤트 매핑을 추가한다:

```kotlin
StationEvent.RefreshFailed -> Timber.w("refresh_failed reason=${event.reason} retried=${event.wasRetried}")
StationEvent.LocationFailed -> Timber.w("location_failed type=${event.resultType}")
StationEvent.RetryAttempted -> Timber.i("retry_attempted reason=${event.originalReason} succeeded=${event.succeeded}")
```

### 테스트

- 각 실패 시나리오에서 올바른 이벤트가 로거에 전달되는지 확인
- `wasRetried` 플래그가 재시도 정책과 일관되는지 확인

---

## File Ownership

### 이동 파일 (6개)

| From | To |
|------|----|
| `domain/station/src/main/kotlin/.../model/Brand.kt` | `core/model/src/main/kotlin/.../model/Brand.kt` |
| `domain/station/src/main/kotlin/.../model/BrandFilter.kt` | `core/model/src/main/kotlin/.../model/BrandFilter.kt` |
| `domain/station/src/main/kotlin/.../model/FuelType.kt` | `core/model/src/main/kotlin/.../model/FuelType.kt` |
| `domain/station/src/main/kotlin/.../model/MapProvider.kt` | `core/model/src/main/kotlin/.../model/MapProvider.kt` |
| `domain/station/src/main/kotlin/.../model/SearchRadius.kt` | `core/model/src/main/kotlin/.../model/SearchRadius.kt` |
| `domain/station/src/main/kotlin/.../model/SortOrder.kt` | `core/model/src/main/kotlin/.../model/SortOrder.kt` |

### 새 파일 (4개)

| 파일 | 모듈 | 역할 |
|------|------|------|
| `LocationStateMachine.kt` | `feature:station-list` | 위치 상태 관리 |
| `LocationStateMachineTest.kt` | `feature:station-list` (test) | 위치 상태 테스트 |
| `StationSearchOrchestrator.kt` | `feature:station-list` | 검색 오케스트레이션 |
| `StationSearchOrchestratorTest.kt` | `feature:station-list` (test) | 검색 오케스트레이션 테스트 |
| `StationRetryPolicy.kt` | `data:station` | 재시도 정책 |
| `StationRetryPolicyTest.kt` | `data:station` (test) | 재시도 테스트 |

### 수정 파일

**build.gradle.kts (4개):**
- `core/datastore/build.gradle.kts`: `domain:station` 제거
- `core/network/build.gradle.kts`: `domain:station` 제거
- `core/designsystem/build.gradle.kts`: `domain:station` → `core:model`
- `domain/settings/build.gradle.kts`: `domain:station` 제거

**import 경로 변경 (~138개 파일):**
- 6개 enum의 `com.gasstation.domain.station.model.*` → `com.gasstation.core.model.*`

**ViewModel 리팩터링 (4개):**
- `StationListViewModel.kt`: 위치/검색 로직 추출, 조합으로 대체
- `StationListViewModelTest.kt`: mock 대상 변경
- `StationListUiState.kt`: 변경 없음 (데이터 클래스는 동일)
- `StationListAction.kt`: 변경 없음

**재시도 정책 (2개):**
- `DefaultStationRepository.kt`: withRetry 적용
- `DefaultStationRepositoryTest.kt`: 재시도 시나리오 추가

**에러 로깅 (4개):**
- `StationEvent.kt`: 새 variant 추가
- `LogcatStationEventLogger.kt`: 새 이벤트 매핑 추가
- `StationRetryPolicy.kt`: RetryAttempted 이벤트 발행
- `StationListViewModel.kt`: RefreshFailed, LocationFailed 이벤트 발행

**문서 (3개):**
- `docs/architecture.md`: 모듈 그래프와 의존성 해석 기준 갱신
- `docs/module-contracts.md`: 모듈 인벤토리 갱신
- `AGENTS.md`: 변경 없음 (docs 참조 구조만 사용)

**테스트 (기존 수정):**
- `DomainContractSurfaceTest.kt`: enum import 경로 변경
- `BrandFilterTest.kt`: import 경로 변경
- 기타 ~40개 테스트 파일: import 경로 변경

---

## Implementation Order

작업은 5개 슬라이스로 나누며, 각 슬라이스는 독립적으로 빌드·테스트 가능하다.

### Slice 1: Enum 이동 (Part 1 + Part 2)

1. 6개 enum 파일을 `core:model`로 복사
2. 패키지 선언 변경
3. 모든 소비자 파일의 import 경로 변경
4. `domain:station`의 원본 enum 파일 삭제
5. build.gradle.kts 의존성 정리 (4개 모듈)
6. 전체 빌드 확인: `./gradlew build`
7. 전체 테스트 확인: `./gradlew test`

**검증:** `core:datastore`, `core:network`, `core:designsystem`이 `domain:station`에 의존하지 않음. `domain:settings`가 `domain:station`에 의존하지 않음.

### Slice 2: ViewModel 책임 분리 (Part 3)

1. `LocationStateMachine` 클래스 생성
2. ViewModel에서 위치 관련 코드를 `LocationStateMachine`으로 이동
3. `StationSearchOrchestrator` 클래스 생성
4. ViewModel에서 검색 관련 코드를 `StationSearchOrchestrator`로 이동
5. ViewModel을 조합 패턴으로 리팩터링
6. `LocationStateMachineTest` 작성
7. `StationSearchOrchestratorTest` 작성
8. `StationListViewModelTest` 갱신
9. 빌드 + 테스트 확인

**검증:** 기존 ViewModel 테스트의 모든 시나리오가 동일하게 통과. ViewModel 줄 수 250줄 이하.

### Slice 3: 재시도 정책 (Part 4)

1. `StationRetryPolicy` 클래스 생성
2. `StationRetryPolicyTest` 작성
3. `DefaultStationRepository`에 재시도 정책 적용
4. `DefaultStationRepositoryTest`에 재시도 시나리오 추가
5. 빌드 + 테스트 확인

**검증:** Timeout/Network 실패 시 1회 재시도 후 성공하면 성공 반환. 재시도 불가 실패는 즉시 전파.

### Slice 4: 구조화된 에러 로깅 (Part 5)

1. `StationEvent`에 새 variant 추가
2. `LogcatStationEventLogger`에 새 매핑 추가
3. 재시도 정책에 `RetryAttempted` 이벤트 발행 추가
4. ViewModel에 `RefreshFailed`, `LocationFailed` 이벤트 발행 추가
5. 관련 테스트 갱신
6. 빌드 + 테스트 확인

**검증:** 실패 시나리오에서 올바른 로그 이벤트가 기록됨.

### Slice 5: 문서 갱신

1. `docs/architecture.md` 모듈 그래프 갱신
2. `docs/module-contracts.md` 모듈 인벤토리 갱신
3. 기타 참조 문서 확인 및 갱신

---

## Risks and Mitigations

### Risk: 138개 파일 import 변경 시 누락

대응: IDE의 "Move to Package" 리팩터링이나 sed 일괄 치환을 사용한다. 변경 후 `./gradlew build`로 컴파일 에러가 없는지 확인한다. Kotlin은 import가 틀리면 컴파일 에러가 나므로 누락 위험은 낮다.

### Risk: ViewModel 분리 시 상태 동기화 문제

대응: 추출한 클래스는 순수 Kotlin 클래스이며, ViewModel의 `viewModelScope`에서 실행된다. 별도 CoroutineScope를 만들지 않아 생명주기 문제를 방지한다.

### Risk: 재시도로 인한 응답 지연

대응: 최대 1회 재시도이고, 재시도 간격은 500ms이므로 최악의 경우 추가 지연은 `500ms + timeout(10s) = 10.5s`이다. 기존 단일 timeout(10s)과 비교해 체감 차이가 작고, 성공률 향상이 더 크다.

### Risk: DomainContractSurfaceTest 깨짐

대응: 이 테스트는 `domain:station`의 public API를 고정한다. enum이 `core:model`로 이동하면 `domain:station`의 public API에서 빠지므로, 테스트를 갱신해야 한다. `core:model`에 대한 별도 surface test를 추가할지 검토한다.

---

## Success Criteria

1. `core:datastore`, `core:network`, `core:designsystem`의 build.gradle.kts에 `domain:station` 의존이 없다.
2. `domain:settings`의 build.gradle.kts에 `domain:station` 의존이 없다.
3. `StationListViewModel`이 250줄 이하이다.
4. `LocationStateMachine`과 `StationSearchOrchestrator`가 독립적으로 테스트 가능하다.
5. Timeout/Network 실패 시 자동 1회 재시도가 동작한다.
6. 실패 시 구조화된 로그 이벤트가 기록된다.
7. `./gradlew build`와 `./gradlew test`가 통과한다.
8. demo/prod의 사용자 동작이 기존과 동일하다.
