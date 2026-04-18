# Clean Architecture Boundary Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce a domain-level location contract, remove `SettingsRepository` direct access from features, and simplify `StationQuery` so the presentation layer no longer owns infrastructure and persistence concerns.

**Architecture:** Add a new `:domain:location` JVM module that owns permission/result/repository contracts, keep Android/demo location details in `:core:location`, and refactor `feature:settings` / `feature:station-list` to depend on explicit use cases instead of repositories and infrastructure classes. Preserve current UX by keeping route-driven lifecycle collection for location availability while moving the source of truth behind the new domain port.

**Tech Stack:** Kotlin, Android Gradle multi-module setup, Hilt, Coroutines Flow, JUnit4, Turbine, Robolectric

---

## File Map

- `settings.gradle.kts`
  Add `:domain:location` to the module graph.
- `domain/location/build.gradle.kts`
  Configure the new JVM domain module.
- `domain/location/src/main/kotlin/com/gasstation/domain/location/*`
  Own `LocationPermissionState`, `LocationLookupResult`, `LocationRepository`, and the two location use cases.
- `domain/location/src/test/kotlin/com/gasstation/domain/location/LocationUseCasesTest.kt`
  Lock the domain location surface and delegation behavior.
- `core/location/build.gradle.kts`
  Add `:domain:location` and Robolectric test dependencies.
- `core/location/src/main/kotlin/com/gasstation/core/location/DefaultLocationRepository.kt`
  Implement `LocationRepository` using the existing Android client and optional demo override.
- `core/location/src/main/kotlin/com/gasstation/core/location/LocationAvailabilityFlow.kt`
  Move the broadcast-backed GPS/network availability flow out of `feature`.
- `core/location/src/main/kotlin/com/gasstation/core/location/LocationModule.kt`
  Bind `LocationRepository` into Hilt.
- `core/location/src/test/kotlin/com/gasstation/core/location/DefaultLocationRepositoryTest.kt`
  Verify timeout, cancellation, errors, and demo override behavior.
- `core/location/src/test/kotlin/com/gasstation/core/location/LocationAvailabilityFlowTest.kt`
  Verify provider broadcasts update the availability flow.
- `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/*`
  Add explicit write use cases for fuel type, radius, brand filter, and map provider.
- `domain/settings/src/test/kotlin/com/gasstation/domain/settings/UpdateSettingsUseCasesTest.kt`
  Verify each new use case delegates to `SettingsRepository`.
- `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsViewModel.kt`
  Replace repository writes with explicit use cases.
- `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsViewModelTest.kt`
  Update the constructor and assert action-to-use-case routing.
- `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQuery.kt`
  Remove `mapProvider`.
- `domain/station/src/test/kotlin/com/gasstation/domain/station/StationQueryCacheKeyTest.kt`
  Verify cache keys still ignore brand filter and sort order.
- `feature/station-list/build.gradle.kts`
  Replace `:core:location` with `:domain:location`.
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
  Consume the new location and settings use cases, remove repository and `core:location` direct dependencies, and keep availability collection lifecycle-aware.
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
  Replace `context.gpsAvailabilityFlow()` usage with a `repeatOnLifecycle` call into the ViewModel.
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListAction.kt`
  Switch the permission type import to `domain:location`.
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt`
  Switch the permission type import to `domain:location`.
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListBannerModel.kt`
  Switch the permission type import to `domain:location`.
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/GpsAvailabilityMonitor.kt`
  Delete after moving the flow to `core:location`.
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`
  Replace `core:location` and repository direct-write assumptions with domain use cases.
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/GpsAvailabilityMonitorTest.kt`
  Replace with a route lifecycle test that drives a fake availability flow through the ViewModel.
- `data/station/src/test/kotlin/com/gasstation/data/station/DefaultStationRepositoryTest.kt`
  Remove `mapProvider` from `StationQuery` builders.
- `data/station/src/test/kotlin/com/gasstation/data/station/StationRemoteDataSourceTest.kt`
  Remove `mapProvider` from `StationQuery` builders.
- `app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt`
  Remove `mapProvider` from `StationQuery` construction.
