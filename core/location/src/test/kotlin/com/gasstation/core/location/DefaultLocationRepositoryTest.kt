package com.gasstation.core.location

import android.content.ContextWrapper
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationAddressLookupResult as DomainLocationAddressLookupResult
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
                addressResolver = FakeAddressResolver(DomainLocationAddressLookupResult.Unavailable),
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
            addressResolver = FakeAddressResolver(DomainLocationAddressLookupResult.Unavailable),
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
                addressResolver = FakeAddressResolver(DomainLocationAddressLookupResult.Unavailable),
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
            addressResolver = FakeAddressResolver(DomainLocationAddressLookupResult.Unavailable),
            demoLocationOverride = Optional.of(DemoLocationOverride { null }),
        )

        assertEquals(true, repository.observeAvailability().first())
    }

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

private class FakeAddressResolver(
    private val result: DomainLocationAddressLookupResult,
) : AddressResolver {
    var lastCoordinates: Coordinates? = null

    override suspend fun addressFor(coordinates: Coordinates): DomainLocationAddressLookupResult {
        lastCoordinates = coordinates
        return result
    }
}
