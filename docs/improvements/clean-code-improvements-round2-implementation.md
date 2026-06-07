# 구현 문서: 이펙티브 자바 · 클린코드 관점 코드 개선 (Round 2)

Date: 2026-06-07
연관 스펙: [clean-code-improvements-round2-spec.md](./clean-code-improvements-round2-spec.md)
방식: 각 항목 TDD(실패 재현 테스트 → 구현 → 그린). 순서 P1 → P2 → P3 → P4.
선행: Round 1에서 추가된 `Coordinates.ofOrNull`(`core/model/.../Coordinates.kt:14`)을 재사용한다.

---

## P1. 읽기 경계 안전 생성 (필수)

### 설계 결정

Round 1과 대칭으로, 읽기 경계에도 **비예외 팩터리**를 적용한다. 좌표는 기존 `Coordinates.ofOrNull`을 쓰고, 가격은 `MoneyWon.ofOrNull`을 신설한다. `toDomainStation`은 두 팩터리 중 하나라도 실패하면 `Station?`의 `null`을 반환하고, 호출처가 행을 스킵한다. 도메인 `require`는 그대로 둬 내부 fail-fast를 보존한다(스펙 R1.2).

### 변경 1 — core/model: `MoneyWon.ofOrNull` 추가

파일: `core/model/src/main/kotlin/com/gasstation/core/model/MoneyWon.kt`

```kotlin
@JvmInline
value class MoneyWon(val value: Int) {
    init {
        require(value >= 0) { "money won must be non-negative" }
    }

    companion object {
        /** 신뢰할 수 없는 외부/영속 입력용. 음수면 예외 대신 null. */
        fun ofOrNull(value: Int): MoneyWon? = if (value >= 0) MoneyWon(value) else null
    }
}
```

- 이펙티브 자바 아이템 1(정적 팩터리): 이름 `ofOrNull`로 "실패 가능"을 표현. `Coordinates.ofOrNull`과 동일 컨벤션.

### 변경 2 — data/station: `toDomainStation`을 nullable로

파일: `data/station/src/main/kotlin/com/gasstation/data/station/mapper/StationMappers.kt`

```kotlin
internal fun StationCacheEntity.toDomainStation(queryCoordinates: Coordinates): Station? {
    val coordinates = Coordinates.ofOrNull(latitude = latitude, longitude = longitude) ?: return null
    val price = MoneyWon.ofOrNull(priceWon) ?: return null
    return Station(
        id = stationId,
        name = name,
        brand = Brand.fromCode(brandCode),
        price = price,
        distance = queryCoordinates.distanceTo(coordinates),
        coordinates = coordinates,
    )
}
```

- `distance` 계산도 안전 좌표를 재사용해 `Coordinates`를 두 번 생성하지 않는다(기존 32·33행의 이중 생성 제거 — 부수적 정리).

### 변경 3 — data/station: 검색 결과 조립이 불량 행을 스킵

파일: `data/station/src/main/kotlin/com/gasstation/data/station/StationSearchResultAssembler.kt` (21행 부근)

```kotlin
val stations = mapNotNull { cacheRow ->
    val station = cacheRow.toDomainStation(query.coordinates) ?: return@mapNotNull null
    StationListEntry(
        station = station,
        priceDelta = StationPriceDelta.from(
            previousPriceWon = historyRowsByStationId.previousPriceFor(cacheRow),
            currentPriceWon = cacheRow.priceWon,
        ),
        isWatched = cacheRow.stationId in watchedStationIds,
        lastSeenAt = Instant.ofEpochMilli(cacheRow.fetchedAtEpochMillis),
    )
}
    .filter { query.brandFilter.matches(it.station.brand) }
    .sortedFor(query.sortOrder)
```

- `map` → `mapNotNull`. 나머지(`filter`/`sortedFor`)는 불변.
- 주의: `StationPriceDelta.from`의 `require(currentPriceWon >= 0)`도 음수면 던진다. `toDomainStation`이 먼저 `MoneyWon.ofOrNull`로 걸러 `null` 반환 → `return@mapNotNull null`로 빠지므로 `from`까지 도달하지 않는다. (순서 의존: station 생성 실패 시 즉시 스킵)

### 변경 4 — data/station: Watchlist fallback 경로 안전화

