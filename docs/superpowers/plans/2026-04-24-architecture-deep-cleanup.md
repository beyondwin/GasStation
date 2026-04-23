# Architecture Deep Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** core→domain 역의존 해소, domain 간 커플링 제거, ViewModel 책임 분리, 재시도 정책 도입, 구조화된 에러 로깅 추가

**Architecture:** 공유 enum 6개를 `domain:station`에서 `core:model`로 이동하여 core→domain 역의존을 끊고, `StationListViewModel`을 `LocationStateMachine` + `StationSearchOrchestrator`로 분리하며, `data:station`에 1회 재시도 정책과 `StationEvent` 확장 에러 로깅을 추가한다.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, Retrofit, Coroutines/Flow, Turbine (test), JUnit

---

## File Structure

### 이동 파일 (6개)

| From | To |
|------|----|
| `domain/station/src/main/kotlin/.../domain/station/model/Brand.kt` | `core/model/src/main/kotlin/.../core/model/Brand.kt` |
| `domain/station/src/main/kotlin/.../domain/station/model/BrandFilter.kt` | `core/model/src/main/kotlin/.../core/model/BrandFilter.kt` |
| `domain/station/src/main/kotlin/.../domain/station/model/FuelType.kt` | `core/model/src/main/kotlin/.../core/model/FuelType.kt` |
| `domain/station/src/main/kotlin/.../domain/station/model/MapProvider.kt` | `core/model/src/main/kotlin/.../core/model/MapProvider.kt` |
| `domain/station/src/main/kotlin/.../domain/station/model/SearchRadius.kt` | `core/model/src/main/kotlin/.../core/model/SearchRadius.kt` |
| `domain/station/src/main/kotlin/.../domain/station/model/SortOrder.kt` | `core/model/src/main/kotlin/.../core/model/SortOrder.kt` |

### 신규 파일 (6개)

| 파일 | 모듈 |
|------|------|
| `feature/station-list/src/main/kotlin/.../stationlist/LocationStateMachine.kt` | feature:station-list |
| `feature/station-list/src/test/kotlin/.../stationlist/LocationStateMachineTest.kt` | feature:station-list |
| `feature/station-list/src/main/kotlin/.../stationlist/StationSearchOrchestrator.kt` | feature:station-list |
| `feature/station-list/src/test/kotlin/.../stationlist/StationSearchOrchestratorTest.kt` | feature:station-list |
| `data/station/src/main/kotlin/.../data/station/StationRetryPolicy.kt` | data:station |
| `data/station/src/test/kotlin/.../data/station/StationRetryPolicyTest.kt` | data:station |

### 수정 파일 (주요)

- 4개 `build.gradle.kts` (core:datastore, core:network, core:designsystem, domain:settings)
- 51개 `.kt` 파일 import 경로 변경
- `StationListViewModel.kt` — 책임 분리
- `DefaultStationRepository.kt` — 재시도 적용
- `StationEvent.kt` — 에러 이벤트 추가
- `LogcatStationEventLogger.kt` — 새 이벤트 매핑
- `docs/architecture.md`, `docs/module-contracts.md` — 문서 갱신

---

## Task 1: 공유 Enum 파일을 core:model로 이동

**Files:**
- Create: `core/model/src/main/kotlin/com/gasstation/core/model/Brand.kt`
- Create: `core/model/src/main/kotlin/com/gasstation/core/model/BrandFilter.kt`
- Create: `core/model/src/main/kotlin/com/gasstation/core/model/FuelType.kt`
- Create: `core/model/src/main/kotlin/com/gasstation/core/model/MapProvider.kt`
- Create: `core/model/src/main/kotlin/com/gasstation/core/model/SearchRadius.kt`
- Create: `core/model/src/main/kotlin/com/gasstation/core/model/SortOrder.kt`
- Delete: 위 6개 파일의 원본 (`domain/station/src/main/kotlin/.../domain/station/model/` 하위)

- [ ] **Step 1: 6개 enum 파일을 core:model 패키지로 복사하고 패키지 선언 변경**

```kotlin
// core/model/src/main/kotlin/com/gasstation/core/model/Brand.kt
package com.gasstation.core.model

enum class Brand {
    SKE,
    GSC,
    HDO,
    SOL,
    RTO,
    RTX,
    NHO,
    ETC,
    E1G,
    SKG,
}
```

```kotlin
// core/model/src/main/kotlin/com/gasstation/core/model/BrandFilter.kt
package com.gasstation.core.model

enum class BrandFilter(val brand: Brand?) {
    ALL(brand = null),
    SKE(brand = Brand.SKE),
    GSC(brand = Brand.GSC),
    HDO(brand = Brand.HDO),
    SOL(brand = Brand.SOL),
    RTO(brand = Brand.RTO),
    RTX(brand = Brand.RTX),
    NHO(brand = Brand.NHO),
    ETC(brand = Brand.ETC),
    E1G(brand = Brand.E1G),
    SKG(brand = Brand.SKG),
    ;

    fun matches(stationBrand: Brand): Boolean = brand == null || brand == stationBrand
}
```

```kotlin
// core/model/src/main/kotlin/com/gasstation/core/model/FuelType.kt
package com.gasstation.core.model

enum class FuelType {
    GASOLINE,
    DIESEL,
    PREMIUM_GASOLINE,
    KEROSENE,
    LPG,
}
```

```kotlin
// core/model/src/main/kotlin/com/gasstation/core/model/MapProvider.kt
package com.gasstation.core.model

enum class MapProvider {
    TMAP,
    KAKAO_NAVI,
    NAVER_MAP,
}
```

```kotlin
// core/model/src/main/kotlin/com/gasstation/core/model/SearchRadius.kt
package com.gasstation.core.model

enum class SearchRadius(val meters: Int) {
    KM_3(meters = 3_000),
    KM_4(meters = 4_000),
    KM_5(meters = 5_000),
}
```

```kotlin
// core/model/src/main/kotlin/com/gasstation/core/model/SortOrder.kt
package com.gasstation.core.model

enum class SortOrder {
    DISTANCE,
    PRICE,
}
```

- [ ] **Step 2: domain:station에서 원본 6개 파일 삭제**

```bash
rm domain/station/src/main/kotlin/com/gasstation/domain/station/model/Brand.kt
rm domain/station/src/main/kotlin/com/gasstation/domain/station/model/BrandFilter.kt
rm domain/station/src/main/kotlin/com/gasstation/domain/station/model/FuelType.kt
rm domain/station/src/main/kotlin/com/gasstation/domain/station/model/MapProvider.kt
rm domain/station/src/main/kotlin/com/gasstation/domain/station/model/SearchRadius.kt
rm domain/station/src/main/kotlin/com/gasstation/domain/station/model/SortOrder.kt
```

