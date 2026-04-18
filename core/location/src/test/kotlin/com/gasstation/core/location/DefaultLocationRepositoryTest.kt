package com.gasstation.core.location

import android.content.ContextWrapper
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationLookupResult as DomainLocationLookupResult
import com.gasstation.domain.location.LocationPermissionState as DomainLocationPermissionState
import java.util.Optional
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultLocationRepositoryTest {
    @Test
    fun `get current location maps each domain permission to the matching core permission`() = runTest {
        val cases = listOf(
            DomainLocationPermissionState.PreciseGranted to LocationPermissionState.PreciseGranted,
            DomainLocationPermissionState.ApproximateGranted to
                LocationPermissionState.ApproximateGranted,
            DomainLocationPermissionState.Denied to LocationPermissionState.Denied,
        )

        cases.forEach { (domainPermission, expectedCorePermission) ->
            val provider = FakeForegroundLocationProvider(
                result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
            )
            val repository = DefaultLocationRepository(
                context = ContextWrapper(null),
                foregroundLocationProvider = provider,
                demoLocationOverride = Optional.empty(),
            )

            val result = repository.getCurrentLocation(domainPermission)

            assertEquals(
                DomainLocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
                result,
            )
            assertEquals(expectedCorePermission, provider.lastPermissionState)
        }
    }

    @Test
    fun `get current location maps core failures to domain failures`() = runTest {
        val repository = DefaultLocationRepository(
            context = ContextWrapper(null),
            foregroundLocationProvider = FakeForegroundLocationProvider(
                result = LocationLookupResult.PermissionDenied,
            ),
            demoLocationOverride = Optional.empty(),
        )

        assertEquals(
            DomainLocationLookupResult.PermissionDenied,
            repository.getCurrentLocation(DomainLocationPermissionState.Denied),
        )

        val cases = listOf(
            LocationLookupResult.Unavailable to DomainLocationLookupResult.Unavailable,
            LocationLookupResult.TimedOut to DomainLocationLookupResult.TimedOut,
            LocationLookupResult.Error(IllegalStateException("gps crashed")) to
                DomainLocationLookupResult.Error(IllegalStateException("gps crashed")),
        )

        cases.forEach { (coreResult, expectedDomainResult) ->
            val fakeProvider = FakeForegroundLocationProvider(result = coreResult)
            val mappingRepository = DefaultLocationRepository(
                context = ContextWrapper(null),
                foregroundLocationProvider = fakeProvider,
                demoLocationOverride = Optional.empty(),
            )

            val actual = mappingRepository.getCurrentLocation(
                DomainLocationPermissionState.ApproximateGranted,
            )

            when {
                coreResult is LocationLookupResult.Error &&
                    actual is DomainLocationLookupResult.Error -> {
                    assertEquals(coreResult.throwable.message, actual.throwable.message)
                }

                else -> assertEquals(expectedDomainResult, actual)
            }
        }
    }

    @Test
    fun `observe availability stays true when demo override is present`() = runTest {
        val repository = DefaultLocationRepository(
            context = ContextWrapper(null),
            foregroundLocationProvider = FakeForegroundLocationProvider(
                result = LocationLookupResult.Unavailable,
            ),
            demoLocationOverride = Optional.of(DemoLocationOverride { null }),
        )

        assertEquals(true, repository.observeAvailability().first())
    }
}

private class FakeForegroundLocationProvider(
    private val result: LocationLookupResult,
) : ForegroundLocationProvider {
    var lastPermissionState: LocationPermissionState? = null

    override suspend fun currentLocation(permissionState: LocationPermissionState): LocationLookupResult {
        lastPermissionState = permissionState
        return result
    }
}
