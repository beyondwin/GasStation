# Clean Architecture SOLID Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** GasStation의 남은 clean architecture / SOLID 위반 후보를 제거하고, 큰 production/test 파일을 책임별로 분리한다.

**Architecture:** Cross-cutting observability 계약은 `core:observability`가 소유하고, 위치 주소 정규화는 pure domain location 함수로 올린다. 좌표 거리와 브랜드 fallback은 `core:model` 값 객체/vocabulary에 둔다. Station-list UI는 screen scaffold, card, state, query summary, body state 파일로 분리하고, data repository는 orchestration과 read-model assembly를 분리한다.

**Tech Stack:** Kotlin 2.3.20, AGP 9.1.1, Compose, Hilt, Room, kotlinx-coroutines-test, Robolectric/Compose UI tests, Roborazzi.

**Spec:** [`docs/superpowers/specs/2026-05-13-clean-architecture-solid-remediation-design.md`](../specs/2026-05-13-clean-architecture-solid-remediation-design.md)

---

## Source Of Truth

- Start with `AGENTS.md`, `docs/agent-workflow.md`, `docs/module-contracts.md`, `docs/architecture.md`, and the spec linked above.
- Use `settings.gradle.kts` as the active module list.
- Preserve the `demo` and `prod` paths as equal first-class runtime paths.
- Do not change user-facing behavior or visual hierarchy. Price remains the first read target in station cards.

## File Structure

Create:

- `core/observability/build.gradle.kts`
- `core/observability/src/main/kotlin/com/gasstation/core/observability/CrashReporter.kt`
- `core/observability/src/test/kotlin/com/gasstation/core/observability/CrashReporterContractTest.kt`
- `domain/location/src/main/kotlin/com/gasstation/domain/location/AddressLabelNormalizer.kt`
- `domain/location/src/test/kotlin/com/gasstation/domain/location/AddressLabelNormalizerTest.kt`
- `core/model/src/main/kotlin/com/gasstation/core/model/CoordinatesDistance.kt`
- `core/model/src/test/kotlin/com/gasstation/core/model/CoordinatesDistanceTest.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListBodyState.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListCards.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListStates.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListQuerySummary.kt`
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTestFixtures.kt`
- `data/station/src/test/kotlin/com/gasstation/data/station/RepositoryDoubles.kt`

Modify:

- `settings.gradle.kts`
- `app/build.gradle.kts`
- `core/location/build.gradle.kts`
- `data/station/build.gradle.kts`
- `core/location/src/main/kotlin/com/gasstation/core/location/AndroidAddressResolver.kt`
- `core/location/src/main/kotlin/com/gasstation/core/location/AddressLabelFormatter.kt`
- `core/location/src/test/kotlin/com/gasstation/core/location/AndroidAddressResolverCrashReporterTest.kt`
- `domain/station/src/main/kotlin/com/gasstation/domain/station/CrashReporter.kt` (delete)
- `domain/station/src/test/kotlin/com/gasstation/domain/station/CrashReporterContractTest.kt` (delete)
- `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/mapper/StationMappers.kt`
- `data/station/src/test/kotlin/com/gasstation/data/station/DefaultStationRepositoryTest.kt`
- `data/station/src/test/kotlin/com/gasstation/data/station/WatchlistRepositoryTest.kt`
- `app/src/demo/kotlin/com/gasstation/analytics/NoOpCrashReporter.kt`
- `app/src/demo/kotlin/com/gasstation/di/DemoCrashReporterModule.kt`
- `app/src/prod/kotlin/com/gasstation/analytics/LogcatCrashReporter.kt`
- `app/src/prod/kotlin/com/gasstation/di/ProdCrashReporterModule.kt`
- `app/src/testDemo/java/com/gasstation/analytics/NoOpCrashReporterTest.kt`
- `app/src/testProd/java/com/gasstation/analytics/LogcatCrashReporterTest.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/LocationStateMachine.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`
- `docs/architecture.md`
- `docs/module-contracts.md`
- `docs/project-reading-guide.md`
- `docs/state-model.md`
- `docs/verification-matrix.md`

---

## Task 0: Preflight

**Files:**
- Read: `AGENTS.md`
- Read: `docs/agent-workflow.md`
- Read: `docs/module-contracts.md`
- Read: `settings.gradle.kts`

- [x] **Step 1: Confirm clean starting state**

Run:

```bash
git status --short
```

Expected: no output, or only user changes that are unrelated and must be preserved.

- [x] **Step 2: Confirm the current issue still exists**

Run:

```bash
rg -n 'implementation\(project\(":domain:station"\)|domain\.station\.CrashReporter' core/location
rg -n 'toDongLevelAddressLabel|joinSplitAdministrativeTokens|findFallbackRegionIndexBefore' feature/station-list/src/main
rg -n 'distanceBetween|fun String\.toBrand|OpinetStationDto|toFuelProductCode' data/station/src/main/kotlin/com/gasstation/data/station
```

Expected:
- `core/location` still reports `domain:station`.
- station-list still reports address parsing helpers.
- data station still reports duplicated distance/brand/dead DTO mapping helpers.

- [x] **Step 3: Baseline compile**

Run:

```bash
./gradlew :data:station:compileDebugKotlin :feature:station-list:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 1: Move CrashReporter To `core:observability`