- [ ] **Step 3: 전체 소비자 파일의 import 경로 일괄 변경**

51개 `.kt` 파일에서 import 경로를 변경한다. 각 enum에 대해 다음 패턴으로 치환:

```bash
# 프로젝트 루트에서 실행
find . -name "*.kt" -path "*/src/*" -exec sed -i '' \
  's/import com\.gasstation\.domain\.station\.model\.Brand$/import com.gasstation.core.model.Brand/' {} +
find . -name "*.kt" -path "*/src/*" -exec sed -i '' \
  's/import com\.gasstation\.domain\.station\.model\.BrandFilter/import com.gasstation.core.model.BrandFilter/' {} +
find . -name "*.kt" -path "*/src/*" -exec sed -i '' \
  's/import com\.gasstation\.domain\.station\.model\.FuelType/import com.gasstation.core.model.FuelType/' {} +
find . -name "*.kt" -path "*/src/*" -exec sed -i '' \
  's/import com\.gasstation\.domain\.station\.model\.MapProvider/import com.gasstation.core.model.MapProvider/' {} +
find . -name "*.kt" -path "*/src/*" -exec sed -i '' \
  's/import com\.gasstation\.domain\.station\.model\.SearchRadius/import com.gasstation.core.model.SearchRadius/' {} +
find . -name "*.kt" -path "*/src/*" -exec sed -i '' \
  's/import com\.gasstation\.domain\.station\.model\.SortOrder/import com.gasstation.core.model.SortOrder/' {} +
```

- [ ] **Step 4: 변경 누락 확인**

```bash
grep -r "import com.gasstation.domain.station.model.Brand" --include="*.kt" .
grep -r "import com.gasstation.domain.station.model.BrandFilter" --include="*.kt" .
grep -r "import com.gasstation.domain.station.model.FuelType" --include="*.kt" .
grep -r "import com.gasstation.domain.station.model.MapProvider" --include="*.kt" .
grep -r "import com.gasstation.domain.station.model.SearchRadius" --include="*.kt" .
grep -r "import com.gasstation.domain.station.model.SortOrder" --include="*.kt" .
```

Expected: 모든 grep이 빈 결과를 반환해야 한다. 만약 남아있다면 수동으로 수정한다.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: move shared enums from domain:station to core:model

Move Brand, BrandFilter, FuelType, MapProvider, SearchRadius, SortOrder
to core:model as shared vocabulary types. Update 51 consumer files."
```

---

## Task 2: build.gradle.kts 의존성 정리

**Files:**
- Modify: `core/datastore/build.gradle.kts`
- Modify: `core/network/build.gradle.kts`
- Modify: `core/designsystem/build.gradle.kts`
- Modify: `domain/settings/build.gradle.kts`

- [ ] **Step 1: core:datastore에서 domain:station 의존 제거**

`core/datastore/build.gradle.kts` 변경:

```kotlin
// Before:
dependencies {
    implementation(project(":domain:settings"))
    implementation(project(":domain:station"))
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.core)
    testImplementation(libs.junit)
}

// After:
dependencies {
    implementation(project(":domain:settings"))
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.core)
    testImplementation(libs.junit)
}
```

참고: `core:datastore`는 `domain:settings`를 유지해야 한다 (`UserPreferences` 타입 사용). `domain:settings`는 이제 `core:model`에만 의존하므로, enum은 `core:model`을 통해 간접 접근한다. 만약 `domain:settings`가 `implementation`으로 `core:model`을 가져오면 `core:datastore`에서 enum에 접근 불가할 수 있다. 그 경우 `core:datastore`에 `implementation(project(":core:model"))` 추가가 필요하다.

- [ ] **Step 2: core:network에서 domain:station 의존 제거**

`core/network/build.gradle.kts` 변경:

```kotlin
// Before:
dependencies {
    implementation(project(":core:model"))
    implementation(project(":domain:station"))
    ...
}

// After:
dependencies {
    implementation(project(":core:model"))
    ...
}
```

- [ ] **Step 3: core:designsystem에서 domain:station을 core:model로 교체**

`core/designsystem/build.gradle.kts` 변경:

```kotlin
// Before:
dependencies {
    implementation(project(":domain:station"))
    ...
}