- `app/src/testDemo/java/com/gasstation/startup/DemoSeedStartupHookTest.kt`
  Update the expected query shape after removing `mapProvider`.
- `docs/architecture.md`
  Document the new `domain:location` module and the feature-to-domain location boundary.
- `docs/module-contracts.md`
  Update module ownership and dependency rules.

### Task 1: Add the `domain:location` Contract Module

**Files:**
- Modify: `settings.gradle.kts`
- Create: `domain/location/build.gradle.kts`
- Create: `domain/location/src/main/kotlin/com/gasstation/domain/location/LocationPermissionState.kt`
- Create: `domain/location/src/main/kotlin/com/gasstation/domain/location/LocationLookupResult.kt`
- Create: `domain/location/src/main/kotlin/com/gasstation/domain/location/LocationRepository.kt`
- Create: `domain/location/src/main/kotlin/com/gasstation/domain/location/GetCurrentLocationUseCase.kt`
- Create: `domain/location/src/main/kotlin/com/gasstation/domain/location/ObserveLocationAvailabilityUseCase.kt`
- Test: `domain/location/src/test/kotlin/com/gasstation/domain/location/LocationUseCasesTest.kt`

- [x] **Step 1: Write the failing module and test scaffold**

```kotlin
// settings.gradle.kts
include(
    ":app",
    ":core:model",
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

// domain/location/build.gradle.kts
plugins {
    id("gasstation.jvm.library")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// domain/location/src/test/kotlin/com/gasstation/domain/location/LocationUseCasesTest.kt
package com.gasstation.domain.location

import com.gasstation.core.model.Coordinates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationUseCasesTest {
    @Test
    fun `observe availability use case delegates to repository flow`() = runTest {
        val repository = FakeLocationRepository(
            availability = MutableStateFlow(true),
            result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
        )

        assertEquals(true, ObserveLocationAvailabilityUseCase(repository)().first())
    }

    @Test
    fun `get current location use case delegates to repository result`() = runTest {
        val expected = LocationLookupResult.Success(Coordinates(37.498095, 127.027610))
        val repository = FakeLocationRepository(
            availability = MutableStateFlow(false),
            result = expected,
        )

        assertEquals(
            expected,
            GetCurrentLocationUseCase(repository)(LocationPermissionState.PreciseGranted),
        )
    }
}

private class FakeLocationRepository(
    private val availability: MutableStateFlow<Boolean>,
    private val result: LocationLookupResult,
) : LocationRepository {
    override fun observeAvailability(): Flow<Boolean> = availability

    override suspend fun getCurrentLocation(
        permissionState: LocationPermissionState,
    ): LocationLookupResult = result
}
```

- [x] **Step 2: Run the new domain test to verify it fails**

Run: `./gradlew :domain:location:test --tests "com.gasstation.domain.location.LocationUseCasesTest"`

Expected: FAIL with unresolved references for `LocationRepository`, `LocationLookupResult`, `LocationPermissionState`, or the two use cases.

- [x] **Step 3: Write the minimal production implementation**

```kotlin
// domain/location/src/main/kotlin/com/gasstation/domain/location/LocationPermissionState.kt
package com.gasstation.domain.location

sealed interface LocationPermissionState {
    data object Denied : LocationPermissionState
    data object ApproximateGranted : LocationPermissionState
    data object PreciseGranted : LocationPermissionState
}

// domain/location/src/main/kotlin/com/gasstation/domain/location/LocationLookupResult.kt
package com.gasstation.domain.location

import com.gasstation.core.model.Coordinates

sealed interface LocationLookupResult {
    data class Success(val coordinates: Coordinates) : LocationLookupResult
    data object PermissionDenied : LocationLookupResult
    data object Unavailable : LocationLookupResult
    data object TimedOut : LocationLookupResult
    data class Error(val throwable: Throwable) : LocationLookupResult
}

// domain/location/src/main/kotlin/com/gasstation/domain/location/LocationRepository.kt
package com.gasstation.domain.location

import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun observeAvailability(): Flow<Boolean>

    suspend fun getCurrentLocation(
        permissionState: LocationPermissionState,
    ): LocationLookupResult
}

// domain/location/src/main/kotlin/com/gasstation/domain/location/GetCurrentLocationUseCase.kt
package com.gasstation.domain.location

import javax.inject.Inject

class GetCurrentLocationUseCase @Inject constructor(
    private val repository: LocationRepository,
) {
    suspend operator fun invoke(
        permissionState: LocationPermissionState,
    ): LocationLookupResult = repository.getCurrentLocation(permissionState)
}

// domain/location/src/main/kotlin/com/gasstation/domain/location/ObserveLocationAvailabilityUseCase.kt
package com.gasstation.domain.location

import javax.inject.Inject

class ObserveLocationAvailabilityUseCase @Inject constructor(
    private val repository: LocationRepository,
) {
    operator fun invoke() = repository.observeAvailability()
}
```