**Files:**
- Modify: `settings.gradle.kts`
- Create: `core/observability/build.gradle.kts`
- Create: `core/observability/src/main/kotlin/com/gasstation/core/observability/CrashReporter.kt`
- Create: `core/observability/src/test/kotlin/com/gasstation/core/observability/CrashReporterContractTest.kt`
- Delete: `domain/station/src/main/kotlin/com/gasstation/domain/station/CrashReporter.kt`
- Delete: `domain/station/src/test/kotlin/com/gasstation/domain/station/CrashReporterContractTest.kt`
- Modify imports in app/core/data tests listed in File Structure

- [x] **Step 1: Register the module**

Add `":core:observability"` after `":core:model"` in `settings.gradle.kts`:

```kotlin
include(
    ":app",
    ":core:model",
    ":core:observability",
    ":core:designsystem",
    ":core:location",
    ":core:network",
    ":core:database",
    ":core:datastore",
    ":domain:location",
    ":domain:settings",
    ":domain:station",
    ":data:settings",
    ":data:station",
    ":feature:settings",
    ":feature:station-list",
    ":feature:watchlist",
    ":tools:demo-seed",
    ":benchmark",
)
```

- [x] **Step 2: Add the module build file**

Create `core/observability/build.gradle.kts`:

```kotlin
plugins {
    id("gasstation.jvm.library")
}
```

- [x] **Step 3: Add the neutral CrashReporter contract**

Create `core/observability/src/main/kotlin/com/gasstation/core/observability/CrashReporter.kt`:

```kotlin
package com.gasstation.core.observability

interface CrashReporter {
    fun recordNonFatal(throwable: Throwable, metadata: Map<String, String> = emptyMap())
    fun log(message: String)
}
```

- [x] **Step 4: Move the contract test**

Create `core/observability/src/test/kotlin/com/gasstation/core/observability/CrashReporterContractTest.kt`:

```kotlin
package com.gasstation.core.observability

import kotlin.test.Test
import kotlin.test.assertEquals

class CrashReporterContractTest {
    @Test
    fun fake_reporter_records_nonfatal() {
        val reporter = FakeCrashReporter()
        val error = IllegalStateException("boom")

        reporter.recordNonFatal(error, mapOf("module" to "station"))

        assertEquals(1, reporter.records.size)
        assertEquals(error, reporter.records.first().throwable)
        assertEquals("station", reporter.records.first().metadata["module"])
    }

    @Test
    fun fake_reporter_logs_breadcrumb() {
        val reporter = FakeCrashReporter()

        reporter.log("refresh started")

        assertEquals(listOf("refresh started"), reporter.logs)
    }

    private class FakeCrashReporter : CrashReporter {
        data class Record(val throwable: Throwable, val metadata: Map<String, String>)

        val records = mutableListOf<Record>()
        val logs = mutableListOf<String>()

        override fun recordNonFatal(throwable: Throwable, metadata: Map<String, String>) {
            records += Record(throwable, metadata)
        }

        override fun log(message: String) {
            logs += message
        }
    }
}
```

- [x] **Step 5: Update module dependencies**

Change `core/location/build.gradle.kts` dependencies to:

```kotlin
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:observability"))
    implementation(project(":domain:location"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.play.services.location)
    testImplementation(libs.app.cash.turbine)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.junit)
}
```

Add this line to `data/station/build.gradle.kts`:

```kotlin
implementation(project(":core:observability"))
```

Add this line to `app/build.gradle.kts` with the other `core:*` modules:

```kotlin
implementation(project(":core:observability"))
```

- [x] **Step 6: Replace imports**

Run this search:

```bash
rg -l 'com\.gasstation\.domain\.station\.CrashReporter' app core data domain feature
```

In every returned file, replace:

```kotlin
import com.gasstation.domain.station.CrashReporter
```

with:

