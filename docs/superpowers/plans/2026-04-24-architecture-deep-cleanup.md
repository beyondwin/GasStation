# Architecture Deep Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `core:* -> domain:station` 역의존과 `domain:settings -> domain:station` 커플링을 제거하고, station list refresh 흐름을 위치, 검색, retry, logging 책임으로 분리한다.

**Architecture:** 공유 enum 6개는 `core:model`로 이동한다. `StationListViewModel`은 UI state 조합과 action dispatch만 맡고, 위치 상태는 `LocationStateMachine`, 검색 query/cache/failure 판단은 `StationSearchOrchestrator`, 일시적 원격 실패 재시도는 `StationRetryPolicy`가 맡는다. 사용자 동작, demo/prod 경로, 캐시 의미는 유지한다.

**Tech Stack:** Kotlin, Gradle multi-module, Jetpack Compose, Hilt, Room, Retrofit, Coroutines/Flow, JUnit, Turbine

---

## Operating Notes

- 시작 전 항상 `git status --short`를 확인한다.
- 활성 모듈은 `settings.gradle.kts`의 include 기준으로 판단한다.
- `demo`와 `prod`는 모두 정식 실행 경로다.
- 가격 우선 UI 위계는 바꾸지 않는다.
- 주소 라벨은 검색 입력이 아니라 표시용 context다. 주소 조회는 주유소 검색을 막지 않는다.
- 캐시 존재 판단은 `fetchedAt != null`이 아니라 `StationSearchResult.hasCachedSnapshot`을 따른다.
- 문서와 테스트 명령은 Android library 모듈 기준 task명을 쓴다. 예: `:data:station:testDebugUnitTest`, `:feature:station-list:testDebugUnitTest`.

## Current Code Facts

- `StationListViewModel.kt`는 현재 522줄이다.
- enum import 영향은 현재 기준 51개 `.kt` 파일, 129개 import occurrence다. 실행 시 다시 측정한다.
- `core:datastore`, `core:network`, `core:designsystem`, `domain:settings`가 현재 `domain:station`에 직접 또는 간접 enum 의존을 가진다.
- `feature:settings`는 enum만 사용하므로 `domain:station` 직접 의존을 제거할 수 있다.
- `feature:watchlist`, `feature:station-list`, `data:station`, `tools:demo-seed`, `app`은 enum 외 station domain 계약도 사용하므로 `domain:station` 의존을 유지한다.

## File Structure

### Move To `core:model`

| From | To |
| --- | --- |
| `domain/station/src/main/kotlin/com/gasstation/domain/station/model/Brand.kt` | `core/model/src/main/kotlin/com/gasstation/core/model/Brand.kt` |
| `domain/station/src/main/kotlin/com/gasstation/domain/station/model/BrandFilter.kt` | `core/model/src/main/kotlin/com/gasstation/core/model/BrandFilter.kt` |
| `domain/station/src/main/kotlin/com/gasstation/domain/station/model/FuelType.kt` | `core/model/src/main/kotlin/com/gasstation/core/model/FuelType.kt` |
| `domain/station/src/main/kotlin/com/gasstation/domain/station/model/MapProvider.kt` | `core/model/src/main/kotlin/com/gasstation/core/model/MapProvider.kt` |
| `domain/station/src/main/kotlin/com/gasstation/domain/station/model/SearchRadius.kt` | `core/model/src/main/kotlin/com/gasstation/core/model/SearchRadius.kt` |
| `domain/station/src/main/kotlin/com/gasstation/domain/station/model/SortOrder.kt` | `core/model/src/main/kotlin/com/gasstation/core/model/SortOrder.kt` |

### Create

| File | Owner | Responsibility |
| --- | --- | --- |
| `core/model/src/test/kotlin/com/gasstation/core/model/SharedEnumContractTest.kt` | `core:model` | enum identity and no UI/transport field contract |
| `data/station/src/main/kotlin/com/gasstation/data/station/StationRetryPolicy.kt` | `data:station` | retry once for retryable refresh failures |
| `data/station/src/test/kotlin/com/gasstation/data/station/StationRetryPolicyTest.kt` | `data:station` | retry policy behavior and retry logging |
| `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/LocationStateMachine.kt` | `feature:station-list` | permission, GPS, coordinates, address label, recovery flag |
| `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/LocationStateMachineTest.kt` | `feature:station-list` | location state transitions |
| `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationSearchOrchestrator.kt` | `feature:station-list` | active query, observed result, cache state, pending blocking failure |
| `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationSearchOrchestratorTest.kt` | `feature:station-list` | query/cache/failure state transitions |

### Modify

| File | Change |
| --- | --- |
| `core/datastore/build.gradle.kts` | remove `domain:station`, add `core:model` |
| `core/network/build.gradle.kts` | remove `domain:station` |
| `core/designsystem/build.gradle.kts` | replace `domain:station` with `core:model` |
| `domain/settings/build.gradle.kts` | replace `domain:station` with `core:model` |
| `feature/settings/build.gradle.kts` | replace `domain:station` with `core:model` |
| `data/settings/build.gradle.kts` | replace test `domain:station` with `core:model` |
| `domain/station/src/main/kotlin/com/gasstation/domain/station/model/*.kt` | import moved enum types from `core:model` where needed |
| `domain/station/src/test/kotlin/com/gasstation/domain/station/DomainContractSurfaceTest.kt` | remove enum identity assertions, keep station contract assertions |
| `domain/station/src/test/kotlin/com/gasstation/domain/station/BrandFilterTest.kt` | move or repoint to `core:model` |
| `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt` | inject and apply `StationRetryPolicy` |
| `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationEvent.kt` | add structured error events |
| `app/src/main/java/com/gasstation/analytics/LogcatStationEventLogger.kt` | map new events |
| `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt` | integrate `LocationStateMachine` and `StationSearchOrchestrator` |
| `docs/architecture.md` | update dependency graph and runtime flow |
| `docs/module-contracts.md` | update module ownership and direct dependency table |
| `docs/state-model.md` | replace `StationListSessionState` with extracted state owners |
| `docs/offline-strategy.md` | mention retry without cache invalidation |
| `docs/test-strategy.md` | add new test files and responsibilities |
| `docs/verification-matrix.md` | include correct module verification commands |

---

## Task 0: Baseline And Safety Checks

**Files:**
- Read: `settings.gradle.kts`
- Read: `docs/module-contracts.md`
- Read: `docs/agent-workflow.md`
- Read: `docs/state-model.md`
- Read: `docs/offline-strategy.md`
- Read: `docs/test-strategy.md`
- Read: `docs/verification-matrix.md`

