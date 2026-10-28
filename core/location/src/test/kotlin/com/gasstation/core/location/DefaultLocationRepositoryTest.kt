package com.gasstation.core.location

import android.content.ContextWrapper
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationAddressLookupResult
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Optional

class DefaultLocationRepositoryTest {
    @Test
    fun `get current location forwards every permission state to provider`() = runTest {
        val cases = listOf(
            LocationPermissionState.PreciseGranted,
            LocationPermissionState.ApproximateGranted,
            LocationPermissionState.Denied,
        )

        cases.forEach { permissionState ->
            val expected = LocationLookupResult.Success(Coordinates(37.498095, 127.027610))
            val provider = FakeForegroundLocationProvider(result = expected)
            val repository = DefaultLocationRepository(
                context = ContextWrapper(null),
                foregroundLocationProvider = provider,
                addressResolver = FakeAddressResolver(LocationAddressLookupResult.Unavailable),
                demoLocationOverride = Optional.empty(),
            )

            assertSame(expected, repository.getCurrentLocation(permissionState))
            assertSame(permissionState, provider.lastPermissionState)
        }
    }

    @Test
    fun `get current location preserves provider failure results`() = runTest {
        val cases = listOf(
            LocationLookupResult.PermissionDenied,
            LocationLookupResult.Unavailable,
            LocationLookupResult.TimedOut,
            LocationLookupResult.Error(IllegalStateException("gps crashed")),
        )

        cases.forEach { expected ->
            val repository = DefaultLocationRepository(
                context = ContextWrapper(null),
                foregroundLocationProvider = FakeForegroundLocationProvider(result = expected),
                addressResolver = FakeAddressResolver(LocationAddressLookupResult.Unavailable),
                demoLocationOverride = Optional.empty(),
            )

            assertSame(
                expected,
                repository.getCurrentLocation(LocationPermissionState.ApproximateGranted),
            )
        }
    }

    @Test
    fun `observe availability stays true when demo override is present`() = runTest {
        val repository = DefaultLocationRepository(
            context = ContextWrapper(null),
            foregroundLocationProvider = FakeForegroundLocationProvider(
                result = LocationLookupResult.Unavailable,
            ),
            addressResolver = FakeAddressResolver(LocationAddressLookupResult.Unavailable),
            demoLocationOverride = Optional.of(DemoLocationOverride { null }),
        )

        assertEquals(true, repository.observeAvailability().first())
    }

    @Test
    fun `get current address delegates coordinates to address resolver`() = runTest {
        val coordinates = Coordinates(37.498095, 127.027610)
        val resolver = FakeAddressResolver(
            result = LocationAddressLookupResult.Success("서울 영등포구 당산동 194-32"),
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
            LocationAddressLookupResult.Success("서울 영등포구 당산동 194-32"),
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
            addressResolver = FakeAddressResolver(LocationAddressLookupResult.Unavailable),
            demoLocationOverride = Optional.empty(),
        )

        assertEquals(
            LocationAddressLookupResult.Unavailable,
            repository.getCurrentAddress(Coordinates(37.498095, 127.027610)),
        )
    }
}

private class FakeForegroundLocationProvider(private val result: LocationLookupResult) : ForegroundLocationProvider {
    var lastPermissionState: LocationPermissionState? = null

    override suspend fun currentLocation(permissionState: LocationPermissionState): LocationLookupResult {
        lastPermissionState = permissionState
        return result
    }
}

private class FakeAddressResolver(private val result: LocationAddressLookupResult) : AddressResolver {
    var lastCoordinates: Coordinates? = null

    override suspend fun addressFor(coordinates: Coordinates): LocationAddressLookupResult {
        lastCoordinates = coordinates
        return result
    }
}
