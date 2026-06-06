# 구현 문서: 이펙티브 자바 · 클린코드 관점 코드 개선

Date: 2026-06-07
연관 스펙: [clean-code-improvements-spec.md](./clean-code-improvements-spec.md)
방식: 각 항목 TDD(실패 재현 테스트 → 구현 → 그린). P1 → P3 → P2 → P4 순.

---

## P1. 좌표 경계 안전 생성 (필수)

### 설계 결정

`Coordinates`의 `require` 불변식은 그대로 두고(내부 fail-fast 유지), **경계 전용 비예외 팩터리**를 추가해 두 fetcher가 공유하게 한다. 이로써 공개 시그니처 충격을 최소화하고 "경계에서 검증"(클린코드) 원칙을 한 곳에 모은다.

### 변경 1 — core/model: 안전 팩터리 추가

파일: `core/model/src/main/kotlin/com/gasstation/core/model/Coordinates.kt`

```kotlin
data class Coordinates(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in LATITUDE_RANGE) { "latitude must be between -90.0 and 90.0" }
        require(longitude in LONGITUDE_RANGE) { "longitude must be between -180.0 and 180.0" }
    }

    companion object {
        private val LATITUDE_RANGE = -90.0..90.0
        private val LONGITUDE_RANGE = -180.0..180.0

        /** 신뢰할 수 없는 외부 입력용. 범위를 벗어나면 예외 대신 null. */
        fun ofOrNull(latitude: Double, longitude: Double): Coordinates? =
            if (latitude in LATITUDE_RANGE && longitude in LONGITUDE_RANGE) {
                Coordinates(latitude, longitude)
            } else {
                null
            }
    }
}
```

- 이펙티브 자바 아이템 1(정적 팩터리): 이름(`ofOrNull`)으로 "실패 가능"을 표현.
- 범위 상수를 한 곳으로 모아 init과 팩터리가 동일 기준을 공유(DRY).

### 변경 2 — core/network: KATEC 변환기를 비예외화

파일: `core/network/src/main/kotlin/com/gasstation/core/network/station/LocalKoreanCoordinateTransform.kt`

```kotlin
fun ktmToWgs84(x: Double, y: Double): Coordinates? {
    val source = ProjCoordinate(x, y)
    val target = ProjCoordinate()
    katecToWgs84Transform.transform(source, target)
    return Coordinates.ofOrNull(latitude = target.y, longitude = target.x)
}
```

- 반환 타입 `Coordinates` → `Coordinates?`.

### 변경 3 — core/network: Opinet 매퍼 정합성

파일: `core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationMappers.kt`

```kotlin
internal fun rawCoordinatesToWgs84(rawX: Double, rawY: Double): Coordinates? {
    Coordinates.ofOrNull(latitude = rawY, longitude = rawX)?.let { return it }
    return LocalKoreanCoordinateTransform.ktmToWgs84(x = rawX, y = rawY)
}
```

- in-range 직통 분기도 `ofOrNull`로 통일. fallback의 `Coordinates?`가 자연스럽게 전파된다(기존 `?: return null` 계약이 실제로 성립).

### 변경 4 — core/network: Proxy 매퍼 안전 생성

파일: `core/network/src/main/kotlin/com/gasstation/core/network/station/ProxyStationFetcher.kt` (`toNetworkRemoteStation`, 44행 부근)

```kotlin
    coordinates = Coordinates.ofOrNull(latitude = lat, longitude = lon) ?: return null,
```

### 변경 5 — 기존 테스트 시그니처 보정

파일: `core/network/src/test/kotlin/com/gasstation/core/network/station/LocalKoreanCoordinateTransformTest.kt`

`ktmToWgs84`가 nullable이 됐으므로 round-trip 테스트에서 결과를 `requireNotNull(...)`로 받는다.

```kotlin
val wgs84 = requireNotNull(
    LocalKoreanCoordinateTransform.ktmToWgs84(x = ktm.x, y = ktm.y),
)
```

### 신규 테스트 (TDD: 먼저 작성해 빨강 확인)

1. `CoordinatesTest`(신규, core/model)
   - `ofOrNull` 정상값 → 인스턴스 반환.
   - `ofOrNull(200.0, 0.0)` → null.
   - `ofOrNull(0.0, 200.0)` → null.
   - `Coordinates(200.0, 0.0)` 생성자는 여전히 `IllegalArgumentException`(불변식 유지 확인).

2. `ProxyStationFetcherTest`에 케이스 추가 (AC1.1)
   - 응답 = [정상 행, `latitude=200.0` 행]. 기대: `Success` + 정상 행만, **예외 없음**. (수정 전엔 `IllegalArgumentException`으로 실패해야 정상)
   - 응답 = [`latitude=200.0` 행만]. 기대: `Failure`.

