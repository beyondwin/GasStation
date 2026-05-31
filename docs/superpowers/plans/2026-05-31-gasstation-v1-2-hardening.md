# GasStation v1.2 Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close GasStation v1.2's performance evidence, backend proxy readiness, and developer verification velocity gaps without changing the user-facing station comparison flow.

**Architecture:** Keep the existing Android module boundaries intact. Track A changes only benchmark selectors and benchmark documentation, Track B adds a proxy-ready network contract behind `core:network` while preserving the direct Opinet default, and Track C measures and documents the build/CI settings that are already enabled.

**Tech Stack:** Kotlin, Jetpack Compose semantics, AndroidX Macrobenchmark, Hilt, Retrofit/Gson, MockWebServer, Gradle, GitHub Actions.

---

## Scope Check

The approved spec spans three independent hardening tracks. This plan keeps one umbrella file for the v1.2 story, but each task is written as an independently commit-able unit. Do not batch Track A benchmark changes with Track B network changes or Track C CI documentation.

The existing worktree has a user-owned `.gitignore` modification. Do not stage or rewrite it unless the user explicitly asks.

## File Structure Map

Track A files:

- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`: expose Compose test tags as resource IDs at the station-list screen root and tag the top-bar watchlist action.
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListCards.kt`: tag the station-card watch toggle action.
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListBenchmarkSemantics.kt`: new stable benchmark tag constants owned by the feature.
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`: verify the new stable tags while preserving content descriptions.
- `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`: expose Compose test tags as resource IDs at the watchlist screen root.
- `benchmark/src/main/kotlin/com/gasstation/benchmark/GasStationBenchmarkActions.kt`: use resource-id selectors instead of ambiguous Korean content-description selectors.
- `benchmark/src/main/kotlin/com/gasstation/benchmark/StationListBenchmark.kt`: keep benchmark methods but route through the stable helper.
- `benchmark/src/main/kotlin/com/gasstation/benchmark/BaselineProfileGenerator.kt`: keep the same journey after helper stabilization.
- `docs/performance.md`: update known limitations and measured result text only after a physical-device run.
- `docs/verification-matrix.md`: keep benchmark commands aligned with actual Gradle tasks.

Track B files:

- `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfig.kt`: add station endpoint mode and proxy base URL config.
- `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkModule.kt`: provide direct and proxy Retrofit services, and choose the active station network source.
- `core/network/src/main/kotlin/com/gasstation/core/network/service/ProxyStationService.kt`: new proxy API contract.
- `core/network/src/main/kotlin/com/gasstation/core/network/model/ProxyStationDtos.kt`: new Android-ready proxy request/response DTOs.
- `core/network/src/main/kotlin/com/gasstation/core/network/station/StationNetworkSource.kt`: common fetch contract for direct Opinet and proxy implementations.
- `core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationFetcher.kt`: implement `StationNetworkSource` for current direct Opinet.
- `core/network/src/main/kotlin/com/gasstation/core/network/station/ProxyStationFetcher.kt`: map proxy payloads into existing `NetworkRemoteStation`.
- `core/network/src/test/kotlin/com/gasstation/core/network/station/ProxyStationFetcherTest.kt`: contract tests for proxy request/response behavior.
- `core/network/src/test/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfigTest.kt`: config and provider tests.
- `data/station/src/main/kotlin/com/gasstation/data/station/StationRemoteDataSource.kt`: depend on `StationNetworkSource` instead of direct `NetworkStationFetcher`.
- `data/station/src/test/kotlin/com/gasstation/data/station/StationRemoteDataSourceTest.kt`: ensure data layer still maps network source failures and exceptions.
- `app/build.gradle.kts`: add optional `gasstation.stationEndpointMode` and `gasstation.proxyBaseUrl` BuildConfig fields.
- `app/src/main/java/com/gasstation/di/AppConfigModule.kt`: pass endpoint mode and proxy base URL into `NetworkRuntimeConfig`.
- `app/src/testDemo/java/com/gasstation/NetworkConfigResourceTest.kt`: verify demo/prod assemble defaults stay direct Opinet unless configured.
- `docs/security-trade-offs.md`, `docs/adr/2026-05-18-backend-proxy-escalation.md`: align proxy readiness wording.

Track C files:

- `docs/build-velocity.md`: new source of truth for local timing, CI timing interpretation, and decisions for parallel/cache/configuration-cache/release gates.
- `docs/verification-matrix.md`: link the build-velocity note and clarify current CI gate placement.
- `docs/test-strategy.md`: clarify that velocity settings are valid only when correctness verification stays green.
- `CHANGELOG.md`: record v1.2 hardening under Unreleased.

## Task 1: Track A Stable Benchmark Selectors

**Files:**
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListBenchmarkSemantics.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListCards.kt`
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`
- Modify: `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistScreenTest.kt`

- [ ] **Step 1: Write failing tests for station-list benchmark tags**

Add imports to `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt` if they are missing:

```kotlin
import androidx.compose.ui.test.onNodeWithTag
```

Add this test near `top bar watchlist action uses bookmark copy`:

```kotlin
    @Test
    fun `station list exposes stable benchmark tags without changing copy`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    stations = listOf(testStation()),
                    selectedFuelType = FuelType.GASOLINE,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
                onWatchlistClick = {},
            )
        }

        composeRule.onNodeWithContentDescription("북마크").assertExists()
        composeRule.onNodeWithTag(STATION_LIST_WATCHLIST_ACTION_TAG, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("저장").assertExists()
        composeRule.onNodeWithTag(STATION_LIST_WATCH_TOGGLE_TAG, useUnmergedTree = true).assertExists()
    }
```