- [x] **Step 4: Run the new domain test to verify it passes**

Run: `./gradlew :domain:location:test --tests "com.gasstation.domain.location.LocationUseCasesTest"`

Expected: PASS

- [x] **Step 5: Commit**

```bash
git add settings.gradle.kts domain/location
git commit -m "refactor: add domain location contracts"
```

### Task 2: Move Android and Demo Location Behavior Behind `LocationRepository`

**Files:**
- Modify: `core/location/build.gradle.kts`
- Create: `core/location/src/main/kotlin/com/gasstation/core/location/DefaultLocationRepository.kt`
- Create: `core/location/src/main/kotlin/com/gasstation/core/location/LocationAvailabilityFlow.kt`
- Modify: `core/location/src/main/kotlin/com/gasstation/core/location/LocationModule.kt`
- Modify: `core/location/src/main/kotlin/com/gasstation/core/location/CurrentLocationClient.kt`
- Modify: `core/location/src/main/kotlin/com/gasstation/core/location/DemoLocationOverride.kt`
- Test: `core/location/src/test/kotlin/com/gasstation/core/location/DefaultLocationRepositoryTest.kt`
- Test: `core/location/src/test/kotlin/com/gasstation/core/location/LocationAvailabilityFlowTest.kt`

- [x] **Step 1: Write the failing core-location tests**

```kotlin
// core/location/src/test/kotlin/com/gasstation/core/location/DefaultLocationRepositoryTest.kt
package com.gasstation.core.location

import android.content.ContextWrapper
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Optional
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultLocationRepositoryTest {
    @Test
    fun `demo override wins before denied permission result`() = runTest {
        val expected = Coordinates(37.498095, 127.027610)
        val repository = DefaultLocationRepository(
            context = ContextWrapper(null),
            demoLocationOverride = Optional.of(DemoLocationOverride { expected }),
            currentLocationClient = unusedCurrentLocationClient(),
        )

        assertEquals(
            LocationLookupResult.Success(expected),
            repository.getCurrentLocation(LocationPermissionState.Denied),
        )
    }

    @Test
    fun `timeout returns timed out result and cancels token`() = runTest {
        val client = FakeCurrentLocationClient()
        val repository = DefaultLocationRepository(
            context = ContextWrapper(null),
            demoLocationOverride = Optional.empty(),
            currentLocationClient = client,
        )

        val result = async {
            repository.getCurrentLocation(LocationPermissionState.PreciseGranted)
        }

        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(LocationLookupResult.TimedOut, result.await())
        assertTrue(client.lastCancellationTokenSource!!.token.isCancellationRequested)
    }
}

private class FakeCurrentLocationClient(
    private val coordinates: Coordinates? = null,
) : CurrentLocationClient {
    var lastCancellationTokenSource: CancellationTokenSource? = null

    override fun getCurrentLocation(
        context: android.content.Context,
        priority: Int,
        cancellationTokenSource: CancellationTokenSource,
        onSuccess: (Coordinates?) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        lastCancellationTokenSource = cancellationTokenSource
        coordinates?.let(onSuccess)
    }
}

private fun unusedCurrentLocationClient(): CurrentLocationClient = CurrentLocationClient {
        _,
        _,
        _,
        _,
        _,
    ->
    throw AssertionError("CurrentLocationClient should not be used in this test")
}

// core/location/src/test/kotlin/com/gasstation/core/location/LocationAvailabilityFlowTest.kt
package com.gasstation.core.location

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class LocationAvailabilityFlowTest {
    @Test
    fun `provider change broadcast emits updated availability`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowLocationManager = shadowOf(locationManager)

        shadowLocationManager.setLocationEnabled(true)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)

        context.locationAvailabilityFlow().test {
            assertEquals(false, awaitItem())

            shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
            context.sendBroadcast(Intent(LocationManager.PROVIDERS_CHANGED_ACTION))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(true, awaitItem())
        }
    }
}
```

