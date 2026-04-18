# Station List Resilience Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make GPS-off, location timeout/error, and remote refresh failure paths deterministic, bounded, and truthfully represented in the station list UI.

**Architecture:** Introduce typed failure results at the location and remote-data boundaries, then let `StationListViewModel` map them into persistent UI state only when there is no cached content. Keep cached results visible on transient failures, but stop flattening cancellation and timeouts into generic `null` or empty states. Replace resume-only GPS polling with a broadcast-backed availability flow so the UI reacts while the screen remains in the foreground.

**Tech Stack:** Kotlin, Coroutines/Flow, Hilt, Play Services Location, OkHttp/Retrofit, Jetpack Compose, JUnit4, Turbine, Robolectric

---

## File Map

- Create: `core/location/src/main/kotlin/com/gasstation/core/location/LocationLookupResult.kt`
  Typed result contract for foreground location lookup.
- Create: `core/location/src/main/kotlin/com/gasstation/core/location/CurrentLocationClient.kt`
  Seam over `FusedLocationProviderClient` so timeout and cancellation behavior can be unit-tested.
- Modify: `core/location/src/main/kotlin/com/gasstation/core/location/ForegroundLocationProvider.kt`
  Change the interface from nullable coordinates to typed location results.
- Modify: `core/location/src/main/kotlin/com/gasstation/core/location/AndroidForegroundLocationProvider.kt`
  Add timeout, cancellation cleanup, and explicit result mapping.
- Modify: `core/location/src/main/kotlin/com/gasstation/core/location/LocationModule.kt`
  Provide the new location client seam.
- Create: `core/location/src/test/kotlin/com/gasstation/core/location/AndroidForegroundLocationProviderTest.kt`
  Regression tests for timeout, exception mapping, and cancellation cleanup.
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/StationRefreshFailureReason.kt`
  Typed remote refresh failure reasons for timeout/network/payload/generic cases.
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/StationRefreshException.kt`
  Exception wrapper that carries a failure reason without losing the underlying cause.
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/StationRemoteDataSource.kt`
  Preserve cancellation, classify transport failures, and stop swallowing every exception uniformly.
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
  Throw typed refresh exceptions while preserving cached snapshots.
- Create: `data/station/src/test/kotlin/com/gasstation/data/station/StationRemoteDataSourceTest.kt`
  Focused tests for failure classification and cancellation propagation.
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/DefaultStationRepositoryTest.kt`
  Verify typed refresh failures preserve cache and carry the right reason.
- Modify: `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkModule.kt`
  Add bounded `OkHttp` call/connect/read timeouts.
- Modify: `core/network/src/test/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfigTest.kt`
  Assert the default client exposes the chosen timeout policy.
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListFailureReason.kt`
  UI-facing failure reasons for location and refresh failures when no cache exists.
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/GpsAvailabilityMonitor.kt`
  Broadcast-backed `Flow<Boolean>` for live GPS/provider availability updates.
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt`
  Carry a blocking failure reason separate from station content.
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
  Always clear loading flags in `finally`, map typed failures into UI state, and keep cached results visible.
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
  Collect the GPS availability flow instead of only checking on `RESUMED`.
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
  Add a dedicated failure body state and stop reusing the no-results card for failed refreshes.
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`
  Cover location exceptions, timeout mapping, dirty-state cleanup, and no-cache failure states.
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`
  Verify failure cards replace misleading empty-state copy.
- Create: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/GpsAvailabilityMonitorTest.kt`
  Verify provider changes are observed while the screen remains active.
- Modify: `docs/architecture.md`
  Document the new typed location/refresh failure boundaries.
- Modify: `docs/state-model.md`
  Document `blockingFailure` and the difference between empty results vs failed refresh without cache.

### Task 1: Harden the foreground location contract

**Files:**
- Create: `core/location/src/main/kotlin/com/gasstation/core/location/LocationLookupResult.kt`
- Create: `core/location/src/main/kotlin/com/gasstation/core/location/CurrentLocationClient.kt`
- Modify: `core/location/src/main/kotlin/com/gasstation/core/location/ForegroundLocationProvider.kt`
- Modify: `core/location/src/main/kotlin/com/gasstation/core/location/AndroidForegroundLocationProvider.kt`
- Modify: `core/location/src/main/kotlin/com/gasstation/core/location/LocationModule.kt`
- Test: `core/location/src/test/kotlin/com/gasstation/core/location/AndroidForegroundLocationProviderTest.kt`