Add this assertion to `pull to refresh on populated results requests refresh` after the existing `performTouchInput` assertion:

```kotlin
        composeRule.onNodeWithTag(STATION_LIST_ROOT_TAG, useUnmergedTree = true).assertExists()
```

- [ ] **Step 2: Write failing tests for watchlist root resource-id exposure**

In `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistScreenTest.kt`, add this test near the stable semantics hook test:

```kotlin
    @Test
    fun `watchlist exposes root tag for benchmark resource id lookup`() {
        composeRule.setContent {
            WatchlistScreen(
                uiState = WatchlistUiState(
                    stations = listOf(watchlistStation("station-1", "강남주유소", "1,689")),
                ),
                onCloseClick = {},
            )
        }

        composeRule.onNodeWithTag(WATCHLIST_ROOT_TAG, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(WATCHLIST_CARD_TEST_TAG, useUnmergedTree = true).assertExists()
    }
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest --tests com.gasstation.feature.stationlist.StationListScreenTest
./gradlew :feature:watchlist:testDebugUnitTest --tests com.gasstation.feature.watchlist.WatchlistScreenTest
```

Expected:

- `StationListScreenTest` fails with unresolved references for `STATION_LIST_ROOT_TAG`, `STATION_LIST_WATCHLIST_ACTION_TAG`, and `STATION_LIST_WATCH_TOGGLE_TAG`.
- `WatchlistScreenTest` fails with unresolved reference for `WATCHLIST_ROOT_TAG`.

- [ ] **Step 4: Add station-list benchmark semantics constants**

Create `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListBenchmarkSemantics.kt`:

```kotlin
package com.gasstation.feature.stationlist

const val STATION_LIST_ROOT_TAG = "station-list-root"
const val STATION_LIST_WATCHLIST_ACTION_TAG = "station-list-watchlist-action"
const val STATION_LIST_WATCH_TOGGLE_TAG = "station-list-watch-toggle"
```

- [ ] **Step 5: Add watchlist root constant**

Modify `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistSemantics.kt`:

```kotlin
package com.gasstation.feature.watchlist

const val WATCHLIST_ROOT_TAG = "watchlist-root"
const val WATCHLIST_CARD_TEST_TAG = "watchlist-card"
const val WATCHLIST_DISTANCE_METRIC_TAG = "watchlist-distance-metric"
```

- [ ] **Step 6: Expose station-list test tags as resource IDs**

Modify imports in `StationListScreen.kt`:

```kotlin
import androidx.compose.ui.semantics.testTagsAsResourceId
```

Update the `GasStationBackground` call in `StationListScreen`:

```kotlin
    GasStationBackground(
        modifier = Modifier
            .fillMaxSize()
            .testTag(STATION_LIST_ROOT_TAG)
            .semantics {
                testTagsAsResourceId = true
            },
    ) {
```

Update the top-bar watchlist `IconButton` modifier:

```kotlin
                            IconButton(
                                modifier = Modifier
                                    .testTag(STATION_LIST_WATCHLIST_ACTION_TAG)
                                    .semantics { contentDescription = bookmarkLabel },
                                onClick = onWatchlistClick,
                            ) {
```

- [ ] **Step 7: Tag the station watch toggle**

Modify the `IconButton` modifier in `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListCards.kt`:

```kotlin
    IconButton(
        modifier = Modifier
            .testTag(STATION_LIST_WATCH_TOGGLE_TAG)
            .semantics {
                contentDescription = watchActionLabel
                selected = watched
                stateDescription = if (watched) {
                    watchSavedLabel
                } else {
                    watchNotSavedLabel
                }
            },
        onClick = onClick,
    ) {
```

- [ ] **Step 8: Expose watchlist test tags as resource IDs**

Modify imports in `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`:

```kotlin
import androidx.compose.ui.semantics.testTagsAsResourceId
```

Update the `GasStationBackground` call:

```kotlin
    GasStationBackground(
        modifier = Modifier
            .fillMaxSize()
            .testTag(WATCHLIST_ROOT_TAG)
            .semantics {
                testTagsAsResourceId = true
            },
    ) {
```

- [ ] **Step 9: Run feature tests**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 10: Commit Track A selector groundwork**

```bash
git add \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListBenchmarkSemantics.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListCards.kt \
  feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt \
  feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistSemantics.kt \
  feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt \
  feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistScreenTest.kt
git commit -m "test: expose stable benchmark selectors"
```

## Task 2: Track A Benchmark Helper Stabilization

**Files:**
- Modify: `benchmark/src/main/kotlin/com/gasstation/benchmark/GasStationBenchmarkActions.kt`
- Modify: `benchmark/src/main/kotlin/com/gasstation/benchmark/StationListBenchmark.kt`
- Modify: `benchmark/src/main/kotlin/com/gasstation/benchmark/BaselineProfileGenerator.kt`

- [ ] **Step 1: Replace benchmark selector constants**

Modify the constants at the top of `GasStationBenchmarkActions.kt`:

```kotlin
internal const val TARGET_PACKAGE = "com.gasstation.demo"

private const val WAIT_TIMEOUT_MS = 10_000L
private const val COARSE_LOCATION_PERMISSION = "android.permission.ACCESS_COARSE_LOCATION"
private const val FINE_LOCATION_PERMISSION = "android.permission.ACCESS_FINE_LOCATION"
private const val STATION_TEXT_FRAGMENT = "주유소"
private const val REFRESH_ACTION_DESCRIPTION = "새로고침"
private const val REFRESH_RAIL_TITLE = "가격 갱신 중"
private const val STATION_LIST_WATCHLIST_ACTION_TAG = "station-list-watchlist-action"
private const val STATION_LIST_WATCH_TOGGLE_TAG = "station-list-watch-toggle"
private const val WATCHLIST_CARD_TAG = "watchlist-card"
```