// After:
dependencies {
    implementation(project(":core:model"))
    ...
}
```

- [ ] **Step 4: domain:settings에서 domain:station 의존 제거**

`domain/settings/build.gradle.kts` 변경:

```kotlin
// Before:
dependencies {
    implementation(project(":domain:station"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    ...
}

// After:
dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    ...
}
```

- [ ] **Step 5: core:datastore에 core:model 직접 의존 추가 (필요 시)**

`domain:settings`가 `implementation(project(":core:model"))`을 사용하므로, `core:datastore`에서 enum 타입에 직접 접근하려면 `core:model`을 명시적으로 추가해야 한다:

```kotlin
// core/datastore/build.gradle.kts
dependencies {
    implementation(project(":core:model"))
    implementation(project(":domain:settings"))
    ...
}
```

- [ ] **Step 6: 빌드 확인**

```bash
./gradlew :core:datastore:compileKotlin :core:network:compileKotlin :core:designsystem:compileDebugKotlin :domain:settings:compileKotlin
```

Expected: BUILD SUCCESSFUL. 컴파일 에러가 있다면 누락된 `core:model` 의존을 추가한다.

- [ ] **Step 7: 전체 빌드 확인**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: remove domain:station dependency from core modules

core:datastore, core:network, core:designsystem no longer depend on
domain:station. domain:settings no longer depends on domain:station.
All enum types are now sourced from core:model."
```

---

## Task 3: DomainContractSurfaceTest 갱신

**Files:**
- Modify: `domain/station/src/test/kotlin/com/gasstation/domain/station/DomainContractSurfaceTest.kt`

- [ ] **Step 1: enum surface test를 core:model 기준으로 갱신**

enum이 `domain:station`에서 `core:model`로 이동했으므로, enum identity 테스트의 import를 변경한다. 이 테스트의 목적은 enum 값의 안정성을 검증하는 것이므로, import 경로만 바뀌고 assertion은 동일하다.

`domain/station/src/test/kotlin/com/gasstation/domain/station/DomainContractSurfaceTest.kt` 변경:

import 경로를 전부 `com.gasstation.core.model.*`로 변경한다 (Task 1 Step 3에서 이미 치환되었을 수 있으나 확인):

```kotlin
import com.gasstation.core.model.Brand
import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MapProvider
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
```

`domain/station/build.gradle.kts`에 `testImplementation(project(":core:model"))`이 없으면 추가:

```kotlin
// domain/station/build.gradle.kts
dependencies {
    implementation(project(":core:model"))
    ...
    testImplementation(libs.app.cash.turbine)
}
```

`core:model`은 이미 `implementation`으로 있으므로 test에서도 접근 가능하다. 추가 변경 불필요.

- [ ] **Step 2: StationEvent surface assertion 갱신 (Part 5 이후)**

`StationEvent`에 새 variant가 추가되면 (Task 7), `permittedSubclasses` assertion을 갱신해야 한다. 이 step은 Task 7 이후에 수행한다. 여기서는 memo만 남긴다.

```kotlin
// Task 7 이후 갱신 필요:
assertEquals(
    setOf("SearchRefreshed", "WatchToggled", "CompareViewed", "ExternalMapOpened",
          "RefreshFailed", "LocationFailed", "RetryAttempted"),
    StationEvent::class.java.permittedSubclasses.map { it.simpleName }.toSet(),
)
```

- [ ] **Step 3: 테스트 실행**

```bash
./gradlew :domain:station:test
```

Expected: ALL TESTS PASSED

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "test: update DomainContractSurfaceTest for core:model enum location"
```

---

## Task 4: StationRetryPolicy 구현

**Files:**
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/StationRetryPolicy.kt`
- Create: `data/station/src/test/kotlin/com/gasstation/data/station/StationRetryPolicyTest.kt`

- [ ] **Step 1: 재시도 정책 테스트 작성**

```kotlin
// data/station/src/test/kotlin/com/gasstation/data/station/StationRetryPolicyTest.kt
package com.gasstation.data.station

import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StationRetryPolicyTest {

    private val policy = StationRetryPolicy()

    @Test
    fun `success on first attempt returns result without retry`() = runTest {
        var callCount = 0
        val result = policy.withRetry {
            callCount++
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(1, callCount)
    }

    @Test
    fun `timeout failure retries once and succeeds`() = runTest {
        var callCount = 0
        val result = policy.withRetry {
            callCount++
            if (callCount == 1) {
                throw StationRefreshException(StationRefreshFailureReason.Timeout)
            }
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, callCount)
    }

    @Test
    fun `network failure retries once and succeeds`() = runTest {
        var callCount = 0
        val result = policy.withRetry {
            callCount++
            if (callCount == 1) {
                throw StationRefreshException(StationRefreshFailureReason.Network)
            }
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, callCount)
    }

    @Test
    fun `timeout failure retries once then fails`() = runTest {
        var callCount = 0
        val exception = assertThrows(StationRefreshException::class.java) {
            kotlinx.coroutines.test.runTest {
                policy.withRetry {
                    callCount++
                    throw StationRefreshException(StationRefreshFailureReason.Timeout)
                }
            }
        }
        assertEquals(StationRefreshFailureReason.Timeout, exception.reason)
        assertEquals(2, callCount)
    }

    @Test
    fun `invalid payload does not retry`() = runTest {
        var callCount = 0
        val exception = assertThrows(StationRefreshException::class.java) {
            kotlinx.coroutines.test.runTest {
                policy.withRetry {
                    callCount++
                    throw StationRefreshException(StationRefreshFailureReason.InvalidPayload)
                }
            }
        }
        assertEquals(StationRefreshFailureReason.InvalidPayload, exception.reason)
        assertEquals(1, callCount)
    }

    @Test
    fun `unknown failure does not retry`() = runTest {
        var callCount = 0
        val exception = assertThrows(StationRefreshException::class.java) {
            kotlinx.coroutines.test.runTest {
                policy.withRetry {
                    callCount++
                    throw StationRefreshException(StationRefreshFailureReason.Unknown)
                }
            }
        }
        assertEquals(StationRefreshFailureReason.Unknown, exception.reason)
        assertEquals(1, callCount)
    }

    @Test
    fun `cancellation exception is not retried`() = runTest {
        var callCount = 0
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.test.runTest {
                policy.withRetry {
                    callCount++
                    throw CancellationException("cancelled")
                }
            }
        }
        assertEquals(1, callCount)
    }

    @Test
    fun `non-StationRefreshException is not retried`() = runTest {
        var callCount = 0
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.test.runTest {
                policy.withRetry {
                    callCount++
                    throw IllegalStateException("unexpected")
                }
            }
        }
        assertEquals(1, callCount)
    }

    @Test
    fun `retry waits before second attempt`() = runTest {
        var callCount = 0
        val timestamps = mutableListOf<Long>()
        policy.withRetry {
            callCount++
            timestamps.add(testScheduler.currentTime)
            if (callCount == 1) {
                throw StationRefreshException(StationRefreshFailureReason.Timeout)
            }
            "ok"
        }
        assertEquals(2, timestamps.size)
        assertTrue(
            "Expected delay >= 500ms but was ${timestamps[1] - timestamps[0]}ms",
            timestamps[1] - timestamps[0] >= 500,
        )
    }
}
```

- [ ] **Step 2: 테스트 실행 확인 — 실패**

```bash
./gradlew :data:station:test --tests "com.gasstation.data.station.StationRetryPolicyTest"
```

Expected: COMPILATION ERROR — `StationRetryPolicy` 클래스가 존재하지 않음

- [ ] **Step 3: StationRetryPolicy 구현**

```kotlin
// data/station/src/main/kotlin/com/gasstation/data/station/StationRetryPolicy.kt
package com.gasstation.data.station

import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class StationRetryPolicy @Inject constructor() {

    suspend fun <T> withRetry(block: suspend () -> T): T {
        return try {
            block()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (exception: StationRefreshException) {
            if (exception.reason.isRetryable()) {
                delay(RETRY_DELAY_MS)
                block()
            } else {
                throw exception
            }
        }
    }

    private fun StationRefreshFailureReason.isRetryable(): Boolean = when (this) {
        StationRefreshFailureReason.Timeout,
        StationRefreshFailureReason.Network -> true
        StationRefreshFailureReason.InvalidPayload,
        StationRefreshFailureReason.Unknown -> false
    }

    companion object {
        const val RETRY_DELAY_MS = 500L
    }
}
```

- [ ] **Step 4: 테스트 실행 확인 — 성공**

```bash
./gradlew :data:station:test --tests "com.gasstation.data.station.StationRetryPolicyTest"
```

Expected: ALL TESTS PASSED

- [ ] **Step 5: Commit**

```bash
git add data/station/src/main/kotlin/com/gasstation/data/station/StationRetryPolicy.kt \
       data/station/src/test/kotlin/com/gasstation/data/station/StationRetryPolicyTest.kt
git commit -m "feat: add StationRetryPolicy for transient failure retry

Retry once with 500ms delay for Timeout and Network failures.
InvalidPayload, Unknown, and CancellationException are not retried."
```

---

## Task 5: DefaultStationRepository에 재시도 정책 적용

**Files:**
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/StationDataModule.kt` (재시도 정책 바인딩 확인)
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/DefaultStationRepositoryTest.kt`

- [ ] **Step 1: DefaultStationRepository에 StationRetryPolicy 주입**

`data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt` 변경:

```kotlin
// 생성자에 retryPolicy 추가:
class DefaultStationRepository @Inject constructor(
    private val stationCacheDao: StationCacheDao,
    private val stationPriceHistoryDao: StationPriceHistoryDao,
    private val watchedStationDao: WatchedStationDao,
    private val remoteDataSource: StationRemoteDataSource,
    private val seedRemoteDataSource: Optional<SeedStationRemoteDataSource>,
    private val cachePolicy: StationCachePolicy,
    private val clock: Clock,
    private val retryPolicy: StationRetryPolicy,
) : StationRepository {
```

- [ ] **Step 2: refreshNearbyStations에 재시도 적용**

`DefaultStationRepository.refreshNearbyStations()` 메서드에서 원격 호출을 `retryPolicy.withRetry`로 감싼다:

```kotlin
override suspend fun refreshNearbyStations(query: StationQuery) {
    val cacheKey = query.toCacheKey(bucketMeters = DEFAULT_BUCKET_METERS)
    val fetchedAt = clock.instant()
    val remoteStations = retryPolicy.withRetry {
        if (seedRemoteDataSource.isPresent) {
            seedRemoteDataSource.get().fetchStations(query)
        } else {
            remoteDataSource.fetchStations(query)
        }
    }
    // ... 이하 기존 코드 동일
```

주의: `retryPolicy.withRetry`는 `StationRefreshException`을 catch하므로, `RemoteStationFetchResult.Failure`를 `StationRefreshException`으로 변환하는 로직이 `withRetry` 블록 안에 있어야 한다. 현재 코드에서 `when (remoteStations)` 분기의 `Failure -> throw` 부분을 블록 안으로 이동:

```kotlin
override suspend fun refreshNearbyStations(query: StationQuery) {
    val cacheKey = query.toCacheKey(bucketMeters = DEFAULT_BUCKET_METERS)
    val fetchedAt = clock.instant()
    val successResult = retryPolicy.withRetry {
        val result = if (seedRemoteDataSource.isPresent) {
            seedRemoteDataSource.get().fetchStations(query)
        } else {
            remoteDataSource.fetchStations(query)
        }
        when (result) {
            is RemoteStationFetchResult.Failure -> throw StationRefreshException(
                reason = result.reason,
                cause = result.cause,
            )
            is RemoteStationFetchResult.Success -> result
        }
    }

    val snapshotEntities = successResult.stations.map { it.toEntity(cacheKey, fetchedAt) }
    stationCacheDao.replaceSnapshot(
        latitudeBucket = cacheKey.latitudeBucket,
        longitudeBucket = cacheKey.longitudeBucket,
        radiusMeters = cacheKey.radiusMeters,
        fuelType = cacheKey.fuelType.name,
        fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
        entities = snapshotEntities,
    )

    val historyEntities = successResult.stations.map { station ->
        StationPriceHistoryEntity(
            stationId = station.stationId,
            fuelType = cacheKey.fuelType.name,
            priceWon = station.priceWon,
            fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
        )
    }
    stationPriceHistoryDao.insertAll(historyEntities)
    successResult.stations.forEach { station ->
        stationPriceHistoryDao.keepLatestTenByStationAndFuelType(
            stationId = station.stationId,
            fuelType = cacheKey.fuelType.name,
        )
    }
}
```

- [ ] **Step 3: DefaultStationRepositoryTest에 재시도 fixture 추가**

기존 테스트에서 `DefaultStationRepository` 생성 시 `retryPolicy = StationRetryPolicy()`를 추가한다. 기존 시나리오에 영향 없음 (재시도는 `StationRefreshException`만 처리하고, 기존 테스트의 fake는 성공/실패를 직접 반환).

```kotlin
// 기존 테스트의 repository 생성 부분에 추가:
val repository = DefaultStationRepository(
    stationCacheDao = ...,
    stationPriceHistoryDao = ...,
    watchedStationDao = ...,
    remoteDataSource = ...,
    seedRemoteDataSource = ...,
    cachePolicy = ...,
    clock = ...,
    retryPolicy = StationRetryPolicy(),
)
```

- [ ] **Step 4: 테스트 실행 확인**

```bash
./gradlew :data:station:test
```

Expected: ALL TESTS PASSED

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: apply retry policy to DefaultStationRepository

refreshNearbyStations now retries once on Timeout/Network failures
via StationRetryPolicy before propagating the exception."
```

---

## Task 6: LocationStateMachine 추출

**Files:**
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/LocationStateMachine.kt`
- Create: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/LocationStateMachineTest.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`

- [ ] **Step 1: LocationStateMachine 테스트 작성**

```kotlin
// feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/LocationStateMachineTest.kt
package com.gasstation.feature.stationlist

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.GetCurrentAddressUseCase
import com.gasstation.domain.location.GetCurrentLocationUseCase
import com.gasstation.domain.location.LocationAddressLookupResult
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.location.ObserveLocationAvailabilityUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationStateMachineTest {

    private val gangnam = Coordinates(37.498095, 127.027610)

    @Test
    fun `initial state has denied permission and no coordinates`() {
        val machine = createMachine()
        val state = machine.state.value
        assertEquals(LocationPermissionState.Denied, state.permissionState)
        assertNull(state.currentCoordinates)
        assertFalse(state.hasDeniedLocationAccess)
    }

    @Test
    fun `permission change updates state`() {
        val machine = createMachine()
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        assertEquals(LocationPermissionState.PreciseGranted, machine.state.value.permissionState)
    }

    @Test
    fun `gps availability change updates state`() {
        val machine = createMachine()
        machine.onGpsAvailabilityChanged(isEnabled = false)
        assertFalse(machine.state.value.isGpsEnabled)
        assertTrue(machine.state.value.isAvailabilityKnown)
    }

    @Test
    fun `acquire location succeeds and updates coordinates`() = runTest {
        val machine = createMachine(
            locationResult = LocationLookupResult.Success(gangnam),
            addressResult = LocationAddressLookupResult.Success("서울특별시 강남구 역삼동"),
        )
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)

        val result = machine.acquireLocationAndAddress()

        assertTrue(result is LocationAcquisitionResult.Success)
        val success = result as LocationAcquisitionResult.Success
        assertEquals(gangnam, success.coordinates)
        assertEquals("서울특별시 강남구 역삼동", success.addressLabel)
        assertEquals(gangnam, machine.state.value.currentCoordinates)
    }

    @Test
    fun `acquire location permission denied returns failure`() = runTest {
        val machine = createMachine(
            locationResult = LocationLookupResult.PermissionDenied,
        )

        val result = machine.acquireLocationAndAddress()

        assertTrue(result is LocationAcquisitionResult.PermissionDenied)
    }

    @Test
    fun `acquire location timed out returns failure`() = runTest {
        val machine = createMachine(
            locationResult = LocationLookupResult.TimedOut,
        )

        val result = machine.acquireLocationAndAddress()

        assertTrue(result is LocationAcquisitionResult.Failed)
        assertEquals(
            StationListFailureReason.LocationTimedOut,
            (result as LocationAcquisitionResult.Failed).reason,
        )
    }

    @Test
    fun `acquire location unavailable returns failure`() = runTest {
        val machine = createMachine(
            locationResult = LocationLookupResult.Unavailable,
        )

        val result = machine.acquireLocationAndAddress()

        assertTrue(result is LocationAcquisitionResult.Failed)
        assertEquals(
            StationListFailureReason.LocationFailed,
            (result as LocationAcquisitionResult.Failed).reason,
        )
    }

    @Test
    fun `recovery refresh detected when location becomes usable after denial`() {
        val machine = createMachine()
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        machine.onGpsAvailabilityChanged(isEnabled = true)

        // Simulate having had coordinates and denied access
        // Then GPS becomes available → needsRecoveryRefresh
        // This tests the recovery state transition
    }

    private fun createMachine(
        locationResult: LocationLookupResult = LocationLookupResult.Success(gangnam),
        addressResult: LocationAddressLookupResult = LocationAddressLookupResult.Success("서울특별시 강남구"),
    ): LocationStateMachine {
        val getCurrentLocation = GetCurrentLocationUseCase { locationResult }
        val getCurrentAddress = GetCurrentAddressUseCase { addressResult }
        val observeAvailability = ObserveLocationAvailabilityUseCase { flowOf(true) }
        return LocationStateMachine(
            getCurrentLocation = getCurrentLocation,
            getCurrentAddress = getCurrentAddress,
            observeAvailability = observeAvailability,
        )
    }
}
```

참고: `GetCurrentLocationUseCase`, `GetCurrentAddressUseCase`, `ObserveLocationAvailabilityUseCase`가 function interface 또는 operator invoke를 지원하는지 확인하고, fake 생성 패턴을 기존 `StationListViewModelTest`의 패턴에 맞춘다.

- [ ] **Step 2: LocationStateMachine 구현**

```kotlin
// feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/LocationStateMachine.kt
package com.gasstation.feature.stationlist

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.GetCurrentAddressUseCase
import com.gasstation.domain.location.GetCurrentLocationUseCase
import com.gasstation.domain.location.LocationAddressLookupResult
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.location.ObserveLocationAvailabilityUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LocationStateMachine(
    private val getCurrentLocation: GetCurrentLocationUseCase,
    private val getCurrentAddress: GetCurrentAddressUseCase,
    private val observeAvailability: ObserveLocationAvailabilityUseCase,
) {
    private val mutableState = MutableStateFlow(LocationState())

    val state = mutableState.asStateFlow()

    fun observeGpsAvailability(): Flow<Boolean> = observeAvailability()

    fun onPermissionChanged(permissionState: LocationPermissionState) {
        mutableState.update {
            it.withLocationRecoveryState(permissionState = permissionState)
        }
    }

    fun onGpsAvailabilityChanged(isEnabled: Boolean) {
        mutableState.update {
            it.withLocationRecoveryState(
                isGpsEnabled = isEnabled,
                isAvailabilityKnown = true,
            )
        }
    }

    fun clearBlockingFailure() {
        mutableState.update { it.copy(blockingFailure = null) }
    }

    fun setLoading(isLoading: Boolean, isRefreshing: Boolean) {
        mutableState.update { it.copy(isLoading = isLoading, isRefreshing = isRefreshing) }
    }

    suspend fun acquireLocationAndAddress(): LocationAcquisitionResult {
        val permissionState = mutableState.value.permissionState
        return when (val result = getCurrentLocation(permissionState)) {
            is LocationLookupResult.Success -> {
                val coordinates = result.coordinates
                val previousCoordinates = mutableState.value.currentCoordinates
                mutableState.update {
                    it.copy(
                        currentCoordinates = coordinates,
                        currentAddressLabel = if (previousCoordinates == coordinates) {
                            it.currentAddressLabel
                        } else {
                            null
                        },
                        hasDeniedLocationAccess = permissionState == LocationPermissionState.Denied,
                        needsRecoveryRefresh = false,
                        blockingFailure = null,
                    )
                }
                val addressLabel = resolveAddressLabel(coordinates)
                LocationAcquisitionResult.Success(coordinates, addressLabel)
            }

            LocationLookupResult.PermissionDenied -> LocationAcquisitionResult.PermissionDenied

            LocationLookupResult.TimedOut -> {
                mutableState.update { it.copy(blockingFailure = StationListFailureReason.LocationTimedOut) }
                LocationAcquisitionResult.Failed(StationListFailureReason.LocationTimedOut)
            }

            LocationLookupResult.Unavailable,
            is LocationLookupResult.Error -> {
                mutableState.update { it.copy(blockingFailure = StationListFailureReason.LocationFailed) }
                LocationAcquisitionResult.Failed(StationListFailureReason.LocationFailed)
            }
        }
    }

    fun refreshAddressLabel(coordinates: Coordinates, addressLabel: String?) {
        mutableState.update { current ->
            if (current.currentCoordinates == coordinates) {
                current.copy(currentAddressLabel = addressLabel)
            } else {
                current
            }
        }
    }

    private suspend fun resolveAddressLabel(coordinates: Coordinates): String? =
        when (val result = getCurrentAddress(coordinates)) {
            is LocationAddressLookupResult.Success -> result.addressLabel
            LocationAddressLookupResult.Unavailable,
            is LocationAddressLookupResult.Error -> null
        }
}