- [ ] **Step 1: Write the failing location timeout/cancellation tests**

```kotlin
class AndroidForegroundLocationProviderTest {
    @Test
    fun `timeout returns timed out result`() = runTest {
        val client = FakeCurrentLocationClient()
        val provider = AndroidForegroundLocationProvider(
            context = ContextWrapper(null),
            demoLocationOverride = Optional.empty(),
            currentLocationClient = client,
        )

        val result = async {
            provider.currentLocation(LocationPermissionState.PreciseGranted)
        }

        advanceTimeBy(10_000)

        assertEquals(LocationLookupResult.TimedOut, result.await())
        assertTrue(client.lastCancellationTokenSource!!.token.isCancellationRequested)
    }

    @Test
    fun `unexpected provider exception returns error result`() = runTest {
        val client = FakeCurrentLocationClient(failure = IllegalStateException("gps stack crashed"))
        val provider = AndroidForegroundLocationProvider(
            context = ContextWrapper(null),
            demoLocationOverride = Optional.empty(),
            currentLocationClient = client,
        )

        val result = provider.currentLocation(LocationPermissionState.PreciseGranted)

        assertTrue(result is LocationLookupResult.Error)
    }

    @Test
    fun `coroutine cancellation cancels location token`() = runTest {
        val client = FakeCurrentLocationClient()
        val provider = AndroidForegroundLocationProvider(
            context = ContextWrapper(null),
            demoLocationOverride = Optional.empty(),
            currentLocationClient = client,
        )

        val job = launch {
            provider.currentLocation(LocationPermissionState.PreciseGranted)
        }
        runCurrent()
        job.cancel()

        assertTrue(client.lastCancellationTokenSource!!.token.isCancellationRequested)
    }
}
```

- [ ] **Step 2: Run the location tests to verify the contract does not exist yet**

Run: `./gradlew :core:location:testDebugUnitTest --tests "*AndroidForegroundLocationProviderTest"`

Expected: FAIL with unresolved references such as `LocationLookupResult`, `currentLocationClient`, or timeout-specific assertions.

- [ ] **Step 3: Implement typed location lookup results with timeout and cancellation cleanup**

```kotlin
sealed interface LocationLookupResult {
    data class Success(val coordinates: Coordinates) : LocationLookupResult
    data object PermissionDenied : LocationLookupResult
    data object Unavailable : LocationLookupResult
    data object TimedOut : LocationLookupResult
    data class Error(val throwable: Throwable) : LocationLookupResult
}

interface ForegroundLocationProvider {
    suspend fun currentLocation(permissionState: LocationPermissionState): LocationLookupResult
}

interface CurrentLocationClient {
    fun getCurrentLocation(
        priority: Int,
        cancellationTokenSource: CancellationTokenSource,
        onSuccess: (Coordinates?) -> Unit,
        onFailure: (Throwable) -> Unit,
    )
}

class AndroidForegroundLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val demoLocationOverride: Optional<DemoLocationOverride>,
    private val currentLocationClient: CurrentLocationClient,
) : ForegroundLocationProvider {
    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(permissionState: LocationPermissionState): LocationLookupResult {
        if (demoLocationOverride.isPresent) {
            return demoLocationOverride.get().currentLocation(permissionState)
                ?.let(LocationLookupResult::Success)
                ?: LocationLookupResult.Unavailable
        }
        if (permissionState == LocationPermissionState.Denied) return LocationLookupResult.PermissionDenied

        val priority = when (permissionState) {
            LocationPermissionState.PreciseGranted -> Priority.PRIORITY_HIGH_ACCURACY
            LocationPermissionState.ApproximateGranted -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            LocationPermissionState.Denied -> return LocationLookupResult.PermissionDenied
        }

        val cancellationTokenSource = CancellationTokenSource()
        val lookup = withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { cancellationTokenSource.cancel() }
                currentLocationClient.getCurrentLocation(
                    priority = priority,
                    cancellationTokenSource = cancellationTokenSource,
                    onSuccess = { coordinates ->
                        if (continuation.isActive) {
                            continuation.resume(
                                coordinates?.let(LocationLookupResult::Success)
                                    ?: LocationLookupResult.Unavailable,
                            )
                        }
                    },
                    onFailure = { throwable ->
                        if (continuation.isActive) {
                            continuation.resume(LocationLookupResult.Error(throwable))
                        }
                    },
                )
            }
        }

        return lookup ?: LocationLookupResult.TimedOut.also {
            cancellationTokenSource.cancel()
        }
    }
}
```

