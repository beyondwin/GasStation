# 객체지향 협력 정리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 조영호 "오브젝트" 강의의 묻지 말고 시켜라·변경 보호·디미터 법칙·다형적 메시지 원칙에 따라, 흩어진 `StationPriceDelta` 해석과 가격/거리 표기 중복을 단일 책임으로 모으고 캐시 키 매직넘버를 명시한다 — 사용자 출력은 불변.

**Architecture:** delta의 도메인 의미(방향/변동액)는 `domain:station`의 `StationPriceDelta`로, 값 객체 표시 포맷은 `core:designsystem`의 확장 함수로 단일화한다. 두 feature UI 모델은 이 둘을 조합만 하고 자체 중복 포맷/`when`을 버린다. 기존 단위 테스트가 출력 문자열을 고정하므로 이 테스트들을 수정 없이 통과시키는 것이 정확성 기준이다.

**Tech Stack:** Kotlin, Gradle 멀티모듈, JUnit4 + kotlin-test, Android library(`testDebugUnitTest`) / 순수 JVM(`test`).

**짝 설계 문서:** `docs/superpowers/specs/2026-06-06-oo-collaboration-tell-dont-ask-design.md`

---

## 파일 구조

- **수정** `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationPriceDelta.kt` — `PriceDirection` enum + `direction`/`amountWonOrNull` 프로퍼티 추가 (Task 1)
- **수정** `domain/station/src/test/kotlin/com/gasstation/domain/station/StationPriceDeltaTest.kt` — 신규 단언 (Task 1)
- **생성** `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/ValueFormats.kt` — 값 객체 표시 포맷터 (Task 2)
- **생성** `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/ValueFormatsTest.kt` — 포맷터 테스트 (Task 2)
- **수정** `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt` — 중복 제거 + Track A·B 소비 + 불변식 대칭 (Task 3)
- **수정** `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistItemUiModel.kt` — 중복 제거 + Track A·B 소비 (Task 4)
- **수정** `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQuery.kt` — 매직넘버 명명/주석 (Task 5)

**불변(수정 금지):** `StationListItemUiModelTest.kt`, `WatchlistItemUiModelTest.kt`, `StationQueryCacheKeyTest.kt`, `DomainContractSurfaceTest.kt`의 기존 단언값. 이들은 회귀 안전망이다.

---

## Task 1: `StationPriceDelta`에 방향/변동액 책임 부여 (Track A)

**Files:**
- Modify: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationPriceDelta.kt`
- Test: `domain/station/src/test/kotlin/com/gasstation/domain/station/StationPriceDeltaTest.kt`

- [ ] **Step 1: Write the failing tests**

`StationPriceDeltaTest.kt`의 클래스 본문 마지막 `}` 직전에 추가:

```kotlin
    @Test
    fun `direction classifies each variant`() {
        assertEquals(StationPriceDelta.PriceDirection.NEUTRAL, StationPriceDelta.Unavailable.direction)
        assertEquals(StationPriceDelta.PriceDirection.NEUTRAL, StationPriceDelta.Unchanged.direction)
        assertEquals(StationPriceDelta.PriceDirection.RISE, StationPriceDelta.Increased(20).direction)
        assertEquals(StationPriceDelta.PriceDirection.FALL, StationPriceDelta.Decreased(20).direction)
    }

    @Test
    fun `amountWonOrNull exposes signed magnitude only for changed variants`() {
        assertEquals(null, StationPriceDelta.Unavailable.amountWonOrNull)
        assertEquals(null, StationPriceDelta.Unchanged.amountWonOrNull)
        assertEquals(20, StationPriceDelta.Increased(20).amountWonOrNull)
        assertEquals(20, StationPriceDelta.Decreased(20).amountWonOrNull)
    }
```

`import com.gasstation.domain.station.model.StationPriceDelta`는 이미 존재한다.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :domain:station:test --tests "*StationPriceDeltaTest"`
Expected: 컴파일 실패 — `direction`/`amountWonOrNull`/`PriceDirection` 미정의.