3. `NetworkStationFetcherTest`에 케이스 추가 (AC1.2/1.3)
   - GIS 좌표가 KATEC 변환 시 범위를 벗어나도록 극단값을 주입한 행 + 정상 행. 기대: 정상 행만 보존, 예외 없음.
   - 결정적 재현이 어려우면 `rawCoordinatesToWgs84`/`ofOrNull` 단위 테스트로 대체하고, fetcher 레벨은 "정상 보존"만 검증.

### P1 검증 명령

```bash
./gradlew :core:model:test :core:network:test
```

---

## P3. ViewModel init 추출 (권장, 동작 불변)

파일: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`

`init` 본문을 호출 목록으로 환원한다. 각 함수는 기존 코드 블록을 **그대로** 옮긴 순수 추출이다.

```kotlin
init {
    observePreferences()
    triggerRefreshOnQueryChange()
    observeSearchProjection()
    bindUiState()
}

private fun observePreferences() {
    observeUserPreferences()
        .onEach { preferences.value = it }
        .launchIn(viewModelScope)
}

private fun triggerRefreshOnQueryChange() {
    var previousQuery: StationQuery? = null
    val queryFlow = combine(preferences, locationStateMachine.state) { prefs, location ->
        location.usableCoordinates()
            ?.let { coordinates -> buildQuery(preferences = prefs, coordinates = coordinates) }
    }.distinctUntilChanged()
        .onEach { query ->
            if (searchOrchestrator.shouldRefreshForCriteriaChange(previousQuery, query) && query != null) {
                refreshActiveQuery(query)
            }
            previousQuery = query
        }
    searchOrchestrator.observe(queryFlow).launchIn(viewModelScope)
}
// observeSearchProjection(): runningFold 프로젝션 (76~91행)
// bindUiState(): 5-인자 combine → mutableUiState (93~120행)
```

주의: `observeUserPreferences`는 생성자 파라미터(비 private)이므로 함수 내부에서 그대로 호출 가능. 동작/순서 변경 금지(현재 init 등록 순서 유지).

검증: `./gradlew :feature:station-list:test`

---

## P2. Repository 결과 조립 추출 (권장, 동작 불변)

파일: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`

`observeNearbyStations`의 인라인 결과 생성을 의도 드러나는 함수로 추출한다.

```kotlin
}.flatMapLatest { (snapshot, cachedStations) ->
    if (snapshot == null) return@flatMapLatest flowOf(emptySearchResult())

    val fetchedAt = Instant.ofEpochMilli(snapshot.fetchedAtEpochMillis)
    if (cachedStations.isEmpty()) {
        return@flatMapLatest flowOf(snapshotOnlyResult(fetchedAt))
    }
    // ... 기존 combine 유지
}

private fun emptySearchResult(): StationSearchResult =
    StationSearchResult(emptyList(), StationFreshness.Stale, fetchedAt = null, hasCachedSnapshot = false)

private fun snapshotOnlyResult(fetchedAt: Instant): StationSearchResult =
    StationSearchResult(
        stations = emptyList(),
        freshness = cachePolicy.freshnessOf(fetchedAt, clock.instant()),
        fetchedAt = fetchedAt,
        hasCachedSnapshot = true,
    )
```

검증: `./gradlew :data:station:test`

---

## P4. Brand.fromCode 명확화 (선택)

파일: `core/model/src/main/kotlin/com/gasstation/core/model/Brand.kt`

```kotlin
companion object {
    private val BY_CODE = entries.associateBy(Brand::name)
    fun fromCode(code: String): Brand = BY_CODE[code] ?: ETC
}
```

검증: `./gradlew :core:model:test`

---

## 통합 검증 및 순서

1. P1 → 2. P3 → 3. P2 → 4. P4 (독립적이므로 분리 커밋 권장)

전체:

```bash
./gradlew :core:model:test :core:network:test :data:station:test :feature:station-list:test
./gradlew spotlessApply
```

각 단계는 해당 모듈 테스트 그린 확인 후 커밋. P1은 결함 수정이므로 단독 PR 권장(리뷰 집중).

## 영향 파일 요약

| 항목 | 수정 | 신규 테스트 |
|------|------|------------|
| P1 | Coordinates.kt, LocalKoreanCoordinateTransform.kt, NetworkStationMappers.kt, ProxyStationFetcher.kt, LocalKoreanCoordinateTransformTest.kt | CoordinatesTest, ProxyStationFetcherTest(+2), NetworkStationFetcherTest(+1) |
| P3 | StationListViewModel.kt | 기존 회귀 |
| P2 | DefaultStationRepository.kt | 기존 회귀 |
| P4 | Brand.kt | 기존 회귀 |