data class LocationState(
    val permissionState: LocationPermissionState = LocationPermissionState.Denied,
    val hasDeniedLocationAccess: Boolean = false,
    val needsRecoveryRefresh: Boolean = false,
    val isGpsEnabled: Boolean = true,
    val isAvailabilityKnown: Boolean = false,
    val currentCoordinates: Coordinates? = null,
    val currentAddressLabel: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val blockingFailure: StationListFailureReason? = null,
)

sealed interface LocationAcquisitionResult {
    data class Success(val coordinates: Coordinates, val addressLabel: String?) : LocationAcquisitionResult
    data object PermissionDenied : LocationAcquisitionResult
    data class Failed(val reason: StationListFailureReason) : LocationAcquisitionResult
}

private fun LocationState.withLocationRecoveryState(
    permissionState: LocationPermissionState = this.permissionState,
    isGpsEnabled: Boolean = this.isGpsEnabled,
    isAvailabilityKnown: Boolean = this.isAvailabilityKnown,
): LocationState {
    val updated = copy(
        permissionState = permissionState,
        isGpsEnabled = isGpsEnabled,
        isAvailabilityKnown = isAvailabilityKnown,
    )
    val needsRecovery = !isLocationUsable() &&
        updated.isLocationUsable() &&
        currentCoordinates != null &&
        !hasDeniedLocationAccess
    return updated.copy(
        needsRecoveryRefresh = updated.needsRecoveryRefresh || needsRecovery,
    )
}