- [ ] **Step 4: Run the location module tests to verify the new boundary**

Run: `./gradlew :core:location:testDebugUnitTest --tests "*AndroidForegroundLocationProviderTest" --tests "*AndroidForegroundLocationProviderSurfaceTest"`

Expected: PASS. Timeout returns `TimedOut`, exception returns `Error`, cancellation cancels the token, and demo override behavior still passes.

- [ ] **Step 5: Commit the location contract hardening**

```bash
git add \
  core/location/src/main/kotlin/com/gasstation/core/location/LocationLookupResult.kt \
  core/location/src/main/kotlin/com/gasstation/core/location/CurrentLocationClient.kt \
  core/location/src/main/kotlin/com/gasstation/core/location/ForegroundLocationProvider.kt \
  core/location/src/main/kotlin/com/gasstation/core/location/AndroidForegroundLocationProvider.kt \
  core/location/src/main/kotlin/com/gasstation/core/location/LocationModule.kt \
  core/location/src/test/kotlin/com/gasstation/core/location/AndroidForegroundLocationProviderTest.kt
git commit -m "feat: harden foreground location lookup"
```

### Task 2: Preserve remote refresh failure reasons and bound HTTP delays

**Files:**
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/StationRefreshFailureReason.kt`
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/StationRefreshException.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/StationRemoteDataSource.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- Create: `data/station/src/test/kotlin/com/gasstation/data/station/StationRemoteDataSourceTest.kt`
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/DefaultStationRepositoryTest.kt`
- Modify: `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkModule.kt`
- Modify: `core/network/src/test/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfigTest.kt`

- [ ] **Step 1: Write failing tests for timeout classification and cancellation propagation**

```kotlin
class StationRemoteDataSourceTest {
    @Test
    fun `socket timeout maps to timeout failure`() = runTest {
        val dataSource = DefaultStationRemoteDataSource(
            networkStationFetcher = FakeNetworkStationFetcher(throwable = SocketTimeoutException("slow")),
        )

        assertEquals(
            RemoteStationFetchResult.Failure(StationRefreshFailureReason.Timeout),
            dataSource.fetchStations(stationQuery()),
        )
    }

    @Test
    fun `cancellation exception is rethrown`() = runTest {
        val dataSource = DefaultStationRemoteDataSource(
            networkStationFetcher = FakeNetworkStationFetcher(throwable = CancellationException("cancelled")),
        )

        assertFailsWith<CancellationException> {
            dataSource.fetchStations(stationQuery())
        }
    }
}

@Test
fun `repository keeps cache and exposes typed refresh reason`() = runBlocking {
    val repository = repository(
        remoteDataSource = FakeStationRemoteDataSource(
            RemoteStationFetchResult.Failure(StationRefreshFailureReason.Timeout),
        ),
    )

    val error = assertThrows(StationRefreshException::class.java) {
        runBlocking { repository.refreshNearbyStations(stationQuery()) }
    }

    assertEquals(StationRefreshFailureReason.Timeout, error.reason)
}
```

- [ ] **Step 2: Run the failing data and network tests**

Run: `./gradlew :data:station:testDebugUnitTest --tests "*StationRemoteDataSourceTest" --tests "*DefaultStationRepositoryTest" :core:network:test --tests "*NetworkRuntimeConfigTest"`

Expected: FAIL because `RemoteStationFetchResult.Failure` has no reason, `StationRefreshException` has no `reason`, and the default `OkHttpClient` still uses unbounded defaults.

- [ ] **Step 3: Implement typed failure reasons and explicit HTTP timeout policy**

