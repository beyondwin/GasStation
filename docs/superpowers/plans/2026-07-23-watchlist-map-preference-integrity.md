# Watchlist And Map Preference Integrity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make watchlist prices obey the selected fuel type and make every external-map setting label, stored enum, package, URI, and fallback refer to the same provider.

**Architecture:** Add an explicit fuel-bearing `WatchlistQuery`, reshape saved-station summaries so identity survives when the selected fuel has no price, and let Watchlist observe the same settings stream as Nearby. Rename Kakao Navi to KakaoMap with a data-layer legacy mapping. Extract provider targets from the Android launcher, set packages explicitly, and return a structured launch result that the feature can turn into feedback.

**Tech Stack:** Kotlin, Coroutines/Flow, Room, DataStore, Hilt, Jetpack Compose, Robolectric, Android instrumentation

## Global Constraints

- Watchlist remains a saved-item comparison screen, not a second nearby search or refresh session.
- Search radius and brand filter do not remove saved items; Nearby sort does not reorder them.
- Every watchlist row uses the selected fuel type or displays an explicit unavailable price.
- Saved identity, coordinates, brand, and removal remain available when selected-fuel price is absent.
- Keep watched-time ordering.
- Rename `KAKAO_NAVI` to `KAKAO_MAP`; accept the legacy stored string and write only the new name.
- KakaoMap remains URI-based; do not add the Kakao Navi SDK or an app key.
- NAVER Maps must receive the runtime application ID as `appname`.
- Preserve existing Android package visibility declarations.
- Plans 1 and 2 are applied first.
- Preserve the user's existing `settings.gradle.kts` and `gradle/gradle-daemon-jvm.properties` changes.

---

## File Structure

### Shared preference vocabulary and map identity

- Rename `core:model` `MapProvider.KAKAO_NAVI` to `KAKAO_MAP`.
- Add shared radius and fuel labels to `core:designsystem`; keep screen-specific sort wording local.
- Add the legacy Kakao stored-name mapper in `data:settings`.
- Update Settings strings/tests and enum surface tests.

### Fuel-scoped watchlist domain/data

- Create `domain:station/model/WatchlistQuery.kt`.
- Reshape `WatchedStationSummary` around saved identity plus nullable price.
- Add `removeWatchedStation(stationId)` and `RemoveWatchedStationUseCase`.
- Add a fuel-filtered latest-cache DAO query.
- Replace implicit latest-fuel selection in `DefaultStationRepository` and `WatchlistSummaryAssembler`.

### Watchlist feature

- Observe `UserPreferences.fuelType` in `WatchlistViewModel`.
- Add loading/failure/fuel context to `WatchlistUiState`.
- Render unavailable prices without dropping saved rows.
- Keep existing row, remove, accessibility, and benchmark tags.

### External map

- Extract `ExternalMapTarget`.
- Add `ExternalMapLaunchResult`.
- Set route packages explicitly and implement ordered route/market/HTTPS fallback.
- Return Boolean success through the app-to-feature callback and show feedback on final failure.

### Connected flow and live docs

- Bind a recording map launcher in `StationPortfolioFlowTest`.
- Verify selected-fuel watchlist and selected map provider.
- Update offline, state, architecture, testing, verification, and README contracts.

---

### Task 1: Rename KakaoMap And Centralize Shared Radius/Fuel Labels

**Files:**
- Modify: `core/model/src/main/kotlin/com/gasstation/core/model/MapProvider.kt`
- Modify: `core/model/src/test/kotlin/com/gasstation/core/model/SharedEnumContractTest.kt`
- Create: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/PreferenceLabels.kt`
- Create: `core/designsystem/src/main/res/values/strings.xml`
- Create: `core/designsystem/src/main/res/values-en/strings.xml`
- Create: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/PreferenceLabelsTest.kt`
- Modify: `data/settings/src/main/kotlin/com/gasstation/data/settings/DefaultSettingsRepository.kt`
- Modify: `data/settings/src/test/kotlin/com/gasstation/data/settings/DefaultSettingsRepositoryTest.kt`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsUiState.kt`
- Modify: `feature/settings/src/main/res/values/strings.xml`
- Modify: `feature/settings/src/main/res/values-en/strings.xml`
- Modify: `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsScreenTest.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListFilterRail.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListQuerySummary.kt`
- Modify: `app/src/main/java/com/gasstation/map/ExternalMapLauncher.kt`
- Modify: `app/src/testDemo/java/com/gasstation/map/ExternalMapLauncherTest.kt`

**Interfaces:**
- Produces: `MapProvider.KAKAO_MAP`.
- Produces: `SearchRadius.gasStationSearchRadiusLabel(): StringResource`.
- Produces: `FuelType.gasStationFuelTypeLabel(): StringResource`.
- Produces: legacy stored `KAKAO_NAVI` maps to domain `KAKAO_MAP`.

- [ ] **Step 1: Write failing enum, migration, and label tests**

Update the enum contract:

```kotlin
assertEquals(
    listOf("TMAP", "KAKAO_MAP", "NAVER_MAP"),
    MapProvider.entries.map { it.name },
)
```

Add to `DefaultSettingsRepositoryTest`:

```kotlin
@Test
fun `legacy kakao navi stored name restores as kakao map and next write is stable`() = runBlocking {
    val dataSource = InMemoryUserPreferencesDataSource(
        StoredUserPreferences.Default.copy(mapProviderName = "KAKAO_NAVI"),
    )
    val repository = DefaultSettingsRepository(dataSource)

    val restored = repository.observeUserPreferences().first()
    val committed = repository.updateUserPreferences { it }

    assertEquals(MapProvider.KAKAO_MAP, restored.mapProvider)
    assertEquals(MapProvider.KAKAO_MAP, committed.mapProvider)
    assertEquals("KAKAO_MAP", dataSource.current.mapProviderName)
}
```

Create label tests:

```kotlin
@Test
fun `radius and fuel values map to canonical shared resources`() {
    assertEquals(
        StringResource.fromId(R.string.gas_station_radius_km5),
        SearchRadius.KM_5.gasStationSearchRadiusLabel(),
    )
    assertEquals(
        StringResource.fromId(R.string.gas_station_fuel_diesel),
        FuelType.DIESEL.gasStationFuelTypeLabel(),
    )
}
```

- [ ] **Step 2: Run focused tests and capture RED**

Run:

```bash
./gradlew \
  :core:model:test \
  :core:designsystem:testDebugUnitTest \
  :data:settings:testDebugUnitTest \
  --warning-mode fail