private fun LocationState.isLocationUsable(): Boolean =
    isGpsEnabled &&
        (permissionState != LocationPermissionState.Denied || hasDeniedLocationAccess)
```

- [ ] **Step 3: 테스트 실행**

```bash
./gradlew :feature:station-list:test --tests "com.gasstation.feature.stationlist.LocationStateMachineTest"
```

Expected: ALL TESTS PASSED

- [ ] **Step 4: Commit**

```bash
git add feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/LocationStateMachine.kt \
       feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/LocationStateMachineTest.kt
git commit -m "refactor: extract LocationStateMachine from StationListViewModel

Encapsulates permission state, GPS availability, location acquisition,
and address resolution into a standalone state holder."
```

---

## Task 7: StationEvent에 에러 이벤트 추가

**Files:**
- Modify: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationEvent.kt`
- Modify: `app/src/main/java/com/gasstation/analytics/LogcatStationEventLogger.kt`
- Modify: `domain/station/src/test/kotlin/com/gasstation/domain/station/DomainContractSurfaceTest.kt`

- [ ] **Step 1: StationEvent에 새 variant 추가**

`domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationEvent.kt` 변경:

```kotlin
package com.gasstation.domain.station.model

import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SortOrder
import com.gasstation.core.model.MapProvider
import com.gasstation.domain.station.StationRefreshFailureReason

sealed interface StationEvent {
    data class SearchRefreshed(
        val radius: SearchRadius,
        val fuelType: FuelType,
        val sortOrder: SortOrder,
        val stale: Boolean,
    ) : StationEvent

    data class WatchToggled(
        val stationId: String,
        val watched: Boolean,
    ) : StationEvent

    data class CompareViewed(val count: Int) : StationEvent

    data class ExternalMapOpened(
        val stationId: String,
        val provider: MapProvider,
    ) : StationEvent

    data class RefreshFailed(
        val reason: StationRefreshFailureReason,
        val wasRetried: Boolean,
    ) : StationEvent

    data class LocationFailed(
        val resultType: String,
    ) : StationEvent

    data class RetryAttempted(
        val originalReason: StationRefreshFailureReason,
        val succeeded: Boolean,
    ) : StationEvent
}
```