- [x] **Step 2: Run the core-location tests to verify they fail**

Run: `./gradlew :core:location:testDebugUnitTest --tests "com.gasstation.core.location.DefaultLocationRepositoryTest" --tests "com.gasstation.core.location.LocationAvailabilityFlowTest"`

Expected: FAIL because `DefaultLocationRepository` and `locationAvailabilityFlow()` do not exist yet, and `core:location` does not expose the domain location types.

- [x] **Step 3: Write the minimal implementation and Hilt binding**

```kotlin
// core/location/build.gradle.kts
dependencies {
    implementation(project(":core:model"))
    implementation(project(":domain:location"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}

// core/location/src/main/kotlin/com/gasstation/core/location/LocationAvailabilityFlow.kt
package com.gasstation.core.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

internal fun Context.locationAvailabilityFlow(): Flow<Boolean> {
    val appContext = applicationContext
    return callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(appContext.isLocationAvailable())
            }
        }

        val filter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }

        trySend(appContext.isLocationAvailable())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(receiver, filter)
        }

        awaitClose { appContext.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}

private fun Context.isLocationAvailable(): Boolean {
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

// core/location/src/main/kotlin/com/gasstation/core/location/DefaultLocationRepository.kt
package com.gasstation.core.location

import android.annotation.SuppressLint
import android.content.Context
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.location.LocationRepository
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class DefaultLocationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val demoLocationOverride: Optional<DemoLocationOverride>,
    private val currentLocationClient: CurrentLocationClient,
) : LocationRepository {
    override fun observeAvailability(): Flow<Boolean> = context.locationAvailabilityFlow()

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(
        permissionState: LocationPermissionState,
    ): LocationLookupResult {
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
        val result = withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine<LocationLookupResult> { continuation ->
                continuation.invokeOnCancellation { cancellationTokenSource.cancel() }
                try {
                    currentLocationClient.getCurrentLocation(
                        context = context,
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
                } catch (cancel: CancellationException) {
                    continuation.cancel(cancel)
                } catch (exception: Exception) {
                    if (continuation.isActive) {
                        continuation.resume(LocationLookupResult.Error(exception))
                    }
                }
            }
        }

        if (result != null) return result
        cancellationTokenSource.cancel()
        return LocationLookupResult.TimedOut
    }
}

// core/location/src/main/kotlin/com/gasstation/core/location/LocationModule.kt
@Module
@InstallIn(SingletonComponent::class)
object LocationModule {
    @Provides
    @Singleton
    fun provideLocationRepository(
        repository: DefaultLocationRepository,
    ): LocationRepository = repository
}
```

- [x] **Step 4: Run the core-location tests to verify they pass**

Run: `./gradlew :core:location:testDebugUnitTest --tests "com.gasstation.core.location.DefaultLocationRepositoryTest" --tests "com.gasstation.core.location.LocationAvailabilityFlowTest"`

Expected: PASS

- [x] **Step 5: Commit**

```bash
git add core/location
git commit -m "refactor: move location infrastructure behind repository"
```

### Task 3: Add Explicit Settings Update Use Cases

**Files:**
- Create: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/UpdateFuelTypeUseCase.kt`
- Create: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/UpdateSearchRadiusUseCase.kt`
- Create: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/UpdateBrandFilterUseCase.kt`
- Create: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/UpdateMapProviderUseCase.kt`
- Test: `domain/settings/src/test/kotlin/com/gasstation/domain/settings/UpdateSettingsUseCasesTest.kt`

- [x] **Step 1: Write the failing settings use-case tests**