```

Expected: compilation fails because `KAKAO_MAP` and label functions do not exist.

- [ ] **Step 3: Implement enum migration and shared labels**

Use:

```kotlin
enum class MapProvider {
    TMAP,
    KAKAO_MAP,
    NAVER_MAP,
}
```

In `DefaultSettingsRepository`:

```kotlin
private fun parseMapProvider(value: String): MapProvider = when (value) {
    "KAKAO_NAVI" -> MapProvider.KAKAO_MAP
    else -> enumOrDefault(value, MapProvider.TMAP)
}
```

Use `parseMapProvider(mapProviderName)` in `toDomain`; `toStored` naturally writes `KAKAO_MAP`.

Create:

```kotlin
package com.gasstation.core.designsystem

import com.gasstation.core.designsystem.string.StringResource
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius

fun SearchRadius.gasStationSearchRadiusLabel(): StringResource = when (this) {
    SearchRadius.KM_3 -> StringResource.fromId(R.string.gas_station_radius_km3)
    SearchRadius.KM_4 -> StringResource.fromId(R.string.gas_station_radius_km4)
    SearchRadius.KM_5 -> StringResource.fromId(R.string.gas_station_radius_km5)
}

fun FuelType.gasStationFuelTypeLabel(): StringResource = when (this) {
    FuelType.GASOLINE -> StringResource.fromId(R.string.gas_station_fuel_gasoline)
    FuelType.DIESEL -> StringResource.fromId(R.string.gas_station_fuel_diesel)
    FuelType.PREMIUM_GASOLINE -> StringResource.fromId(R.string.gas_station_fuel_premium)
    FuelType.KEROSENE -> StringResource.fromId(R.string.gas_station_fuel_kerosene)
    FuelType.LPG -> StringResource.fromId(R.string.gas_station_fuel_lpg)
}
```

Korean resources are `3km`, `4km`, `5km`, `휘발유`, `경유`, `고급휘발유`, `등유`, `LPG`. English resources are `3 km`, `4 km`, `5 km`, `Gasoline`, `Diesel`, `Premium gasoline`, `Kerosene`, `LPG`.

Replace duplicate radius/fuel label functions in Nearby and Settings with the
shared functions. `SettingsUiState` passes the shared `StringResource`
directly. In `StationListFilterRail` and `QueryContextSummary`, resolve it at
the Compose boundary:

```kotlin
val context = LocalContext.current
val radiusLabel =
    preferences.searchRadius.gasStationSearchRadiusLabel().resolve(context)
val fuelLabel =
    preferences.fuelType.gasStationFuelTypeLabel().resolve(context)
```

Build `StationListFilterOption` labels with the same
`gasStationSearchRadiusLabel().resolve(context)` and
`gasStationFuelTypeLabel().resolve(context)` calls. Keep `거리순` versus
`거리순 보기` local because those labels have different screen meaning.

Update Settings copy:

```xml
<string name="settings_radius_subtitle">주변 목록 검색에 사용할 반경을 정합니다.</string>
<string name="settings_fuel_type_subtitle">주변 목록과 관심 주유소 비교에 사용할 유종을 고릅니다.</string>
<string name="settings_brand_filter_subtitle">주변 목록에서 비교할 브랜드 범위를 정합니다.</string>
<string name="settings_sort_order_subtitle">주변 목록의 가격·거리 정렬 기준을 정합니다.</string>
<string name="settings_map_kakao">카카오맵</string>
<string name="settings_map_kakao_desc">카카오맵으로 자동차 길찾기를 엽니다.</string>
```

English:

```xml
<string name="settings_radius_subtitle">Set the radius used by the Nearby list.</string>
<string name="settings_fuel_type_subtitle">Choose the fuel used by Nearby and saved-station comparisons.</string>
<string name="settings_brand_filter_subtitle">Choose which brands appear in the Nearby list.</string>
<string name="settings_sort_order_subtitle">Choose price or distance ordering for the Nearby list.</string>
<string name="settings_map_kakao">KakaoMap</string>
<string name="settings_map_kakao_desc">Open KakaoMap for driving directions.</string>
```

Change all code references to `MapProvider.KAKAO_MAP`. Extend
`SettingsScreenTest` to assert the four consumer-scope subtitles and the
KakaoMap label so future copy cannot claim that radius, brand, or nearby sort
filters the watchlist.

- [ ] **Step 4: Run enum, labels, settings, and Nearby tests**

Run:

```bash
./gradlew \
  :core:model:test \
  :core:designsystem:testDebugUnitTest \
  :data:settings:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  --warning-mode fail