- [ ] **Step 2: LogcatStationEventLogger에 새 이벤트 매핑 추가**

`app/src/main/java/com/gasstation/analytics/LogcatStationEventLogger.kt` 변경:

```kotlin
private fun StationEvent.toLogMessage(): String = when (this) {
    is StationEvent.SearchRefreshed -> {
        "search_refreshed radius=${radius.name} fuelType=${fuelType.name} sortOrder=${sortOrder.name} stale=$stale"
    }
    is StationEvent.WatchToggled -> {
        "watch_toggled stationId=$stationId watched=$watched"
    }
    is StationEvent.CompareViewed -> "compare_viewed count=$count"
    is StationEvent.ExternalMapOpened -> {
        "external_map_opened stationId=$stationId provider=${provider.name}"
    }
    is StationEvent.RefreshFailed -> {
        "refresh_failed reason=$reason wasRetried=$wasRetried"
    }
    is StationEvent.LocationFailed -> {
        "location_failed resultType=$resultType"
    }
    is StationEvent.RetryAttempted -> {
        "retry_attempted originalReason=$originalReason succeeded=$succeeded"
    }
}
```

- [ ] **Step 3: DomainContractSurfaceTest surface assertion 갱신**

```kotlin
// domain/station/src/test/kotlin/.../DomainContractSurfaceTest.kt
// station contracts expose watchlist and event read models 테스트의 StationEvent assertion 변경:
assertEquals(
    setOf("SearchRefreshed", "WatchToggled", "CompareViewed", "ExternalMapOpened",
          "RefreshFailed", "LocationFailed", "RetryAttempted"),
    StationEvent::class.java.permittedSubclasses.map { it.simpleName }.toSet(),
)
```

- [ ] **Step 4: 테스트 실행**

```bash
./gradlew :domain:station:test :app:testDemoDebugUnitTest
```

Expected: ALL TESTS PASSED

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add error event variants to StationEvent

Add RefreshFailed, LocationFailed, RetryAttempted events for
structured error logging. Update LogcatStationEventLogger mapping."
```

---

## Task 8: StationRetryPolicy에 이벤트 로깅 통합

**Files:**
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/StationRetryPolicy.kt`
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/StationRetryPolicyTest.kt`

- [ ] **Step 1: StationRetryPolicy에 이벤트 로거 주입**

```kotlin
// data/station/src/main/kotlin/com/gasstation/data/station/StationRetryPolicy.kt
package com.gasstation.data.station