- [ ] **Step 2: Add a resource-id selector helper**

Add this helper above `clickStable`:

```kotlin
private fun resourceId(tag: String): BySelector = By.res(TARGET_PACKAGE, tag)
```

- [ ] **Step 3: Use resource-id selectors for watchlist flow**

Replace `openWatchlistWithSavedStation()` with:

```kotlin
internal fun MacrobenchmarkScope.openWatchlistWithSavedStation() {
    clickStable(
        selector = resourceId(STATION_LIST_WATCH_TOGGLE_TAG),
        label = "station-list watch toggle resource id '$STATION_LIST_WATCH_TOGGLE_TAG'",
    )
    clickStable(
        selector = resourceId(STATION_LIST_WATCHLIST_ACTION_TAG),
        label = "station-list watchlist action resource id '$STATION_LIST_WATCHLIST_ACTION_TAG'",
    )
    waitForObject(
        selector = resourceId(WATCHLIST_CARD_TAG),
        label = "watchlist card resource id '$WATCHLIST_CARD_TAG'",
    )
}
```

Keep `waitForStationListContent()` and `refreshStationList()` using text/content descriptions because those already pass and reflect user-visible UI.

- [ ] **Step 4: Assemble benchmark code**

Run:

```bash
./gradlew :app:assembleDemoBenchmark :benchmark:assembleBenchmark
```

Expected: PASS.

- [ ] **Step 5: Run physical-device benchmark when a device is available**

Run:

```bash
adb devices
ANDROID_SERIAL=SERIAL_FROM_ADB ./gradlew :benchmark:connectedBenchmarkAndroidTest
```

Expected:

- PASS with benchmark JSON files under `benchmark/build/outputs/connected_android_test_additional_output`, or
- FAIL with a message naming `station-list watch toggle resource id`, `station-list watchlist action resource id`, or `watchlist card resource id`.

Do not update README performance numbers on failure.

- [ ] **Step 6: Commit benchmark helper stabilization**

```bash
git add benchmark/src/main/kotlin/com/gasstation/benchmark/GasStationBenchmarkActions.kt
git commit -m "test: stabilize watchlist benchmark selectors"
```

## Task 3: Track A Performance Documentation Closure

**Files:**
- Modify: `docs/performance.md`
- Modify: `docs/verification-matrix.md`
- Modify: `README.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Locate benchmark outputs**

Run after a successful physical-device benchmark:

```bash
find benchmark/build/outputs/connected_android_test_additional_output -name '*benchmarkData.json' -print
find benchmark/build/outputs/connected_android_test_additional_output -name '*.perfetto-trace' -print
```

Expected: at least one benchmark JSON path is printed. If no JSON path exists, keep README numbers unchanged and document the blocker in `docs/performance.md`.

- [ ] **Step 2: Update `docs/performance.md` after success**

Replace the Known Limitations bullets for baseline profile and `openWatchlistFrameTiming` with this success text, using the real date/device/JSON path from the run:

```markdown
## Known Limitations

- **Physical-device only evidence.** Committed performance numbers still require a physical device. Emulator benchmark runs remain smoke checks only and must not replace the README table.
- **Thermal state not locked.** The current reference device does not expose sustained performance mode. Re-run on a cooled device before comparing across firmware, OS, or hardware revisions.
```

Add rows for `Open watchlist` only if the JSON contains complete metrics for `openWatchlistFrameTiming`. Copy the exact `frameDurationCpuMs`, `frameOverrunMs`, iteration count, and sample count from the JSON into the same table shape used by the existing `List scroll` and `Refresh` rows.

- [ ] **Step 3: Update `README.md` only with successful numbers**

If `docs/performance.md` gained watchlist metrics, add one `Open watchlist` row to the README Performance Snapshot table. Use the exact `frameDurationCpuMs` p50 and p95 values copied from `docs/performance.md`. If the benchmark did not pass, skip this README row.

- [ ] **Step 4: Update `docs/verification-matrix.md`**

In the Hero Benchmark Evidence section, add this sentence after the command block:

```markdown
The watchlist benchmark uses Compose test tags exposed as resource IDs for the save action, top-bar bookmark action, and watchlist card. If those selectors fail, treat it as a benchmark contract regression before changing production UI copy.
```

- [ ] **Step 5: Update `CHANGELOG.md`**

Under `## Unreleased`, add:

```markdown
### 개발자 영향

- v1.2 hardening planning: benchmark selector contracts now use stable Compose test tags exposed as resource IDs, keeping watchlist macrobenchmark selectors separate from Korean accessibility copy.
```

- [ ] **Step 6: Run doc checks**

Run:

```bash
git diff --check -- README.md CHANGELOG.md docs/performance.md docs/verification-matrix.md
```

Expected: no output.

- [ ] **Step 7: Commit performance docs**

```bash
git add README.md CHANGELOG.md docs/performance.md docs/verification-matrix.md
git commit -m "docs: update v1.2 performance evidence"
```

## Task 4: Track B Network Proxy Contract

**Files:**
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/station/StationNetworkSource.kt`
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/service/ProxyStationService.kt`
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/model/ProxyStationDtos.kt`
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/station/ProxyStationFetcher.kt`
- Create: `core/network/src/test/kotlin/com/gasstation/core/network/station/ProxyStationFetcherTest.kt`
- Modify: `core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationFetcher.kt`

- [ ] **Step 1: Write proxy fetcher contract tests**

Create `core/network/src/test/kotlin/com/gasstation/core/network/station/ProxyStationFetcherTest.kt`:

```kotlin
package com.gasstation.core.network.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.network.di.NetworkModule
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyStationFetcherTest {
    @Test
    fun `fetchStations posts Android-ready query and maps proxy stations`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "stations": [
                        {
                          "stationId": "station-1",
                          "name": "강남주유소",
                          "brandCode": "SKG",
                          "fuelType": "GASOLINE",
                          "priceWon": 1689,
                          "latitude": 37.4987,
                          "longitude": 127.0285,
                          "fetchedAtEpochMillis": 1776501938392
                        }
                      ],
                      "fetchedAtEpochMillis": 1776501938392
                    }
                    """.trimIndent(),
                ),
        )
        server.start()

        try {
            val fetcher = ProxyStationFetcher(
                proxyStationService = NetworkModule.provideProxyStationService(server.url("/").toString()),
            )

            val result = fetcher.fetchStations(
                origin = Coordinates(latitude = 37.497927, longitude = 127.027583),
                radius = SearchRadius.KM_3,
                fuelType = FuelType.GASOLINE,
            )

            val request = requireNotNull(server.takeRequest())
            assertEquals("/v1/stations/nearby", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("\"latitude\":37.497927"))
            assertTrue(body.contains("\"longitude\":127.027583"))
            assertTrue(body.contains("\"radiusMeters\":3000"))
            assertTrue(body.contains("\"fuelType\":\"GASOLINE\""))

            assertTrue(result is NetworkStationFetchResult.Success)
            val stations = (result as NetworkStationFetchResult.Success).stations
            assertEquals("station-1", stations.single().stationId)
            assertEquals("강남주유소", stations.single().name)
            assertEquals("SKG", stations.single().brandCode)
            assertEquals(1689, stations.single().priceWon)
            assertEquals(Coordinates(latitude = 37.4987, longitude = 127.0285), stations.single().coordinates)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `fetchStations returns empty success for empty proxy station list`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("""{"stations":[],"fetchedAtEpochMillis":1776501938392}"""),
        )
        server.start()

        try {
            val fetcher = ProxyStationFetcher(
                proxyStationService = NetworkModule.provideProxyStationService(server.url("/").toString()),
            )

            val result = fetcher.fetchStations(
                origin = Coordinates(latitude = 37.497927, longitude = 127.027583),
                radius = SearchRadius.KM_3,
                fuelType = FuelType.DIESEL,
            )

            assertEquals(NetworkStationFetchResult.Success(emptyList()), result)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `fetchStations returns failure when proxy station payload is incomplete`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"stations":[{"stationId":"station-1","name":"","brandCode":"SKG","fuelType":"GASOLINE","priceWon":1689,"latitude":37.4987,"longitude":127.0285}]}
                    """.trimIndent(),
                ),
        )
        server.start()

        try {
            val fetcher = ProxyStationFetcher(
                proxyStationService = NetworkModule.provideProxyStationService(server.url("/").toString()),
            )

            val result = fetcher.fetchStations(
                origin = Coordinates(latitude = 37.497927, longitude = 127.027583),
                radius = SearchRadius.KM_3,
                fuelType = FuelType.GASOLINE,
            )

            assertEquals(NetworkStationFetchResult.Failure, result)
        } finally {
            server.shutdown()
        }
    }
}
```

- [ ] **Step 2: Run proxy tests to verify they fail**

Run:

```bash
./gradlew :core:network:test --tests com.gasstation.core.network.station.ProxyStationFetcherTest
```

Expected: FAIL with unresolved references for `ProxyStationFetcher` and `provideProxyStationService`.

- [ ] **Step 3: Add common station network source**

Create `core/network/src/main/kotlin/com/gasstation/core/network/station/StationNetworkSource.kt`:

```kotlin
package com.gasstation.core.network.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius

interface StationNetworkSource {
    suspend fun fetchStations(origin: Coordinates, radius: SearchRadius, fuelType: FuelType): NetworkStationFetchResult
}
```

Modify the class declaration in `NetworkStationFetcher.kt`:

```kotlin
class NetworkStationFetcher(private val opinetService: OpinetService, private val opinetApiKey: String) : StationNetworkSource {
    override suspend fun fetchStations(origin: Coordinates, radius: SearchRadius, fuelType: FuelType): NetworkStationFetchResult {
```

- [ ] **Step 4: Add proxy DTOs**

Create `core/network/src/main/kotlin/com/gasstation/core/network/model/ProxyStationDtos.kt`:

```kotlin
package com.gasstation.core.network.model

data class ProxyStationSearchRequestDto(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val fuelType: String,
)

data class ProxyStationSearchResponseDto(
    val stations: List<ProxyStationDto> = emptyList(),
    val fetchedAtEpochMillis: Long? = null,
)

data class ProxyStationDto(
    val stationId: String? = null,
    val name: String? = null,
    val brandCode: String? = null,
    val fuelType: String? = null,
    val priceWon: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val fetchedAtEpochMillis: Long? = null,
)
```

- [ ] **Step 5: Add proxy Retrofit service**

Create `core/network/src/main/kotlin/com/gasstation/core/network/service/ProxyStationService.kt`:

```kotlin
package com.gasstation.core.network.service

import com.gasstation.core.network.model.ProxyStationSearchRequestDto
import com.gasstation.core.network.model.ProxyStationSearchResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface ProxyStationService {
    @POST("/v1/stations/nearby")
    suspend fun findStations(@Body request: ProxyStationSearchRequestDto): ProxyStationSearchResponseDto
}
```

- [ ] **Step 6: Add proxy fetcher**

Create `core/network/src/main/kotlin/com/gasstation/core/network/station/ProxyStationFetcher.kt`:

```kotlin
package com.gasstation.core.network.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.network.model.ProxyStationDto
import com.gasstation.core.network.model.ProxyStationSearchRequestDto
import com.gasstation.core.network.service.ProxyStationService

class ProxyStationFetcher(private val proxyStationService: ProxyStationService) : StationNetworkSource {
    override suspend fun fetchStations(origin: Coordinates, radius: SearchRadius, fuelType: FuelType): NetworkStationFetchResult {
        val response = proxyStationService.findStations(
            ProxyStationSearchRequestDto(
                latitude = origin.latitude,
                longitude = origin.longitude,
                radiusMeters = radius.meters,
                fuelType = fuelType.name,
            ),
        )
        val rawStations = response.stations
        val mappedStations = rawStations.mapNotNull(ProxyStationDto::toNetworkRemoteStation)

        return when {
            mappedStations.isNotEmpty() -> NetworkStationFetchResult.Success(mappedStations)
            rawStations.isEmpty() -> NetworkStationFetchResult.Success(emptyList())
            else -> NetworkStationFetchResult.Failure
        }
    }
}

private fun ProxyStationDto.toNetworkRemoteStation(): NetworkRemoteStation? {
    val id = stationId?.takeIf(String::isNotBlank) ?: return null
    val stationName = name?.takeIf(String::isNotBlank) ?: return null
    val brand = brandCode?.takeIf(String::isNotBlank) ?: return null
    val price = priceWon?.takeIf { it > 0 } ?: return null
    val lat = latitude ?: return null
    val lon = longitude ?: return null

    return NetworkRemoteStation(
        stationId = id,
        name = stationName,
        brandCode = brand,
        priceWon = price,
        coordinates = Coordinates(latitude = lat, longitude = lon),
    )
}
```

- [ ] **Step 7: Add proxy service provider**

Modify `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkModule.kt`:

```kotlin
import com.gasstation.core.network.service.ProxyStationService
```

Add:

```kotlin
    fun provideProxyStationService(baseUrl: String): ProxyStationService = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(defaultOkHttpClient())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ProxyStationService::class.java)
```

- [ ] **Step 8: Update network module method-list test for proxy service**

In `core/network/src/test/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfigTest.kt`, update the expected method list in `network module exposes only opinet runtime helpers`:

```kotlin
        assertEquals(
            listOf(
                "defaultOkHttpClient",
                "provideOpinetApiKey",
                "provideOpinetBaseUrl",
                "provideOpinetService",
                "provideProxyStationService",
            ),
            methodNames,
        )
```

- [ ] **Step 9: Run proxy tests**

Run:

```bash
./gradlew :core:network:test --tests com.gasstation.core.network.station.ProxyStationFetcherTest
./gradlew :core:network:test --tests com.gasstation.core.network.di.NetworkRuntimeConfigTest
```

Expected: PASS.

- [ ] **Step 10: Commit proxy contract**

```bash
git add \
  core/network/src/main/kotlin/com/gasstation/core/network/station/StationNetworkSource.kt \
  core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationFetcher.kt \
  core/network/src/main/kotlin/com/gasstation/core/network/service/ProxyStationService.kt \
  core/network/src/main/kotlin/com/gasstation/core/network/model/ProxyStationDtos.kt \
  core/network/src/main/kotlin/com/gasstation/core/network/station/ProxyStationFetcher.kt \
  core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkModule.kt \
  core/network/src/test/kotlin/com/gasstation/core/network/station/ProxyStationFetcherTest.kt
git commit -m "feat(network): add proxy station contract"
```

## Task 5: Track B Android Endpoint Mode Wiring

**Files:**
- Modify: `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfig.kt`
- Modify: `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkModule.kt`
- Modify: `core/network/src/test/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfigTest.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/StationRemoteDataSource.kt`
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/StationRemoteDataSourceTest.kt`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/gasstation/di/AppConfigModule.kt`
- Modify: `app/src/testDemo/java/com/gasstation/NetworkConfigResourceTest.kt`

- [ ] **Step 1: Write config tests**

Replace `runtime config keeps only the externally provided opinet api key` in `core/network/src/test/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfigTest.kt` with:

```kotlin
    @Test
    fun `runtime config keeps externally provided key and station endpoint settings`() {
        val config = NetworkRuntimeConfig(
            opinetApiKey = "opinet-key",
            stationEndpointMode = StationEndpointMode.Proxy,
            stationBaseUrl = "https://gasstation-proxy.example/",
        )

        assertEquals("opinet-key", config.opinetApiKey)
        assertEquals(StationEndpointMode.Proxy, config.stationEndpointMode)
        assertEquals("https://gasstation-proxy.example/", config.stationBaseUrl)
        assertEquals(
            listOf("opinetApiKey", "stationEndpointMode", "stationBaseUrl"),
            NetworkRuntimeConfig::class.java.declaredFields.map { it.name },
        )
    }
```

Add to the same test file:

```kotlin
    @Test
    fun `default runtime config uses direct Opinet`() {
        val config = NetworkRuntimeConfig(opinetApiKey = "opinet-key")

        assertEquals(StationEndpointMode.DirectOpinet, config.stationEndpointMode)
        assertEquals(NetworkModule.provideOpinetBaseUrl(), config.stationBaseUrl)
    }

    @Test
    fun `runtime config supports proxy endpoint mode`() {
        val config = NetworkRuntimeConfig(
            opinetApiKey = "opinet-key",
            stationEndpointMode = StationEndpointMode.Proxy,
            stationBaseUrl = "https://gasstation-proxy.example/",
        )

        assertEquals(StationEndpointMode.Proxy, config.stationEndpointMode)
        assertEquals("https://gasstation-proxy.example/", config.stationBaseUrl)
    }
```

- [ ] **Step 2: Run config tests to verify they fail**