- [x] **Step 1: Confirm clean working tree**

Run:

```bash
git status --short
```

Expected: no output. If there is output, inspect it and do not overwrite user changes.

- [x] **Step 2: Confirm active modules**

Run:

```bash
sed -n '1,220p' settings.gradle.kts
```

Expected: includes `:app`, `:core:model`, `:core:designsystem`, `:core:location`, `:core:network`, `:core:database`, `:core:datastore`, `:domain:location`, `:domain:settings`, `:domain:station`, `:data:settings`, `:data:station`, `:feature:settings`, `:feature:station-list`, `:feature:watchlist`, `:tools:demo-seed`, `:benchmark`.

- [x] **Step 3: Measure current enum import surface**

Run:

```bash
rg -l "import com\.gasstation\.domain\.station\.model\.(Brand|BrandFilter|FuelType|MapProvider|SearchRadius|SortOrder)" -g "*.kt" | wc -l
rg "import com\.gasstation\.domain\.station\.model\.(Brand|BrandFilter|FuelType|MapProvider|SearchRadius|SortOrder)" -g "*.kt" | wc -l
```

Expected at the time this plan was written: `51` files and `129` import occurrences. If counts differ, continue using the current repo output.

- [x] **Step 4: Commit nothing**

This task is read-only.

---

## Task 1: Move Shared Enums To `core:model`

**Files:**
- Create: `core/model/src/main/kotlin/com/gasstation/core/model/Brand.kt`
- Create: `core/model/src/main/kotlin/com/gasstation/core/model/BrandFilter.kt`
- Create: `core/model/src/main/kotlin/com/gasstation/core/model/FuelType.kt`
- Create: `core/model/src/main/kotlin/com/gasstation/core/model/MapProvider.kt`
- Create: `core/model/src/main/kotlin/com/gasstation/core/model/SearchRadius.kt`
- Create: `core/model/src/main/kotlin/com/gasstation/core/model/SortOrder.kt`
- Delete: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/Brand.kt`
- Delete: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/BrandFilter.kt`
- Delete: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/FuelType.kt`
- Delete: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/MapProvider.kt`
- Delete: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/SearchRadius.kt`
- Delete: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/SortOrder.kt`
- Create: `core/model/src/test/kotlin/com/gasstation/core/model/SharedEnumContractTest.kt`
- Modify: all `.kt` files importing the six moved types

- [x] **Step 1: Move the six enum files**

Use `git mv` so history is preserved:

```bash
git mv domain/station/src/main/kotlin/com/gasstation/domain/station/model/Brand.kt core/model/src/main/kotlin/com/gasstation/core/model/Brand.kt
git mv domain/station/src/main/kotlin/com/gasstation/domain/station/model/BrandFilter.kt core/model/src/main/kotlin/com/gasstation/core/model/BrandFilter.kt
git mv domain/station/src/main/kotlin/com/gasstation/domain/station/model/FuelType.kt core/model/src/main/kotlin/com/gasstation/core/model/FuelType.kt
git mv domain/station/src/main/kotlin/com/gasstation/domain/station/model/MapProvider.kt core/model/src/main/kotlin/com/gasstation/core/model/MapProvider.kt
git mv domain/station/src/main/kotlin/com/gasstation/domain/station/model/SearchRadius.kt core/model/src/main/kotlin/com/gasstation/core/model/SearchRadius.kt
git mv domain/station/src/main/kotlin/com/gasstation/domain/station/model/SortOrder.kt core/model/src/main/kotlin/com/gasstation/core/model/SortOrder.kt
```

- [x] **Step 2: Change package declarations**

Each moved file must start with:

```kotlin
package com.gasstation.core.model
```

`BrandFilter.kt` must keep this behavior:

```kotlin
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

- [x] **Step 3: Rewrite imports for moved enum types**

Run:

```bash
perl -pi -e 's/import com\.gasstation\.domain\.station\.model\.Brand$/import com.gasstation.core.model.Brand/' $(rg -l "import com\.gasstation\.domain\.station\.model\.Brand$" -g "*.kt")
perl -pi -e 's/import com\.gasstation\.domain\.station\.model\.BrandFilter$/import com.gasstation.core.model.BrandFilter/' $(rg -l "import com\.gasstation\.domain\.station\.model\.BrandFilter$" -g "*.kt")
perl -pi -e 's/import com\.gasstation\.domain\.station\.model\.FuelType$/import com.gasstation.core.model.FuelType/' $(rg -l "import com\.gasstation\.domain\.station\.model\.FuelType$" -g "*.kt")
perl -pi -e 's/import com\.gasstation\.domain\.station\.model\.MapProvider$/import com.gasstation.core.model.MapProvider/' $(rg -l "import com\.gasstation\.domain\.station\.model\.MapProvider$" -g "*.kt")
perl -pi -e 's/import com\.gasstation\.domain\.station\.model\.SearchRadius$/import com.gasstation.core.model.SearchRadius/' $(rg -l "import com\.gasstation\.domain\.station\.model\.SearchRadius$" -g "*.kt")
perl -pi -e 's/import com\.gasstation\.domain\.station\.model\.SortOrder$/import com.gasstation.core.model.SortOrder/' $(rg -l "import com\.gasstation\.domain\.station\.model\.SortOrder$" -g "*.kt")
```

If one of the `rg -l` commands returns no files, run the corresponding replacement manually with the remaining files found by Step 4.

- [x] **Step 4: Verify no moved enum imports remain**

Run:

```bash
rg "import com\.gasstation\.domain\.station\.model\.(Brand|BrandFilter|FuelType|MapProvider|SearchRadius|SortOrder)" -g "*.kt"
```

Expected: no output.

- [x] **Step 5: Add enum contract test to `core:model`**

Create `core/model/src/test/kotlin/com/gasstation/core/model/SharedEnumContractTest.kt`:

```kotlin
package com.gasstation.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedEnumContractTest {

    @Test
    fun `shared fuel type identities stay stable without ui or transport fields`() {
        assertEquals(
            listOf("GASOLINE", "DIESEL", "PREMIUM_GASOLINE", "KEROSENE", "LPG"),
            FuelType.entries.map { it.name },
        )
        assertTrue(FuelType::class.java.declaredFields.none { it.name.contains("code", ignoreCase = true) })
        assertFalse(FuelType::class.java.declaredMethods.any { it.name == "getDisplayName" })
        assertFalse(FuelType::class.java.declaredMethods.any { it.name == "getProductCode" })
    }

    @Test
    fun `shared brand identities and filters stay stable`() {
        assertEquals(
            listOf("SKE", "GSC", "HDO", "SOL", "RTO", "RTX", "NHO", "ETC", "E1G", "SKG"),
            Brand.entries.map { it.name },
        )
        assertTrue(Brand::class.java.declaredFields.none { it.name.contains("code", ignoreCase = true) })
        assertFalse(Brand::class.java.declaredMethods.any { it.name == "getDisplayName" })
        assertFalse(Brand::class.java.declaredMethods.any { it.name == "getBrandCode" })

        assertEquals(
            listOf("ALL", "SKE", "GSC", "HDO", "SOL", "RTO", "RTX", "NHO", "ETC", "E1G", "SKG"),
            BrandFilter.entries.map { it.name },
        )
        assertTrue(BrandFilter.ALL.matches(Brand.GSC))
        assertTrue(BrandFilter.GSC.matches(Brand.GSC))
        assertFalse(BrandFilter.GSC.matches(Brand.SKE))
    }

    @Test
    fun `shared preference enums stay stable`() {
        assertEquals(listOf("DISTANCE", "PRICE"), SortOrder.entries.map { it.name })
        assertEquals(3_000, SearchRadius.KM_3.meters)
        assertEquals(4_000, SearchRadius.KM_4.meters)
        assertEquals(5_000, SearchRadius.KM_5.meters)
        assertEquals(listOf("TMAP", "KAKAO_NAVI", "NAVER_MAP"), MapProvider.entries.map { it.name })
    }
}
```

- [x] **Step 6: Move or delete old domain enum tests**

Move `BrandFilterTest` to `core:model` if it tests only `BrandFilter.matches()`:

```bash
git mv domain/station/src/test/kotlin/com/gasstation/domain/station/BrandFilterTest.kt core/model/src/test/kotlin/com/gasstation/core/model/BrandFilterTest.kt
```

Then change its package to:

```kotlin
package com.gasstation.core.model
```

Remove imports for `Brand` and `BrandFilter` from that moved test because they now live in the same package.

- [x] **Step 7: Trim `DomainContractSurfaceTest`**

In `domain/station/src/test/kotlin/com/gasstation/domain/station/DomainContractSurfaceTest.kt`, remove imports for the six moved enum types and remove the test named:

```kotlin
fun `station domain enums keep stable identities without ui or transport fields`()
```

Keep the `StationEvent`, `StationRepository`, `StationSearchResult`, `StationPriceDelta`, and watchlist contract assertions.

- [x] **Step 8: Run focused tests and expect compile failures from missing Gradle dependencies**

Run:

```bash
./gradlew :core:model:test :domain:station:test
```

Expected before Task 2 is complete: `:core:model:test` should compile after package fixes. `:domain:station:test` may fail if imports or dependencies still reference old enum packages. Fix import misses before moving on.

Do not commit until Task 2 also passes. The enum move and Gradle dependency cleanup are one atomic slice.

---

## Task 2: Clean Gradle Dependencies For Enum Move

**Files:**
- Modify: `core/datastore/build.gradle.kts`
- Modify: `core/network/build.gradle.kts`
- Modify: `core/designsystem/build.gradle.kts`
- Modify: `domain/settings/build.gradle.kts`
- Modify: `feature/settings/build.gradle.kts`
- Modify: `data/settings/build.gradle.kts`

- [x] **Step 1: Update `core:datastore` dependencies**

Change `core/datastore/build.gradle.kts` dependencies to include `core:model` directly and remove `domain:station`:

```kotlin
dependencies {
    implementation(project(":core:model"))
    implementation(project(":domain:settings"))
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.core)
    testImplementation(libs.junit)
}
```

- [x] **Step 2: Update `core:network` dependencies**

Change `core/network/build.gradle.kts` dependencies so it no longer depends on `domain:station`:

```kotlin
dependencies {
    implementation(project(":core:model"))
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation("org.locationtech.proj4j:proj4j:1.4.1")
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
}
```

- [x] **Step 3: Update `core:designsystem` dependencies**

Change `core/designsystem/build.gradle.kts` dependencies to use `core:model`:

```kotlin
dependencies {
    implementation(project(":core:model"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
}
```

- [x] **Step 4: Update `domain:settings` dependencies**

Change `domain/settings/build.gradle.kts` dependencies to use `core:model`:

```kotlin
dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    testImplementation(libs.app.cash.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [x] **Step 5: Update `feature:settings` dependencies**

`feature:settings` uses settings use cases and enum types, but does not use station repository or station read models. Change `feature/settings/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":core:model"))
    implementation(project(":domain:settings"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
```

- [x] **Step 6: Update `data:settings` test dependency**

`data/settings/src/test/.../DefaultSettingsRepositoryTest.kt` only needs moved enum types from `core:model`. Change `data/settings/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":core:datastore"))
    implementation(project(":domain:settings"))
    testImplementation(project(":core:model"))
    testImplementation(libs.junit)
}
```

- [x] **Step 7: Verify removed dependency edges**

Run:

```bash
rg 'project\(":domain:station"\)' core/datastore/build.gradle.kts core/network/build.gradle.kts core/designsystem/build.gradle.kts domain/settings/build.gradle.kts feature/settings/build.gradle.kts data/settings/build.gradle.kts
```

Expected: no output.

- [x] **Step 8: Run enum and dependency verification**

Run:

```bash
./gradlew \
  :core:model:test \
  :domain:settings:test \
  :domain:station:test \
  :core:datastore:testDebugUnitTest \
  :core:network:test \
  :core:designsystem:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :data:settings:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [x] **Step 9: Commit enum move and dependency cleanup**

```bash
git add -A
git commit -m "refactor: move shared station enums to core:model"
```

---

## Task 3: Add Structured Error Events

**Files:**
- Modify: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationEvent.kt`
- Modify: `domain/station/src/test/kotlin/com/gasstation/domain/station/DomainContractSurfaceTest.kt`
- Modify: `app/src/main/java/com/gasstation/analytics/LogcatStationEventLogger.kt`

- [x] **Step 1: Add event variants**

Change `StationEvent.kt` so it includes these extra variants:

```kotlin
data class RefreshFailed(
    val reason: StationRefreshFailureReason,
) : StationEvent

data class LocationFailed(
    val resultType: String,
) : StationEvent

data class RetryAttempted(
    val originalReason: StationRefreshFailureReason,
    val succeeded: Boolean,
) : StationEvent
```

Add this import:

```kotlin
import com.gasstation.domain.station.StationRefreshFailureReason
```

`RefreshFailed` intentionally has no `wasRetried` boolean. Retry outcome is represented by `RetryAttempted`, which is emitted by `data:station`.

- [x] **Step 2: Update station event surface assertion**

In `DomainContractSurfaceTest`, update the `StationEvent` assertion:

```kotlin
assertEquals(
    setOf(
        "SearchRefreshed",
        "WatchToggled",
        "CompareViewed",
        "ExternalMapOpened",
        "RefreshFailed",
        "LocationFailed",
        "RetryAttempted",
    ),
    StationEvent::class.java.permittedSubclasses.map { it.simpleName }.toSet(),
)
```

- [x] **Step 3: Update logcat mapping**

In `LogcatStationEventLogger.kt`, add branches:

```kotlin
is StationEvent.RefreshFailed -> {
    "refresh_failed reason=${reason::class.java.simpleName}"
}
is StationEvent.LocationFailed -> {
    "location_failed resultType=$resultType"
}
is StationEvent.RetryAttempted -> {
    "retry_attempted originalReason=${originalReason::class.java.simpleName} succeeded=$succeeded"
}
```

- [x] **Step 4: Run event contract tests**

Run:

```bash
./gradlew :domain:station:test :app:testDemoDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [x] **Step 5: Commit structured event contract**

```bash
git add domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationEvent.kt \
        domain/station/src/test/kotlin/com/gasstation/domain/station/DomainContractSurfaceTest.kt \
        app/src/main/java/com/gasstation/analytics/LogcatStationEventLogger.kt
git commit -m "feat: add structured station failure events"
```

---

## Task 4: Add Retry Policy Tests In `data:station`

**Files:**
- Create: `data/station/src/test/kotlin/com/gasstation/data/station/StationRetryPolicyTest.kt`

- [x] **Step 1: Write retry policy tests first**

Create `data/station/src/test/kotlin/com/gasstation/data/station/StationRetryPolicyTest.kt`:

```kotlin
package com.gasstation.data.station

import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.model.StationEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StationRetryPolicyTest {

    private val logger = RecordingStationEventLogger()
    private val policy = StationRetryPolicy(stationEventLogger = logger)

    @Test
    fun `success on first attempt returns result without retry event`() = runTest {
        var callCount = 0

        val result = policy.withRetry {
            callCount += 1
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(1, callCount)
        assertTrue(logger.events.isEmpty())
    }

    @Test
    fun `timeout failure retries once and succeeds`() = runTest {
        var callCount = 0

        val result = policy.withRetry {
            callCount += 1
            if (callCount == 1) {
                throw StationRefreshException(StationRefreshFailureReason.Timeout)
            }
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, callCount)
        val event = assertIs<StationEvent.RetryAttempted>(logger.events.single())
        assertEquals(StationRefreshFailureReason.Timeout, event.originalReason)
        assertEquals(true, event.succeeded)
    }

    @Test
    fun `network failure retries once and succeeds`() = runTest {
        var callCount = 0

        val result = policy.withRetry {
            callCount += 1
            if (callCount == 1) {
                throw StationRefreshException(StationRefreshFailureReason.Network)
            }
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, callCount)
        val event = assertIs<StationEvent.RetryAttempted>(logger.events.single())
        assertEquals(StationRefreshFailureReason.Network, event.originalReason)
        assertEquals(true, event.succeeded)
    }

    @Test
    fun `retryable failure retries once then propagates second failure`() = runTest {
        var callCount = 0

        val exception = assertFailsWith<StationRefreshException> {
            policy.withRetry {
                callCount += 1
                throw StationRefreshException(StationRefreshFailureReason.Timeout)
            }
        }

        assertEquals(StationRefreshFailureReason.Timeout, exception.reason)
        assertEquals(2, callCount)
        val event = assertIs<StationEvent.RetryAttempted>(logger.events.single())
        assertEquals(StationRefreshFailureReason.Timeout, event.originalReason)
        assertEquals(false, event.succeeded)
    }

    @Test
    fun `invalid payload does not retry`() = runTest {
        var callCount = 0

        val exception = assertFailsWith<StationRefreshException> {
            policy.withRetry {
                callCount += 1
                throw StationRefreshException(StationRefreshFailureReason.InvalidPayload)
            }
        }

        assertEquals(StationRefreshFailureReason.InvalidPayload, exception.reason)
        assertEquals(1, callCount)
        assertTrue(logger.events.isEmpty())
    }

    @Test
    fun `unknown failure does not retry`() = runTest {
        var callCount = 0

        val exception = assertFailsWith<StationRefreshException> {
            policy.withRetry {
                callCount += 1
                throw StationRefreshException(StationRefreshFailureReason.Unknown)
            }
        }

        assertEquals(StationRefreshFailureReason.Unknown, exception.reason)
        assertEquals(1, callCount)
        assertTrue(logger.events.isEmpty())
    }

    @Test
    fun `cancellation is never retried`() = runTest {
        var callCount = 0

        assertFailsWith<CancellationException> {
            policy.withRetry {
                callCount += 1
                throw CancellationException("cancelled")
            }
        }

        assertEquals(1, callCount)
        assertTrue(logger.events.isEmpty())
    }

    @Test
    fun `non station refresh exception is not retried`() = runTest {
        var callCount = 0

        assertFailsWith<IllegalStateException> {
            policy.withRetry {
                callCount += 1
                throw IllegalStateException("unexpected")
            }
        }

        assertEquals(1, callCount)
        assertTrue(logger.events.isEmpty())
    }

    @Test
    fun `retry waits 500ms before second attempt`() = runTest {
        var callCount = 0
        val attemptTimes = mutableListOf<Long>()

        policy.withRetry {
            callCount += 1
            attemptTimes += testScheduler.currentTime
            if (callCount == 1) {
                throw StationRefreshException(StationRefreshFailureReason.Network)
            }
            "ok"
        }

        assertEquals(listOf(0L, StationRetryPolicy.RETRY_DELAY_MS), attemptTimes)
    }
}

private class RecordingStationEventLogger : StationEventLogger {
    val events = mutableListOf<StationEvent>()

    override fun log(event: StationEvent) {
        events += event
    }
}
```

- [x] **Step 2: Run test and verify it fails because retry class does not exist**

Run:

```bash
./gradlew :data:station:testDebugUnitTest --tests "com.gasstation.data.station.StationRetryPolicyTest"
```

Expected: compilation failure for `StationRetryPolicy`.

Do not commit this failing test alone. Commit it with the implementation in Task 5.

---

## Task 5: Implement Retry Policy And Apply It To Repository

**Files:**
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/StationRetryPolicy.kt`
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/StationRetryPolicyTest.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/DefaultStationRepositoryTest.kt`
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/WatchlistRepositoryTest.kt`

- [x] **Step 1: Implement `StationRetryPolicy`**

Create `data/station/src/main/kotlin/com/gasstation/data/station/StationRetryPolicy.kt`:

```kotlin
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
            if (!exception.reason.isRetryable()) {
                throw exception
            }

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

- [x] **Step 2: Run retry policy tests**

Run:

```bash
./gradlew :data:station:testDebugUnitTest --tests "com.gasstation.data.station.StationRetryPolicyTest"
```

Expected: BUILD SUCCESSFUL.

- [x] **Step 3: Inject retry policy into repository**

In `DefaultStationRepository`, add constructor parameter:

```kotlin
private val retryPolicy: StationRetryPolicy,
```

Then change `refreshNearbyStations(query)` so remote fetch and failure conversion both run inside `withRetry`:

```kotlin
override suspend fun refreshNearbyStations(query: StationQuery) {
    val cacheKey = query.toCacheKey(bucketMeters = DEFAULT_BUCKET_METERS)
    val fetchedAt = clock.instant()
    val successResult = retryPolicy.withRetry {
        when (
            val result = if (seedRemoteDataSource.isPresent) {
                seedRemoteDataSource.get().fetchStations(query)
            } else {
                remoteDataSource.fetchStations(query)
            }
        ) {
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

- [x] **Step 4: Update repository test fixtures**

In `DefaultStationRepositoryTest` and `WatchlistRepositoryTest`, update repository helpers:

```kotlin
retryPolicy = StationRetryPolicy(RecordingStationEventLogger()),
```

Add this helper to each test file or a local test helper file in the same package:

```kotlin
private class RecordingStationEventLogger : StationEventLogger {
    val events = mutableListOf<StationEvent>()

    override fun log(event: StationEvent) {
        events += event
    }
}
```

Add imports:

```kotlin
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.model.StationEvent
```

- [x] **Step 5: Add repository retry tests**

In `DefaultStationRepositoryTest`, add a fake remote that returns multiple results:

```kotlin
private class QueueStationRemoteDataSource(
    private val results: ArrayDeque<RemoteStationFetchResult>,
) : StationRemoteDataSource {
    override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult =
        results.removeFirst()
}
```

Add test:

```kotlin
@Test
fun `refresh retries network failure once and stores successful retry result`() = runTest {
    val repository = repository(
        remoteDataSource = QueueStationRemoteDataSource(
            ArrayDeque(
                listOf(
                    RemoteStationFetchResult.Failure(StationRefreshFailureReason.Network),
                    RemoteStationFetchResult.Success(
                        listOf(
                            RemoteStation(
                                stationId = "station-1",
                                name = "Retry Station",
                                brandCode = "GSC",
                                priceWon = 1699,
                                coordinates = Coordinates(37.498095, 127.027610),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    repository.refreshNearbyStations(stationQuery())

    val result = repository.observeNearbyStations(stationQuery()).first()
    assertEquals(listOf("station-1"), result.stations.map { it.station.id })
    assertTrue(result.hasCachedSnapshot)
}
```

- [x] **Step 6: Run data station tests**

Run:

```bash
./gradlew :data:station:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [x] **Step 7: Commit retry policy**

```bash
git add -A
git commit -m "feat: retry transient station refresh failures"
```

---

## Task 6: Extract `LocationStateMachine`

**Files:**
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/LocationStateMachine.kt`
- Create: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/LocationStateMachineTest.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/GpsAvailabilityMonitorTest.kt`

- [ ] **Step 1: Create location state machine**

Create `LocationStateMachine.kt` with this responsibility boundary:

```kotlin
package com.gasstation.feature.stationlist

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.GetCurrentAddressUseCase
import com.gasstation.domain.location.GetCurrentLocationUseCase
import com.gasstation.domain.location.LocationAddressLookupResult
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.location.ObserveLocationAvailabilityUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LocationStateMachine @Inject constructor(
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

    suspend fun acquireLocation(): LocationAcquisitionResult =
        when (val result = getCurrentLocation(state.value.permissionState)) {
            is LocationLookupResult.Success -> {
                val coordinates = result.coordinates
                val previousCoordinates = state.value.currentCoordinates
                mutableState.update {
                    it.copy(
                        currentCoordinates = coordinates,
                        currentAddressLabel = if (previousCoordinates == coordinates) {
                            it.currentAddressLabel
                        } else {
                            null
                        },
                        hasDeniedLocationAccess = it.permissionState == LocationPermissionState.Denied,
                        needsRecoveryRefresh = false,
                    )
                }
                LocationAcquisitionResult.Success(coordinates)
            }
            LocationLookupResult.PermissionDenied -> LocationAcquisitionResult.PermissionDenied
            LocationLookupResult.TimedOut -> LocationAcquisitionResult.TimedOut
            LocationLookupResult.Unavailable -> LocationAcquisitionResult.Unavailable
            is LocationLookupResult.Error -> LocationAcquisitionResult.Error(result.throwable)
        }

    suspend fun resolveAddressLabel(coordinates: Coordinates): String? =
        when (val result = getCurrentAddress(coordinates)) {
            is LocationAddressLookupResult.Success -> result.addressLabel
            LocationAddressLookupResult.Unavailable,
            is LocationAddressLookupResult.Error -> null
        }

    fun onAddressResolved(coordinates: Coordinates, addressLabel: String?) {
        mutableState.update { current ->
            if (current.currentCoordinates == coordinates) {
                current.copy(currentAddressLabel = addressLabel)
            } else {
                current
            }
        }
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
)

sealed interface LocationAcquisitionResult {
    data class Success(val coordinates: Coordinates) : LocationAcquisitionResult
    data object PermissionDenied : LocationAcquisitionResult
    data object TimedOut : LocationAcquisitionResult
    data object Unavailable : LocationAcquisitionResult
    data class Error(val throwable: Throwable) : LocationAcquisitionResult
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
    val needsRecoveryRefresh = !isLocationUsable() &&
        updated.isLocationUsable() &&
        currentCoordinates != null &&
        !hasDeniedLocationAccess
    return updated.copy(
        needsRecoveryRefresh = updated.needsRecoveryRefresh || needsRecoveryRefresh,
    )
}

private fun LocationState.isLocationUsable(): Boolean =
    isGpsEnabled &&
        (
            permissionState != LocationPermissionState.Denied ||
                hasDeniedLocationAccess
            )
```

- [ ] **Step 2: Write location state tests**

Create tests covering:

```kotlin
@Test fun `initial state starts denied with no coordinates`()
@Test fun `permission change updates permission state`()
@Test fun `gps availability change marks availability known`()
@Test fun `successful location acquisition stores coordinates and resets recovery flag`()
@Test fun `permission denied result does not set coordinates`()
@Test fun `timeout result maps to timed out acquisition result`()
@Test fun `unavailable result maps to unavailable acquisition result`()
@Test fun `error result maps to error acquisition result`()
@Test fun `address resolution updates label only for current coordinates`()
@Test fun `recovery refresh is set when location becomes usable after prior coordinates`()
```

Use a fake `LocationRepository`, then construct existing use cases:

```kotlin
private fun createMachine(
    repository: LocationRepository = FakeLocationRepository(),
): LocationStateMachine = LocationStateMachine(
    getCurrentLocation = GetCurrentLocationUseCase(repository),
    getCurrentAddress = GetCurrentAddressUseCase(repository),
    observeAvailability = ObserveLocationAvailabilityUseCase(repository),
)
```

- [ ] **Step 3: Run location state machine tests**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest --tests "com.gasstation.feature.stationlist.LocationStateMachineTest"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Integrate into ViewModel without changing search behavior**

In `StationListViewModel`, replace direct location use cases with:

```kotlin
private val locationStateMachine: LocationStateMachine,
```

Keep loading and blocking failure in ViewModel for this task:

```kotlin
private val transientState = MutableStateFlow(StationListTransientState())

private data class StationListTransientState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val blockingFailure: StationListFailureReason? = null,
)
```

Combine `preferences`, `locationStateMachine.state`, `transientState`, and `searchResult` for UI state. `LocationStateMachine` must not own `blockingFailure`, `isLoading`, or `isRefreshing`.

- [ ] **Step 5: Keep address lookup non-blocking**

Replace old `refreshAddressLabel(coordinates)` with:

```kotlin
private fun refreshAddressLabel(coordinates: Coordinates) {
    viewModelScope.launch {
        val addressLabel = locationStateMachine.resolveAddressLabel(coordinates)
        locationStateMachine.onAddressResolved(coordinates, addressLabel)
    }
}
```

Call this after successful location acquisition, but do not wait for it before `refreshNearbyStations(query)`.

- [ ] **Step 6: Update failure mapping and logging**

Add:

```kotlin
private fun LocationAcquisitionResult.failureEventType(): String? = when (this) {
    is LocationAcquisitionResult.Success -> null
    LocationAcquisitionResult.PermissionDenied -> "PermissionDenied"
    LocationAcquisitionResult.TimedOut -> "TimedOut"
    LocationAcquisitionResult.Unavailable -> "Unavailable"
    is LocationAcquisitionResult.Error -> "Error"
}
```

When location acquisition fails, log:

```kotlin
acquisitionResult.failureEventType()?.let { resultType ->
    stationEventLogger.log(StationEvent.LocationFailed(resultType = resultType))
}
```

Keep snackbar and blocking failure messages exactly as they are today.

- [ ] **Step 7: Update ViewModel tests**

Update ViewModel and GPS monitor test helpers to construct a `LocationStateMachine`:

```kotlin
val locationStateMachine = LocationStateMachine(
    getCurrentLocation = GetCurrentLocationUseCase(locationRepository),
    getCurrentAddress = GetCurrentAddressUseCase(locationRepository),
    observeAvailability = ObserveLocationAvailabilityUseCase(locationRepository),
)
```

Then pass `locationStateMachine = locationStateMachine` to `StationListViewModel`.

- [ ] **Step 8: Run station-list tests**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit location extraction**

```bash
git add -A
git commit -m "refactor: extract station list location state machine"
```

---

## Task 7: Extract `StationSearchOrchestrator`

**Files:**
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationSearchOrchestrator.kt`
- Create: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationSearchOrchestratorTest.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`

- [ ] **Step 1: Create orchestrator with clear boundary**

`StationSearchOrchestrator` owns:

- current active query
- observed `StationSearchResult`
- `CachedSnapshotState`
- pending blocking refresh failure
- refresh call wrapper

It does not own:

- permission/GPS/location lookup
- snackbar messages
- external map action
- watch toggle action
- settings writes

Create `StationSearchOrchestrator.kt`:

```kotlin
package com.gasstation.feature.stationlist

import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.usecase.ObserveNearbyStationsUseCase
import com.gasstation.domain.station.usecase.RefreshNearbyStationsUseCase
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
class StationSearchOrchestrator @Inject constructor(
    private val observeNearbyStations: ObserveNearbyStationsUseCase,
    private val refreshNearbyStations: RefreshNearbyStationsUseCase,
) {
    private val mutableActiveQueryState = MutableStateFlow(ActiveStationQueryState())
    private val mutableSearchResult = MutableStateFlow(emptySearchResult())
    private val mutableBlockingFailure = MutableStateFlow<StationListFailureReason?>(null)
    private val pendingBlockingFailure = MutableStateFlow<PendingBlockingFailure?>(null)

    val activeQueryState = mutableActiveQueryState.asStateFlow()
    val searchResult = mutableSearchResult.asStateFlow()
    val blockingFailure = mutableBlockingFailure.asStateFlow()

    fun observe(queryFlow: Flow<StationQuery?>): Flow<StationSearchResult> =
        queryFlow.distinctUntilChanged()
            .onEach(::onQueryChanged)
            .flatMapLatest { query ->
                if (query == null) {
                    flowOf(emptySearchResult())
                } else {
                    observeNearbyStations(query)
                }
            }
            .onEach(::onObservedResult)

    suspend fun refresh(query: StationQuery): RefreshOutcome {
        return try {
            refreshNearbyStations(query)
            if (activeQueryState.value.query == query) {
                pendingBlockingFailure.value = null
                mutableBlockingFailure.value = null
            }
            RefreshOutcome.Success
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (throwable: Throwable) {
            RefreshOutcome.Failed((throwable as? StationRefreshException)?.reason)
        }
    }

    fun onRefreshFailure(query: StationQuery, reason: StationRefreshFailureReason?) {
        if (activeQueryState.value.query != query) return
        val failureReason = reason.toStationListFailureReason()
        when (activeQueryState.value.cacheState) {
            CachedSnapshotState.Present -> {
                pendingBlockingFailure.value = null
                mutableBlockingFailure.value = null
            }
            CachedSnapshotState.Absent -> {
                pendingBlockingFailure.value = null
                mutableBlockingFailure.value = failureReason
            }
            CachedSnapshotState.Unknown -> {
                pendingBlockingFailure.value = PendingBlockingFailure(query, failureReason)
            }
        }
    }

    fun shouldRefreshForCriteriaChange(previous: StationQuery?, next: StationQuery?): Boolean =
        previous != null &&
            next != null &&
            previous.coordinates == next.coordinates &&
            (
                previous.radius != next.radius ||
                    previous.fuelType != next.fuelType ||
                    previous.brandFilter != next.brandFilter ||
                    previous.sortOrder != next.sortOrder
                )

    private fun onQueryChanged(query: StationQuery?) {
        val previousQuery = activeQueryState.value.query
        mutableActiveQueryState.value = ActiveStationQueryState(
            query = query,
            cacheState = if (query == null) CachedSnapshotState.Absent else CachedSnapshotState.Unknown,
        )
        if (previousQuery != query) {
            pendingBlockingFailure.value = null
            mutableBlockingFailure.value = null
        }
    }

    private fun onObservedResult(result: StationSearchResult) {
        mutableSearchResult.value = result
        val hasCachedSnapshot = result.hasCachedSnapshot
        mutableActiveQueryState.update { current ->
            current.copy(
                cacheState = if (hasCachedSnapshot) {
                    CachedSnapshotState.Present
                } else {
                    CachedSnapshotState.Absent
                },
            )
        }
        syncBlockingFailureWithObservedResult(hasCachedSnapshot)
    }

    private fun syncBlockingFailureWithObservedResult(hasCachedSnapshot: Boolean) {
        if (hasCachedSnapshot) {
            pendingBlockingFailure.value = null
            mutableBlockingFailure.value = null
            return
        }

        val activeQuery = activeQueryState.value.query ?: return
        val pendingFailure = pendingBlockingFailure.value
            ?.takeIf { it.query == activeQuery }
            ?: return
        pendingBlockingFailure.value = null
        mutableBlockingFailure.value = pendingFailure.reason
    }
}

data class ActiveStationQueryState(
    val query: StationQuery? = null,
    val cacheState: CachedSnapshotState = CachedSnapshotState.Absent,
)

data class PendingBlockingFailure(
    val query: StationQuery,
    val reason: StationListFailureReason,
)

enum class CachedSnapshotState {
    Unknown,
    Present,
    Absent,
}

sealed interface RefreshOutcome {
    data object Success : RefreshOutcome
    data class Failed(val reason: StationRefreshFailureReason?) : RefreshOutcome
}

private fun StationRefreshFailureReason?.toStationListFailureReason(): StationListFailureReason = when (this) {
    StationRefreshFailureReason.Timeout -> StationListFailureReason.RefreshTimedOut
    StationRefreshFailureReason.Network,
    StationRefreshFailureReason.InvalidPayload,
    StationRefreshFailureReason.Unknown,
    null -> StationListFailureReason.RefreshFailed
}

private fun emptySearchResult(): StationSearchResult = StationSearchResult(
    stations = emptyList(),
    freshness = StationFreshness.Stale,
    fetchedAt = null,
)
```

- [ ] **Step 2: Write orchestrator tests**

Create tests covering:

```kotlin
@Test fun `null query emits empty stale result`()
@Test fun `new query observes repository result`()
@Test fun `query change clears blocking failure`()
@Test fun `criteria change with same coordinates requires refresh`()
@Test fun `criteria change with different coordinates does not count as criteria refresh`()
@Test fun `refresh success clears blocking failure for active query`()
@Test fun `refresh failure with cached snapshot does not expose blocking failure`()
@Test fun `refresh failure without cached snapshot exposes blocking failure`()
@Test fun `refresh failure before cache state is known waits for observed result`()
```

Use a fake `StationRepository` with `MutableSharedFlow<StationSearchResult>` like existing `StationListViewModelTest`.

- [ ] **Step 3: Run orchestrator tests**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest --tests "com.gasstation.feature.stationlist.StationSearchOrchestratorTest"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Integrate orchestrator into ViewModel**

Replace direct `observeNearbyStations`, `refreshNearbyStations`, `activeQueryState`, `pendingBlockingFailure`, `searchResult`, `CachedSnapshotState`, and `shouldRefreshForCriteriaChange()` ownership with:

```kotlin
private val searchOrchestrator: StationSearchOrchestrator,
```

The ViewModel still builds `StationQuery` from preferences and current coordinates. The orchestrator observes that query flow.

- [ ] **Step 5: Log refresh failures from ViewModel**

When `RefreshOutcome.Failed(reason)` returns:

```kotlin
reason?.let {
    stationEventLogger.log(StationEvent.RefreshFailed(reason = it))
}
searchOrchestrator.onRefreshFailure(query = query, reason = reason)
mutableEffects.emit(StationListEffect.ShowSnackbar(reason.refreshFailureMessage()))
```

Use:

```kotlin
private fun StationRefreshFailureReason?.refreshFailureMessage(): String = when (this) {
    StationRefreshFailureReason.Timeout -> "서버 응답이 늦어 가격을 새로고침하지 못했습니다."
    StationRefreshFailureReason.Network,
    StationRefreshFailureReason.InvalidPayload,
    StationRefreshFailureReason.Unknown,
    null -> "주유소 목록을 새로고침하지 못했습니다."
}
```

- [ ] **Step 6: Run station-list tests**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Check ViewModel size**

Run:

```bash
wc -l feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt
```

Expected: lower than 350 lines. If slightly above 350 because of imports or helper functions, inspect whether the remaining code is UI action dispatch and state composition rather than hidden search/location policy.

- [ ] **Step 8: Commit search orchestrator extraction**

```bash
git add -A
git commit -m "refactor: extract station search orchestration"
```

---

## Task 8: Update Documentation

**Files:**
- Modify: `docs/architecture.md`
- Modify: `docs/module-contracts.md`
- Modify: `docs/state-model.md`
- Modify: `docs/offline-strategy.md`
- Modify: `docs/test-strategy.md`
- Modify: `docs/verification-matrix.md`

- [ ] **Step 1: Update module graph docs**

In `docs/architecture.md`, remove these graph edges:

```text
cdesign --> domStation
cstore --> domStation
cnetwork --> domStation
domSettings --> domStation
```

Add or keep:

```text
cdesign --> cmodel
cstore --> cmodel
cnetwork --> cmodel
domSettings --> cmodel
```

Update `core:model` responsibility to include:

```text
Coordinates, DistanceMeters, MoneyWon value objects plus Brand, BrandFilter, FuelType, MapProvider, SearchRadius, SortOrder shared enum vocabulary.
```

- [ ] **Step 2: Update module contracts**

In `docs/module-contracts.md`:

- `core:model`: add the six shared enums to owned scope.
- `core:designsystem`: direct dependency should be `core:model`, not `domain:station`.
- `core:network`: direct dependency should be `core:model`, not `domain:station`.
- `core:datastore`: direct dependencies should be `core:model`, `domain:settings`.
- `domain:settings`: direct dependency should be `core:model`.
- `feature:settings`: direct dependency should be `core:model`, `domain:settings`, `core:designsystem`.

- [ ] **Step 3: Update state model**

In `docs/state-model.md`, replace `StationListSessionState` as the sole owner with:

```text
LocationStateMachine owns permission, GPS availability, current coordinates, address label, denied-access flag, and recovery refresh flag.
StationSearchOrchestrator owns active query, cache snapshot state, observed search result, and pending blocking refresh failure.
StationListViewModel owns loading flags, user action dispatch, one-shot effects, and final StationListUiState composition.
```

Mention that address label resolution is non-blocking and must not delay station refresh.

- [ ] **Step 4: Update offline strategy**

In `docs/offline-strategy.md`, add:

```text
Retry policy does not clear or mutate existing snapshots on failure. Timeout and Network failures are retried once before the final StationRefreshException reaches feature code. InvalidPayload and Unknown are not retried.
```

Keep the `hasCachedSnapshot` rule as the source of truth.

- [ ] **Step 5: Update test strategy**

In `docs/test-strategy.md`, add:

- `core:model/SharedEnumContractTest`
- `data:station/StationRetryPolicyTest`
- `feature:station-list/LocationStateMachineTest`
- `feature:station-list/StationSearchOrchestratorTest`

Update `StationListViewModelTest` description so it focuses on UI state composition, effects, and action dispatch after extraction.

- [ ] **Step 6: Update verification matrix**

In `docs/verification-matrix.md`, ensure the merge regression set includes:

```bash
:core:model:test
:core:datastore:testDebugUnitTest
:core:network:test
:core:designsystem:testDebugUnitTest
:domain:settings:test
:domain:station:test
:data:settings:testDebugUnitTest
:data:station:testDebugUnitTest
:feature:settings:testDebugUnitTest
:feature:station-list:testDebugUnitTest
:feature:watchlist:testDebugUnitTest
:app:testDemoDebugUnitTest
:app:testProdDebugUnitTest
:tools:demo-seed:test
:app:assembleDemoDebug
:app:assembleProdDebug
:benchmark:assemble
```

- [ ] **Step 7: Commit docs**

```bash
git add docs/architecture.md docs/module-contracts.md docs/state-model.md docs/offline-strategy.md docs/test-strategy.md docs/verification-matrix.md
git commit -m "docs: update architecture cleanup contracts"
```

---

## Task 9: Final Verification

**Files:**
- Verify: full working tree

- [ ] **Step 1: Verify removed build dependency edges**

Run:

```bash
rg 'project\(":domain:station"\)' core/datastore/build.gradle.kts core/network/build.gradle.kts core/designsystem/build.gradle.kts domain/settings/build.gradle.kts feature/settings/build.gradle.kts data/settings/build.gradle.kts
```

Expected: no output.

- [ ] **Step 2: Verify no moved enum imports remain**

Run:

```bash
rg "import com\.gasstation\.domain\.station\.model\.(Brand|BrandFilter|FuelType|MapProvider|SearchRadius|SortOrder)" -g "*.kt"
```

Expected: no output.

- [ ] **Step 3: Verify old enum files are gone**

Run:

```bash
ls domain/station/src/main/kotlin/com/gasstation/domain/station/model/Brand.kt \
   domain/station/src/main/kotlin/com/gasstation/domain/station/model/BrandFilter.kt \
   domain/station/src/main/kotlin/com/gasstation/domain/station/model/FuelType.kt \
   domain/station/src/main/kotlin/com/gasstation/domain/station/model/MapProvider.kt \
   domain/station/src/main/kotlin/com/gasstation/domain/station/model/SearchRadius.kt \
   domain/station/src/main/kotlin/com/gasstation/domain/station/model/SortOrder.kt
```

Expected: each path reports no such file.

- [ ] **Step 4: Run full merge regression set**

Run:

```bash
./gradlew \
  :domain:location:test \
  :core:model:test \
  :domain:station:test \
  :domain:settings:test \
  :core:database:testDebugUnitTest \
  :core:datastore:testDebugUnitTest \
  :core:designsystem:testDebugUnitTest \
  :core:location:testDebugUnitTest \
  :core:network:test \
  :data:settings:testDebugUnitTest \
  :data:station:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :tools:demo-seed:test \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  :benchmark:assemble
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Check final diff**

Run:

```bash
git status --short
git diff --stat HEAD
```

Expected: only intended files changed.

- [ ] **Step 6: Commit final verification fixes if any**

If Task 9 required fixes, commit them:

```bash
git add -A
git commit -m "chore: finish architecture cleanup verification"
```

If no fixes were required, do not create an empty commit.

---

## Success Criteria

- `core:datastore`, `core:network`, `core:designsystem`, `domain:settings`, `feature:settings`, and `data:settings` no longer depend on `domain:station` just to access shared enums.
- The six shared enum types live in `core:model`.
- `domain:station` still owns station contracts, use cases, repository interface, station read models, and station events.
- `StationRetryPolicy` retries `Timeout` and `Network` once, waits 500ms, preserves cancellation behavior, and logs retry outcomes.
- `RefreshFailed`, `LocationFailed`, and `RetryAttempted` are represented as `StationEvent` variants.
- `LocationStateMachine` owns location state only. It does not own loading flags or blocking failure policy.
- Address label resolution remains non-blocking for station search.
- `StationSearchOrchestrator` owns query/cache/failure state and preserves `hasCachedSnapshot` semantics.
- `StationListViewModel` is reduced below 350 lines or its remaining lines are only UI state composition, actions, effects, and small helpers.
- `demo` and `prod` behavior remains user-visible compatible.
- Full merge regression set passes.

## Explicit Non-Goals

- Do not change permission prompt UI flow.
- Do not change GPS settings UI flow.
- Do not change Room schema or DataStore persisted format.
- Do not add a new Gradle module.
- Do not change station card visual hierarchy.
- Do not make address lookup part of the station search input.
- Do not clear cache snapshots on refresh failure.