```kotlin
sealed interface StationRefreshFailureReason {
    data object Timeout : StationRefreshFailureReason
    data object Network : StationRefreshFailureReason
    data object InvalidPayload : StationRefreshFailureReason
    data object Unknown : StationRefreshFailureReason
}

class StationRefreshException(
    val reason: StationRefreshFailureReason,
    cause: Throwable? = null,
) : IllegalStateException("Failed to refresh nearby stations: $reason", cause)

sealed interface RemoteStationFetchResult {
    data class Success(val stations: List<RemoteStation>) : RemoteStationFetchResult
    data class Failure(val reason: StationRefreshFailureReason) : RemoteStationFetchResult
}

override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult {
    return try {
        when (val result = networkStationFetcher.fetchStations(query.coordinates, query.radius, query.fuelType)) {
            is NetworkStationFetchResult.Success -> RemoteStationFetchResult.Success(result.stations.map(::toRemoteStation))
            NetworkStationFetchResult.Failure -> RemoteStationFetchResult.Failure(StationRefreshFailureReason.InvalidPayload)
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: SocketTimeoutException) {
        RemoteStationFetchResult.Failure(StationRefreshFailureReason.Timeout)
    } catch (_: IOException) {
        RemoteStationFetchResult.Failure(StationRefreshFailureReason.Network)
    } catch (throwable: Exception) {
        RemoteStationFetchResult.Failure(StationRefreshFailureReason.Unknown)
    }
}

private fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .callTimeout(Duration.ofSeconds(8))
    .connectTimeout(Duration.ofSeconds(4))
    .readTimeout(Duration.ofSeconds(8))
    .build()
```

- [ ] **Step 4: Run targeted tests for failure typing and timeout policy**

Run: `./gradlew :data:station:testDebugUnitTest --tests "*StationRemoteDataSourceTest" --tests "*DefaultStationRepositoryTest" :core:network:test --tests "*NetworkRuntimeConfigTest"`

Expected: PASS. Timeout is classified, cancellation is rethrown, cache is preserved with a typed reason, and the reflected `OkHttpClient` exposes the chosen timeout values.

- [ ] **Step 5: Commit the remote failure handling changes**

```bash
git add \
  data/station/src/main/kotlin/com/gasstation/data/station/StationRefreshFailureReason.kt \
  data/station/src/main/kotlin/com/gasstation/data/station/StationRefreshException.kt \
  data/station/src/main/kotlin/com/gasstation/data/station/StationRemoteDataSource.kt \
  data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt \
  data/station/src/test/kotlin/com/gasstation/data/station/StationRemoteDataSourceTest.kt \
  data/station/src/test/kotlin/com/gasstation/data/station/DefaultStationRepositoryTest.kt \
  core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkModule.kt \
  core/network/src/test/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfigTest.kt
git commit -m "feat: classify station refresh failures"
```

### Task 3: Make refresh cleanup deterministic in `StationListViewModel`

**Files:**
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListFailureReason.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`

- [ ] **Step 1: Write failing view-model tests for dirty refresh cleanup and no-cache failure mapping**

```kotlin
@Test
fun `location exception clears loading state and exposes blocking failure when cache is empty`() = runTest(dispatcher) {
    val repository = FakeStationRepository(
        result = StationSearchResult(emptyList(), StationFreshness.Stale, null),
    )
    val viewModel = stationListViewModel(
        repository = repository,
        locationProvider = ThrowingForegroundLocationProvider(IllegalStateException("boom")),
    )

    viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
    viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
    viewModel.onAction(StationListAction.RefreshRequested)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertFalse(viewModel.uiState.value.isRefreshing)
    assertEquals(StationListFailureReason.LocationFailed, viewModel.uiState.value.blockingFailure)
}

