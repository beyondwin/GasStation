# Station List Query Context Summary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the station-list `현재 조건` card with a lightweight query context summary that can show the current address plus `3km · 휘발유 기준`.

**Architecture:** Add a domain-level address lookup result and use case to `:domain:location`, implement Android platform reverse geocoding behind a small `AddressResolver` in `:core:location`, then let `StationListViewModel` expose an optional address label. The Compose screen renders that label and the selected radius/fuel as unframed list context instead of a `GasStationCard`.

**Tech Stack:** Kotlin, Coroutines, Hilt, Android `Geocoder`, Jetpack Compose, Robolectric, Compose UI tests, Gradle module tests.

---

## File Structure

- `domain/location/src/main/kotlin/com/gasstation/domain/location/LocationAddressLookupResult.kt`
  - New sealed result for reverse geocoding.
- `domain/location/src/main/kotlin/com/gasstation/domain/location/LocationRepository.kt`
  - Add address lookup contract.
- `domain/location/src/main/kotlin/com/gasstation/domain/location/GetCurrentAddressUseCase.kt`
  - New use case that delegates to the repository.
- `domain/location/src/test/kotlin/com/gasstation/domain/location/LocationUseCasesTest.kt`
  - Add delegation coverage and update fake repository.
- `core/location/src/main/kotlin/com/gasstation/core/location/AddressResolver.kt`
  - Internal test seam for address lookup.
- `core/location/src/main/kotlin/com/gasstation/core/location/AndroidAddressResolver.kt`
  - Android `Geocoder` implementation.
- `core/location/src/main/kotlin/com/gasstation/core/location/AddressLabelFormatter.kt`
  - Converts `android.location.Address` into a display label.
- `core/location/src/main/kotlin/com/gasstation/core/location/DefaultLocationRepository.kt`
  - Delegate domain address lookup to `AddressResolver`.
- `core/location/src/main/kotlin/com/gasstation/core/location/LocationModule.kt`
  - Provide `AddressResolver`.
- `core/location/src/test/kotlin/com/gasstation/core/location/DefaultLocationRepositoryTest.kt`
  - Add repository mapping tests and update constructor calls.
- `core/location/src/test/kotlin/com/gasstation/core/location/AddressLabelFormatterTest.kt`
  - Add formatter tests.
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt`
  - Add `currentAddressLabel`.
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
  - Inject address use case, clear stale labels, and populate address labels without blocking station refresh.
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`
  - Add address success/failure/stale-address coverage and update fake repository.
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
  - Replace `FilterSummary` with `QueryContextSummary`.
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`
  - Add UI coverage for address/condition text and removed copy.

---

### Task 1: Domain Address Lookup Contract

**Files:**
- Create: `domain/location/src/main/kotlin/com/gasstation/domain/location/LocationAddressLookupResult.kt`
- Create: `domain/location/src/main/kotlin/com/gasstation/domain/location/GetCurrentAddressUseCase.kt`
- Modify: `domain/location/src/main/kotlin/com/gasstation/domain/location/LocationRepository.kt`
- Modify: `domain/location/src/test/kotlin/com/gasstation/domain/location/LocationUseCasesTest.kt`

- [ ] **Step 1: Write the failing domain use case test**

Add this test inside `LocationUseCasesTest`:

```kotlin
@Test
fun `get current address use case delegates to repository result`() = runTest {
    val coordinates = Coordinates(37.498095, 127.027610)
    val expected = LocationAddressLookupResult.Success("서울 영등포구 당산동 194-32")
    val repository = FakeLocationRepository(
        availability = MutableStateFlow(false),
        result = LocationLookupResult.Success(coordinates),
        addressResult = expected,
    )

    assertEquals(
        expected,
        GetCurrentAddressUseCase(repository)(coordinates),
    )
    assertEquals(coordinates, repository.lastRequestedAddressCoordinates)
}
```

Update the fake in the same test file:

```kotlin
private class FakeLocationRepository(
    private val availability: MutableStateFlow<Boolean>,
    private val result: LocationLookupResult,
    private val addressResult: LocationAddressLookupResult = LocationAddressLookupResult.Unavailable,
) : LocationRepository {
    var lastRequestedPermissionState: LocationPermissionState? = null
    var lastRequestedAddressCoordinates: Coordinates? = null

    override fun observeAvailability(): Flow<Boolean> = availability

    override suspend fun getCurrentLocation(
        permissionState: LocationPermissionState,
    ): LocationLookupResult {
        lastRequestedPermissionState = permissionState
        return result
    }

    override suspend fun getCurrentAddress(
        coordinates: Coordinates,
    ): LocationAddressLookupResult {
        lastRequestedAddressCoordinates = coordinates
        return addressResult
    }
}
```

- [ ] **Step 2: Run the domain test and verify it fails**

Run:

```bash
./gradlew :domain:location:test --tests com.gasstation.domain.location.LocationUseCasesTest
```

Expected: compilation fails because `LocationAddressLookupResult`, `GetCurrentAddressUseCase`, and `LocationRepository.getCurrentAddress` do not exist.

- [ ] **Step 3: Add the domain result type**

Create `domain/location/src/main/kotlin/com/gasstation/domain/location/LocationAddressLookupResult.kt`:

```kotlin
package com.gasstation.domain.location