```kotlin
import com.gasstation.core.observability.CrashReporter
```

- [x] **Step 7: Delete the old domain files**

Delete:

```text
domain/station/src/main/kotlin/com/gasstation/domain/station/CrashReporter.kt
domain/station/src/test/kotlin/com/gasstation/domain/station/CrashReporterContractTest.kt
```

- [x] **Step 8: Verify the boundary**

Run:

```bash
rg -n 'domain\.station\.CrashReporter|project\(":domain:station"\)' core/location
./gradlew :core:observability:test :core:location:testDebugUnitTest :data:station:testDebugUnitTest :app:testDemoDebugUnitTest :app:testProdDebugUnitTest
```

Expected:
- `rg` prints no output.
- Gradle reports `BUILD SUCCESSFUL`.

- [x] **Step 9: Commit**

```bash
git add settings.gradle.kts core/observability core/location/build.gradle.kts data/station/build.gradle.kts app/build.gradle.kts app core data domain
git commit -m "refactor: move crash reporting contract to observability core"
```

---

## Task 2: Centralize Address Label Normalization In `domain:location`

**Files:**
- Create: `domain/location/src/main/kotlin/com/gasstation/domain/location/AddressLabelNormalizer.kt`
- Create: `domain/location/src/test/kotlin/com/gasstation/domain/location/AddressLabelNormalizerTest.kt`
- Modify: `core/location/src/main/kotlin/com/gasstation/core/location/AddressLabelFormatter.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/LocationStateMachine.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`

- [x] **Step 1: Add failing domain tests**

Create `domain/location/src/test/kotlin/com/gasstation/domain/location/AddressLabelNormalizerTest.kt`:

```kotlin
package com.gasstation.domain.location

import kotlin.test.Test
import kotlin.test.assertEquals

class AddressLabelNormalizerTest {
    @Test
    fun `normalizes road address through administrative dong`() {
        assertEquals(
            "서울 영등포구 당산동",
            normalizeCurrentAddressLabel("서울 영등포구 당산동 194-32"),
        )
    }

    @Test
    fun `ignores country code and building dong before administrative dong`() {
        assertEquals(
            "서울특별시 강남구 역삼동",
            normalizeCurrentAddressLabel("대한민국 서울 특별시 강남구 지하 번지 동 상가 27호 KR 서울특별시 강남구 역삼동"),
        )
    }

    @Test
    fun `joins split administrative region tokens`() {
        assertEquals(
            "서울특별시 강남구 역삼동",
            normalizeCurrentAddressLabel("서울 특별시 강남구 역삼동"),
        )
    }

    @Test
    fun `returns original label when administrative dong is unavailable`() {
        assertEquals(
            "서울특별시 강남구 테헤란로 152",
            normalizeCurrentAddressLabel("서울특별시 강남구 테헤란로 152"),
        )
    }
}
```

Run:

```bash
./gradlew :domain:location:test --tests com.gasstation.domain.location.AddressLabelNormalizerTest
```

Expected: FAIL because `normalizeCurrentAddressLabel` is not defined.

- [x] **Step 2: Add the domain normalizer**

Create `domain/location/src/main/kotlin/com/gasstation/domain/location/AddressLabelNormalizer.kt`:

```kotlin
package com.gasstation.domain.location

fun normalizeCurrentAddressLabel(rawLabel: String): String =
    administrativeDongLabelOrNull(rawLabel) ?: rawLabel

fun administrativeDongLabelOrNull(rawLabel: String): String? =
    rawLabel.toAddressTokens().toAdministrativeDongLabel()

private fun String.isAdministrativeDongPart(): Boolean {
    val normalized = trim('(', ')', '[', ']', ',', '.')
    return normalized.endsWith("동") && normalized.dropLast(1).any { it in '가'..'힣' }
}

private fun String.toAddressTokens(): List<String> = split(Regex("\\s+"))
    .asSequence()
    .map { it.trim('(', ')', '[', ']', ',', '.') }
    .filter(String::isNotBlank)
    .filterNot { it == "대한민국" || it.equals("KR", ignoreCase = true) }
    .toList()
    .joinSplitAdministrativeTokens()

private fun List<String>.joinSplitAdministrativeTokens(): List<String> {
    val result = mutableListOf<String>()
    var index = 0
    while (index < size) {
        val current = this[index]
        val next = getOrNull(index + 1)
        if (next in setOf("특별시", "광역시", "특별자치시", "특별자치도")) {
            result += current + next
            index += 2
        } else {
            result += current
            index += 1
        }
    }
    return result
}

private fun List<String>.toAdministrativeDongLabel(): String? {
    val dongIndex = indexOfLast(String::isAdministrativeDongPart)
    if (dongIndex < 0) return null

    val districtIndex = findLastAdminIndexBefore(dongIndex, suffixes = listOf("구", "군"))
    val regionIndex = if (districtIndex >= 0) {
        findLastAdminIndexBefore(districtIndex, suffixes = listOf("시", "도"))
            .takeIf { it >= 0 } ?: findFallbackRegionIndexBefore(districtIndex)
    } else {
        findLastAdminIndexBefore(dongIndex, suffixes = listOf("시", "도"))
    }

    return listOf(regionIndex, districtIndex, dongIndex)
        .filter { it >= 0 }
        .distinct()
        .map(::get)
        .joinToString(separator = " ")
        .takeIf(String::isNotBlank)
}

private fun List<String>.findLastAdminIndexBefore(endExclusive: Int, suffixes: List<String>): Int = asSequence()
    .take(endExclusive)
    .withIndex()
    .filter { (_, token) -> suffixes.any(token::endsWith) && token.dropLast(1).any { it in '가'..'힣' } }
    .lastOrNull()
    ?.index ?: -1

private fun List<String>.findFallbackRegionIndexBefore(endExclusive: Int): Int = asSequence()
    .take(endExclusive)
    .withIndex()
    .filter { (_, token) ->
        token in setOf("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종")
    }
    .lastOrNull()
    ?.index ?: -1
```

- [x] **Step 3: Use the normalizer in `core:location`**

Modify `AddressLabelFormatter.kt`:

```kotlin
import com.gasstation.domain.location.administrativeDongLabelOrNull
import com.gasstation.domain.location.normalizeCurrentAddressLabel
```

Replace `String.toAdministrativeDongLabel()` calls with:

```kotlin
administrativeDongLabelOrNull(cleanAddressPart)
```

Replace `joinThroughAdministrativeDong()` implementation with:

```kotlin
private fun List<String?>.joinThroughAdministrativeDong(): String? =
    mapNotNull(String?::cleanAddressPart)
        .joinToString(separator = " ")
        .let(::administrativeDongLabelOrNull)
```

Replace the `getAddressLine(0)` branch with:

```kotlin
getAddressLine(0)
    ?.cleanAddressPart()
    ?.let(::normalizeCurrentAddressLabel)
    ?.let { return it }
```

Delete the duplicate private token helpers from `AddressLabelFormatter.kt`.

- [x] **Step 4: Normalize fake/raw success values in `LocationStateMachine`**

In `LocationStateMachine.kt`, add:

```kotlin
import com.gasstation.domain.location.normalizeCurrentAddressLabel
```

Change the success branch in `resolveAddressLabel` to:

```kotlin
is LocationAddressLookupResult.Success -> normalizeCurrentAddressLabel(result.addressLabel)
```

- [x] **Step 5: Remove address parsing from `StationListScreen.kt`**

In `QueryContextSummary`, replace:

```kotlin
val addressLabel = uiState.currentAddressLabel
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.toDongLevelAddressLabel()
```

with:

```kotlin
val addressLabel = uiState.currentAddressLabel
    ?.trim()
    ?.takeIf(String::isNotEmpty)
```

Delete these functions from `StationListScreen.kt`:

```text
String.toDongLevelAddressLabel
String.isAdministrativeDongPart
String.toAddressTokens
List<String>.joinSplitAdministrativeTokens
List<String>.toAdministrativeDongLabel
List<String>.findLastAdminIndexBefore
List<String>.findFallbackRegionIndexBefore
```

- [x] **Step 6: Update UI tests to keep UI focused on rendering**

In `StationListScreenTest.kt`, change the test named `query context does not treat building dong as administrative dong` so it passes a normalized label:

```kotlin
currentAddressLabel = "서울특별시 강남구 역삼동",
```

Keep this assertion:

```kotlin
composeRule.onNodeWithText("서울특별시 강남구 역삼동").assertExists()
```

Remove the assertion that the raw full address is absent from this UI test; that behavior is now covered by `AddressLabelNormalizerTest`.

- [x] **Step 7: Verify**

Run:

```bash
rg -n 'toDongLevelAddressLabel|joinSplitAdministrativeTokens|findFallbackRegionIndexBefore' feature/station-list/src/main core/location/src/main
./gradlew :domain:location:test :core:location:testDebugUnitTest :feature:station-list:testDebugUnitTest
```

Expected:
- `rg` prints no output from feature main source.
- Gradle reports `BUILD SUCCESSFUL`.

- [x] **Step 8: Commit**