- [ ] **Step 3: Add the members to the sealed interface**

`StationPriceDelta.kt`를 아래로 교체(기존 변형/companion 유지, 멤버만 추가):

```kotlin
package com.gasstation.domain.station.model

sealed interface StationPriceDelta {
    enum class PriceDirection { RISE, FALL, NEUTRAL }

    val direction: PriceDirection
        get() = when (this) {
            is Increased -> PriceDirection.RISE
            is Decreased -> PriceDirection.FALL
            Unavailable, Unchanged -> PriceDirection.NEUTRAL
        }

    val amountWonOrNull: Int?
        get() = when (this) {
            is Increased -> amountWon
            is Decreased -> amountWon
            Unavailable, Unchanged -> null
        }

    data object Unavailable : StationPriceDelta
    data object Unchanged : StationPriceDelta
    data class Increased(val amountWon: Int) : StationPriceDelta {
        init {
            require(amountWon > 0) { "Increased price delta amount must be positive." }
        }
    }

    data class Decreased(val amountWon: Int) : StationPriceDelta {
        init {
            require(amountWon > 0) { "Decreased price delta amount must be positive." }
        }
    }

    companion object {
        fun from(previousPriceWon: Int?, currentPriceWon: Int): StationPriceDelta {
            require(currentPriceWon >= 0) { "Current price must be non-negative." }
            require(previousPriceWon == null || previousPriceWon >= 0) {
                "Previous price must be non-negative when present."
            }

            return when {
                previousPriceWon == null -> Unavailable
                previousPriceWon == currentPriceWon -> Unchanged
                previousPriceWon < currentPriceWon -> Increased(currentPriceWon - previousPriceWon)
                else -> Decreased(previousPriceWon - currentPriceWon)
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :domain:station:test`
Expected: PASS (신규 2개 + 기존 8개 + `DomainContractSurfaceTest` 모두 통과. permitted subclass 집합은 불변).

- [ ] **Step 5: Commit**

```bash
git add domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationPriceDelta.kt \
        domain/station/src/test/kotlin/com/gasstation/domain/station/StationPriceDeltaTest.kt
git commit -m "refactor(domain): give StationPriceDelta its own direction and amount"
```

---

## Task 2: 값 객체 표시 포맷터 단일화 (Track B)

**Files:**
- Create: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/ValueFormats.kt`
- Test: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/ValueFormatsTest.kt`

- [ ] **Step 1: Write the failing test**

`ValueFormatsTest.kt` 신규 생성:

```kotlin
package com.gasstation.core.designsystem

import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.MoneyWon
import org.junit.Assert.assertEquals
import org.junit.Test

class ValueFormatsTest {
    @Test
    fun `price digits group thousands`() {
        assertEquals("1,689", MoneyWon(1689).gasStationPriceDigits())
    }

    @Test
    fun `price label appends won unit`() {
        assertEquals("1,689원", MoneyWon(1689).gasStationPriceLabel())
    }

    @Test
    fun `distance digits render one decimal kilometer`() {
        assertEquals("0.3", DistanceMeters(300).gasStationDistanceDigits())
    }

    @Test
    fun `distance label appends km unit`() {
        assertEquals("0.3km", DistanceMeters(300).gasStationDistanceLabel())
    }

    @Test
    fun `units expose canonical strings`() {
        assertEquals("원", GAS_STATION_WON_UNIT)
        assertEquals("km", GAS_STATION_DISTANCE_UNIT)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*ValueFormatsTest"`
Expected: 컴파일 실패 — 포맷터/상수 미정의.

- [ ] **Step 3: Write the formatters**

`ValueFormats.kt` 신규 생성:

```kotlin
package com.gasstation.core.designsystem

import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.MoneyWon
import java.text.DecimalFormat

// Korean Won unit (U+C6D0 = 원); single source so price labels never drift per screen.
const val GAS_STATION_WON_UNIT = "원"
const val GAS_STATION_DISTANCE_UNIT = "km"

fun MoneyWon.gasStationPriceDigits(): String = DecimalFormat("#,###").format(value)

fun MoneyWon.gasStationPriceLabel(): String = "${gasStationPriceDigits()}$GAS_STATION_WON_UNIT"

fun DistanceMeters.gasStationDistanceDigits(): String = DecimalFormat("#,##0.0").format(value / 1000.0)

fun DistanceMeters.gasStationDistanceLabel(): String = "${gasStationDistanceDigits()}$GAS_STATION_DISTANCE_UNIT"
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*ValueFormatsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/ValueFormats.kt \
        core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/ValueFormatsTest.kt
git commit -m "refactor(designsystem): add single-source price and distance formatters"
```

---

## Task 3: station-list UI 모델 정리 (Track C-1)

**Files:**
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt`
- Test(불변): `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListItemUiModelTest.kt`

- [ ] **Step 1: Confirm the existing test is the spec (no edit)**

Run: `./gradlew :feature:station-list:testDebugUnitTest --tests "*StationListItemUiModelTest"`
Expected: 현재 코드에서 PASS. 이 테스트가 `"32원"`/`"-"`/`PriceDeltaTone.Rise` 등 출력 계약을 고정한다. **수정하지 않는다.**

- [ ] **Step 2: Rewrite the UI model to consume Tasks 1·2**

`StationListItemUiModel.kt` 전체를 아래로 교체:

```kotlin
package com.gasstation.feature.stationlist

import com.gasstation.core.designsystem.GAS_STATION_DISTANCE_UNIT
import com.gasstation.core.designsystem.GAS_STATION_WON_UNIT
import com.gasstation.core.designsystem.gasStationBrandLabel
import com.gasstation.core.designsystem.gasStationDistanceDigits
import com.gasstation.core.designsystem.gasStationDistanceLabel
import com.gasstation.core.designsystem.gasStationPriceDigits
import com.gasstation.core.designsystem.gasStationPriceLabel
import com.gasstation.core.model.Brand
import com.gasstation.domain.station.model.StationListEntry
import com.gasstation.domain.station.model.StationPriceDelta

data class StationListItemUiModel(
    val id: String,
    val name: String,
    val brand: Brand = Brand.ETC,
    val brandLabel: String,
    val priceLabel: String,
    val distanceLabel: String,
    val priceNumberLabel: String,
    val priceUnitLabel: String,
    val distanceNumberLabel: String,
    val distanceUnitLabel: String,
    val priceDeltaLabel: String,
    val priceDeltaTone: PriceDeltaTone = PriceDeltaTone.Neutral,
    val isWatched: Boolean,
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(priceNumberLabel.isNotBlank()) { "priceNumberLabel must not be blank" }
        require(priceUnitLabel.isNotBlank()) { "priceUnitLabel must not be blank" }
        require(distanceNumberLabel.isNotBlank()) { "distanceNumberLabel must not be blank" }
        require(distanceUnitLabel.isNotBlank()) { "distanceUnitLabel must not be blank" }
    }

    constructor(entry: StationListEntry) : this(
        id = entry.station.id,
        name = entry.station.name,
        brand = entry.station.brand,
        brandLabel = entry.station.brand.gasStationBrandLabel(),
        priceLabel = entry.station.price.gasStationPriceLabel(),
        distanceLabel = entry.station.distance.gasStationDistanceLabel(),
        priceNumberLabel = entry.station.price.gasStationPriceDigits(),
        priceUnitLabel = GAS_STATION_WON_UNIT,
        distanceNumberLabel = entry.station.distance.gasStationDistanceDigits(),
        distanceUnitLabel = GAS_STATION_DISTANCE_UNIT,
        priceDeltaLabel = entry.priceDelta.toDeltaLabel(),
        priceDeltaTone = entry.priceDelta.direction.toTone(),
        isWatched = entry.isWatched,
        latitude = entry.station.coordinates.latitude,
        longitude = entry.station.coordinates.longitude,
    )
}

enum class PriceDeltaTone {
    Rise,
    Fall,
    Neutral,
}