파일: `data/station/src/main/kotlin/com/gasstation/data/station/WatchlistSummaryAssembler.kt` (25-39행 부근)

`cachedSnapshot`은 이미 `cachedStation?.toDomainStation(origin)`이라 변경 2로 자동 nullable 전파. fallback(history만 있는) 분기의 직접 생성을 안전화한다.

```kotlin
val station = when {
    cachedSnapshot != null -> cachedSnapshot
    latestPrice != null -> {
        val stationCoordinates = Coordinates.ofOrNull(latitude, longitude) ?: return null
        val price = MoneyWon.ofOrNull(latestPrice.priceWon) ?: return null
        Station(
            id = stationId,
            name = name,
            brand = Brand.fromCode(brandCode),
            price = price,
            distance = origin.distanceTo(stationCoordinates),
            coordinates = stationCoordinates,
        )
    }
    else -> return null
}
```

- 불량 watched 행은 `toWatchedSummary`가 `null` 반환 → 호출처 `DefaultStationRepository.kt:124` `mapNotNull`이 자연 스킵.

### 신규/보강 테스트 (TDD: 먼저 작성해 빨강 확인)

1. `MoneyWonTest`(신규, `core/model/src/test/kotlin/com/gasstation/core/model/`) — `CoordinatesTest` 스타일을 그대로 따른다.
   - `ofOrNull(1_680)` → 인스턴스.
   - `ofOrNull(0)` → 인스턴스(도메인은 0 허용).
   - `ofOrNull(-1)` → null.
   - `MoneyWon(-1)` 생성자는 여전히 `IllegalArgumentException`(불변식 유지). (※ 이미 `ValueObjectInvariantTest`에 있으나 팩터리 대비 명시)

2. `StationSearchResultAssemblerTest`(신규, `data/station/src/test/kotlin/com/gasstation/data/station/`) — `internal` 확장이므로 같은 패키지에서 직접 호출. 픽스처는 `WatchlistRepositoryTest`의 `StationCacheEntity` 생성 패턴 참고.
   - AC1.1: `listOf(정상行, latitude=200.0行).toSearchResult(...)` → entries 1개(정상), 예외 없음. (수정 전엔 `IllegalArgumentException`으로 실패해야 정상)
   - AC1.2: `listOf(정상行, priceWon=-1行).toSearchResult(...)` → entries 1개(정상), 예외 없음.

3. `WatchlistSummaryAssemblerTest` 또는 `WatchlistRepositoryTest`에 케이스 추가
   - AC1.3: 캐시 스냅샷 없음 + history 있음 + `WatchedStationEntity.latitude=200.0` → `toWatchedSummary == null`, 예외 없음.

### P1 검증 명령

```bash
./gradlew :core:model:test :data:station:test
```

---

## P2. Opinet 가격 검증을 Proxy와 통일 (필수)

파일: `core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationMappers.kt` (11행)

```kotlin
val priceWon = priceWon?.toIntOrNull()?.takeIf { it > 0 } ?: return null
```

- Proxy(`ProxyStationFetcher.kt:35`)의 `takeIf { it > 0 }`와 정확히 동일한 계약. 0·음수 가격 행은 진입 경계에서 걸러져 DB까지 전파되지 않는다.
- `DistanceMeters`/`MoneyWon`의 도메인 불변식은 `>= 0`이지만, 가스 가격 0은 무효 데이터이므로 두 fetcher 모두 `> 0`을 강제(일관성 우선).

### 신규 테스트

`NetworkStationFetcherTest`에 케이스 추가:
- AC2.1/2.2: 응답 = [정상 행, `priceWon="-1"` 행, `priceWon="0"` 행]. 기대: 정상 행만 `Success`에 포함.
- `toNetworkRemoteStation` 단위 레벨에서 음수/0 가격 → `null` 직접 검증(결정적).

### P2 검증 명령

```bash
./gradlew :core:network:test
```

---

## P3. Watchlist 요약 조립 추출 (권장, 동작 불변)

파일: `data/station/src/main/kotlin/com/gasstation/data/station/WatchlistSummaryAssembler.kt`

`toWatchedSummary` 본문을 "선택 → 계산 → 조립" 3단계로 환원한다. 각 추출 함수는 기존 로직을 **그대로** 옮긴 순수 추출이다(신규 로직 금지).