```

Expected: PASS.

- [ ] **Step 5: Commit provider identity and vocabulary**

```bash
git add \
  core/model \
  core/designsystem \
  data/settings \
  feature/settings \
  feature/station-list \
  app/src/main/java/com/gasstation/map/ExternalMapLauncher.kt \
  app/src/testDemo/java/com/gasstation/map/ExternalMapLauncherTest.kt
git commit -m "fix: align map provider identity and labels"
```

### Task 2: Add Fuel-Scoped Watchlist Domain And Data

**Files:**
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/WatchlistQuery.kt`
- Modify: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/WatchedStationSummary.kt`
- Modify: `domain/station/src/main/kotlin/com/gasstation/domain/station/StationRepository.kt`
- Modify: `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/ObserveWatchlistUseCase.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/RemoveWatchedStationUseCase.kt`
- Modify: `domain/station/src/test/kotlin/com/gasstation/domain/station/DomainContractSurfaceTest.kt`
- Modify: `core/database/src/main/kotlin/com/gasstation/core/database/station/StationCacheDao.kt`
- Modify: `core/database/src/test/kotlin/com/gasstation/core/database/station/StationCacheDaoTest.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/WatchlistSummaryAssembler.kt`
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/WatchlistRepositoryTest.kt`
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/RepositoryDoubles.kt`
- Modify StationRepository test doubles in:
  - `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/GpsAvailabilityMonitorTest.kt`
  - `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTestFixtures.kt`
  - `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationSearchOrchestratorTest.kt`
  - `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistViewModelTest.kt`

**Interfaces:**
- Produces: `WatchlistQuery(origin: Coordinates, fuelType: FuelType)`.
- Produces: `WatchedStationSummary` with nullable `price`.
- Produces: `StationRepository.observeWatchlist(query)`.
- Produces: `StationRepository.removeWatchedStation(stationId)`.
- Produces: fuel-filtered latest cache DAO flow.

- [ ] **Step 1: Write failing DAO and repository tests**

Add a DAO test that inserts GASOLINE and DIESEL cache rows for the same station:

```kotlin
@Test
fun `latest watchlist cache query returns only requested fuel`() = runBlocking {
    val gasolineKey = CacheKey(
        latitudeBucket = 16649,
        longitudeBucket = 50811,
        radiusMeters = 3_000,
        fuelType = "GASOLINE",
    )
    val dieselKey = gasolineKey.copy(fuelType = "DIESEL")
    dao.upsertAll(
        listOf(
            station(
                cacheKey = gasolineKey,
                stationId = "station-1",
                priceWon = 1_700,
                fetchedAtEpochMillis = 100,
            ),
            station(
                cacheKey = dieselKey,
                stationId = "station-1",
                priceWon = 1_550,
                fetchedAtEpochMillis = 200,
            ),
        ),
    )

    val rows = dao.observeLatestStationsByIdsAndFuelType(
        stationIds = listOf("station-1"),
        fuelType = "GASOLINE",
    ).first()

    assertEquals(listOf("GASOLINE"), rows.map { it.fuelType })
    assertEquals(listOf(1_700), rows.map { it.priceWon })
}
```

Replace implicit-context repository tests with:

```kotlin
@Test
fun `watchlist uses requested fuel and never substitutes newer other fuel`() = runBlocking {
    val repository = repository(
        stationCacheDao = RecordingWatchlistStationCacheDao(
            cachedStations = listOf(
                cachedStation(
                    stationId = "station-1",
                    name = "Gasoline snapshot",
                    brandCode = "GSC",
                    priceWon = 1_700,
                    latitude = 37.498095,
                    longitude = 127.027610,
                    fetchedAt = now.minusSeconds(60),
                    fuelType = "GASOLINE",
                ),
                cachedStation(
                    stationId = "station-1",
                    name = "Diesel snapshot",
                    brandCode = "GSC",
                    priceWon = 1_520,
                    latitude = 37.498095,
                    longitude = 127.027610,
                    fetchedAt = now.minusSeconds(10),
                    fuelType = "DIESEL",
                ),
            ),
        ),
        watchedStationDao = RecordingWatchedStationDao(
            watchedStations = listOf(
                watched(
                    stationId = "station-1",
                    watchedAt = now,
                    name = "Saved",
                    brandCode = "GSC",
                ),
            ),
        ),
    )

    val item = repository.observeWatchlist(
        WatchlistQuery(
            origin = Coordinates(37.498095, 127.027610),
            fuelType = FuelType.GASOLINE,
        ),
    ).first().single()

    assertEquals(1_700, item.price?.value)
}
```

```kotlin
@Test
fun `watchlist retains saved identity when selected fuel has no price`() = runBlocking {
    val repository = repository(
        watchedStationDao = RecordingWatchedStationDao(
            watchedStations = listOf(
                watched(
                    stationId = "station-1",
                    name = "Saved Without Diesel",
                    brandCode = "GSC",
                    latitude = 37.498095,
                    longitude = 127.027610,
                    watchedAt = now,
                ),
            ),
        ),
    )

    val item = repository.observeWatchlist(
        WatchlistQuery(
            origin = Coordinates(37.498095, 127.027610),
            fuelType = FuelType.DIESEL,
        ),
    ).first().single()

    assertEquals("station-1", item.id)
    assertEquals("Saved Without Diesel", item.name)
    assertEquals(null, item.price)
    assertEquals(StationPriceDelta.Unavailable, item.priceDelta)
}
```

- [ ] **Step 2: Run focused DAO/repository tests and capture RED**

Run:

```bash
./gradlew \
  :core:database:testDebugUnitTest \
  :data:station:testDebugUnitTest \
  --warning-mode fail