@Test
fun `remote timeout with cached stations keeps list visible and only emits snackbar`() = runTest(dispatcher) {
    val repository = FakeStationRepository(
        result = StationSearchResult(listOf(stationEntry()), StationFreshness.Stale, Instant.now()),
        refreshFailure = StationRefreshException(StationRefreshFailureReason.Timeout),
    )
    val viewModel = stationListViewModel(repository = repository)

    viewModel.effects.test {
        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        assertEquals(
            StationListEffect.ShowSnackbar("서버 응답이 늦어 가격을 새로고침하지 못했습니다."),
            awaitItem(),
        )
    }
    assertEquals(1, viewModel.uiState.value.stations.size)
    assertEquals(null, viewModel.uiState.value.blockingFailure)
}
```

- [ ] **Step 2: Run the view-model tests to confirm the existing behavior is insufficient**

Run: `./gradlew :feature:station-list:testDebugUnitTest --tests "*StationListViewModelTest"`

Expected: FAIL because `ForegroundLocationProvider` does not return typed results, `blockingFailure` is missing, and refresh exceptions do not clear state in a `finally` block.

- [ ] **Step 3: Refactor the refresh path to use `try/finally` and map typed failures**

```kotlin
data class StationListUiState(
    val currentCoordinates: Coordinates? = null,
    val permissionState: LocationPermissionState = LocationPermissionState.Denied,
    val isGpsEnabled: Boolean = true,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val blockingFailure: StationListFailureReason? = null,
    val stations: List<StationListItemUiModel> = emptyList(),
    ...
)

private suspend fun handleLocationResult(result: LocationLookupResult): Coordinates? = when (result) {
    is LocationLookupResult.Success -> {
        sessionState.update { it.copy(blockingFailure = null) }
        result.coordinates
    }
    LocationLookupResult.TimedOut -> {
        onBlockingFailure(StationListFailureReason.LocationTimedOut, "현재 위치 확인이 지연되고 있습니다.")
        null
    }
    LocationLookupResult.Unavailable,
    LocationLookupResult.PermissionDenied -> {
        onBlockingFailure(StationListFailureReason.LocationFailed, "현재 위치를 확인하지 못했습니다.")
        null
    }
    is LocationLookupResult.Error -> {
        onBlockingFailure(StationListFailureReason.LocationFailed, "현재 위치를 확인하지 못했습니다.")
        null
    }
}

private fun refresh() {
    viewModelScope.launch {
        val session = sessionState.value
        if (!session.isGpsEnabled) {
            mutableEffects.emit(StationListEffect.OpenLocationSettings)
            return@launch
        }
        if (effectivePermissionState(session.permissionState) == LocationPermissionState.Denied) {
            mutableEffects.emit(StationListEffect.ShowSnackbar("위치 권한을 허용해주세요."))
            return@launch
        }

        sessionState.update { it.copy(isLoading = it.currentCoordinates == null, isRefreshing = true) }
        try {
            val coordinates = when (val locationResult = foregroundLocationProvider.currentLocation(session.permissionState)) {
                is LocationLookupResult.Success -> locationResult.coordinates
                else -> handleLocationResult(locationResult) ?: return@launch
            }

            sessionState.update { it.copy(currentCoordinates = coordinates, blockingFailure = null) }

            runCatching {
                refreshNearbyStations(buildQuery(preferences.value, coordinates))
            }.onFailure { throwable ->
                val reason = (throwable as? StationRefreshException)?.reason
                handleRefreshFailure(reason)
            }
        } finally {
            sessionState.update { it.copy(isLoading = false, isRefreshing = false) }
        }
    }
}
```

- [ ] **Step 4: Run the view-model regression suite**

Run: `./gradlew :feature:station-list:testDebugUnitTest --tests "*StationListViewModelTest"`

Expected: PASS. Location exceptions no longer leave dirty loading state, timeout messages are specific, and cached content remains visible while no-cache failures set `blockingFailure`.

- [ ] **Step 5: Commit the ViewModel state-model changes**

```bash
git add \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListFailureReason.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt \
  feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt
git commit -m "feat: stabilize station list refresh state"
```

### Task 4: Observe GPS/provider changes while the screen stays foregrounded

**Files:**
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/GpsAvailabilityMonitor.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
- Create: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/GpsAvailabilityMonitorTest.kt`

- [ ] **Step 1: Write a failing monitor test that toggles providers without recreating the screen**