```bash
git add domain/location core/location feature/station-list
git commit -m "refactor: centralize address label normalization"
```

---

## Task 3: Centralize Distance And Brand Fallback In `core:model`

**Files:**
- Create: `core/model/src/main/kotlin/com/gasstation/core/model/CoordinatesDistance.kt`
- Create: `core/model/src/test/kotlin/com/gasstation/core/model/CoordinatesDistanceTest.kt`
- Modify: `core/model/src/main/kotlin/com/gasstation/core/model/Brand.kt`
- Modify: `core/model/src/test/kotlin/com/gasstation/core/model/ValueObjectInvariantTest.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/mapper/StationMappers.kt`
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/DefaultStationRepositoryTest.kt`

- [x] **Step 1: Add core model tests**

Create `CoordinatesDistanceTest.kt`:

```kotlin
package com.gasstation.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CoordinatesDistanceTest {
    @Test
    fun `distanceTo returns rounded haversine distance in meters`() {
        val origin = Coordinates(37.498095, 127.027610)
        val destination = Coordinates(37.499095, 127.027610)

        assertEquals(111, origin.distanceTo(destination).value)
    }

    @Test
    fun `distanceTo returns zero for identical coordinates`() {
        val origin = Coordinates(37.498095, 127.027610)

        assertEquals(DistanceMeters(0), origin.distanceTo(origin))
    }
}
```

Add this test to `ValueObjectInvariantTest.kt`:

```kotlin
@Test
fun `brand fromCode falls back to ETC for unknown code`() {
    assertEquals(Brand.GSC, Brand.fromCode("GSC"))
    assertEquals(Brand.ETC, Brand.fromCode("UNKNOWN"))
}
```

Run:

```bash
./gradlew :core:model:test
```

Expected: FAIL because `distanceTo` and `Brand.fromCode` are not defined.

- [x] **Step 2: Add `Coordinates.distanceTo`**

Create `core/model/src/main/kotlin/com/gasstation/core/model/CoordinatesDistance.kt`:

```kotlin
package com.gasstation.core.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

fun Coordinates.distanceTo(destination: Coordinates): DistanceMeters {
    val latitudeDelta = Math.toRadians(destination.latitude - latitude)
    val longitudeDelta = Math.toRadians(destination.longitude - longitude)
    val originLatitudeRadians = Math.toRadians(latitude)
    val destinationLatitudeRadians = Math.toRadians(destination.latitude)
    val haversine = sin(latitudeDelta / 2).let { it * it } +
        cos(originLatitudeRadians) *
        cos(destinationLatitudeRadians) *
        sin(longitudeDelta / 2).let { it * it }
    val centralAngle = 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
    return DistanceMeters((EARTH_RADIUS_METERS * centralAngle).roundToInt())
}
```

- [x] **Step 3: Add `Brand.fromCode`**

Modify `Brand.kt`:

```kotlin
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
    ;

    companion object {
        fun fromCode(code: String): Brand = entries.firstOrNull { it.name == code } ?: ETC
    }
}
```

- [x] **Step 4: Update data station mappers**

In `StationMappers.kt`, change imports to remove:

```kotlin
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.FuelType
import com.gasstation.core.network.model.OpinetStationDto
import com.gasstation.core.network.station.LocalKoreanCoordinateTransform
import com.gasstation.data.station.RemoteStation
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
```

Add:

```kotlin
import com.gasstation.core.model.distanceTo
```

Delete these functions from `StationMappers.kt`:

```text
OpinetStationDto.toRemoteStation
rawCoordinatesToWgs84
FuelType.toFuelProductCode
private fun String.toBrand
private fun distanceBetween
```

Change `toDomainStation` to:

```kotlin
internal fun StationCacheEntity.toDomainStation(queryCoordinates: Coordinates): Station = Station(
    id = stationId,
    name = name,
    brand = Brand.fromCode(brandCode),
    price = MoneyWon(priceWon),
    distance = queryCoordinates.distanceTo(Coordinates(latitude, longitude)),
    coordinates = Coordinates(latitude = latitude, longitude = longitude),
)
```

- [x] **Step 5: Update watchlist fallback distance and brand parsing**

In `DefaultStationRepository.kt`, add:

```kotlin
import com.gasstation.core.model.distanceTo
```

Remove:

```kotlin
import com.gasstation.core.model.Brand
import com.gasstation.core.model.DistanceMeters
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
```

Change the fallback station branch to:

```kotlin
latestPrice != null -> {
    val stationCoordinates = Coordinates(latitude, longitude)
    Station(
        id = stationId,
        name = name,
        brand = Brand.fromCode(brandCode),
        price = MoneyWon(latestPrice.priceWon),
        distance = origin.distanceTo(stationCoordinates),
        coordinates = stationCoordinates,
    )
}
```

Keep `import com.gasstation.core.model.Brand` if `Brand.fromCode` requires it after the cleanup.

Delete from `DefaultStationRepository.kt`:

```text
private fun String.toBrand
private fun distanceBetween
```

- [x] **Step 6: Update tests to use the shared distance helper**

In `DefaultStationRepositoryTest.kt`, replace `expectedDistanceMeters(origin, destination)` helper body usages with:

```kotlin
origin.distanceTo(destination).value
```

Add import:

```kotlin
import com.gasstation.core.model.distanceTo
```

Delete the private `expectedDistanceMeters` helper.

- [x] **Step 7: Verify no duplicate helpers remain**

Run:

```bash
rg -n 'distanceBetween|fun String\.toBrand|OpinetStationDto|toFuelProductCode|rawCoordinatesToWgs84' data/station/src/main/kotlin/com/gasstation/data/station
./gradlew :core:model:test :core:network:test :data:station:testDebugUnitTest
```

Expected:
- `rg` prints no output from `data/station/src/main`.
- Gradle reports `BUILD SUCCESSFUL`.

- [x] **Step 8: Commit**

```bash
git add core/model data/station
git commit -m "refactor: centralize station value conversions"
```

---

## Task 4: Split `StationListScreen.kt` By UI Responsibility

**Files:**
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListBodyState.kt`
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListCards.kt`
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListStates.kt`
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListQuerySummary.kt`

- [x] **Step 1: Move body state calculation**

Create `StationListBodyState.kt` with the sealed interface currently in `StationListScreen.kt:92-105` and `toBodyState()` currently in `StationListScreen.kt:750-757`.

The file must contain:

```kotlin
package com.gasstation.feature.stationlist

