package com.gasstation.domain.location

import com.gasstation.core.model.Coordinates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LocationUseCasesTest {
    @Test
    fun `observe availability use case delegates to repository flow`() = runTest {
        val availability = MutableStateFlow(true)
        val repository = FakeLocationRepository(
            availability = availability,
            result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
        )

        assertSame(availability, ObserveLocationAvailabilityUseCase(repository)())
    }

    @Test
    fun `get current location use case delegates to repository result`() = runTest {
        val expected = LocationLookupResult.Success(Coordinates(37.498095, 127.027610))
        val permissionState = LocationPermissionState.PreciseGranted
        val repository = FakeLocationRepository(
            availability = MutableStateFlow(false),
            result = expected,
        )

        assertEquals(
            expected,
            GetCurrentLocationUseCase(repository)(permissionState),
        )
        assertEquals(permissionState, repository.lastRequestedPermissionState)
    }

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
}

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
