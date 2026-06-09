# Cache Snapshot And Remote Source Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `StationSearchResult.hasCachedSnapshot`를 모든 생성 지점에서 명시하게 만들고, demo/prod remote source 선택을 `DefaultStationRepository` 밖의 전용 data source로 분리한다.

**Architecture:** domain read model은 cache snapshot marker 존재 여부를 생성자가 명시하도록 강제한다. data 계층에는 `FlavorAwareStationRemoteDataSource`를 추가해 optional demo seed binding을 단일 `StationRemoteDataSource`로 접고, repository는 단일 remote 계약만 소비한다.

**Tech Stack:** Kotlin, Gradle multi-module Android, Hilt, JUnit4, kotlin-test, kotlinx-coroutines-test.

**짝 설계 문서:** `docs/superpowers/specs/2026-06-09-cache-snapshot-source-boundary-design.md`

---

## 파일 구조

- **수정** `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationSearchResult.kt` - `hasCachedSnapshot` 기본값 제거.
- **수정** `domain/station/src/test/kotlin/com/gasstation/domain/station/DomainContractSurfaceTest.kt` - 기본값 생성자 방지 계약 추가.
- **생성** `data/station/src/main/kotlin/com/gasstation/data/station/FlavorAwareStationRemoteDataSource.kt` - seed/prod source 선택 전용 wrapper.
- **생성** `data/station/src/test/kotlin/com/gasstation/data/station/FlavorAwareStationRemoteDataSourceTest.kt` - wrapper 선택 정책 테스트.
- **수정** `data/station/src/main/kotlin/com/gasstation/data/station/StationDataModule.kt` - `StationRemoteDataSource` binding을 provider로 변경.
- **수정** `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt` - `Optional<SeedStationRemoteDataSource>` 제거, 단일 remote source만 사용.
- **수정** `data/station/src/test/kotlin/com/gasstation/data/station/DefaultStationRepositoryTest.kt` - seed 선택 테스트 제거, helper constructor 정리.
- **수정** feature/watchlist/station-list tests - `StationSearchResult` test fake에 `hasCachedSnapshot` 명시.

---

## Task 1: `StationSearchResult` cache marker 명시 계약 추가

**Files:**
- Modify: `domain/station/src/test/kotlin/com/gasstation/domain/station/DomainContractSurfaceTest.kt`
- Modify: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationSearchResult.kt`

- [ ] **Step 1: Write the failing contract test**

`DomainContractSurfaceTest.kt`의 `station contracts expose watchlist and event read models` 테스트에서 `stationSearchResultField` 단언 직후 아래 코드를 추가한다.

```kotlin
        assertTrue(
            StationSearchResult::class.java.declaredConstructors.none { constructor ->
                constructor.parameterTypes.any { it.name == "kotlin.jvm.internal.DefaultConstructorMarker" }
            },
            "StationSearchResult creation must set cache snapshot presence explicitly.",
        )
```

- [ ] **Step 2: Run RED**

Run:

```bash
./gradlew :domain:station:test --tests com.gasstation.domain.station.DomainContractSurfaceTest
```

Expected: FAIL. 현재 `hasCachedSnapshot: Boolean = fetchedAt != null` 때문에 Kotlin default-argument synthetic constructor가 생성되고, 테스트 메시지 `StationSearchResult creation must set cache snapshot presence explicitly.`로 실패한다.

- [ ] **Step 3: Remove the default value**

`StationSearchResult.kt` 전체를 아래로 교체한다.

```kotlin
package com.gasstation.domain.station.model

import java.time.Instant

data class StationSearchResult(
    val stations: List<StationListEntry>,
    val freshness: StationFreshness,
    val fetchedAt: Instant?,
    val hasCachedSnapshot: Boolean,
)
```

- [ ] **Step 4: Update compile failures with explicit cache marker values**

Run:

```bash
./gradlew :domain:station:test :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest
```

Expected: compile failures in test fake constructors that omitted `hasCachedSnapshot`.

Apply these exact rules:

- `fetchedAt = null` and the fake represents no cached snapshot: add `hasCachedSnapshot = false`.
- `fetchedAt = Instant...` and the fake represents cached content: add `hasCachedSnapshot = true`.
- Existing production constructors in `DefaultStationRepository.kt`, `StationSearchResultAssembler.kt`, and `StationSearchOrchestrator.kt` already pass explicit values; do not change their behavior.

Known current omissions to update:

```kotlin
// feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt
StationSearchResult(
    stations = listOf(stationEntry()),
    freshness = StationFreshness.Stale,
    fetchedAt = null,
    hasCachedSnapshot = false,
)

// feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistViewModelTest.kt
StationSearchResult(
    stations = emptyList(),
    freshness = StationFreshness.Stale,
    fetchedAt = null,
    hasCachedSnapshot = false,
)
```

For every additional compiler-reported constructor, add the same fourth argument using the rule above. Do not infer cache state from `fetchedAt` mechanically if the test name says otherwise.

- [ ] **Step 5: Run GREEN**

Run:

```bash
./gradlew :domain:station:test :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationSearchResult.kt \
        domain/station/src/test/kotlin/com/gasstation/domain/station/DomainContractSurfaceTest.kt \
        feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt \
        feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistViewModelTest.kt
git commit -m "refactor(domain): require explicit cache snapshot state"
```

---

## Task 2: remote source 선택 wrapper 테스트 작성

**Files:**
- Create: `data/station/src/test/kotlin/com/gasstation/data/station/FlavorAwareStationRemoteDataSourceTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `FlavorAwareStationRemoteDataSourceTest.kt` with:

```kotlin
package com.gasstation.data.station

import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.model.StationQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Optional

class FlavorAwareStationRemoteDataSourceTest {

    @Test
    fun `fetchStations delegates to seed source when demo seed is bound`() = runTest {
        val prod = RecordingDefaultStationRemoteDataSource(
            RemoteStationFetchResult.Failure(StationRefreshFailureReason.Network),
        )
        val seed = RecordingSeedStationRemoteDataSource(
            RemoteStationFetchResult.Success(
                listOf(
                    RemoteStation(
                        stationId = "seed-station",
                        name = "Seed Station",
                        brandCode = "SKE",
                        priceWon = 1_777,
                        coordinates = Coordinates(37.497927, 127.027583),
                    ),
                ),
            ),
        )
        val source = FlavorAwareStationRemoteDataSource(
            prodRemoteDataSource = prod,
            seedRemoteDataSource = Optional.of(seed),
        )

        val result = source.fetchStations(stationQuery())

        assertEquals(0, prod.calls)
        assertEquals(1, seed.calls)
        assertEquals(
            listOf("seed-station"),
            (result as RemoteStationFetchResult.Success).stations.map { it.stationId },
        )
    }

    @Test
    fun `fetchStations delegates to prod source when seed source is absent`() = runTest {
        val prod = RecordingDefaultStationRemoteDataSource(
            RemoteStationFetchResult.Success(
                listOf(
                    RemoteStation(
                        stationId = "prod-station",
                        name = "Prod Station",
                        brandCode = "GSC",
                        priceWon = 1_699,
                        coordinates = Coordinates(37.498095, 127.027610),
                    ),
                ),
            ),
        )
        val source = FlavorAwareStationRemoteDataSource(
            prodRemoteDataSource = prod,
            seedRemoteDataSource = Optional.empty(),
        )

        val result = source.fetchStations(stationQuery())

        assertEquals(1, prod.calls)
        assertEquals(
            listOf("prod-station"),
            (result as RemoteStationFetchResult.Success).stations.map { it.stationId },
        )
    }

    private fun stationQuery() = StationQuery(
        coordinates = Coordinates(37.498095, 127.027610),
        radius = SearchRadius.KM_3,
        fuelType = FuelType.GASOLINE,
        brandFilter = BrandFilter.ALL,
        sortOrder = SortOrder.DISTANCE,
    )
}

private class RecordingDefaultStationRemoteDataSource(private val result: RemoteStationFetchResult) : StationRemoteDataSource {
    var calls = 0
        private set

    override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult {
        calls += 1
        return result
    }
}

private class RecordingSeedStationRemoteDataSource(private val result: RemoteStationFetchResult) : SeedStationRemoteDataSource {
    var calls = 0
        private set

    override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult {
        calls += 1
        return result
    }
}
```

- [ ] **Step 2: Run RED**

Run:

```bash
./gradlew :data:station:testDebugUnitTest --tests com.gasstation.data.station.FlavorAwareStationRemoteDataSourceTest
```

Expected: compile failure because `FlavorAwareStationRemoteDataSource` does not exist.

---

## Task 3: `FlavorAwareStationRemoteDataSource` 구현 및 Hilt binding 변경

**Files:**
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/FlavorAwareStationRemoteDataSource.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/StationDataModule.kt`

- [ ] **Step 1: Implement the wrapper**

Create `FlavorAwareStationRemoteDataSource.kt`:

```kotlin
package com.gasstation.data.station

import com.gasstation.domain.station.model.StationQuery
import java.util.Optional

class FlavorAwareStationRemoteDataSource(
    private val prodRemoteDataSource: StationRemoteDataSource,
    private val seedRemoteDataSource: Optional<SeedStationRemoteDataSource>,
) : StationRemoteDataSource {
    override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult {
        val seed = seedRemoteDataSource.orElse(null)
        return seed?.fetchStations(query) ?: prodRemoteDataSource.fetchStations(query)
    }
}
```

Note: constructor accepts `StationRemoteDataSource` rather than `DefaultStationRemoteDataSource` so tests can pass a simple fake. Hilt provider will pass the concrete prod implementation.

- [ ] **Step 2: Change `StationDataModule` binding**

Replace `StationDataModule.kt` with:

```kotlin
package com.gasstation.data.station

import com.gasstation.domain.station.StationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.util.Optional
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StationDataModule {

    @Binds
    @Singleton
    abstract fun bindStationRepository(repository: DefaultStationRepository): StationRepository

    companion object {
        @Provides
        @Singleton
        fun provideStationRemoteDataSource(
            prodRemoteDataSource: DefaultStationRemoteDataSource,
            seedRemoteDataSource: Optional<SeedStationRemoteDataSource>,
        ): StationRemoteDataSource = FlavorAwareStationRemoteDataSource(
            prodRemoteDataSource = prodRemoteDataSource,
            seedRemoteDataSource = seedRemoteDataSource,
        )

        @Provides
        @Singleton
        fun provideStationCachePolicy(): StationCachePolicy = StationCachePolicy()

        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.systemUTC()
    }
}
```

Important: remove the old `@Binds abstract fun bindStationRemoteDataSource(...)`. Keeping both bindings creates duplicate Hilt bindings for `StationRemoteDataSource`.

- [ ] **Step 3: Run wrapper GREEN**

Run:

```bash
./gradlew :data:station:testDebugUnitTest --tests com.gasstation.data.station.FlavorAwareStationRemoteDataSourceTest
```

Expected: PASS.

---

## Task 4: Remove source selection from `DefaultStationRepository`

**Files:**
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/DefaultStationRepositoryTest.kt`

- [ ] **Step 1: Modify repository constructor and refresh path**

In `DefaultStationRepository.kt`:

1. Remove `import java.util.Optional`.
2. Remove constructor parameter:

```kotlin
private val seedRemoteDataSource: Optional<SeedStationRemoteDataSource>,
```

3. In `refreshNearbyStationsInternal`, replace:

```kotlin
when (val result = fetchRemoteStations(query)) {
```

with:

```kotlin
when (val result = remoteDataSource.fetchStations(query)) {
```

4. Delete the private helper:

```kotlin
private suspend fun fetchRemoteStations(query: StationQuery): RemoteStationFetchResult = if (seedRemoteDataSource.isPresent) {
    seedRemoteDataSource.get().fetchStations(query)
} else {
    remoteDataSource.fetchStations(query)
}
```

- [ ] **Step 2: Update repository tests**

In `DefaultStationRepositoryTest.kt`:

1. Delete the test named:

```kotlin
fun `refreshNearbyStations prefers seed data source when available`()
```

The behavior moved to `FlavorAwareStationRemoteDataSourceTest`.

2. Remove `import java.util.Optional` if no longer used.

3. Update the `repository(...)` helper:

```kotlin
private fun repository(
    stationCacheDao: StationCacheDao = RecordingStationCacheDao(),
    stationPriceHistoryDao: RecordingStationPriceHistoryDao = RecordingStationPriceHistoryDao(),
    watchedStationDao: RecordingWatchedStationDao = RecordingWatchedStationDao(),
    remoteDataSource: StationRemoteDataSource = FakeStationRemoteDataSource(
        RemoteStationFetchResult.Success(emptyList()),
    ),
    analytics: StationEventLogger = RepositoryDoubles.RecordingStationEventLogger(),
    crashReporter: CrashReporter = FakeCrashReporter(),
    transactionRunner: ImmediateDatabaseTransactionRunner = ImmediateDatabaseTransactionRunner(),
) = DefaultStationRepository(
    stationCacheDao = stationCacheDao,
    stationPriceHistoryDao = stationPriceHistoryDao,
    watchedStationDao = watchedStationDao,
    remoteDataSource = remoteDataSource,
    cachePolicy = StationCachePolicy(),
    retryPolicy = StationRetryPolicy(analytics),
    stationEventLogger = analytics,
    crashReporter = crashReporter,
    transactionRunner = transactionRunner,
    clock = clock,
)
```

- [ ] **Step 3: Run repository GREEN**

Run:

```bash
./gradlew :data:station:testDebugUnitTest --tests com.gasstation.data.station.DefaultStationRepositoryTest
```

Expected: PASS. Repository tests still prove snapshot replacement, history persistence, retry, pruning, watch updates, and crash reporting. They no longer prove source selection.

- [ ] **Step 4: Run full data station tests**

Run:

```bash
./gradlew :data:station:testDebugUnitTest
```

Expected: PASS, including `FlavorAwareStationRemoteDataSourceTest`, `StationRemoteDataSourceTest`, `DefaultStationRepositoryTest`, and watchlist tests.

- [ ] **Step 5: Commit**

```bash
git add data/station/src/main/kotlin/com/gasstation/data/station/FlavorAwareStationRemoteDataSource.kt \
        data/station/src/main/kotlin/com/gasstation/data/station/StationDataModule.kt \
        data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt \
        data/station/src/test/kotlin/com/gasstation/data/station/FlavorAwareStationRemoteDataSourceTest.kt \
        data/station/src/test/kotlin/com/gasstation/data/station/DefaultStationRepositoryTest.kt
git commit -m "refactor(data): move station source selection out of repository"
```

---

## Task 5: Graph and flavor verification

**Files:**
- No production changes unless verification exposes a concrete wiring failure.

- [ ] **Step 1: Run module boundary check**

Run:

```bash
./gradlew verifyModuleBoundaries
```

Expected: PASS. No new project dependency edge is introduced.

- [ ] **Step 2: Run flavor app unit tests**

Run:

```bash
./gradlew :app:testDemoDebugUnitTest :app:testProdDebugUnitTest
```

Expected: PASS. Demo graph still has `SeedStationRemoteDataSource`; prod graph still receives `Optional.empty()`.

- [ ] **Step 3: Run final targeted suite**

Run:

```bash
./gradlew \
  :domain:station:test \
  :data:station:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  verifyModuleBoundaries
```

Expected: PASS.

- [ ] **Step 4: Commit any verification-only doc adjustment only if needed**

If implementation discovers that `docs/architecture.md` or `docs/module-contracts.md` now names repository as the source selector, update the sentence to name `FlavorAwareStationRemoteDataSource` instead. Current code-backed scan did not find a required product-doc change, so this step should usually be no-op.

If a doc change is required:

```bash
git add docs/architecture.md docs/module-contracts.md
git commit -m "docs: reflect station remote source boundary"
```

---

## Self-review checklist

- [ ] `StationSearchResult` no longer has a default argument for `hasCachedSnapshot`.
- [ ] All `StationSearchResult(...)` call sites pass `hasCachedSnapshot` explicitly.
- [ ] `DefaultStationRepository` constructor no longer mentions `SeedStationRemoteDataSource` or `Optional`.
- [ ] The only seed/prod selection logic lives in `FlavorAwareStationRemoteDataSource`.
- [ ] `StationRetryPolicy` still wraps `remoteDataSource.fetchStations(query)` in repository refresh.
- [ ] Existing demo seed binding in `app/src/demo/kotlin/com/gasstation/di/DemoStationRemoteDataSourceModule.kt` remains unchanged.
- [ ] `StationDataModule` has exactly one binding/provider for `StationRemoteDataSource`.
- [ ] No UI string, sorting, cache key, retry reason, or event payload changes.