import com.gasstation.domain.location.LocationPermissionState

internal sealed interface StationListBodyState {
    data object PermissionRequired : StationListBodyState
    data object GpsRequired : StationListBodyState
    data object InitialLoading : StationListBodyState
    data class Failure(val reason: StationListFailureReason) : StationListBodyState
    data object Results : StationListBodyState
}

internal fun StationListUiState.toBodyState(): StationListBodyState = when {
    permissionState == LocationPermissionState.Denied &&
        !(hasDeniedLocationAccess && currentCoordinates != null) -> StationListBodyState.PermissionRequired
    !isGpsEnabled -> StationListBodyState.GpsRequired
    isLoading && stations.isEmpty() -> StationListBodyState.InitialLoading
    blockingFailure != null && stations.isEmpty() -> StationListBodyState.Failure(blockingFailure)
    else -> StationListBodyState.Results
}
```

Delete the moved declarations from `StationListScreen.kt`.

- [x] **Step 2: Move station card components**

Create `StationListCards.kt` and move these declarations without changing their bodies:

- `StationCard` from `StationListScreen.kt:367-462`
- `PriceDeltaIndicator` from `StationListScreen.kt:464-502`
- `FuelChip` from `StationListScreen.kt:504-534`
- `WatchToggleButton` from `StationListScreen.kt:536-565`
- `PriceDeltaTone.toColor()` from `StationListScreen.kt:849-853`

Keep these test tags public to the package:

```kotlin
internal const val STATION_LIST_METRIC_ROW_TAG = "station-list-metric-row"
internal const val STATION_LIST_CARD_TITLE_TAG = "station-list-card-title"
internal const val STATION_LIST_PRICE_CHANGE_TAG = "station-list-price-change"
internal const val STATION_LIST_FUEL_CHIP_TAG = "station-list-fuel-chip"
```

- [x] **Step 3: Move state surfaces**

Create `StationListStates.kt` and move these declarations without changing their user-facing strings:

- `PermissionRequired` from `StationListScreen.kt:567-577`
- `GpsRequired` from `StationListScreen.kt:579-589`
- `LoadingState` from `StationListScreen.kt:591-606`
- `FailureState` from `StationListScreen.kt:608-620`
- `EmptyState` from `StationListScreen.kt:622-631`
- `BrandedStateContainer` from `StationListScreen.kt:633-641`
- `StationListFailureCardContent` from `StationListScreen.kt:891`
- `StationListFailureReason.toFailureCardContent()` from `StationListScreen.kt:893-914`

- [x] **Step 4: Move query summary and labels**

Create `StationListQuerySummary.kt` and move:

- `QueryContextSummary` from `StationListScreen.kt:324-365`
- `SearchRadius.toLabel()` from `StationListScreen.kt:874-878`
- `FuelType.toLabel()` from `StationListScreen.kt:880-887`
- `BrandFilter.toLabel()` from `StationListScreen.kt:889`

The moved `QueryContextSummary` must use the post-Task-2 implementation:

```kotlin
val addressLabel = uiState.currentAddressLabel
    ?.trim()
    ?.takeIf(String::isNotEmpty)