Run:

```bash
./gradlew :core:network:test --tests com.gasstation.core.network.di.NetworkRuntimeConfigTest
```

Expected: FAIL with unresolved reference `StationEndpointMode` and missing `stationEndpointMode`/`stationBaseUrl`.

- [ ] **Step 3: Add endpoint mode to runtime config**

Replace `NetworkRuntimeConfig.kt` with:

```kotlin
package com.gasstation.core.network.di

enum class StationEndpointMode {
    DirectOpinet,
    Proxy,
}

data class NetworkRuntimeConfig(
    val opinetApiKey: String,
    val stationEndpointMode: StationEndpointMode = StationEndpointMode.DirectOpinet,
    val stationBaseUrl: String = NetworkModule.provideOpinetBaseUrl(),
)
```

- [ ] **Step 4: Add station source provider**

Modify `NetworkModule.kt` imports:

```kotlin
import com.gasstation.core.network.station.NetworkStationFetcher
import com.gasstation.core.network.station.ProxyStationFetcher
import com.gasstation.core.network.station.StationNetworkSource
```

Add:

```kotlin
    fun provideStationNetworkSource(config: NetworkRuntimeConfig): StationNetworkSource = when (config.stationEndpointMode) {
        StationEndpointMode.DirectOpinet -> NetworkStationFetcher(
            opinetService = provideOpinetService(provideOpinetBaseUrl()),
            opinetApiKey = config.opinetApiKey,
        )
        StationEndpointMode.Proxy -> ProxyStationFetcher(
            proxyStationService = provideProxyStationService(config.stationBaseUrl),
        )
    }
```

Update `network module exposes only opinet runtime helpers` again so it includes the new source selector:

```kotlin
        assertEquals(
            listOf(
                "defaultOkHttpClient",
                "provideOpinetApiKey",
                "provideOpinetBaseUrl",
                "provideOpinetService",
                "provideProxyStationService",
                "provideStationNetworkSource",
            ),
            methodNames,
        )
```

- [ ] **Step 5: Switch data source to the common network source**

Modify `data/station/src/main/kotlin/com/gasstation/data/station/StationRemoteDataSource.kt` imports:

```kotlin
import com.gasstation.core.network.station.StationNetworkSource
```

Replace the constructor and call site:

```kotlin
class DefaultStationRemoteDataSource @Inject constructor(private val stationNetworkSource: StationNetworkSource) :
    StationRemoteDataSource {
    override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult = try {
        when (
            val result = stationNetworkSource.fetchStations(
                origin = query.coordinates,
                radius = query.radius,
                fuelType = query.fuelType,
            )
        ) {
```

- [ ] **Step 6: Update app BuildConfig fields**

In `app/build.gradle.kts`, add providers near the existing `opinet.apikey` provider:

```kotlin
val stationEndpointMode = providers.gradleProperty("gasstation.stationEndpointMode").orElse("direct")
val proxyBaseUrl = providers.gradleProperty("gasstation.proxyBaseUrl").orElse("")
```

Add BuildConfig fields in the default config block beside `OPINET_API_KEY`:

```kotlin
buildConfigField("String", "STATION_ENDPOINT_MODE", "\"${stationEndpointMode.get()}\"")
buildConfigField("String", "PROXY_BASE_URL", "\"${proxyBaseUrl.get()}\"")
```

- [ ] **Step 7: Update app Hilt config**

Modify imports in `app/src/main/java/com/gasstation/di/AppConfigModule.kt`:

```kotlin
import com.gasstation.core.network.di.StationEndpointMode
import com.gasstation.core.network.station.StationNetworkSource
```

Replace `provideNetworkRuntimeConfig()` with:

```kotlin
    fun provideNetworkRuntimeConfig(): NetworkRuntimeConfig {
        val endpointMode = when (BuildConfig.STATION_ENDPOINT_MODE.lowercase()) {
            "proxy" -> StationEndpointMode.Proxy
            else -> StationEndpointMode.DirectOpinet
        }
        val stationBaseUrl = when (endpointMode) {
            StationEndpointMode.DirectOpinet -> NetworkModule.provideOpinetBaseUrl()
            StationEndpointMode.Proxy -> BuildConfig.PROXY_BASE_URL
        }
        return NetworkRuntimeConfig(
            opinetApiKey = BuildConfig.OPINET_API_KEY,
            stationEndpointMode = endpointMode,
            stationBaseUrl = stationBaseUrl,
        )
    }
```

Replace the `provideOpinetService`, `provideOpinetApiKey`, and `provideNetworkStationFetcher` providers with:

```kotlin
    @Provides
    @Singleton
    fun provideStationNetworkSource(config: NetworkRuntimeConfig): StationNetworkSource = NetworkModule.provideStationNetworkSource(config)
```

- [ ] **Step 8: Update data remote tests**

In `data/station/src/test/kotlin/com/gasstation/data/station/StationRemoteDataSourceTest.kt`, replace `NetworkStationFetcher(...)` construction with fake `StationNetworkSource` instances:

```kotlin
private class ThrowingStationNetworkSource(private val throwable: Throwable) : com.gasstation.core.network.station.StationNetworkSource {
    override suspend fun fetchStations(
        origin: com.gasstation.core.model.Coordinates,
        radius: com.gasstation.core.model.SearchRadius,
        fuelType: com.gasstation.core.model.FuelType,
    ): NetworkStationFetchResult {
        throw throwable
    }
}
```

For timeout tests, instantiate:

```kotlin
val dataSource = DefaultStationRemoteDataSource(
    stationNetworkSource = ThrowingStationNetworkSource(SocketTimeoutException("slow")),
)
```

For success tests, use:

```kotlin
private class FakeStationNetworkSource(private val result: NetworkStationFetchResult) : com.gasstation.core.network.station.StationNetworkSource {
    override suspend fun fetchStations(
        origin: com.gasstation.core.model.Coordinates,
        radius: com.gasstation.core.model.SearchRadius,
        fuelType: com.gasstation.core.model.FuelType,
    ): NetworkStationFetchResult = result
}
```

- [ ] **Step 9: Add app config resource test**

Create or update `app/src/testDemo/java/com/gasstation/NetworkConfigResourceTest.kt`:

```kotlin
package com.gasstation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkConfigResourceTest {
    @Test
    fun `default station endpoint mode stays direct`() {
        assertEquals("direct", BuildConfig.STATION_ENDPOINT_MODE)
        assertTrue(BuildConfig.PROXY_BASE_URL.isBlank())
    }
}
```

- [ ] **Step 10: Run network and app tests**

Run:

```bash
./gradlew :core:network:test :data:station:testDebugUnitTest :app:testDemoDebugUnitTest :app:testProdDebugUnitTest
```

Expected: PASS.

- [ ] **Step 11: Run assemble checks**

Run:

```bash
./gradlew :app:assembleDemoDebug :app:assembleProdDebug
```

Expected: PASS. The default endpoint remains direct Opinet.

- [ ] **Step 12: Commit endpoint wiring**

```bash
git add \
  app/build.gradle.kts \
  app/src/main/java/com/gasstation/di/AppConfigModule.kt \
  app/src/testDemo/java/com/gasstation/NetworkConfigResourceTest.kt \
  core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfig.kt \
  core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkModule.kt \
  core/network/src/test/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfigTest.kt \
  data/station/src/main/kotlin/com/gasstation/data/station/StationRemoteDataSource.kt \
  data/station/src/test/kotlin/com/gasstation/data/station/StationRemoteDataSourceTest.kt
git commit -m "feat(network): wire station endpoint mode"
```

## Task 6: Track B Proxy Readiness Documentation

**Files:**
- Modify: `docs/security-trade-offs.md`
- Modify: `docs/adr/2026-05-18-backend-proxy-escalation.md`
- Modify: `docs/project-reading-guide.md`
- Modify: `README.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Update security trade-offs**

In `docs/security-trade-offs.md`, under the API key section, add:

```markdown
**v1.2 readiness:** Android can be configured for a proxy endpoint through `gasstation.stationEndpointMode=proxy` and `gasstation.proxyBaseUrl=https://gasstation-proxy.example/`. The default remains direct Opinet access until a separately approved proxy service is deployed. The proxy contract must return Android-ready station payloads rather than leaking Opinet raw DTOs into `domain:*` or `feature:*`.
```

- [ ] **Step 2: Update backend proxy ADR**

In `docs/adr/2026-05-18-backend-proxy-escalation.md`, replace the "Android Code Impact" section with:

```markdown
## Android Code Impact

The Android app keeps the direct Opinet path as the default. v1.2 adds a proxy-ready network boundary:

- `core:network` can select direct Opinet or proxy endpoint mode from runtime config.
- `ProxyStationService` owns the Android-facing proxy contract.
- `ProxyStationFetcher` maps proxy payloads into the existing `NetworkRemoteStation` model.
- `data:station`, `domain:station`, `feature:*`, cache policy, stale fallback, and watchlist comparison contracts remain unchanged.

The endpoint swap preserves:

- `StationQuery`
- `StationRepository`
- `StationRefreshException`
- `StationSearchResult`
- demo seed behavior
```

- [ ] **Step 3: Update project reading guide**

In `docs/project-reading-guide.md`, add this row to the table that points readers to backend proxy material:

```markdown
| proxy endpoint mode는 어디서 보나 | `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfig.kt`, `core/network/src/main/kotlin/com/gasstation/core/network/station/ProxyStationFetcher.kt`, `docs/adr/2026-05-18-backend-proxy-escalation.md` |
```

- [ ] **Step 4: Update README wording**

In README's security/prod key paragraph, add:

```markdown
The app can be built with a proxy endpoint mode for future public deployment, but the default checked-in configuration remains direct Opinet access for the Android-focused demo/prod paths.
```

- [ ] **Step 5: Update changelog**

Under `## Unreleased`, add:

```markdown
### 개발자 영향

- Backend proxy readiness: `core:network` now has a proxy endpoint contract and endpoint-mode boundary while keeping direct Opinet as the default Android path.
```

- [ ] **Step 6: Run docs check**

Run:

```bash
git diff --check -- README.md CHANGELOG.md docs/security-trade-offs.md docs/adr/2026-05-18-backend-proxy-escalation.md docs/project-reading-guide.md
```

Expected: no output.

- [ ] **Step 7: Commit proxy docs**

```bash
git add README.md CHANGELOG.md docs/security-trade-offs.md docs/adr/2026-05-18-backend-proxy-escalation.md docs/project-reading-guide.md
git commit -m "docs: document proxy readiness boundary"
```

## Task 7: Track C Build Velocity Measurement

**Files:**
- Create: `docs/build-velocity.md`
- Modify: `docs/verification-matrix.md`
- Modify: `docs/test-strategy.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Capture local fast-path timing**

Run:

```bash
/usr/bin/time -p ./gradlew \
  :core:model:test \
  :domain:location:test \
  :core:observability:test \
  :core:designsystem:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :app:assembleDemoDebug \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :benchmark:assemble