sealed interface LocationAddressLookupResult {
    data class Success(val addressLabel: String) : LocationAddressLookupResult

    data object Unavailable : LocationAddressLookupResult

    data class Error(val throwable: Throwable) : LocationAddressLookupResult
}
```

- [ ] **Step 4: Extend the repository contract**

Modify `LocationRepository.kt`:

```kotlin
package com.gasstation.domain.location

import com.gasstation.core.model.Coordinates
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun observeAvailability(): Flow<Boolean>

    suspend fun getCurrentLocation(
        permissionState: LocationPermissionState,
    ): LocationLookupResult

    suspend fun getCurrentAddress(
        coordinates: Coordinates,
    ): LocationAddressLookupResult
}
```

- [ ] **Step 5: Add the use case**

Create `domain/location/src/main/kotlin/com/gasstation/domain/location/GetCurrentAddressUseCase.kt`:

```kotlin
package com.gasstation.domain.location

import com.gasstation.core.model.Coordinates
import javax.inject.Inject

class GetCurrentAddressUseCase @Inject constructor(
    private val repository: LocationRepository,
) {
    suspend operator fun invoke(
        coordinates: Coordinates,
    ): LocationAddressLookupResult = repository.getCurrentAddress(coordinates)
}
```

- [ ] **Step 6: Run the domain test and verify it passes**

Run:

```bash
./gradlew :domain:location:test --tests com.gasstation.domain.location.LocationUseCasesTest
```

Expected: all tests in `LocationUseCasesTest` pass.

- [ ] **Step 7: Commit**

```bash
git add domain/location/src/main/kotlin/com/gasstation/domain/location/LocationAddressLookupResult.kt \
  domain/location/src/main/kotlin/com/gasstation/domain/location/GetCurrentAddressUseCase.kt \
  domain/location/src/main/kotlin/com/gasstation/domain/location/LocationRepository.kt \
  domain/location/src/test/kotlin/com/gasstation/domain/location/LocationUseCasesTest.kt