```

Expected: compilation fails because `WatchlistQuery`, nullable summary price, and the DAO method do not exist.

- [ ] **Step 3: Add domain models and repository contracts**

Create:

```kotlin
package com.gasstation.domain.station.model

import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType

data class WatchlistQuery(
    val origin: Coordinates,
    val fuelType: FuelType,
)
```

Replace the summary with:

```kotlin
data class WatchedStationSummary(
    val id: String,
    val name: String,
    val brand: Brand,
    val price: MoneyWon?,
    val distance: DistanceMeters,
    val coordinates: Coordinates,
    val priceDelta: StationPriceDelta,
    val lastSeenAt: Instant?,
)
```

Change:

```kotlin
fun observeWatchlist(query: WatchlistQuery): Flow<List<WatchedStationSummary>>

suspend fun removeWatchedStation(stationId: String)
```

Create:

```kotlin
class RemoveWatchedStationUseCase @Inject constructor(
    private val stationRepository: StationRepository,
) {
    suspend operator fun invoke(stationId: String) {
        stationRepository.removeWatchedStation(stationId)
    }
}
```

`ObserveWatchlistUseCase.invoke` accepts `WatchlistQuery`.

- [ ] **Step 4: Add the fuel-filtered DAO query**

Add:

```kotlin
@Query(
    """
    SELECT * FROM station_cache AS latest
    WHERE latest.stationId IN (:stationIds)
      AND latest.fuelType = :fuelType
      AND NOT EXISTS (
          SELECT 1 FROM station_cache AS candidate
          WHERE candidate.stationId = latest.stationId
            AND candidate.fuelType = :fuelType
            AND (
                candidate.fetchedAtEpochMillis > latest.fetchedAtEpochMillis
                OR (
                    candidate.fetchedAtEpochMillis = latest.fetchedAtEpochMillis
                    AND candidate.radiusMeters < latest.radiusMeters
                )
                OR (
                    candidate.fetchedAtEpochMillis = latest.fetchedAtEpochMillis
                    AND candidate.radiusMeters = latest.radiusMeters
                    AND candidate.latitudeBucket < latest.latitudeBucket
                )
                OR (
                    candidate.fetchedAtEpochMillis = latest.fetchedAtEpochMillis
                    AND candidate.radiusMeters = latest.radiusMeters
                    AND candidate.latitudeBucket = latest.latitudeBucket
                    AND candidate.longitudeBucket < latest.longitudeBucket
                )
            )
      )
    ORDER BY latest.stationId ASC
    """,
)
abstract fun observeLatestStationsByIdsAndFuelType(
    stationIds: List<String>,
    fuelType: String,
): Flow<List<StationCacheEntity>>
```

Implement this abstract method in `RecordingStationCacheDao`, `EmptyStationCacheDao`, and `RecordingWatchlistStationCacheDao` by filtering `fuelType` before choosing the latest row.

- [ ] **Step 5: Implement fuel-scoped watchlist assembly**

`DefaultStationRepository.observeWatchlist` uses:

```kotlin
override fun observeWatchlist(
    query: WatchlistQuery,
): Flow<List<WatchedStationSummary>> =
    watchedStationDao.observeWatchedStations().flatMapLatest { watchedStations ->
        if (watchedStations.isEmpty()) {
            return@flatMapLatest flowOf(emptyList())
        }
        val stationIds = watchedStations.map { it.stationId }.distinct()
        combine(
            stationCacheDao.observeLatestStationsByIdsAndFuelType(
                stationIds = stationIds,
                fuelType = query.fuelType.name,
            ),
            stationPriceHistoryDao.observeByStationIdsAndFuelType(
                stationIds = stationIds,
                fuelType = query.fuelType.name,
            ),
        ) { cachedStations, historyRows ->
            val cacheById = cachedStations.associateBy { it.stationId }
            val historyById = historyRows.groupByStationId()
            watchedStations.mapNotNull { watched ->
                watched.toWatchedSummary(
                    origin = query.origin,
                    cachedStation = cacheById[watched.stationId],
                    history = historyById[watched.stationId].orEmpty(),
                )
            }
        }
    }