```kotlin
@RunWith(RobolectricTestRunner::class)
class GpsAvailabilityMonitorTest {
    @Test
    fun `provider change broadcast emits updated availability`() = runTest {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        shadowOf(locationManager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)

        activity.gpsAvailabilityFlow().test {
            assertEquals(true, awaitItem())

            shadowOf(locationManager).setProviderEnabled(LocationManager.GPS_PROVIDER, false)
            activity.sendBroadcast(Intent(LocationManager.PROVIDERS_CHANGED_ACTION))

            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run the monitor test to verify the resume-only implementation is insufficient**

Run: `./gradlew :feature:station-list:testDebugUnitTest --tests "*GpsAvailabilityMonitorTest"`

Expected: FAIL because `gpsAvailabilityFlow()` does not exist and GPS changes are only read from `LaunchedEffect(lifecycleState)`.

- [ ] **Step 3: Implement a broadcast-backed GPS availability flow and collect it from the route**

```kotlin
fun Context.gpsAvailabilityFlow(): Flow<Boolean> = callbackFlow {
    trySend(isGpsEnabled())

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            trySend(this@gpsAvailabilityFlow.isGpsEnabled())
        }
    }

    val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION).apply {
        addAction(LocationManager.MODE_CHANGED_ACTION)
    }

    ContextCompat.registerReceiver(
        this@gpsAvailabilityFlow,
        receiver,
        filter,
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )

    awaitClose { unregisterReceiver(receiver) }
}.distinctUntilChanged()

LaunchedEffect(context) {
    context.gpsAvailabilityFlow().collectLatest { enabled ->
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(enabled))
    }
}
```

- [ ] **Step 4: Run the GPS-monitor tests and the route-adjacent regressions**

Run: `./gradlew :feature:station-list:testDebugUnitTest --tests "*GpsAvailabilityMonitorTest" --tests "*StationListViewModelTest"`

Expected: PASS. GPS changes emit immediately without waiting for `RESUMED`, and the existing `GpsAvailabilityChanged` behavior still works.

- [ ] **Step 5: Commit live GPS monitoring**

```bash
git add \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/GpsAvailabilityMonitor.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt \
  feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/GpsAvailabilityMonitorTest.kt
git commit -m "feat: observe gps availability changes live"
```

### Task 5: Replace the misleading empty-results fallback with explicit failure UI

**Files:**
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`

- [ ] **Step 1: Write failing screen tests for no-cache failure rendering**

```kotlin
@Test
fun `blocking failure renders retryable failure card instead of empty results copy`() {
    composeRule.setContent {
        StationListScreen(
            uiState = StationListUiState(
                permissionState = LocationPermissionState.PreciseGranted,
                isGpsEnabled = true,
                blockingFailure = StationListFailureReason.RefreshTimedOut,
            ),
            snackbarHostState = SnackbarHostState(),
            onAction = {},
            onRequestPermissions = {},
            onOpenLocationSettings = {},
            onSettingsClick = {},
        )
    }

    composeRule.onNodeWithText("주변 주유소를 불러오지 못했습니다.").assertExists()
    composeRule.onNodeWithText("주변 주유소가 없습니다.").assertDoesNotExist()
}

@Test
fun `cached results stay visible when blocking failure is null`() {
    composeRule.setContent {
        StationListScreen(
            uiState = StationListUiState(
                permissionState = LocationPermissionState.PreciseGranted,
                stations = listOf(sampleStation()),
                blockingFailure = null,
            ),
            snackbarHostState = SnackbarHostState(),
            onAction = {},
            onRequestPermissions = {},
            onOpenLocationSettings = {},
            onSettingsClick = {},
        )
    }

    composeRule.onNodeWithText("테스트 주유소").assertExists()
}
```

- [ ] **Step 2: Run the failing UI tests**

Run: `./gradlew :feature:station-list:testDebugUnitTest --tests "*StationListScreenTest"`

Expected: FAIL because `StationListUiState` has no `blockingFailure` and `StationListScreen` still routes every non-loading granted state to `Results`.

- [ ] **Step 3: Implement a dedicated failure body state and retry card**