git commit -m "feat: add location address lookup contract"
```

---

### Task 2: Core Location Address Resolver

**Files:**
- Create: `core/location/src/main/kotlin/com/gasstation/core/location/AddressResolver.kt`
- Create: `core/location/src/main/kotlin/com/gasstation/core/location/AndroidAddressResolver.kt`
- Create: `core/location/src/main/kotlin/com/gasstation/core/location/AddressLabelFormatter.kt`
- Create: `core/location/src/test/kotlin/com/gasstation/core/location/AddressLabelFormatterTest.kt`
- Modify: `core/location/src/main/kotlin/com/gasstation/core/location/DefaultLocationRepository.kt`
- Modify: `core/location/src/main/kotlin/com/gasstation/core/location/LocationModule.kt`
- Modify: `core/location/src/test/kotlin/com/gasstation/core/location/DefaultLocationRepositoryTest.kt`

- [ ] **Step 1: Write failing repository address mapping tests**

In `DefaultLocationRepositoryTest`, add imports:

```kotlin
import com.gasstation.domain.location.LocationAddressLookupResult as DomainLocationAddressLookupResult
```

Add these tests:

```kotlin
@Test
fun `get current address delegates coordinates to address resolver`() = runTest {
    val coordinates = Coordinates(37.498095, 127.027610)
    val resolver = FakeAddressResolver(
        result = DomainLocationAddressLookupResult.Success("서울 영등포구 당산동 194-32"),
    )
    val repository = DefaultLocationRepository(
        context = ContextWrapper(null),
        foregroundLocationProvider = FakeForegroundLocationProvider(
            result = LocationLookupResult.Unavailable,
        ),
        addressResolver = resolver,
        demoLocationOverride = Optional.empty(),
    )

    assertEquals(
        DomainLocationAddressLookupResult.Success("서울 영등포구 당산동 194-32"),
        repository.getCurrentAddress(coordinates),
    )
    assertEquals(coordinates, resolver.lastCoordinates)
}

@Test
fun `get current address returns unavailable when resolver has no displayable address`() = runTest {
    val repository = DefaultLocationRepository(
        context = ContextWrapper(null),
        foregroundLocationProvider = FakeForegroundLocationProvider(
            result = LocationLookupResult.Unavailable,
        ),
        addressResolver = FakeAddressResolver(DomainLocationAddressLookupResult.Unavailable),
        demoLocationOverride = Optional.empty(),
    )

    assertEquals(
        DomainLocationAddressLookupResult.Unavailable,
        repository.getCurrentAddress(Coordinates(37.498095, 127.027610)),
    )
}
```

Update every existing `DefaultLocationRepository(...)` constructor call in this test file to include:

```kotlin
addressResolver = FakeAddressResolver(DomainLocationAddressLookupResult.Unavailable),
```

Add this fake at the bottom of the file:

```kotlin
private class FakeAddressResolver(
    private val result: DomainLocationAddressLookupResult,
) : AddressResolver {
    var lastCoordinates: Coordinates? = null

    override suspend fun addressFor(coordinates: Coordinates): DomainLocationAddressLookupResult {
        lastCoordinates = coordinates
        return result
    }
}
```

- [ ] **Step 2: Write failing address formatter tests**

Create `core/location/src/test/kotlin/com/gasstation/core/location/AddressLabelFormatterTest.kt`:

```kotlin
package com.gasstation.core.location

import android.location.Address
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressLabelFormatterTest {
    @Test
    fun `formatter prefers complete address line`() {
        val address = Address(Locale.KOREA).apply {
            setAddressLine(0, "서울 영등포구 당산동 194-32")
            adminArea = "서울"
            locality = "영등포구"
            thoroughfare = "당산동"
        }

        assertEquals("서울 영등포구 당산동 194-32", address.toDisplayLabel())
    }

    @Test
    fun `formatter builds label from road parts when address line is blank`() {
        val address = Address(Locale.KOREA).apply {
            adminArea = "서울"
            locality = "영등포구"
            subLocality = "당산동"
            thoroughfare = "당산로"
            subThoroughfare = "123"
        }

        assertEquals("서울 영등포구 당산동 당산로 123", address.toDisplayLabel())
    }

    @Test
    fun `formatter returns null when address has no displayable parts`() {
        assertNull(Address(Locale.KOREA).toDisplayLabel())
    }
}
```

- [ ] **Step 3: Run core location tests and verify they fail**

Run:

```bash
./gradlew :core:location:testDebugUnitTest --tests com.gasstation.core.location.DefaultLocationRepositoryTest --tests com.gasstation.core.location.AddressLabelFormatterTest
```

Expected: compilation fails because `AddressResolver`, `AddressLabelFormatter`, and the new `DefaultLocationRepository` constructor parameter do not exist.

- [ ] **Step 4: Add the resolver interface**

Create `core/location/src/main/kotlin/com/gasstation/core/location/AddressResolver.kt`:

```kotlin
package com.gasstation.core.location

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationAddressLookupResult

internal interface AddressResolver {
    suspend fun addressFor(coordinates: Coordinates): LocationAddressLookupResult
}
```

- [ ] **Step 5: Add the address formatter**

Create `core/location/src/main/kotlin/com/gasstation/core/location/AddressLabelFormatter.kt`:

```kotlin
package com.gasstation.core.location

import android.location.Address

internal fun Address.toDisplayLabel(): String? {
    getAddressLine(0)?.cleanAddressPart()?.let { return it }

    val roadLabel = listOf(
        adminArea,
        locality,
        subLocality,
        thoroughfare,
        subThoroughfare,
    ).joinAddressParts()
    if (roadLabel != null) return roadLabel

    return listOf(
        adminArea,
        locality,
        subLocality,
        featureName,
    ).joinAddressParts()
}

private fun List<String?>.joinAddressParts(): String? =
    mapNotNull(String?::cleanAddressPart)
        .distinct()
        .joinToString(separator = " ")
        .takeIf(String::isNotBlank)

private fun String?.cleanAddressPart(): String? =
    this?.trim()?.takeIf(String::isNotBlank)
```

- [ ] **Step 6: Add the Android Geocoder resolver**

Create `core/location/src/main/kotlin/com/gasstation/core/location/AndroidAddressResolver.kt`:

```kotlin
package com.gasstation.core.location