```kotlin
package com.gasstation.domain.settings

import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.settings.usecase.UpdateBrandFilterUseCase
import com.gasstation.domain.settings.usecase.UpdateFuelTypeUseCase
import com.gasstation.domain.settings.usecase.UpdateMapProviderUseCase
import com.gasstation.domain.settings.usecase.UpdateSearchRadiusUseCase
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateSettingsUseCasesTest {
    @Test
    fun `fuel type use case updates repository state`() = runTest {
        val repository = FakeSettingsRepository(UserPreferences.default())

        UpdateFuelTypeUseCase(repository)(FuelType.DIESEL)

        assertEquals(FuelType.DIESEL, repository.current.fuelType)
    }

    @Test
    fun `radius brand filter and map provider use cases update repository state`() = runTest {
        val repository = FakeSettingsRepository(UserPreferences.default())

        UpdateSearchRadiusUseCase(repository)(SearchRadius.KM_5)
        UpdateBrandFilterUseCase(repository)(BrandFilter.SOL)
        UpdateMapProviderUseCase(repository)(MapProvider.NAVER_MAP)

        assertEquals(SearchRadius.KM_5, repository.current.searchRadius)
        assertEquals(BrandFilter.SOL, repository.current.brandFilter)
        assertEquals(MapProvider.NAVER_MAP, repository.current.mapProvider)
    }
}

private class FakeSettingsRepository(
    initial: UserPreferences,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    val current: UserPreferences
        get() = state.value

    override fun observeUserPreferences(): Flow<UserPreferences> = state

    override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences) {
        state.value = transform(state.value)
    }
}
```

- [x] **Step 2: Run the domain settings test to verify it fails**

Run: `./gradlew :domain:settings:test --tests "com.gasstation.domain.settings.UpdateSettingsUseCasesTest"`

Expected: FAIL with unresolved references for the four new use cases.

- [x] **Step 3: Write the minimal production use cases**

```kotlin
package com.gasstation.domain.settings.usecase

import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import javax.inject.Inject

class UpdateFuelTypeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(fuelType: FuelType) {
        settingsRepository.updateUserPreferences { it.copy(fuelType = fuelType) }
    }
}

class UpdateSearchRadiusUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(searchRadius: SearchRadius) {
        settingsRepository.updateUserPreferences { it.copy(searchRadius = searchRadius) }
    }
}

class UpdateBrandFilterUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(brandFilter: BrandFilter) {
        settingsRepository.updateUserPreferences { it.copy(brandFilter = brandFilter) }
    }
}

class UpdateMapProviderUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(mapProvider: MapProvider) {
        settingsRepository.updateUserPreferences { it.copy(mapProvider = mapProvider) }
    }
}
```

- [x] **Step 4: Run the domain settings test to verify it passes**

Run: `./gradlew :domain:settings:test --tests "com.gasstation.domain.settings.UpdateSettingsUseCasesTest"`

Expected: PASS

- [x] **Step 5: Commit**

```bash
git add domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase domain/settings/src/test/kotlin/com/gasstation/domain/settings/UpdateSettingsUseCasesTest.kt
git commit -m "refactor: add explicit settings update use cases"
```

### Task 4: Refactor `feature:settings` to Use the New Write Use Cases

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsViewModel.kt`
- Modify: `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsViewModelTest.kt`

- [x] **Step 1: Rewrite the ViewModel test first**

```kotlin
// feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsViewModelTest.kt
val repository = FakeSettingsRepository(UserPreferences.default())
val viewModel = SettingsViewModel(
    observeUserPreferences = ObserveUserPreferencesUseCase(repository),
    updatePreferredSortOrder = UpdatePreferredSortOrderUseCase(repository),
    updateFuelType = UpdateFuelTypeUseCase(repository),
    updateSearchRadius = UpdateSearchRadiusUseCase(repository),
    updateBrandFilter = UpdateBrandFilterUseCase(repository),
    updateMapProvider = UpdateMapProviderUseCase(repository),
)

viewModel.onAction(SettingsAction.MapProviderSelected(MapProvider.NAVER_MAP))
advanceUntilIdle()