```

Keep:

```kotlin
internal const val STATION_LIST_QUERY_CONTEXT_TAG = "station-list-query-context"
```

- [x] **Step 5: Keep top-level screen and result pane in `StationListScreen.kt`**

After moving declarations, `StationListScreen.kt` should keep only:

- `StationListScreen`
- `SortToggleTitle`
- `SortToggleSegment`
- `StationListContent`
- `StationListResultsPane`
- `RefreshingStatusRail`
- `subtleContentTransform`
- `StationListBannerTone.toStatusTone`
- `SortOrder.toStateDescription`
- `SortOrder.toNextSortActionLabel`
- `STATION_LIST_PULL_REFRESH_TAG`

- [x] **Step 6: Verify UI behavior**

Run:

```bash
wc -l feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt
./gradlew :feature:station-list:testDebugUnitTest verifyRoborazziDebug
```

Expected:
- `StationListScreen.kt` is below 350 lines.
- Tests and Roborazzi verification pass.

- [x] **Step 7: Commit**

```bash
git add feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist
git commit -m "refactor: split station list screen components"
```

---

## Task 5: Extract Repository Assembly Helpers If Needed

**Files:**
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- Create if `DefaultStationRepository.kt` remains above 300 lines: `data/station/src/main/kotlin/com/gasstation/data/station/StationSearchResultAssembler.kt`
- Create if `DefaultStationRepository.kt` remains above 300 lines: `data/station/src/main/kotlin/com/gasstation/data/station/WatchlistSummaryAssembler.kt`

- [x] **Step 1: Check the file size after Tasks 1-3**

Run:

```bash
wc -l data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt
```

Expected: if the file is 300 lines or less, skip this task and record the skip in the final summary. If above 300 lines, continue.

- [x] **Step 2: Extract search result assembly**

Create `StationSearchResultAssembler.kt` and move these declarations from `DefaultStationRepository.kt`:

- `List<StationCacheEntity>.toSearchResult(...)`
- `Map<String, List<StationPriceHistoryEntity>>.previousPriceFor(...)`
- `List<StationPriceHistoryEntity>.groupByStationId()`
- `List<StationListEntry>.sortedFor(...)`

Keep them `internal`.

- [x] **Step 3: Extract watchlist summary assembly**

Create `WatchlistSummaryAssembler.kt` and move these declarations from `DefaultStationRepository.kt`:

- `WatchedStationEntity.toWatchedSummary(...)`
- `List<StationPriceHistoryEntity>.historyForWatchlistContext(...)`
- `historyRowsBefore(...)`

Keep them `internal`.

- [x] **Step 4: Verify repository behavior**

Run:

```bash
./gradlew :data:station:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: Commit**

```bash
git add data/station/src/main/kotlin/com/gasstation/data/station
git commit -m "refactor: extract station repository assemblers"
```

---

## Task 6: Split Large Test Fixtures From Test Cases

**Files:**
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`
- Create: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTestFixtures.kt`
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/DefaultStationRepositoryTest.kt`
- Create: `data/station/src/test/kotlin/com/gasstation/data/station/RepositoryDoubles.kt`

- [x] **Step 1: Extract station-list ViewModel test fixtures**

Move these private declarations from the bottom of `StationListViewModelTest.kt` into `StationListViewModelTestFixtures.kt` and make them `internal`:

- `FakeStationRepository`
- `FakeLocationRepository`
- `RecordingStationEventLogger`
- `ThrowingStationEventLogger`

The new file starts with:

```kotlin
package com.gasstation.feature.stationlist
```

Keep constructor defaults and behavior identical.

- [x] **Step 2: Verify station-list tests**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest --tests com.gasstation.feature.stationlist.StationListViewModelTest
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Extract data repository doubles**

Move these private declarations from `DefaultStationRepositoryTest.kt` into `RepositoryDoubles.kt` and make them `internal`:

- `FakeStationRemoteDataSource`
- `ThrowingStationRemoteDataSource`
- `FakeSeedStationRemoteDataSource`
- `QueueStationRemoteDataSource`
- `RecordingStationEventLogger`
- `ThrowingStationEventLogger`
- `FakeCrashReporter`
- `RecordingStationCacheDao`

The new file starts with:

```kotlin
package com.gasstation.data.station
```

If `RepositoryStorageDoubles.kt` already contains a related double, do not duplicate it. Keep `RecordingStationPriceHistoryDao` and `RecordingWatchedStationDao` in `RepositoryStorageDoubles.kt`.

- [x] **Step 4: Verify data station tests**

Run:

```bash
./gradlew :data:station:testDebugUnitTest --tests com.gasstation.data.station.DefaultStationRepositoryTest
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: Check large test files**