import android.content.Context
import android.location.Geocoder
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationAddressLookupResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AndroidAddressResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AddressResolver {
    override suspend fun addressFor(coordinates: Coordinates): LocationAddressLookupResult =
        withContext(Dispatchers.IO) {
            try {
                val address = Geocoder(context, Locale.KOREA)
                    .getFromLocation(
                        coordinates.latitude,
                        coordinates.longitude,
                        1,
                    )
                    ?.firstOrNull()

                address
                    ?.toDisplayLabel()
                    ?.let(LocationAddressLookupResult::Success)
                    ?: LocationAddressLookupResult.Unavailable
            } catch (exception: Exception) {
                LocationAddressLookupResult.Error(exception)
            }
        }
}
```

- [ ] **Step 7: Delegate repository address lookup**

Modify the `DefaultLocationRepository` constructor and add `getCurrentAddress`:

```kotlin
internal class DefaultLocationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val foregroundLocationProvider: ForegroundLocationProvider,
    private val addressResolver: AddressResolver,
    private val demoLocationOverride: Optional<DemoLocationOverride>,
) : DomainLocationRepository {
    override fun observeAvailability(): Flow<Boolean> = if (demoLocationOverride.isPresent) {
        flowOf(true)
    } else {
        context.locationAvailabilityFlow()
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(
        permissionState: DomainLocationPermissionState,
    ): DomainLocationLookupResult =
        when (val result = foregroundLocationProvider.currentLocation(permissionState.toCorePermissionState())) {
            is LocationLookupResult.Success -> DomainLocationLookupResult.Success(result.coordinates)
            LocationLookupResult.PermissionDenied -> DomainLocationLookupResult.PermissionDenied
            LocationLookupResult.Unavailable -> DomainLocationLookupResult.Unavailable
            LocationLookupResult.TimedOut -> DomainLocationLookupResult.TimedOut
            is LocationLookupResult.Error -> DomainLocationLookupResult.Error(result.throwable)
        }

    override suspend fun getCurrentAddress(
        coordinates: Coordinates,
    ): DomainLocationAddressLookupResult = addressResolver.addressFor(coordinates)
}
```

Add this import alias at the top of `DefaultLocationRepository.kt`:

```kotlin
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationAddressLookupResult as DomainLocationAddressLookupResult
```

- [ ] **Step 8: Provide the resolver from Hilt**

Add this provider to `LocationModule`:

```kotlin
@Provides
@Singleton
internal fun provideAddressResolver(
    resolver: AndroidAddressResolver,
): AddressResolver = resolver
```

- [ ] **Step 9: Run core location tests and verify they pass**

Run:

```bash
./gradlew :core:location:testDebugUnitTest --tests com.gasstation.core.location.DefaultLocationRepositoryTest --tests com.gasstation.core.location.AddressLabelFormatterTest
```

Expected: both test classes pass.

- [ ] **Step 10: Commit**

```bash
git add core/location/src/main/kotlin/com/gasstation/core/location/AddressResolver.kt \
  core/location/src/main/kotlin/com/gasstation/core/location/AndroidAddressResolver.kt \
  core/location/src/main/kotlin/com/gasstation/core/location/AddressLabelFormatter.kt \
  core/location/src/main/kotlin/com/gasstation/core/location/DefaultLocationRepository.kt \
  core/location/src/main/kotlin/com/gasstation/core/location/LocationModule.kt \
  core/location/src/test/kotlin/com/gasstation/core/location/AddressLabelFormatterTest.kt \
  core/location/src/test/kotlin/com/gasstation/core/location/DefaultLocationRepositoryTest.kt
git commit -m "feat: resolve current address from location"
```

---

### Task 3: Station List ViewModel Address State

**Files:**
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`

- [ ] **Step 1: Write failing ViewModel address tests**

Add imports to `StationListViewModelTest`:

```kotlin
import com.gasstation.domain.location.GetCurrentAddressUseCase
import com.gasstation.domain.location.LocationAddressLookupResult
```

Add these tests:

```kotlin
@Test
fun `refresh success exposes current address label when address lookup succeeds`() = runTest(dispatcher) {
    Dispatchers.setMain(dispatcher)
    try {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = listOf(stationEntry()),
                freshness = StationFreshness.Fresh,
                fetchedAt = null,
            ),
        )
        val coordinates = Coordinates(37.498095, 127.027610)
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.Success(coordinates),
                addressResult = LocationAddressLookupResult.Success("서울 영등포구 당산동 194-32"),
            ),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        assertEquals("서울 영등포구 당산동 194-32", viewModel.uiState.value.currentAddressLabel)
        assertEquals(coordinates, viewModel.uiState.value.currentCoordinates)
        assertEquals(1, viewModel.uiState.value.stations.size)
    } finally {
        Dispatchers.resetMain()
    }
}

@Test
fun `address lookup failure does not block station results`() = runTest(dispatcher) {
    Dispatchers.setMain(dispatcher)
    try {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = listOf(stationEntry()),
                freshness = StationFreshness.Fresh,
                fetchedAt = null,
            ),
        )
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
                addressResult = LocationAddressLookupResult.Error(IllegalStateException("geocoder unavailable")),
            ),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.currentAddressLabel)
        assertEquals(1, viewModel.uiState.value.stations.size)
        assertEquals(null, viewModel.uiState.value.blockingFailure)
    } finally {
        Dispatchers.resetMain()
    }
}

@Test
fun `new coordinates clear stale address before replacement address arrives`() = runTest(dispatcher) {
    Dispatchers.setMain(dispatcher)
    try {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = listOf(stationEntry()),
                freshness = StationFreshness.Fresh,
                fetchedAt = null,
            ),
        )
        val firstCoordinates = Coordinates(37.498095, 127.027610)
        val secondCoordinates = Coordinates(37.497927, 127.027583)
        val addressRequests = mutableListOf<Coordinates>()
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(
                resultForPermission = {
                    if (repository.refreshedQueries.isEmpty()) {
                        LocationLookupResult.Success(firstCoordinates)
                    } else {
                        LocationLookupResult.Success(secondCoordinates)
                    }
                },
                addressResultForCoordinates = { coordinates ->
                    addressRequests += coordinates
                    if (coordinates == firstCoordinates) {
                        LocationAddressLookupResult.Success("서울 영등포구 당산동 194-32")
                    } else {
                        LocationAddressLookupResult.Unavailable
                    }
                },
            ),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()
        assertEquals("서울 영등포구 당산동 194-32", viewModel.uiState.value.currentAddressLabel)

        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        assertEquals(secondCoordinates, viewModel.uiState.value.currentCoordinates)
        assertEquals(null, viewModel.uiState.value.currentAddressLabel)
        assertEquals(listOf(firstCoordinates, secondCoordinates), addressRequests)
    } finally {
        Dispatchers.resetMain()
    }
}
```

- [ ] **Step 2: Update the ViewModel test factory and fake**

Change `stationListViewModel(...)` in the test file to pass the new use case:

```kotlin
private fun stationListViewModel(
    repository: StationRepository,
    settingsFixture: SettingsUseCaseTestFixture,
    locationRepository: LocationRepository,
    analytics: RecordingStationEventLogger = RecordingStationEventLogger(),
): StationListViewModel = StationListViewModel(
    observeNearbyStations = ObserveNearbyStationsUseCase(repository),
    refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
    updateWatchState = UpdateWatchStateUseCase(repository),
    observeUserPreferences = settingsFixture.observeUserPreferences,
    updatePreferredSortOrder = settingsFixture.updatePreferredSortOrder,
    observeLocationAvailability = ObserveLocationAvailabilityUseCase(locationRepository),
    getCurrentLocation = GetCurrentLocationUseCase(locationRepository),
    getCurrentAddress = GetCurrentAddressUseCase(locationRepository),
    stationEventLogger = analytics,
)
```

Replace the fake location repository in the same test file with:

```kotlin
private class FakeLocationRepository(
    private val availability: Flow<Boolean> = MutableStateFlow(true),
    private val result: LocationLookupResult = LocationLookupResult.Success(
        Coordinates(37.498095, 127.027610),
    ),
    private val addressResult: LocationAddressLookupResult = LocationAddressLookupResult.Unavailable,
    private val resultForPermission: ((LocationPermissionState) -> LocationLookupResult)? = null,
    private val addressResultForCoordinates: ((Coordinates) -> LocationAddressLookupResult)? = null,
) : LocationRepository {
    override fun observeAvailability(): Flow<Boolean> = availability

    override suspend fun getCurrentLocation(
        permissionState: LocationPermissionState,
    ): LocationLookupResult = resultForPermission?.invoke(permissionState) ?: result

    override suspend fun getCurrentAddress(
        coordinates: Coordinates,
    ): LocationAddressLookupResult = addressResultForCoordinates?.invoke(coordinates) ?: addressResult
}
```

For anonymous `LocationRepository` objects in existing tests, add:

```kotlin
override suspend fun getCurrentAddress(
    coordinates: Coordinates,
): LocationAddressLookupResult = LocationAddressLookupResult.Unavailable
```

- [ ] **Step 3: Run station list ViewModel tests and verify they fail**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest --tests com.gasstation.feature.stationlist.StationListViewModelTest
```

Expected: compilation fails because `currentAddressLabel`, the new ViewModel constructor parameter, and address-state logic do not exist.

- [ ] **Step 4: Add address label to UI state**

Modify `StationListUiState.kt`:

```kotlin
data class StationListUiState(
    val currentCoordinates: Coordinates? = null,
    val currentAddressLabel: String? = null,
    val permissionState: LocationPermissionState = LocationPermissionState.Denied,
    val hasDeniedLocationAccess: Boolean = false,
    val needsRecoveryRefresh: Boolean = false,
    val isGpsEnabled: Boolean = true,
    val isAvailabilityKnown: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val blockingFailure: StationListFailureReason? = null,
    val stations: List<StationListItemUiModel> = emptyList(),
    val selectedBrandFilter: BrandFilter = BrandFilter.ALL,
    val selectedRadius: SearchRadius = SearchRadius.KM_3,
    val selectedFuelType: FuelType = FuelType.GASOLINE,
    val selectedSortOrder: SortOrder = SortOrder.DISTANCE,
    val lastUpdatedAt: Instant? = null,
)
```

- [ ] **Step 5: Add address state to the ViewModel session**

In `StationListViewModel.kt`, add imports:

```kotlin
import com.gasstation.domain.location.GetCurrentAddressUseCase
import com.gasstation.domain.location.LocationAddressLookupResult
```

Add the constructor parameter after `getCurrentLocation`:

```kotlin
private val getCurrentAddress: GetCurrentAddressUseCase,
```

Add `currentAddressLabel` to the `StationListUiState` creation:

```kotlin
currentAddressLabel = session.currentAddressLabel,
```

Add the field to `StationListSessionState`:

```kotlin
val currentAddressLabel: String? = null,
```

- [ ] **Step 6: Clear stale addresses and launch address lookup**

In `refresh`, replace the successful coordinate session update block with:

```kotlin
val previousCoordinates = sessionState.value.currentCoordinates
sessionState.update {
    it.copy(
        currentCoordinates = coordinates,
        currentAddressLabel = if (previousCoordinates == coordinates) {
            it.currentAddressLabel
        } else {
            null
        },
        hasDeniedLocationAccess = session.permissionState == LocationPermissionState.Denied,
        needsRecoveryRefresh = false,
        blockingFailure = null,
    )
}
refreshAddressLabel(coordinates)
```

Add this private function before `handleLocationResult`:

```kotlin
private fun refreshAddressLabel(coordinates: Coordinates) {
    viewModelScope.launch {
        val addressLabel = when (val result = getCurrentAddress(coordinates)) {
            is LocationAddressLookupResult.Success -> result.addressLabel
            LocationAddressLookupResult.Unavailable,
            is LocationAddressLookupResult.Error -> null
        }

        sessionState.update { current ->
            if (current.currentCoordinates == coordinates) {
                current.copy(currentAddressLabel = addressLabel)
            } else {
                current
            }
        }
    }
}
```

- [ ] **Step 7: Run station list ViewModel tests and verify they pass**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest --tests com.gasstation.feature.stationlist.StationListViewModelTest
```

Expected: all tests in `StationListViewModelTest` pass.

- [ ] **Step 8: Commit**

```bash
git add feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt \
  feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt
git commit -m "feat: expose station list address context"
```

---

### Task 4: Compose Query Context Summary

**Files:**
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`

- [ ] **Step 1: Write failing Compose tests**

Add this constant near the existing station list test tags in `StationListScreen.kt`:

```kotlin
internal const val STATION_LIST_QUERY_CONTEXT_TAG = "station-list-query-context"
```

Add these tests to `StationListScreenTest`:

```kotlin
@Test
fun `query context shows current address and condition without old card copy`() {
    composeRule.setContent {
        StationListScreen(
            uiState = StationListUiState(
                currentAddressLabel = "서울 영등포구 당산동 194-32",
                permissionState = LocationPermissionState.PreciseGranted,
                stations = listOf(testStation()),
                selectedFuelType = FuelType.GASOLINE,
            ),
            snackbarHostState = androidx.compose.material3.SnackbarHostState(),
            onAction = {},
            onRequestPermissions = {},
            onOpenLocationSettings = {},
            onSettingsClick = {},
        )
    }

    composeRule.onNodeWithTag(STATION_LIST_QUERY_CONTEXT_TAG, useUnmergedTree = true).assertExists()
    composeRule.onNodeWithText("서울 영등포구 당산동 194-32").assertExists()
    composeRule.onNodeWithText("3km · 휘발유 기준").assertExists()
    composeRule.onNodeWithText("현재 조건").assertDoesNotExist()
    composeRule.onNodeWithText("반경과 유종 기준으로 정렬합니다.").assertDoesNotExist()
}

@Test
fun `query context shows condition when current address is unavailable`() {
    composeRule.setContent {
        StationListScreen(
            uiState = StationListUiState(
                permissionState = LocationPermissionState.PreciseGranted,
                stations = listOf(testStation()),
                selectedFuelType = FuelType.DIESEL,
            ),
            snackbarHostState = androidx.compose.material3.SnackbarHostState(),
            onAction = {},
            onRequestPermissions = {},
            onOpenLocationSettings = {},
            onSettingsClick = {},
        )
    }

    composeRule.onNodeWithTag(STATION_LIST_QUERY_CONTEXT_TAG, useUnmergedTree = true).assertExists()
    composeRule.onNodeWithText("3km · 경유 기준").assertExists()
    composeRule.onNodeWithText("현재 위치 확인 중").assertDoesNotExist()
}
```

- [ ] **Step 2: Run Compose tests and verify they fail**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest --tests com.gasstation.feature.stationlist.StationListScreenTest
```

Expected: tests fail because the query context tag and new UI do not exist.

- [ ] **Step 3: Replace `FilterSummary` usage**

In `StationListContent`, replace:

```kotlin
FilterSummary(
    uiState = uiState,
    modifier = Modifier.animateContentSize(),
)
```

with:

```kotlin
QueryContextSummary(
    uiState = uiState,
    modifier = Modifier.animateContentSize(),
)
```

- [ ] **Step 4: Replace the card summary composable**

Delete `FilterSummary` and `FilterPill`, then add:

```kotlin
@Composable
private fun QueryContextSummary(
    uiState: StationListUiState,
    modifier: Modifier = Modifier,
) {
    val spacing = GasStationTheme.spacing
    val typography = GasStationTheme.typography
    val addressLabel = uiState.currentAddressLabel?.takeIf(String::isNotBlank)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(STATION_LIST_QUERY_CONTEXT_TAG)
            .padding(
                horizontal = spacing.space4,
                vertical = spacing.space4,
            ),
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        if (addressLabel != null) {
            Text(
                text = addressLabel,
                style = typography.body.copy(fontWeight = FontWeight.Bold),
                color = ColorBlack,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = uiState.toQueryConditionLabel(),
            style = typography.meta,
            color = ColorGray2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun StationListUiState.toQueryConditionLabel(): String =
    "${selectedRadius.toLabel()} · ${selectedFuelType.toLabel()} 기준"
```

- [ ] **Step 5: Run Compose tests and verify they pass**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest --tests com.gasstation.feature.stationlist.StationListScreenTest
```

Expected: all tests in `StationListScreenTest` pass.

- [ ] **Step 6: Commit**

```bash
git add feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt \
  feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt
git commit -m "feat: show station list query context summary"
```

---

### Task 5: Full Verification

**Files:**
- Verify only. No source edits unless a test failure reveals a defect introduced by Tasks 1-4.

- [ ] **Step 1: Run focused module tests**

Run:

```bash
./gradlew :domain:location:test :core:location:testDebugUnitTest :feature:station-list:testDebugUnitTest
```

Expected: all three module test tasks pass.

- [ ] **Step 2: Run app-level unit tests for Hilt graph coverage**

Run:

```bash
./gradlew :app:testDemoDebugUnitTest
```

Expected: app demo debug unit tests pass, including startup/Hilt graph tests.

- [ ] **Step 3: Run static diff check**

Run:

```bash
git diff --check
```

Expected: no whitespace errors.

- [ ] **Step 4: Review final diff**

Run:

```bash
git status --short
git diff --stat HEAD~4..HEAD
```

Expected: working tree is clean after the task commits, and the diff includes only domain location, core location, station-list ViewModel/UI, and their tests.

---

## Self-Review

- Spec coverage: Tasks 1-2 implement address lookup without reintroducing Kakao/Daum API; Task 3 exposes address state and prevents stale-address display; Task 4 removes the card-style `현재 조건` UI and renders address plus condition as a lightweight summary; Task 5 verifies the affected modules.
- Completeness scan: The plan contains concrete code and commands for every implementation step.
- Type consistency: The address result type is consistently named `LocationAddressLookupResult`; the UI state field is consistently named `currentAddressLabel`; the ViewModel helper is consistently named `refreshAddressLabel`.