```kotlin
internal fun WatchedStationEntity.toWatchedSummary(
    origin: Coordinates,
    cachedStation: StationCacheEntity?,
    history: List<StationPriceHistoryEntity>,
): WatchedStationSummary? {
    val historyForContext = history.historyForWatchlistContext(cachedStation?.fuelType)
    val station = resolveStation(origin, cachedStation, historyForContext) ?: return null
    val priceDelta = resolvePriceDelta(cachedStation, historyForContext)
    return WatchedStationSummary(
        station = station,
        priceDelta = priceDelta,
        lastSeenAt = resolveLastSeenAt(cachedStation, historyForContext),
    )
}
```

- `resolveStation(...)`: 25-39행의 `when`(cachedSnapshot / latestPrice / else)을 그대로 이동(P1 변경 4 포함된 상태).
- `resolvePriceDelta(...)`: 40-53행의 `when`을 그대로 이동.
- `resolveLastSeenAt(...)`: 58-59행의 `lastSeenAt` 산출을 그대로 이동.

검증: `./gradlew :data:station:test` (기존 `WatchlistRepositoryTest` 회귀).

---

## P4. StationPriceDelta 다형성 전환 (선택, 동작 불변)

파일: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationPriceDelta.kt`

`direction`/`amountWonOrNull`을 추상 멤버로 올리고 각 variant가 override한다. 공개 API와 결과는 보존(소비처 무수정).

```kotlin
sealed interface StationPriceDelta {
    enum class PriceDirection { RISE, FALL, NEUTRAL }

    val direction: PriceDirection
    val amountWonOrNull: Int?

    data object Unavailable : StationPriceDelta {
        override val direction = PriceDirection.NEUTRAL
        override val amountWonOrNull: Int? = null
    }

    data object Unchanged : StationPriceDelta {
        override val direction = PriceDirection.NEUTRAL
        override val amountWonOrNull: Int? = null
    }

    data class Increased(val amountWon: Int) : StationPriceDelta {
        init { require(amountWon > 0) { "Increased price delta amount must be positive." } }
        override val direction = PriceDirection.RISE
        override val amountWonOrNull get() = amountWon
    }

    data class Decreased(val amountWon: Int) : StationPriceDelta {
        init { require(amountWon > 0) { "Decreased price delta amount must be positive." } }
        override val direction = PriceDirection.FALL
        override val amountWonOrNull get() = amountWon
    }

    companion object {
        fun from(previousPriceWon: Int?, currentPriceWon: Int): StationPriceDelta { /* 기존 그대로 */ }
    }
}
```

- 새 variant 추가 시 그 클래스 안에서 두 프로퍼티를 강제로 구현하게 되어 "분기 누락"이 컴파일 타임에 드러난다.
- 소비처 `StationListItemUiModel.kt:50,63`, `WatchlistItemUiModel.kt:57,70`은 `direction`/`amountWonOrNull`만 쓰므로 무수정.
- **토론 포인트**: Kotlin exhaustive `when`도 충분히 안전하다는 의견이 있으면 이 항목은 폐기 가능(스펙 P4 선택).

검증: `./gradlew :domain:station:test` (기존 `StationPriceDeltaTest` 회귀).

---

## 통합 검증 및 순서

1. P1 → 2. P2 → 3. P3 → 4. P4 (독립적이므로 분리 커밋 권장).
2. P1+P2는 동일 폭발 지점(읽기 시점 `require`)을 닫는 한 쌍이므로 **같은 PR** 권장.

전체:

```bash
./gradlew :core:model:test :core:network:test :data:station:test :domain:station:test
./gradlew spotlessApply
```

## 영향 파일 요약

| 항목 | 수정 | 신규/보강 테스트 |
|------|------|------------------|
| P1 | MoneyWon.kt, StationMappers.kt, StationSearchResultAssembler.kt, WatchlistSummaryAssembler.kt | MoneyWonTest(신규), StationSearchResultAssemblerTest(신규), WatchlistRepositoryTest(+1) |
| P2 | NetworkStationMappers.kt | NetworkStationFetcherTest(+1) |
| P3 | WatchlistSummaryAssembler.kt | 기존 회귀 |
| P4 | StationPriceDelta.kt | 기존 회귀 |