```

Expected: PASS. Record the `real`, `user`, and `sys` values.

- [ ] **Step 2: Capture release gate timing**

Run:

```bash
/usr/bin/time -p ./gradlew :app:assembleProdRelease
```

Expected: PASS. Record the `real`, `user`, and `sys` values.

- [ ] **Step 3: Create build velocity doc**

Create `docs/build-velocity.md` with exact timing values from Steps 1-2. The table below shows the required rows and columns; write the real numbers from `/usr/bin/time -p` into the cells before saving the file:

```markdown
# Build Velocity

GasStation keeps build-speed decisions tied to correctness checks. `org.gradle.parallel=true`, `org.gradle.caching=true`, and `org.gradle.configuration-cache=true` are currently enabled in `gradle.properties`, so the validation question is whether those defaults remain correct and documented.

## Local Timing Snapshot

Date: 2026-05-31
Machine: local developer machine
Java: 17

| Command | Result | real | user | sys |
| --- | --- | ---: | ---: | ---: |
| fast local check | PASS | use measured value | use measured value | use measured value |
| `:app:assembleProdRelease` | PASS | use measured value | use measured value | use measured value |

## Decisions

- Keep `org.gradle.parallel=true` enabled because the fast local check and release assemble passed with the current module graph.
- Keep `org.gradle.caching=true` enabled because the verification commands passed with the current build cache configuration.
- Keep `org.gradle.configuration-cache=true` enabled only while the documented verification matrix stays green. If a task becomes incompatible, disable configuration cache for the failing command before changing product code.
- Keep `:app:assembleProdRelease` outside the PR default gate. It remains a `main` and `v*` tag gate in GitHub Actions and a release/deployment local check.

## CI Interpretation

GitHub Actions already separates `static-analysis`, `unit-tests`, `screenshot-tests`, `assemble`, `release-assemble`, and `coverage`. The `assemble` job intentionally runs demo debug, prod debug, and benchmark assemble as separate Gradle invocations to avoid a memory peak on hosted runners.
```

- [ ] **Step 4: Update verification matrix**

In `docs/verification-matrix.md`, add `docs/build-velocity.md` to the "참고" section:

```markdown
- `docs/build-velocity.md`는 Gradle parallel/cache/configuration-cache 기본값과 release assemble gate 위치를 timing 근거와 함께 설명합니다.
```

- [ ] **Step 5: Update test strategy**

In `docs/test-strategy.md`, add this bullet near the CI/verification discussion:

```markdown
- Build velocity settings are valid only while the verification matrix stays green. If `parallel`, build cache, or configuration cache changes a task result, treat it as a build correctness issue and fix the build boundary before changing product behavior.
```

- [ ] **Step 6: Update changelog**

Under `## Unreleased`, add:

```markdown
### 문서와 검증

- Build velocity evidence: `docs/build-velocity.md` records timing and current decisions for Gradle parallel/cache/configuration-cache and release assemble gate placement.
```

- [ ] **Step 7: Run docs check**

Run:

```bash
git diff --check -- CHANGELOG.md docs/build-velocity.md docs/verification-matrix.md docs/test-strategy.md
```

Expected: no output.

- [ ] **Step 8: Commit build velocity docs**

```bash
git add CHANGELOG.md docs/build-velocity.md docs/verification-matrix.md docs/test-strategy.md
git commit -m "docs: record build velocity decisions"
```

## Task 8: Final V1.2 Consistency Verification

**Files:**
- Modify only files that fail consistency checks from this task.

- [ ] **Step 1: Check worktree scope**

Run:

```bash
git status --short
```

Expected: only intentional v1.2 changes are present. A pre-existing `.gitignore` modification may still appear as unstaged user-owned work; do not stage it.

- [ ] **Step 2: Run final fast local check**

Run:

```bash
./gradlew \
  :core:model:test \
  :domain:location:test \
  :core:observability:test \
  :core:designsystem:testDebugUnitTest \
  :core:network:test \
  :data:station:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :app:assembleDemoDebug \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :benchmark:assemble
```

Expected: PASS.

- [ ] **Step 3: Run static and screenshot checks**

Run:

```bash
./gradlew spotlessCheck lint verifyRoborazziDebug
```

Expected: PASS.

- [ ] **Step 4: Run release assemble check**

Run:

```bash
./gradlew :app:assembleProdRelease
```

Expected: PASS.

- [ ] **Step 5: Run physical-device benchmark only when available**

Run only with a connected physical device:

```bash
./gradlew :app:assembleDemoBenchmark :benchmark:assembleBenchmark
adb devices
ANDROID_SERIAL=SERIAL_FROM_ADB ./gradlew :benchmark:connectedBenchmarkAndroidTest
```

Expected: PASS with benchmark JSON output. If no physical device is available, leave README performance numbers unchanged and keep `docs/performance.md` honest about the missing run.

- [ ] **Step 6: Check docs for stale claims**

Run:

```bash
rg -n "baseline profile.*unavailable|watchlist benchmark currently unavailable|openWatchlistFrameTiming skipped|proxy already|proxy is deployed" README.md CHANGELOG.md docs
```

Expected:

- No statement that proxy is already deployed.
- If benchmark still fails or was not run, only `docs/performance.md` should describe the current limitation.

- [ ] **Step 7: Commit consistency fixes**

If Step 6 required edits, commit them:

```bash
git add README.md CHANGELOG.md docs/performance.md docs/verification-matrix.md docs/test-strategy.md docs/security-trade-offs.md docs/adr/2026-05-18-backend-proxy-escalation.md docs/project-reading-guide.md docs/build-velocity.md
git commit -m "docs: align v1.2 hardening references"
```

If Step 6 required no edits, do not create an empty commit.

## Final Handoff Notes

After all tasks complete, report:

- exact commits created,
- verification commands run and their results,
- whether a physical-device benchmark was available,
- whether README performance numbers changed,
- whether `.gitignore` remains user-owned unstaged work.