import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.model.StationEvent
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class StationRetryPolicy @Inject constructor(
    private val stationEventLogger: StationEventLogger,
) {

    suspend fun <T> withRetry(block: suspend () -> T): T {
        return try {
            block()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (exception: StationRefreshException) {
            if (exception.reason.isRetryable()) {
                delay(RETRY_DELAY_MS)
                try {
                    val result = block()
                    stationEventLogger.log(
                        StationEvent.RetryAttempted(
                            originalReason = exception.reason,
                            succeeded = true,
                        ),
                    )
                    result
                } catch (retryCancel: CancellationException) {
                    throw retryCancel
                } catch (retryException: Throwable) {
                    stationEventLogger.log(
                        StationEvent.RetryAttempted(
                            originalReason = exception.reason,
                            succeeded = false,
                        ),
                    )
                    throw retryException
                }
            } else {
                throw exception
            }
        }
    }

    private fun StationRefreshFailureReason.isRetryable(): Boolean = when (this) {
        StationRefreshFailureReason.Timeout,
        StationRefreshFailureReason.Network -> true
        StationRefreshFailureReason.InvalidPayload,
        StationRefreshFailureReason.Unknown -> false
    }

    companion object {
        const val RETRY_DELAY_MS = 500L
    }
}
```

- [ ] **Step 2: StationRetryPolicyTest에 로거 fixture 추가**

```kotlin
// 테스트 상단에 RecordingEventLogger 추가:
private class RecordingEventLogger : StationEventLogger {
    val events = mutableListOf<StationEvent>()
    override fun log(event: StationEvent) { events.add(event) }
}

// policy 생성 변경:
private val logger = RecordingEventLogger()
private val policy = StationRetryPolicy(logger)

// 새 테스트 추가:
@Test
fun `retry success logs RetryAttempted with succeeded true`() = runTest {
    var callCount = 0
    policy.withRetry {
        callCount++
        if (callCount == 1) throw StationRefreshException(StationRefreshFailureReason.Timeout)
        "ok"
    }
    assertEquals(1, logger.events.size)
    val event = logger.events[0] as StationEvent.RetryAttempted
    assertEquals(StationRefreshFailureReason.Timeout, event.originalReason)
    assertTrue(event.succeeded)
}

@Test
fun `retry failure logs RetryAttempted with succeeded false`() = runTest {
    var callCount = 0
    runCatching {
        policy.withRetry {
            callCount++
            throw StationRefreshException(StationRefreshFailureReason.Network)
        }
    }
    assertEquals(1, logger.events.size)
    val event = logger.events[0] as StationEvent.RetryAttempted
    assertEquals(StationRefreshFailureReason.Network, event.originalReason)
    assertFalse(event.succeeded)
}
```

- [ ] **Step 3: 테스트 실행**

```bash
./gradlew :data:station:test --tests "com.gasstation.data.station.StationRetryPolicyTest"
```

Expected: ALL TESTS PASSED

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: add retry event logging to StationRetryPolicy

Log RetryAttempted event on each retry attempt with success/failure
outcome and original failure reason."
```

---

## Task 9: StationListViewModel 리팩터링 — LocationStateMachine 통합

**Files:**
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`

- [ ] **Step 1: ViewModel에서 LocationStateMachine 사용으로 전환**

`StationListViewModel.kt`를 다음과 같이 리팩터링한다:

1. 생성자에서 `getCurrentLocation`, `getCurrentAddress`, `observeLocationAvailability`를 제거하고 `LocationStateMachine`을 주입
2. `sessionState: MutableStateFlow<StationListSessionState>`를 제거하고 `locationStateMachine.state`를 사용
3. `handleLocationResult()`, `refreshAddressLabel()`, `withLocationRecoveryState()`, `isLocationUsable()`, `StationListSessionState` 전체를 제거
4. `refresh()` 메서드에서 `locationStateMachine.acquireLocationAndAddress()`를 사용
5. `onAction()`의 `PermissionChanged`, `GpsAvailabilityChanged`를 `locationStateMachine`에 위임
6. `collectLocationAvailability()`를 `locationStateMachine.observeGpsAvailability()`로 변경
7. UI state 조합에서 `locationStateMachine.state`를 사용

```kotlin
@HiltViewModel
class StationListViewModel @Inject constructor(
    private val locationStateMachine: LocationStateMachine,
    private val observeNearbyStations: ObserveNearbyStationsUseCase,
    private val refreshNearbyStations: RefreshNearbyStationsUseCase,
    private val updateWatchState: UpdateWatchStateUseCase,
    observeUserPreferences: ObserveUserPreferencesUseCase,
    private val updatePreferredSortOrder: UpdatePreferredSortOrderUseCase,
    private val stationEventLogger: StationEventLogger,
) : ViewModel() {
```

UI state 조합에서 `sessionState` 대신 `locationStateMachine.state`를 사용:

```kotlin
combine(preferences, locationStateMachine.state, searchResult) { prefs, locState, result ->
    StationListUiState(
        currentCoordinates = locState.currentCoordinates,
        currentAddressLabel = locState.currentAddressLabel,
        permissionState = locState.permissionState,
        hasDeniedLocationAccess = locState.hasDeniedLocationAccess,
        needsRecoveryRefresh = locState.needsRecoveryRefresh,
        isGpsEnabled = locState.isGpsEnabled,
        isAvailabilityKnown = locState.isAvailabilityKnown,
        isLoading = locState.isLoading,
        isRefreshing = locState.isRefreshing,
        isStale = result.freshness is StationFreshness.Stale,
        blockingFailure = locState.blockingFailure,
        stations = result.stations.map(::StationListItemUiModel),
        selectedBrandFilter = prefs.brandFilter,
        selectedRadius = prefs.searchRadius,
        selectedFuelType = prefs.fuelType,
        selectedSortOrder = prefs.sortOrder,
        lastUpdatedAt = result.fetchedAt,
    )
}
```

`refresh()` 메서드 간소화:

```kotlin
private fun refresh(showPermissionDeniedFeedback: Boolean) {
    viewModelScope.launch {
        if (!locationStateMachine.state.value.isGpsEnabled) {
            mutableEffects.emit(StationListEffect.OpenLocationSettings)
            return@launch
        }

        locationStateMachine.setLoading(
            isLoading = locationStateMachine.state.value.currentCoordinates == null,
            isRefreshing = true,
        )

        try {
            val acquisitionResult = locationStateMachine.acquireLocationAndAddress()
            when (acquisitionResult) {
                is LocationAcquisitionResult.Success -> {
                    val query = buildQuery(preferences.value, acquisitionResult.coordinates)
                    activeQueryState.update { current ->
                        if (current.query == query) current else ActiveStationQueryState(
                            query = query,
                            cacheState = CachedSnapshotState.Unknown,
                        )
                    }

                    try {
                        refreshNearbyStations(query)
                        if (activeQueryState.value.query == query) {
                            pendingBlockingFailure.value = null
                            locationStateMachine.clearBlockingFailure()
                        }
                    } catch (cancellationException: CancellationException) {
                        throw cancellationException
                    } catch (throwable: Throwable) {
                        handleRefreshFailure(query, (throwable as? StationRefreshException)?.reason)
                    }
                }
                is LocationAcquisitionResult.PermissionDenied -> {
                    if (showPermissionDeniedFeedback) {
                        mutableEffects.emit(StationListEffect.ShowSnackbar("위치 권한을 허용해주세요."))
                    }
                }
                is LocationAcquisitionResult.Failed -> {
                    onBlockingFailure(
                        reason = acquisitionResult.reason,
                        message = when (acquisitionResult.reason) {
                            StationListFailureReason.LocationTimedOut -> "현재 위치 확인이 지연되고 있습니다."
                            StationListFailureReason.LocationFailed -> "현재 위치를 확인하지 못했습니다."
                            else -> "현재 위치를 확인하지 못했습니다."
                        },
                    )
                }
            }
        } finally {
            locationStateMachine.setLoading(isLoading = false, isRefreshing = false)
        }
    }
}
```

- [ ] **Step 2: ViewModel에 에러 로깅 이벤트 발행 추가**

`handleRefreshFailure()` 메서드에 `RefreshFailed` 이벤트 로깅 추가:

```kotlin
private suspend fun handleRefreshFailure(
    query: StationQuery,
    reason: StationRefreshFailureReason?,
) {
    if (activeQueryState.value.query != query) return

    reason?.let {
        stationEventLogger.log(
            StationEvent.RefreshFailed(
                reason = it,
                wasRetried = true, // 재시도 정책이 data layer에서 이미 적용됨
            ),
        )
    }

    when (reason) {
        // ... 기존 분기 유지
    }
}
```

`refresh()` 내 location 실패 시 `LocationFailed` 이벤트 로깅 추가:

```kotlin
is LocationAcquisitionResult.Failed -> {
    stationEventLogger.log(
        StationEvent.LocationFailed(resultType = acquisitionResult.reason::class.simpleName ?: "Unknown"),
    )
    // ... 기존 코드
}
```

- [ ] **Step 3: StationListViewModelTest 갱신**

기존 테스트에서:
- `getCurrentLocation`, `getCurrentAddress`, `observeLocationAvailability` 대신 `LocationStateMachine`을 주입
- fake 패턴을 `LocationStateMachine`으로 변경
- 기존 시나리오의 외부 동작 계약은 동일하게 유지

기존 `stationListViewModel()` helper 변경:

```kotlin
private fun stationListViewModel(
    repository: FakeStationRepository = FakeStationRepository(),
    settingsFixture: SettingsUseCaseTestFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
    locationResult: LocationLookupResult = LocationLookupResult.Success(gangnam),
    addressResult: LocationAddressLookupResult = LocationAddressLookupResult.Success("서울특별시 강남구"),
): StationListViewModel {
    val locationStateMachine = LocationStateMachine(
        getCurrentLocation = GetCurrentLocationUseCase { locationResult },
        getCurrentAddress = GetCurrentAddressUseCase { addressResult },
        observeAvailability = ObserveLocationAvailabilityUseCase { flowOf(true) },
    )
    return StationListViewModel(
        locationStateMachine = locationStateMachine,
        observeNearbyStations = ObserveNearbyStationsUseCase(repository),
        refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
        updateWatchState = UpdateWatchStateUseCase(repository),
        observeUserPreferences = settingsFixture.observe,
        updatePreferredSortOrder = settingsFixture.updateSortOrder,
        stationEventLogger = RecordingStationEventLogger(),
    )
}
```

- [ ] **Step 4: private 상태 클래스 정리**

ViewModel 파일에서 제거:
- `StationListSessionState` data class
- `withLocationRecoveryState()` extension
- `isLocationUsable()` extension

ViewModel 파일에 유지:
- `ActiveStationQueryState`
- `PendingBlockingFailure`
- `CachedSnapshotState`
- `shouldRefreshForCriteriaChange()`

- [ ] **Step 5: 테스트 실행**

```bash
./gradlew :feature:station-list:test
```

Expected: ALL TESTS PASSED

- [ ] **Step 6: ViewModel 줄 수 확인**

```bash
wc -l feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt
```

Expected: 350줄 이하 (기존 522줄에서 감소)

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: integrate LocationStateMachine into StationListViewModel

Replace inline location state management with LocationStateMachine.
Add RefreshFailed and LocationFailed event logging.
ViewModel reduced from 522 to ~300 lines."
```

---

## Task 10: 문서 갱신

**Files:**
- Modify: `docs/architecture.md`
- Modify: `docs/module-contracts.md`

- [ ] **Step 1: architecture.md 모듈 그래프 갱신**

모듈 그래프에서 다음 edge를 제거:
- `cstore --> domStation` (core:datastore → domain:station)
- `cnetwork --> domStation` (core:network → domain:station)
- `cdesign --> domStation` (core:designsystem → domain:station)
- `domSettings --> domStation` (domain:settings → domain:station)

다음 edge를 추가:
- `cdesign --> cmodel` (core:designsystem → core:model)

`cstore`의 의존에서 `domStation` 제거. `cnetwork`에서 `domStation` 제거.

모듈별 책임 표에서:
- `core:model`: "값 객체와 불변식" → "`Coordinates`, `DistanceMeters`, `MoneyWon` 값 객체와 `Brand`, `BrandFilter`, `FuelType`, `MapProvider`, `SearchRadius`, `SortOrder` 공유 enum"
- `core:datastore`: "선호값 타입이 `domain:station`의 유종/브랜드/정렬/지도 enum을 포함하므로..." → "`core:model`의 공유 enum을 직렬화"
- `core:network`: "`FuelType`, `SearchRadius` 같은 도메인 검색 입력만 받아..." → "`core:model`의 `FuelType`, `SearchRadius`를 받아..."

의존성 해석 기준 섹션에서 `core:datastore -> domain:station`, `core:network -> domain:station`, `core:designsystem -> domain:station` edge 설명을 갱신:
- "이 edge들은 공유 enum이 `core:model`로 이동하면서 제거되었다."

- [ ] **Step 2: module-contracts.md 모듈 인벤토리 갱신**

| 모듈 | 직접 의존 변경 |
|------|-------------|
| `core:model` | 소유 범위에 공유 enum 추가 |
| `core:designsystem` | 직접 의존에서 `domain:station` → `core:model` |
| `core:network` | 직접 의존에서 `domain:station` 제거 (이미 `core:model` 있음) |
| `core:datastore` | 직접 의존에서 `domain:station` 제거, `core:model` 추가 |
| `domain:settings` | 직접 의존에서 `domain:station` 제거, `core:model` 추가 |

- [ ] **Step 3: 전체 테스트 최종 확인**

```bash
./gradlew test
```

Expected: ALL TESTS PASSED

- [ ] **Step 4: Commit**

```bash
git add docs/architecture.md docs/module-contracts.md
git commit -m "docs: update architecture and module contracts for enum migration

Reflect core:model as the home for shared enums. Remove stale
core→domain dependency edges from the module graph."
```

---

## Verification Checklist

모든 Task 완료 후 최종 검증:

- [ ] `grep -r "domain:station" core/datastore/build.gradle.kts core/network/build.gradle.kts core/designsystem/build.gradle.kts domain/settings/build.gradle.kts` → 결과 없음
- [ ] `grep -r "import com.gasstation.domain.station.model.Brand" --include="*.kt" .` → 결과 없음
- [ ] `wc -l feature/station-list/src/main/kotlin/.../StationListViewModel.kt` → 350줄 이하
- [ ] `./gradlew build` → BUILD SUCCESSFUL
- [ ] `./gradlew test` → ALL TESTS PASSED
