package com.gasstation.feature.stationlist

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.GetCurrentAddressUseCase
import com.gasstation.domain.location.GetCurrentLocationUseCase
import com.gasstation.domain.location.LocationAddressLookupResult
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.location.LocationRepository
import com.gasstation.domain.location.ObserveLocationAvailabilityUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationStateMachineTest {

    @Test
    fun `initial state starts denied with no coordinates`() {
        val machine = createMachine()

        assertEquals(LocationPermissionState.Denied, machine.state.value.permissionState)
        assertNull(machine.state.value.currentCoordinates)
        assertNull(machine.state.value.currentAddressLabel)
        assertFalse(machine.state.value.hasDeniedLocationAccess)
        assertFalse(machine.state.value.needsRecoveryRefresh)
        assertTrue(machine.state.value.isGpsEnabled)
        assertFalse(machine.state.value.isAvailabilityKnown)
    }

    @Test
    fun `permission change updates permission state`() {
        val machine = createMachine()

        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)

        assertEquals(LocationPermissionState.PreciseGranted, machine.state.value.permissionState)
    }

    @Test
    fun `gps availability change marks availability known`() {
        val machine = createMachine()

        machine.onGpsAvailabilityChanged(false)

        assertFalse(machine.state.value.isGpsEnabled)
        assertTrue(machine.state.value.isAvailabilityKnown)
    }

    @Test
    fun `successful location acquisition stores coordinates and resets recovery flag`() = runTest {
        val coordinates = Coordinates(37.498095, 127.027610)
        val machine = createMachine(
            FakeLocationStateMachineRepository(result = LocationLookupResult.Success(coordinates)),
        )
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        machine.acquireLocation()
        machine.onGpsAvailabilityChanged(false)
        machine.onGpsAvailabilityChanged(true)
        assertTrue(machine.state.value.needsRecoveryRefresh)

        val result = machine.acquireLocation()

        assertEquals(LocationAcquisitionResult.Success(coordinates), result)
        assertEquals(coordinates, machine.state.value.currentCoordinates)
        assertFalse(machine.state.value.hasDeniedLocationAccess)
        assertFalse(machine.state.value.needsRecoveryRefresh)
    }

    @Test
    fun `permission denied result does not set coordinates`() = runTest {
        val machine = createMachine(
            FakeLocationStateMachineRepository(result = LocationLookupResult.PermissionDenied),
        )

        val result = machine.acquireLocation()

        assertEquals(LocationAcquisitionResult.PermissionDenied, result)
        assertNull(machine.state.value.currentCoordinates)
    }

    @Test
    fun `timeout result maps to timed out acquisition result`() = runTest {
        val machine = createMachine(
            FakeLocationStateMachineRepository(result = LocationLookupResult.TimedOut),
        )

        assertEquals(LocationAcquisitionResult.TimedOut, machine.acquireLocation())
    }

    @Test
    fun `unavailable result maps to unavailable acquisition result`() = runTest {
        val machine = createMachine(
            FakeLocationStateMachineRepository(result = LocationLookupResult.Unavailable),
        )

        assertEquals(LocationAcquisitionResult.Unavailable, machine.acquireLocation())
    }

    @Test
    fun `error result maps to error acquisition result`() = runTest {
        val throwable = IllegalStateException("gps crashed")
        val machine = createMachine(
            FakeLocationStateMachineRepository(result = LocationLookupResult.Error(throwable)),
        )

        val result = machine.acquireLocation()

        assertTrue(result is LocationAcquisitionResult.Error)
        assertSame(throwable, (result as LocationAcquisitionResult.Error).throwable)
    }

    @Test
    fun `address resolution updates label only for current coordinates`() = runTest {
        val currentCoordinates = Coordinates(37.498095, 127.027610)
        val staleCoordinates = Coordinates(37.497927, 127.027583)
        val machine = createMachine(
            FakeLocationStateMachineRepository(
                result = LocationLookupResult.Success(currentCoordinates),
                addressResult = LocationAddressLookupResult.Success("서울 영등포구 당산동 194-32"),
            ),
        )
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        machine.acquireLocation()

        val addressLabel = machine.resolveAddressLabel(currentCoordinates)
        machine.onAddressResolved(staleCoordinates, "stale label")
        machine.onAddressResolved(currentCoordinates, addressLabel)

        assertEquals("서울 영등포구 당산동 194-32", machine.state.value.currentAddressLabel)
    }

    @Test
    fun `recovery refresh is set when location becomes usable after prior coordinates`() = runTest {
        val coordinates = Coordinates(37.498095, 127.027610)
        val machine = createMachine(
            FakeLocationStateMachineRepository(result = LocationLookupResult.Success(coordinates)),
        )
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        machine.acquireLocation()

        machine.onGpsAvailabilityChanged(false)
        machine.onGpsAvailabilityChanged(true)

        assertTrue(machine.state.value.needsRecoveryRefresh)
    }
}

private fun createMachine(
    repository: LocationRepository = FakeLocationStateMachineRepository(),
): LocationStateMachine = LocationStateMachine(
    getCurrentLocation = GetCurrentLocationUseCase(repository),
    getCurrentAddress = GetCurrentAddressUseCase(repository),
    observeAvailability = ObserveLocationAvailabilityUseCase(repository),
)

private class FakeLocationStateMachineRepository(
    private val availability: Flow<Boolean> = MutableStateFlow(true),
    private val result: LocationLookupResult = LocationLookupResult.Success(
        Coordinates(37.498095, 127.027610),
    ),
    private val addressResult: LocationAddressLookupResult = LocationAddressLookupResult.Unavailable,
) : LocationRepository {
    override fun observeAvailability(): Flow<Boolean> = availability

    override suspend fun getCurrentLocation(
        permissionState: LocationPermissionState,
    ): LocationLookupResult = result

    override suspend fun getCurrentAddress(
        coordinates: Coordinates,
    ): LocationAddressLookupResult = addressResult
}