private fun StationPriceDelta.toDeltaLabel(): String =
    amountWonOrNull?.let { "$it$GAS_STATION_WON_UNIT" } ?: "-"

private fun StationPriceDelta.PriceDirection.toTone(): PriceDeltaTone = when (this) {
    StationPriceDelta.PriceDirection.RISE -> PriceDeltaTone.Rise
    StationPriceDelta.PriceDirection.FALL -> PriceDeltaTone.Fall
    StationPriceDelta.PriceDirection.NEUTRAL -> PriceDeltaTone.Neutral
}
```

참고: `PriceDeltaTone.toColor()`는 별도 파일(`StationListStates`/색상 매핑)에 정의돼 있으므로 여기서 제거된 것이 아니다. 만약 `toColor()`가 이 파일에 있었다면 그대로 유지하고 위 교체에 포함시킬 것. (현재 이 파일에는 없음 — 확인 후 진행.)

- [ ] **Step 3: Run the regression test**

Run: `./gradlew :feature:station-list:testDebugUnitTest`
Expected: PASS — 특히 `StationListItemUiModelTest`가 단언값 변경 없이 통과. `"32원"`, `"-"`, `Rise/Fall/Neutral`, `"고속도로알뜰"` 모두 동일.

- [ ] **Step 4: Commit**

```bash
git add feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt
git commit -m "refactor(station-list): consume shared formatters and delta direction"
```

---

## Task 4: watchlist UI 모델 정리 (Track C-2)

**Files:**
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistItemUiModel.kt`
- Test(불변): `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistItemUiModelTest.kt`

- [ ] **Step 1: Confirm the existing test is the spec (no edit)**

Run: `./gradlew :feature:watchlist:testDebugUnitTest --tests "*WatchlistItemUiModelTest"`
Expected: 현재 코드에서 PASS. 이 테스트가 `"1,689원"`/`"1,689"`/`"원"`/`"0.3km"`/`"0.3"`/`"km"`/`"27원"`/`Fall`/`"4월 18일 12:00"`와 blank 거부 불변식을 고정한다. **수정하지 않는다.**

- [ ] **Step 2: Rewrite the UI model to consume Tasks 1·2**

`WatchlistItemUiModel.kt` 전체를 아래로 교체 (`toColor()`와 `lastSeenAt` 포맷은 watchlist 고유라 유지):