```kotlin
private sealed interface StationListBodyState {
    data object PermissionRequired : StationListBodyState
    data object GpsRequired : StationListBodyState
    data object InitialLoading : StationListBodyState
    data class Failure(val reason: StationListFailureReason) : StationListBodyState
    data object Results : StationListBodyState
}

private fun StationListUiState.toBodyState(): StationListBodyState = when {
    permissionState == LocationPermissionState.Denied -> StationListBodyState.PermissionRequired
    !isGpsEnabled -> StationListBodyState.GpsRequired
    isLoading && stations.isEmpty() -> StationListBodyState.InitialLoading
    blockingFailure != null && stations.isEmpty() -> StationListBodyState.Failure(blockingFailure)
    else -> StationListBodyState.Results
}

@Composable
private fun RefreshFailureState(
    reason: StationListFailureReason,
    onRetry: () -> Unit,
) {
    val (title, body) = when (reason) {
        StationListFailureReason.LocationTimedOut ->
            "현재 위치 확인이 지연되고 있습니다." to "잠시 후 다시 시도하거나 위치 설정을 확인해주세요."
        StationListFailureReason.LocationFailed ->
            "현재 위치를 불러오지 못했습니다." to "GPS 상태를 확인한 뒤 다시 조회해주세요."
        StationListFailureReason.RefreshTimedOut ->
            "주변 주유소를 불러오지 못했습니다." to "서버 응답이 늦어 최신 가격을 가져오지 못했습니다."
        StationListFailureReason.RefreshFailed ->
            "주변 주유소를 불러오지 못했습니다." to "네트워크 상태를 확인한 뒤 다시 조회해주세요."
    }

    StationListActionStateCard(
        title = title,
        body = body,
        buttonLabel = "다시 시도",
        onClick = onRetry,
    )
}
```

- [ ] **Step 4: Run the screen tests and the full station-list module suite**

Run: `./gradlew :feature:station-list:testDebugUnitTest --tests "*StationListScreenTest" --tests "*StationListViewModelTest"`

Expected: PASS. No-cache failures render a retryable failure card, while cached results remain visible and the stale/refreshing affordances still work.

- [ ] **Step 5: Commit the failure-state UI**

```bash
git add \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt \
  feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt
git commit -m "feat: show explicit station list failure states"
```

### Task 6: Update docs and run the full resilience regression sweep

**Files:**
- Modify: `docs/architecture.md`
- Modify: `docs/state-model.md`

- [ ] **Step 1: Add failing documentation assertions by checking the current docs for missing state coverage**

```text
Search for:
- "blockingFailure"
- "LocationLookupResult"
- "StationRefreshFailureReason"

Expected before edit:
- no matches in docs/architecture.md
- no matches in docs/state-model.md
```

- [ ] **Step 2: Verify the docs are stale before editing**

Run: `rg -n "blockingFailure|LocationLookupResult|StationRefreshFailureReason" docs/architecture.md docs/state-model.md`

Expected: no matches.

- [ ] **Step 3: Update the architecture and state-model docs**

```markdown
`ForegroundLocationProvider` now returns `LocationLookupResult` instead of nullable coordinates.
This gives `feature:station-list` explicit timeout, unavailable, and unexpected-error branches.

`StationListUiState.blockingFailure` is only populated when a refresh or location lookup fails
and there is no cached station list to keep on screen. Cached failures still use snackbars and stale data.

`DefaultStationRepository` throws `StationRefreshException(reason, cause)` so UI code can distinguish
timeout from generic network failure without losing the cached snapshot behavior.
```

- [ ] **Step 4: Run the full targeted regression sweep**

Run: `./gradlew :core:location:testDebugUnitTest :data:station:testDebugUnitTest :feature:station-list:testDebugUnitTest :core:network:test`

Expected: PASS across all touched modules.

- [ ] **Step 5: Commit the docs and final verification**

```bash
git add \
  docs/architecture.md \
  docs/state-model.md
git commit -m "docs: describe station list failure handling"
```

## Self-Review

**Spec coverage**
- Finding 1 is addressed by Task 3 `try/finally` cleanup and typed location result handling.
- Finding 2 is addressed by Task 1 timeout and cancellation-token cleanup.
- Finding 3 is addressed by Task 4 live GPS availability monitoring.
- Finding 4 is addressed by Task 2 typed transport failure classification and cancellation passthrough.
- Finding 5 is addressed by Task 5 explicit failure UI for no-cache states.

**Placeholder scan**
- No `TBD`, `TODO`, or “handle appropriately” placeholders remain.
- Every task includes exact files, targeted commands, and concrete code/test snippets.

**Type consistency**
- Location boundary uses `LocationLookupResult`.
- Remote refresh boundary uses `StationRefreshFailureReason` and `StationRefreshException`.
- UI boundary uses `StationListFailureReason` and `blockingFailure`.

Plan complete and saved to `docs/superpowers/plans/2026-04-18-station-list-resilience-hardening.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