```

Assembler rules:

```kotlin
val savedCoordinates = Coordinates.ofOrNull(latitude, longitude) ?: return null
val validCachePrice = cachedStation?.priceWon?.let(MoneyWon::ofOrNull)
val validCacheCoordinates = cachedStation?.let {
    Coordinates.ofOrNull(it.latitude, it.longitude)
}
val historyRows = history.sortedByDescending { it.fetchedAtEpochMillis }
val historyPrice = historyRows.firstOrNull()?.priceWon?.let(MoneyWon::ofOrNull)
val price = validCachePrice ?: historyPrice
val coordinates = validCacheCoordinates ?: savedCoordinates
```

Use cached name/brand only when cache coordinates and price are valid; otherwise use saved identity. Compute distance from `origin` to `coordinates`. Compute a delta only within the already fuel-filtered history. Return a summary even when `price == null`.

Implement:

```kotlin
override suspend fun removeWatchedStation(stationId: String) {
    watchedStationDao.delete(stationId)
}
```

Keep `updateWatchState(station, false)` working for Nearby compatibility; internally it may call `removeWatchedStation(station.id)`.

- [ ] **Step 6: Update repository doubles and run full data/domain tests**

Every `StationRepository` fake uses:

```kotlin
override fun observeWatchlist(
    query: WatchlistQuery,
): Flow<List<WatchedStationSummary>> = flowOf(emptyList())

override suspend fun removeWatchedStation(stationId: String) = Unit
```

Run:

```bash
./gradlew \
  :domain:station:test \
  :core:database:testDebugUnitTest \
  :data:station:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  --warning-mode fail
```

Expected: PASS. Do not run `feature:watchlist` yet: its production consumer is
intentionally migrated in Task 3 before this contract is committed.

- [ ] **Step 7: Keep the domain/data change uncommitted until its consumer is migrated**

Run `git status --short` and confirm the Task 2 paths are present together with
only the pre-existing user files. Continue directly to Task 3; do not create an
intermediate commit whose `feature:watchlist` consumer cannot compile.

### Task 3: Observe Fuel Preferences And Render Unavailable Watchlist Prices

**Files:**
- Modify: `feature/watchlist/build.gradle.kts`
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistAction.kt`
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistItemUiModel.kt`
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistSummaryUiModel.kt`
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistUiState.kt`
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistViewModel.kt`
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`
- Create: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistStateScreens.kt`
- Modify: `feature/watchlist/src/main/res/values/strings.xml`
- Modify: `feature/watchlist/src/main/res/values-en/strings.xml`
- Modify: `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistViewModelTest.kt`
- Modify: `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistItemUiModelTest.kt`
- Modify: `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistSummaryUiModelTest.kt`
- Modify: `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistScreenTest.kt`
- Modify: `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistEnglishScreenTest.kt`
- Update watchlist Roborazzi baselines after verification.

**Interfaces:**
- Consumes: `ObserveUserPreferencesUseCase`.
- Consumes: Task 2 `WatchlistQuery` and `RemoveWatchedStationUseCase`.
- Produces: `WatchlistUiState.fuelType`, loading, and failure.
- Produces: nullable price UI model.

- [ ] **Step 1: Write failing ViewModel and UI model tests**

Add the allowed feature-to-domain dependency:

```kotlin
implementation(project(":domain:settings"))
```

Add:

```kotlin
@Test
fun `watchlist re-queries every row when selected fuel changes`() = runTest(dispatcher) {
    Dispatchers.setMain(dispatcher)
    val preferences = MutableStateFlow(UserPreferences.default())
    val repository = RecordingWatchlistRepository()
    val viewModel = watchlistViewModel(repository, preferences)
    val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        viewModel.uiState.collect()
    }

    preferences.value = preferences.value.copy(fuelType = FuelType.DIESEL)
    advanceUntilIdle()

    assertEquals(
        listOf(FuelType.GASOLINE, FuelType.DIESEL),
        repository.queries.map { it.fuelType },
    )
    assertEquals(FuelType.DIESEL, viewModel.uiState.value.fuelType)
    job.cancel()
}
```

```kotlin
@Test
fun `unavailable selected fuel keeps saved row with no price labels`() {
    val item = WatchlistItemUiModel(
        WatchedStationSummary(
            id = "station-1",
            name = "Saved Station",
            brand = Brand.GSC,
            price = null,
            distance = DistanceMeters(300),
            coordinates = Coordinates(37.498095, 127.027610),
            priceDelta = StationPriceDelta.Unavailable,
            lastSeenAt = null,
        ),
    )

    assertEquals(null, item.priceWon)
    assertEquals(null, item.priceNumberLabel)
    assertEquals("station-1", item.id)
}
```

```kotlin
@Test
fun `summary average ignores unavailable prices but count retains saved rows`() {
    val summary = WatchlistSummaryUiModel.from(
        listOf(
            availableItem(priceWon = 1_700),
            unavailableItem(),
        ),
    )

    assertEquals(2, summary.count)
    assertEquals(1_700, summary.averagePriceWon)
}
```

- [ ] **Step 2: Run focused feature tests and capture RED**

Run:

```bash
./gradlew :feature:watchlist:testDebugUnitTest --warning-mode fail
```

Expected: compilation fails because Watchlist does not observe settings and price fields are non-null.

- [ ] **Step 3: Implement Watchlist readiness and fuel observation**

Use:

```kotlin
sealed interface WatchlistAction {
    data object RetryLoad : WatchlistAction
    data class RemoveClicked(val stationId: String) : WatchlistAction
}
```

Use:

```kotlin
data class WatchlistUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val fuelType: FuelType? = null,
    val stations: List<WatchlistItemUiModel> = emptyList(),
    val summary: WatchlistSummaryUiModel = WatchlistSummaryUiModel(),
)
```

`WatchlistViewModel` owns a retryable observation job:

```kotlin
private fun observe() {
    observationJob?.cancel()
    mutableUiState.value = WatchlistUiState(isLoading = true)
    observationJob = observeUserPreferences()
        .map(UserPreferences::fuelType)
        .distinctUntilChanged()
        .flatMapLatest { fuelType ->
            observeWatchlist(WatchlistQuery(origin, fuelType))
                .map { summaries -> fuelType to summaries }
        }
        .onEach { (fuelType, summaries) ->
            val items = summaries.map(::WatchlistItemUiModel)
            mutableUiState.value = WatchlistUiState(
                isLoading = false,
                fuelType = fuelType,
                stations = items,
                summary = WatchlistSummaryUiModel.from(items),
            )
            if (!hasLoggedCompareViewed) {
                hasLoggedCompareViewed = true
                stationEventLogger.logSafely(
                    StationEvent.CompareViewed(count = items.size),
                )
            }
        }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            mutableUiState.value = WatchlistUiState(
                isLoading = false,
                loadFailed = true,
            )
        }
        .launchIn(viewModelScope)
}
```

Removal uses only identity:

```kotlin
is WatchlistAction.RemoveClicked -> viewModelScope.launch {
    removeWatchedStation(action.stationId)
    stationEventLogger.logSafely(
        StationEvent.WatchToggled(
            stationId = action.stationId,
            watched = false,
        ),
    )
}
```

`RetryLoad` calls `observe()`.

- [ ] **Step 4: Implement nullable price UI and fuel context**

Change price fields:

```kotlin
val priceWon: Int?,
val priceLabel: String?,
val priceNumberLabel: String?,
val priceUnitLabel: String?,
```

The summary constructor maps nullable price directly. Require split labels only when `priceWon != null`.

Summary average:

```kotlin
val availablePrices = items.mapNotNull(WatchlistItemUiModel::priceWon)
val average = availablePrices.takeIf { prices -> prices.isNotEmpty() }?.let { prices ->
    ((prices.sumOf(Int::toLong) + prices.size / 2L) / prices.size).toInt()
}
```

In the screen, branch loading, failure, empty, and result. Use `WatchlistStateScreens.kt` with the existing guidance/loading primitives.

Add:

```xml
<string name="watchlist_fuel_context">%1$s 기준</string>
<string name="watchlist_price_unavailable">선택 유종 가격 없음</string>
<string name="watchlist_load_failed_title">관심 주유소를 불러오지 못했습니다.</string>
<string name="watchlist_load_failed_body">저장된 설정과 가격 정보를 다시 확인해 주세요.</string>
<string name="watchlist_retry">다시 시도</string>
```

Add equivalent English strings.

Summary renders the resolved shared fuel label. A row with no price uses `watchlist_price_unavailable` in the price hero position and does not render a won unit or price delta. Keep distance and remove action visible.

- [ ] **Step 5: Run unit, accessibility, and screenshot tests**

Run:

```bash
./gradlew \
  :feature:watchlist:testDebugUnitTest \
  :feature:watchlist:verifyRoborazziDebug \
  --warning-mode fail
```

Expected: unit tests PASS. Record approved fuel-context snapshot changes, then re-verify:

```bash
./gradlew :feature:watchlist:recordRoborazziDebug
./gradlew :feature:watchlist:verifyRoborazziDebug
```

- [ ] **Step 6: Commit Watchlist feature integrity**

```bash
git add \
  domain/station \
  core/database \
  data/station \
  feature/station-list/src/test \
  feature/watchlist
git commit -m "fix: scope watchlist to selected fuel"
```

### Task 4: Build Explicit External Map Targets And Non-Crashing Fallback

**Files:**
- Modify: `app/src/main/java/com/gasstation/map/ExternalMapLauncher.kt`
- Modify: `app/src/testDemo/java/com/gasstation/map/ExternalMapLauncherTest.kt`
- Modify: `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
- Modify: `feature/station-list/src/main/res/values/strings.xml`
- Modify: `feature/station-list/src/main/res/values-en/strings.xml`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`

**Interfaces:**
- Produces: `ExternalMapTarget`.
- Produces: `ExternalMapLaunchResult.Opened`, `StoreOpened`, or `Failed`.
- Produces: `ExternalMapLauncher.open(...): ExternalMapLaunchResult`.
- Produces: app callback returns `Boolean` to feature.

- [ ] **Step 1: Replace the partial map test with a provider matrix**

Use parameterized cases:

```kotlin
private data class ProviderCase(
    val provider: MapProvider,
    val packageName: String,
    val expectedUri: String,
)

private val cases = listOf(
    ProviderCase(
        MapProvider.TMAP,
        "com.skt.tmap.ku",
        "tmap://route?goalx=127.12861&goaly=37.499095&goalname=%EA%B0%95%EB%82%A8%EC%A3%BC%EC%9C%A0%EC%86%8C&reqCoordType=KTM&resCoordType=WGS84",
    ),
    ProviderCase(
        MapProvider.KAKAO_MAP,
        "net.daum.android.map",
        "kakaomap://route?sp=37.498095,127.02761&ep=37.499095,127.12861&ename=%EA%B0%95%EB%82%A8%EC%A3%BC%EC%9C%A0%EC%86%8C&by=car",
    ),
    ProviderCase(
        MapProvider.NAVER_MAP,
        "com.nhn.android.nmap",
        "nmap://route/car?dlat=37.499095&dlng=127.12861&dname=%EA%B0%95%EB%82%A8%EC%A3%BC%EC%9C%A0%EC%86%8C&appname=com.gasstation.demo",
    ),
)
```