```kotlin
package com.gasstation.feature.watchlist

import androidx.compose.ui.graphics.Color
import com.gasstation.core.designsystem.ColorGray2
import com.gasstation.core.designsystem.ColorSupportError
import com.gasstation.core.designsystem.ColorSupportInfo
import com.gasstation.core.designsystem.GAS_STATION_DISTANCE_UNIT
import com.gasstation.core.designsystem.GAS_STATION_WON_UNIT
import com.gasstation.core.designsystem.gasStationBrandLabel
import com.gasstation.core.designsystem.gasStationDistanceDigits
import com.gasstation.core.designsystem.gasStationDistanceLabel
import com.gasstation.core.designsystem.gasStationPriceDigits
import com.gasstation.core.designsystem.gasStationPriceLabel
import com.gasstation.core.model.Brand
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.WatchedStationSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class WatchlistItemUiModel(
    val id: String,
    val name: String,
    val brand: Brand = Brand.ETC,
    val brandLabel: String,
    val priceLabel: String,
    val priceNumberLabel: String,
    val priceUnitLabel: String,
    val distanceLabel: String,
    val distanceNumberLabel: String,
    val distanceUnitLabel: String,
    val priceDeltaLabel: String,
    val priceDeltaTone: WatchlistPriceDeltaTone = WatchlistPriceDeltaTone.Neutral,
    val lastSeenLabel: String,
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(priceNumberLabel.isNotBlank()) { "priceNumberLabel must not be blank" }
        require(priceUnitLabel.isNotBlank()) { "priceUnitLabel must not be blank" }
        require(distanceNumberLabel.isNotBlank()) { "distanceNumberLabel must not be blank" }
        require(distanceUnitLabel.isNotBlank()) { "distanceUnitLabel must not be blank" }
    }

    constructor(summary: WatchedStationSummary) : this(
        id = summary.station.id,
        name = summary.station.name,
        brand = summary.station.brand,
        brandLabel = summary.station.brand.gasStationBrandLabel(),
        priceLabel = summary.station.price.gasStationPriceLabel(),
        priceNumberLabel = summary.station.price.gasStationPriceDigits(),
        priceUnitLabel = GAS_STATION_WON_UNIT,
        distanceLabel = summary.station.distance.gasStationDistanceLabel(),
        distanceNumberLabel = summary.station.distance.gasStationDistanceDigits(),
        distanceUnitLabel = GAS_STATION_DISTANCE_UNIT,
        priceDeltaLabel = summary.priceDelta.toDeltaLabel(),
        priceDeltaTone = summary.priceDelta.direction.toTone(),
        lastSeenLabel = summary.lastSeenAt.toLabel(),
        latitude = summary.station.coordinates.latitude,
        longitude = summary.station.coordinates.longitude,
    )
}

enum class WatchlistPriceDeltaTone {
    Rise,
    Fall,
    Neutral,
}

private fun StationPriceDelta.toDeltaLabel(): String =
    amountWonOrNull?.let { "$it$GAS_STATION_WON_UNIT" } ?: "-"

internal fun StationPriceDelta.PriceDirection.toTone(): WatchlistPriceDeltaTone = when (this) {
    StationPriceDelta.PriceDirection.RISE -> WatchlistPriceDeltaTone.Rise
    StationPriceDelta.PriceDirection.FALL -> WatchlistPriceDeltaTone.Fall
    StationPriceDelta.PriceDirection.NEUTRAL -> WatchlistPriceDeltaTone.Neutral
}

internal fun WatchlistPriceDeltaTone.toColor(): Color = when (this) {
    WatchlistPriceDeltaTone.Rise -> ColorSupportError
    WatchlistPriceDeltaTone.Fall -> ColorSupportInfo
    WatchlistPriceDeltaTone.Neutral -> ColorGray2
}

private fun Instant?.toLabel(): String {
    if (this == null) return "마지막 확인 기록 없음"

    return DateTimeFormatter.ofPattern("M월 d일 HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(this)
}
```

주의: 기존에 `internal fun StationPriceDelta.toTone(): WatchlistPriceDeltaTone`이 다른 watchlist 파일에서 참조될 수 있다. Step 3 전에 확인한다.

- [ ] **Step 3: Check for stale references to the removed extension**

Run: `grep -rn "\.toTone()" feature/watchlist/src`
Expected: 호출부가 `priceDelta.toTone()`(StationPriceDelta 수신자) 형태로 남아 있으면, 이제 시그니처가 `PriceDirection.toTone()`이므로 `priceDelta.direction.toTone()`으로 바꿔야 한다. UI 모델 생성자 외에 호출부가 없으면(현재 그러함) 조치 불필요.

- [ ] **Step 4: Run the regression test**

Run: `./gradlew :feature:watchlist:testDebugUnitTest`
Expected: PASS — `WatchlistItemUiModelTest`의 모든 단언(분리 라벨, delta, tone, lastSeen, blank 거부)이 변경 없이 통과.

- [ ] **Step 5: Commit**

```bash
git add feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistItemUiModel.kt
git commit -m "refactor(watchlist): consume shared formatters and delta direction"
```

---

## Task 5: 캐시 키 매직넘버 캡슐화 (Track D)

**Files:**
- Modify: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQuery.kt`
- Test(불변): `domain/station/src/test/kotlin/com/gasstation/domain/station/StationQueryCacheKeyTest.kt`

- [ ] **Step 1: Confirm the existing test pins the values (no edit)**

Run: `./gradlew :domain:station:test --tests "*StationQueryCacheKeyTest"`
Expected: PASS. `latitudeBucket=16649`, `longitudeBucket=45120`을 고정한다. 상수 명명은 값을 바꾸지 않으므로 이 테스트가 그대로 통과해야 한다. **수정하지 않는다.**

- [ ] **Step 2: Replace magic numbers with named, documented constants**

`StationQuery.kt` 전체를 아래로 교체:

```kotlin
package com.gasstation.domain.station.model