assertEquals(MapProvider.NAVER_MAP, repository.current.mapProvider)
assertEquals(MapProvider.NAVER_MAP, viewModel.uiState.value.mapProvider)
```

- [x] **Step 2: Run the feature settings test to verify it fails**

Run: `./gradlew :feature:settings:testDebugUnitTest --tests "com.gasstation.feature.settings.SettingsViewModelTest"`

Expected: FAIL because `SettingsViewModel` still expects `SettingsRepository`.

- [x] **Step 3: Refactor the ViewModel constructor and action handling**

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeUserPreferences: ObserveUserPreferencesUseCase,
    private val updatePreferredSortOrder: UpdatePreferredSortOrderUseCase,
    private val updateFuelType: UpdateFuelTypeUseCase,
    private val updateSearchRadius: UpdateSearchRadiusUseCase,
    private val updateBrandFilter: UpdateBrandFilterUseCase,
    private val updateMapProvider: UpdateMapProviderUseCase,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SettingsUiState.from(UserPreferences.default()))
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()

    init {
        observeUserPreferences()
            .map(SettingsUiState::from)
            .onEach { mutableUiState.value = it }
            .launchIn(viewModelScope)
    }

    fun onAction(action: SettingsAction) {
        viewModelScope.launch {
            when (action) {
                is SettingsAction.SortOrderSelected -> updatePreferredSortOrder(action.sortOrder)
                is SettingsAction.FuelTypeSelected -> updateFuelType(action.fuelType)
                is SettingsAction.SearchRadiusSelected -> updateSearchRadius(action.radius)
                is SettingsAction.BrandFilterSelected -> updateBrandFilter(action.brandFilter)
                is SettingsAction.MapProviderSelected -> updateMapProvider(action.mapProvider)
            }
        }
    }
}
```

- [x] **Step 4: Run the feature settings test to verify it passes**

Run: `./gradlew :feature:settings:testDebugUnitTest --tests "com.gasstation.feature.settings.SettingsViewModelTest"`

Expected: PASS

- [x] **Step 5: Commit**

```bash
git add feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsViewModel.kt feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsViewModelTest.kt
git commit -m "refactor: route settings viewmodel writes through use cases"
```

### Task 5: Refactor `StationQuery` and `feature:station-list` Around Domain Ports

**Files:**
- Modify: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQuery.kt`
- Modify: `domain/station/src/test/kotlin/com/gasstation/domain/station/StationQueryCacheKeyTest.kt`
- Modify: `feature/station-list/build.gradle.kts`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListAction.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListBannerModel.kt`
- Delete: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/GpsAvailabilityMonitor.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/GpsAvailabilityMonitorTest.kt`
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/DefaultStationRepositoryTest.kt`
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/StationRemoteDataSourceTest.kt`
- Modify: `app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt`
- Modify: `app/src/testDemo/java/com/gasstation/startup/DemoSeedStartupHookTest.kt`

- [ ] **Step 1: Rewrite the failing station-list and query tests**

```kotlin
// domain/station/src/test/kotlin/com/gasstation/domain/station/StationQueryCacheKeyTest.kt
@Test
fun `cache key ignores sort order and brand filter`() {
    val first = StationQuery(
        coordinates = Coordinates(37.498095, 127.027610),
        radius = SearchRadius.KM_3,
        fuelType = FuelType.GASOLINE,
        brandFilter = BrandFilter.ALL,
        sortOrder = SortOrder.DISTANCE,
    )

    val second = first.copy(
        brandFilter = BrandFilter.SKE,
        sortOrder = SortOrder.PRICE,
    )

    assertEquals(first.toCacheKey(bucketMeters = 250), second.toCacheKey(bucketMeters = 250))
}

// feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt
val availability = MutableSharedFlow<Boolean>(replay = 1)
val locationRepository = FakeLocationRepository(
    availability = availability,
    result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
)
val viewModel = StationListViewModel(
    observeNearbyStations = ObserveNearbyStationsUseCase(repository),
    refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
    updateWatchState = UpdateWatchStateUseCase(repository),
    observeUserPreferences = ObserveUserPreferencesUseCase(settingsRepository),
    updatePreferredSortOrder = UpdatePreferredSortOrderUseCase(settingsRepository),
    observeLocationAvailability = ObserveLocationAvailabilityUseCase(locationRepository),
    getCurrentLocation = GetCurrentLocationUseCase(locationRepository),
    stationEventLogger = analytics,
)

viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
viewModel.collectLocationAvailability(availability)
viewModel.onAction(StationListAction.RefreshRequested)
advanceUntilIdle()

assertEquals(SortOrder.DISTANCE, repository.refreshedQueries.single().sortOrder)
assertEquals(true, viewModel.uiState.value.isGpsEnabled)

private class FakeLocationRepository(
    private val availability: MutableSharedFlow<Boolean>,
    private val result: LocationLookupResult,
) : LocationRepository {
    override fun observeAvailability(): Flow<Boolean> = availability

    override suspend fun getCurrentLocation(
        permissionState: LocationPermissionState,
    ): LocationLookupResult = result
}

// feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/GpsAvailabilityMonitorTest.kt
composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
availability.tryEmit(false)
composeRule.waitForIdle()
assertEquals(true, viewModel.uiState.value.isGpsEnabled)
```

- [ ] **Step 2: Run the focused station-list tests to verify they fail**

Run: `./gradlew :domain:station:test --tests "com.gasstation.domain.station.StationQueryCacheKeyTest" :feature:station-list:testDebugUnitTest --tests "com.gasstation.feature.stationlist.StationListViewModelTest" --tests "com.gasstation.feature.stationlist.GpsAvailabilityMonitorTest"`

Expected: FAIL because `StationQuery` still requires `mapProvider`, `feature:station-list` still imports `core:location`, and the ViewModel constructor does not match the new use case set.

- [ ] **Step 3: Implement the query simplification and station-list refactor**

```kotlin
// domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQuery.kt
data class StationQuery(
    val coordinates: Coordinates,
    val radius: SearchRadius,
    val fuelType: FuelType,
    val brandFilter: BrandFilter,
    val sortOrder: SortOrder,
) {
    fun toCacheKey(bucketMeters: Int): StationQueryCacheKey {
        require(bucketMeters > 0) { "bucketMeters must be greater than 0" }

        val latitudeBucket = ((coordinates.latitude * 111_000) / bucketMeters).toInt()
        val longitudeBucket = ((coordinates.longitude * 88_800) / bucketMeters).toInt()

        return StationQueryCacheKey(
            latitudeBucket = latitudeBucket,
            longitudeBucket = longitudeBucket,
            radiusMeters = radius.meters,
            fuelType = fuelType,
        )
    }
}

// feature/station-list/build.gradle.kts
dependencies {
    implementation(project(":core:model"))
    implementation(project(":domain:location"))
    implementation(project(":domain:station"))
    implementation(project(":domain:settings"))
    implementation(project(":core:designsystem"))
}

// feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt
@HiltViewModel
class StationListViewModel @Inject constructor(
    private val observeNearbyStations: ObserveNearbyStationsUseCase,
    private val refreshNearbyStations: RefreshNearbyStationsUseCase,
    private val updateWatchState: UpdateWatchStateUseCase,
    observeUserPreferences: ObserveUserPreferencesUseCase,
    private val updatePreferredSortOrder: UpdatePreferredSortOrderUseCase,
    private val observeLocationAvailability: ObserveLocationAvailabilityUseCase,
    private val getCurrentLocation: GetCurrentLocationUseCase,
    private val stationEventLogger: StationEventLogger,
) : ViewModel() {
    suspend fun collectLocationAvailability(
        flowOverride: Flow<Boolean>? = null,
    ) {
        (flowOverride ?: observeLocationAvailability())
            .collect { isEnabled ->
                onAction(StationListAction.GpsAvailabilityChanged(isEnabled))
            }
    }

    private fun refresh() {
        viewModelScope.launch {
            val session = sessionState.value
            if (!session.isGpsEnabled) {
                mutableEffects.emit(StationListEffect.OpenLocationSettings)
                return@launch
            }
            if (session.permissionState == LocationPermissionState.Denied) {
                mutableEffects.emit(StationListEffect.ShowSnackbar("위치 권한을 허용해주세요."))
                return@launch
            }

            val coordinates = handleLocationResult(
                getCurrentLocation(session.permissionState),
            ) ?: return@launch

            val query = StationQuery(
                coordinates = coordinates,
                radius = preferences.value.searchRadius,
                fuelType = preferences.value.fuelType,
                brandFilter = preferences.value.brandFilter,
                sortOrder = preferences.value.sortOrder,
            )

            refreshNearbyStations(query)
        }
    }

    private fun toggleSortOrder() {
        viewModelScope.launch {
            val toggled = when (preferences.value.sortOrder) {
                SortOrder.DISTANCE -> SortOrder.PRICE
                SortOrder.PRICE -> SortOrder.DISTANCE
            }
            updatePreferredSortOrder(toggled)
        }
    }
}

// feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt
LaunchedEffect(lifecycleOwner, viewModel) {
    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.collectLocationAvailability()
    }
}
```

- [ ] **Step 4: Update the remaining `StationQuery` call sites and rerun focused tests**

```kotlin
// app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt
private fun DemoSeedQueryDocument.toCacheKey(): StationQueryCacheKey = StationQuery(
    coordinates = coordinates,
    radius = radius,
    fuelType = fuelType,
    brandFilter = BrandFilter.ALL,
    sortOrder = SortOrder.DISTANCE,
).toCacheKey(bucketMeters = 250)

// data/station/src/test/kotlin/com/gasstation/data/station/DefaultStationRepositoryTest.kt
private fun stationQuery(
    coordinates: Coordinates = Coordinates(37.498095, 127.027610),
    radius: SearchRadius = SearchRadius.KM_3,
    fuelType: FuelType = FuelType.GASOLINE,
    brandFilter: BrandFilter = BrandFilter.ALL,
    sortOrder: SortOrder = SortOrder.DISTANCE,
) = StationQuery(
    coordinates = coordinates,
    radius = radius,
    fuelType = fuelType,
    brandFilter = brandFilter,
    sortOrder = sortOrder,
)
```

Run: `./gradlew :domain:station:test --tests "com.gasstation.domain.station.StationQueryCacheKeyTest" :feature:station-list:testDebugUnitTest --tests "com.gasstation.feature.stationlist.StationListViewModelTest" --tests "com.gasstation.feature.stationlist.GpsAvailabilityMonitorTest" :data:station:testDebugUnitTest --tests "com.gasstation.data.station.DefaultStationRepositoryTest" --tests "com.gasstation.data.station.StationRemoteDataSourceTest" :app:testDemoDebugUnitTest --tests "com.gasstation.startup.DemoSeedStartupHookTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add domain/station feature/station-list data/station app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt app/src/testDemo/java/com/gasstation/startup/DemoSeedStartupHookTest.kt
git commit -m "refactor: decouple station list from location infrastructure"
```

### Task 6: Update Documentation and Run End-to-End Verification

**Files:**
- Modify: `docs/architecture.md`
- Modify: `docs/module-contracts.md`

- [ ] **Step 1: Update the architecture docs to match the new boundaries**

```markdown
<!-- docs/architecture.md -->
| `domain:location` | `LocationRepository`, permission/result contracts, location use cases |
| `core:location` | Android location implementation, availability broadcasts, demo override |

fstation --> domLocation["domain:location"]
clocation --> domLocation

<!-- docs/module-contracts.md -->
| `feature:station-list` | 목록 화면 상태, 권한/GPS/위치 흐름, effect | `domain:station`, `domain:settings`, `domain:location`, `core:designsystem`, `core:model` | Room/Retrofit 접근 |
| `domain:location` | 위치 권한/결과 계약, 위치 use case | `core:model` | Android 타입 |
| `core:location` | 현재 위치 조회 구현, provider availability 감시, demo override | `domain:location`, `core:model` | 목록 화면 정책 |
```

- [ ] **Step 2: Run the full verification command**

Run: `./gradlew :domain:location:test :domain:settings:test :domain:station:test :core:location:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:station-list:testDebugUnitTest :data:station:testDebugUnitTest :app:assembleDemoDebug :app:testDemoDebugUnitTest`

Expected: PASS across all listed modules

- [ ] **Step 3: Commit**

```bash
git add docs/architecture.md docs/module-contracts.md
git commit -m "docs: document boundary refactor"
```