For each installed package, assert action, explicit package, exact URI, `CATEGORY_BROWSABLE`, and `Opened`.

Add missing-package fallback:

```kotlin
assertEquals(ExternalMapLaunchResult.StoreOpened, result)
assertEquals(
    "market://details?id=com.nhn.android.nmap",
    shadowOf(application).nextStartedActivity.dataString,
)
```

Add a context that throws `ActivityNotFoundException` for market and route but records HTTPS, and another that throws for every call; assert `StoreOpened` and `Failed`.

- [ ] **Step 2: Run map launcher tests and capture RED**

Run:

```bash
./gradlew :app:testDemoDebugUnitTest --tests '*ExternalMapLauncherTest'
```

Expected: compilation fails because targets/results do not exist and route intents have no explicit package or NAVER `appname`.

- [ ] **Step 3: Implement targets and ordered fallback**

Define:

```kotlin
sealed interface ExternalMapLaunchResult {
    data object Opened : ExternalMapLaunchResult
    data object StoreOpened : ExternalMapLaunchResult
    data object Failed : ExternalMapLaunchResult
}

internal data class ExternalMapTarget(
    val packageName: String,
    val routeUri: String,
)
```

Change `ExternalMapLauncher.open` to return `ExternalMapLaunchResult`.

Create a pure target builder:

```kotlin
internal fun MapProvider.externalMapTarget(
    applicationId: String,
    stationName: String,
    originLatitude: Double?,
    originLongitude: Double?,
    latitude: Double,
    longitude: Double,
): ExternalMapTarget
```

Use the exact provider cases from Step 1. NAVER uses `applicationId = context.packageName`. Preserve the checked-in TMAP URI as an explicitly unverified compatibility contract; do not claim installed-device destination success without evidence.

Route intent:

```kotlin
Intent(Intent.ACTION_VIEW, target.routeUri.toUri())
    .setPackage(target.packageName)
    .addCategory(Intent.CATEGORY_BROWSABLE)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
```

Start in this order:

1. explicit route when package is installed;
2. `market://details?id=<package>`;
3. `https://play.google.com/store/apps/details?id=<package>`;
4. return `Failed`.

Catch `ActivityNotFoundException` and `SecurityException` for each attempt. Return `Opened` only for route and `StoreOpened` for either store URI.

- [ ] **Step 4: Return launch failure to the feature**

Keep the feature independent of app result types:

```kotlin
onOpenExternalMap: (StationListEffect.OpenExternalMap) -> Boolean
```

In `GasStationNavHost`:

```kotlin
externalMapLauncher.open(
    provider = effect.provider,
    stationName = effect.stationName,
    originLatitude = effect.originLatitude,
    originLongitude = effect.originLongitude,
    latitude = effect.latitude,
    longitude = effect.longitude,
) != ExternalMapLaunchResult.Failed
```

In the StationList effect collector:

```kotlin
is StationListEffect.OpenExternalMap -> {
    if (!onOpenExternalMap(effect)) {
        snackbarHostState.showSnackbar(
            context.getString(R.string.station_list_external_map_failed),
        )
    }
}
```

Add Korean `지도 앱을 열지 못했습니다.` and English `Could not open the map app.`.

- [ ] **Step 5: Run app and station-list tests**

Run:

```bash
./gradlew \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  --warning-mode fail
```

Expected: PASS.

- [ ] **Step 6: Commit external map integrity**

```bash
git add app/src/main app/src/testDemo feature/station-list
git commit -m "fix: align external map launch contracts"
```

### Task 5: Add Connected Consumer Coverage And Synchronize Live Contracts

**Files:**
- Modify: `app/src/androidTest/java/com/gasstation/StationPortfolioFlowTest.kt`
- Modify: `README.md`
- Modify: `docs/architecture.md`
- Modify: `docs/state-model.md`
- Modify: `docs/offline-strategy.md`
- Modify: `docs/test-strategy.md`
- Modify: `docs/verification-matrix.md`

**Interfaces:**
- Consumes: map launcher interface from Task 4.
- Consumes: Settings option tags from Plan 1.
- Consumes: Watchlist fuel context from Task 3.
- Produces: connected proof for fuel and provider consumers.

- [ ] **Step 1: Bind a recording launcher in the connected test**

Annotate the test class so its recording binding replaces the production
provider:

```kotlin
@UninstallModules(ExternalMapModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StationPortfolioFlowTest
```

Add the station repository injection and recording launcher:

```kotlin
@Inject
lateinit var stationRepository: StationRepository

@BindValue
@JvmField
val externalMapLauncher: ExternalMapLauncher = RecordingExternalMapLauncher()
```

Use:

```kotlin
private class RecordingExternalMapLauncher : ExternalMapLauncher {
    val providers = mutableListOf<MapProvider>()

    override fun open(
        provider: MapProvider,
        stationName: String,
        originLatitude: Double?,
        originLongitude: Double?,
        latitude: Double,
        longitude: Double,
    ): ExternalMapLaunchResult {
        providers += provider
        return ExternalMapLaunchResult.Opened
    }
}
```

Add imports for `ExternalMapModule`, `ExternalMapLauncher`,
`ExternalMapLaunchResult`, `StationRepository`, `Station`, `Brand`,
`Coordinates`, `DistanceMeters`, `MoneyWon`, `BindValue`, and
`UninstallModules`.

- [ ] **Step 2: Add connected fuel and map tests**

Fuel test:

```kotlin
@Test
fun demoWatchlist_keepsSavedRowAndUsesSelectedFuelContext() {
    reseedDemoDatabase()
    waitForNearby()
    runBlocking {
        stationRepository.updateWatchState(
            station = Station(
                id = "connected-gasoline-only",
                name = "휘발유 전용 저장 주유소",
                brand = Brand.ETC,
                price = MoneyWon(1_987),
                distance = DistanceMeters(400),
                coordinates = Coordinates(37.5004, 127.0321),
            ),
            watched = true,
        )
    }

    rule.onNodeWithTag("bottom-nav-settings", useUnmergedTree = true).performClick()
    rule.onNodeWithTag("settings-row-fuel-type", useUnmergedTree = true).performClick()
    rule.onNodeWithTag("settings-option-DIESEL", useUnmergedTree = true).performClick()
    rule.onNodeWithTag("bottom-nav-watchlist", useUnmergedTree = true).performClick()

    rule.onNodeWithText("경유 기준").assertExists()
    rule.onAllNodesWithTag("watchlist-card", useUnmergedTree = true).assertCountEquals(1)
    rule.onNodeWithText("선택 유종 가격 없음").assertExists()
}
```

The connected-only station is inserted through the real repository after demo
reseeding and has no DIESEL cache or history. This keeps the unavailable
assertion deterministic without depending on home-list ordering.

Map test:

```kotlin
@Test
fun demoMapSelection_isConsumedByNearbyHandoff() {
    reseedDemoDatabase()
    waitForNearby()

    rule.onNodeWithTag("bottom-nav-settings", useUnmergedTree = true).performClick()
    rule.onNodeWithTag("settings-row-map-provider", useUnmergedTree = true).performClick()
    rule.onNodeWithTag("settings-option-NAVER_MAP", useUnmergedTree = true).performClick()
    rule.onNodeWithTag("bottom-nav-nearby", useUnmergedTree = true).performClick()
    rule.onAllNodesWithTag("station-list-row", useUnmergedTree = true)
        .onFirst()
        .performClick()

    assertEquals(
        listOf(MapProvider.NAVER_MAP),
        (externalMapLauncher as RecordingExternalMapLauncher).providers,
    )
}
```

- [ ] **Step 3: Run connected demo coverage**

Run:

```bash
./gradlew :app:compileDemoDebugAndroidTestKotlin
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDemoDebugAndroidTest
```

Expected: PASS.

- [ ] **Step 4: Check TMAP device evidence without inventing support**

Run:

```bash
ADB=/Users/kws/Library/Android/sdk/platform-tools/adb
"$ADB" shell pm list packages com.skt.tmap.ku
```

If the package is installed, select TMAP, click a deterministic station, and confirm the destination name and coordinates appear. If it is absent, report TMAP destination behavior as unverified; do not install software or claim success. Unit tests still protect package, URI serialization, and fallback behavior.

- [ ] **Step 5: Update live documentation**

Document:

```text
Watchlist observes the selected fuel type and queries cache/history only for that
fuel. Missing selected-fuel price retains the saved station and renders an
explicit unavailable state.

KakaoMap is the supported Kakao provider. Legacy KAKAO_NAVI storage migrates on
read and the next write stores KAKAO_MAP.

External map intents set the provider package explicitly. NAVER includes the
runtime application ID; final route/store failure returns visible feedback.
```

Update offline fallback, state ownership, connected test scope, and verification commands.

- [ ] **Step 6: Run complete verification**

Run:

```bash
./gradlew \
  :core:model:test \
  :core:designsystem:testDebugUnitTest \
  :core:datastore:testDebugUnitTest \
  :core:database:testDebugUnitTest \
  :domain:settings:test \
  :domain:station:test \
  :data:settings:testDebugUnitTest \
  :data:station:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  --warning-mode fail
```

Run:

```bash
./gradlew \
  :feature:settings:verifyRoborazziDebug \
  :feature:station-list:verifyRoborazziDebug \
  :feature:watchlist:verifyRoborazziDebug
```

Run:

```bash
scripts/agent/verify.sh auto
```

Expected: all executed gates PASS. Live prod Opinet results and an absent TMAP app remain explicitly unverified.

- [ ] **Step 7: Review and commit live contracts**

Run:

```bash
git diff --check
git status --short
git diff --stat
```

Expected: only approved implementation/docs plus pre-existing user changes.

Commit:

```bash
git add README.md docs/architecture.md docs/state-model.md docs/offline-strategy.md docs/test-strategy.md docs/verification-matrix.md app/src/androidTest
git commit -m "test: cover preference consumers end to end"
```