import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder

data class StationQuery(
    val coordinates: Coordinates,
    val radius: SearchRadius,
    val fuelType: FuelType,
    val brandFilter: BrandFilter,
    val sortOrder: SortOrder,
) {
    fun toCacheKey(bucketMeters: Int): StationQueryCacheKey {
        require(bucketMeters > 0) { "bucketMeters must be greater than 0" }

        val latitudeBucket = ((coordinates.latitude * METERS_PER_LATITUDE_DEGREE) / bucketMeters).toInt()
        val longitudeBucket = ((coordinates.longitude * METERS_PER_LONGITUDE_DEGREE_KR) / bucketMeters).toInt()

        return StationQueryCacheKey(
            latitudeBucket = latitudeBucket,
            longitudeBucket = longitudeBucket,
            radiusMeters = radius.meters,
            fuelType = fuelType,
        )
    }

    private companion object {
        // 위도 1도 ≈ 111km (전 지구 공통 근사).
        const val METERS_PER_LATITUDE_DEGREE = 111_000

        // 경도 1도당 미터는 위도에 따라 줄어든다. 이 값은 한국 위도(약 37도) 근사치이며,
        // 캐시 버킷팅 전용 좌표 양자화에만 쓰인다(정밀 거리 계산용 아님).
        const val METERS_PER_LONGITUDE_DEGREE_KR = 88_800
    }
}
```

- [ ] **Step 3: Run test to verify cache key values are unchanged**

Run: `./gradlew :domain:station:test`
Expected: PASS — `StationQueryCacheKeyTest`가 `16649`/`45120`로 그대로 통과.

- [ ] **Step 4: Commit**

```bash
git add domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQuery.kt
git commit -m "refactor(domain): name and document cache-key coordinate constants"
```

---

## Task 6: 전체 검증 + 모듈 경계 확인

- [ ] **Step 1: Run the full affected-module suite + boundary guard**

Run:
```bash
./gradlew \
  :domain:station:test \
  :core:designsystem:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  verifyModuleBoundaries
```
Expected: BUILD SUCCESSFUL. 모든 기존 단언값 불변, 경계 위반 0.

- [ ] **Step 2: Confirm duplication is gone**

Run: `grep -rn "DecimalFormat\|toGroupedDigits\|toDistanceNumberLabel" feature/`
Expected: 출력 없음 — 가격/거리 포맷 중복이 feature에서 완전히 제거됨.

- [ ] **Step 3: Spotless (코드 스타일 게이트)**

Run: `./gradlew spotlessCheck`
Expected: PASS. 실패 시 `./gradlew spotlessApply` 후 재확인하고 변경을 해당 Task 커밋에 포함.

---

## Self-Review 결과 (작성자 점검)

- **Spec coverage:** Track A→Task 1, Track B→Task 2, Track C→Task 3·4, Track D→Task 5, 전체 검증→Task 6. 비목표(tone enum 통합 안 함, delta 텍스트는 feature 유지, 캐시 키 값 불변)는 각 Task 설계 노트에 반영.
- **Placeholder scan:** 모든 코드 스텝은 전체 파일 내용을 포함(부분 생략 없음). TBD/TODO 없음.
- **Type consistency:** `PriceDirection`(Task 1) → `direction.toTone()`(Task 3·4)에서 동일 enum 사용. `gasStationPriceDigits/Label`, `gasStationDistanceDigits/Label`, `GAS_STATION_WON_UNIT`, `GAS_STATION_DISTANCE_UNIT`(Task 2) 명칭이 Task 3·4 import와 정확히 일치. `amountWonOrNull`(Task 1)이 Task 3·4의 `toDeltaLabel`에서 사용됨.
- **회귀 안전망:** UI 모델/캐시 키/도메인 계약 테스트는 단언값을 바꾸지 않고 통과시키는 것을 정확성 기준으로 명시.