Run:

```bash
wc -l feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt data/station/src/test/kotlin/com/gasstation/data/station/DefaultStationRepositoryTest.kt
```

Expected:
- `StationListViewModelTest.kt` is materially smaller than 1260 lines.
- `DefaultStationRepositoryTest.kt` is materially smaller than 783 lines.

- [x] **Step 6: Commit**

```bash
git add feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist data/station/src/test/kotlin/com/gasstation/data/station
git commit -m "test: extract shared repository and viewmodel doubles"
```

---

## Task 7: Update Architecture Documentation

**Files:**
- Modify: `docs/architecture.md`
- Modify: `docs/module-contracts.md`
- Modify: `docs/project-reading-guide.md`
- Modify: `docs/state-model.md`
- Modify: `docs/verification-matrix.md`

- [x] **Step 1: Update module inventory**

In `docs/module-contracts.md`, add `core:observability` to the module inventory:

```markdown
| `core:observability` | `CrashReporter` 같은 SDK-agnostic 관찰/진단 계약 | 없음 | feature 화면 상태, 특정 domain 정책, Timber/Crashlytics SDK 구현 |
```

Change the `core:location` direct dependency description to include `core:observability` and remove any implication that it depends on `domain:station`.

- [x] **Step 2: Update architecture graph**

In `docs/architecture.md`, add `core:observability` to the Mermaid graph:

```mermaid
app --> cobserve["core:observability"]
dstation --> cobserve
clocation --> cobserve
```

Remove any graph edge that suggests `core:location` depends on `domain:station`.

- [x] **Step 3: Update state and reading docs**

In `docs/state-model.md`, update the structured event/crash reporting description so `StationEvent` remains `domain:station`, while unexpected nonfatal exception reporting is `core:observability`.

In `docs/project-reading-guide.md`, update the event/logging entry to mention:

```markdown
`domain/station/model/StationEvent.kt`, `domain/station/StationEventLogger.kt`, `core/observability/CrashReporter.kt`, `app/src/*/kotlin/com/gasstation/analytics/*`
```

- [x] **Step 4: Update verification matrix**

Add `:core:observability:test` to relevant documentation verification command groups.

- [x] **Step 5: Verify docs**

Run:

```bash
git diff --check -- docs/architecture.md docs/module-contracts.md docs/project-reading-guide.md docs/state-model.md docs/verification-matrix.md
```

Expected: no output.

- [x] **Step 6: Commit**

```bash
git add docs/architecture.md docs/module-contracts.md docs/project-reading-guide.md docs/state-model.md docs/verification-matrix.md
git commit -m "docs: document observability and cleanup boundaries"
```

---

## Task 8: Final Verification

**Files:** N/A

- [ ] **Step 1: Run focused full regression**

Run:

```bash
./gradlew \
  :core:model:test \
  :core:observability:test \
  :domain:location:test \
  :core:location:testDebugUnitTest \
  :data:station:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  verifyRoborazziDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run boundary searches**

Run:

```bash
rg -n 'domain\.station\.CrashReporter|project\(":domain:station"\)' core/location
rg -n 'toDongLevelAddressLabel|joinSplitAdministrativeTokens|findFallbackRegionIndexBefore' feature/station-list/src/main core/location/src/main
rg -n 'distanceBetween|fun String\.toBrand|OpinetStationDto|toFuelProductCode|rawCoordinatesToWgs84' data/station/src/main/kotlin/com/gasstation/data/station
```

Expected: no output.

- [ ] **Step 3: Check file sizes**

Run:

```bash
rg --files -g '*.kt' -g '*.kts' | xargs wc -l | sort -nr | head -20
```

Expected:
- No production Kotlin file above 450 lines.
- `StationListScreen.kt` below 350 lines.
- The remaining biggest files are tests or database migration tests.

- [ ] **Step 4: Final status**

Run:

```bash
git status --short
```

Expected: no output after all commits, or only intentional uncommitted changes if the user asked not to commit.
