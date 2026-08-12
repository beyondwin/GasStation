package com.gasstation.feature.stationlist

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.GetCurrentAddressUseCase
import com.gasstation.domain.location.GetCurrentLocationUseCase
import com.gasstation.domain.location.LocationAddressLookupResult
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.location.LocationRepository
import com.gasstation.domain.location.ObserveLocationAvailabilityUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class LocationStateMachineTest {

    @Test
    fun `initial state starts denied with no coordinates`() {
        val machine = createMachine()

        assertEquals(LocationPermissionState.Denied, machine.state.value.permissionState)
        assertNull(machine.state.value.currentCoordinates)
        assertNull(machine.state.value.currentAddressLabel)
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
    fun `denied permission clears retained coordinates and never calls repository`() = runTest {
        val repository = RecordingPermissionLocationRepository()
        val machine = createMachine(repository)
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        assertTrue(machine.acquireLocation() is LocationAcquisitionResult.Success)

        machine.onPermissionChanged(LocationPermissionState.Denied)
        val result = machine.acquireLocation()

        assertEquals(LocationAcquisitionResult.PermissionDenied, result)
        assertNull(machine.state.value.currentCoordinates)
        assertEquals(1, repository.locationRequests)
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
        assertFalse(machine.state.value.needsRecoveryRefresh)
    }

    @Test
    fun `permission denied result does not set coordinates`() = runTest {
        val repository = FakeLocationStateMachineRepository(result = LocationLookupResult.PermissionDenied)
        val machine = createMachine(repository)
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)

        val result = machine.acquireLocation()

        assertEquals(LocationAcquisitionResult.PermissionDenied, result)
        assertNull(machine.state.value.currentCoordinates)
        assertEquals(1, repository.locationRequests)
    }

    @Test
    fun `timeout result maps to timed out acquisition result`() = runTest {
        val machine = createMachine(
            FakeLocationStateMachineRepository(result = LocationLookupResult.TimedOut),
        )
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)

        assertEquals(LocationAcquisitionResult.TimedOut, machine.acquireLocation())
    }

    @Test
    fun `unavailable result maps to unavailable acquisition result`() = runTest {
        val machine = createMachine(
            FakeLocationStateMachineRepository(result = LocationLookupResult.Unavailable),
        )
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)

        assertEquals(LocationAcquisitionResult.Unavailable, machine.acquireLocation())
    }

    @Test
    fun `error result maps to error acquisition result`() = runTest {
        val throwable = IllegalStateException("gps crashed")
        val machine = createMachine(
            FakeLocationStateMachineRepository(result = LocationLookupResult.Error(throwable)),
        )
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)

        val result = machine.acquireLocation()

        assertTrue(result is LocationAcquisitionResult.Error)
        assertSame(throwable, (result as LocationAcquisitionResult.Error).throwable)
    }

    @Test
    fun `address resolution updates label only for current coordinates`() = runTest {
        val currentCoordinates = Coordinates(37.498095, 127.027610)
        val machine = createMachine(
            FakeLocationStateMachineRepository(
                result = LocationLookupResult.Success(currentCoordinates),
                addressResult = LocationAddressLookupResult.Success("서울 영등포구 당산동 194-32"),
            ),
        )
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        machine.acquireLocation()

        machine.resolveAddressLabel(currentCoordinates)

        assertEquals("서울 영등포구 당산동", machine.state.value.currentAddressLabel)
    }

    @Test
    fun `precise completion is superseded after downgrade and never commits`() = runTest(timeout = 10.seconds) {
        val repository = QueuedLocationRepository()
        val request = repository.enqueueLocation()
        val machine = createMachine(repository)
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)

        val acquisition = async { machine.acquireLocation() }
        runCurrent()
        machine.onPermissionChanged(LocationPermissionState.ApproximateGranted)
        request.complete(LocationLookupResult.Success(PRECISE_COORDINATES))

        assertEquals(LocationAcquisitionResult.Superseded, acquisition.await())
        assertNull(machine.state.value.currentCoordinates)
        assertNull(machine.state.value.currentAddressLabel)
    }

    @Test
    fun `downgrade clears retained precise coordinates and address`() = runTest(timeout = 10.seconds) {
        val repository = QueuedLocationRepository()
        repository.enqueueLocation().complete(LocationLookupResult.Success(PRECISE_COORDINATES))
        repository.enqueueAddress().complete(LocationAddressLookupResult.Success("서울 강남구 역삼동 1"))
        val machine = createMachine(repository)
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        machine.acquireLocation()
        machine.resolveAddressLabel(PRECISE_COORDINATES)

        machine.onPermissionChanged(LocationPermissionState.ApproximateGranted)

        assertNull(machine.state.value.currentCoordinates)
        assertNull(machine.state.value.currentAddressLabel)
        assertFalse(machine.state.value.needsRecoveryRefresh)
    }

    @Test
    fun `permission away and back still supersedes the old request`() = runTest(timeout = 10.seconds) {
        val repository = QueuedLocationRepository()
        val request = repository.enqueueLocation()
        val machine = createMachine(repository)
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)

        val acquisition = async { machine.acquireLocation() }
        runCurrent()
        machine.onPermissionChanged(LocationPermissionState.ApproximateGranted)
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        request.complete(LocationLookupResult.Success(PRECISE_COORDINATES))

        assertEquals(LocationAcquisitionResult.Superseded, acquisition.await())
        assertNull(machine.state.value.currentCoordinates)
    }

    @Test
    fun `identical permission callback does not supersede location`() = runTest(timeout = 10.seconds) {
        val repository = QueuedLocationRepository()
        val request = repository.enqueueLocation()
        val machine = createMachine(repository)
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)

        val acquisition = async { machine.acquireLocation() }
        runCurrent()
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        request.complete(LocationLookupResult.Success(PRECISE_COORDINATES))

        assertEquals(LocationAcquisitionResult.Success(PRECISE_COORDINATES), acquisition.await())
        assertEquals(PRECISE_COORDINATES, machine.state.value.currentCoordinates)
    }

    @Test
    fun `older equal-coordinate location cannot overwrite newer request`() = runTest(timeout = 10.seconds) {
        val repository = QueuedLocationRepository()
        val olderRequest = repository.enqueueLocation()
        val newerRequest = repository.enqueueLocation()
        val machine = createMachine(repository)
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)

        val olderAcquisition = async { machine.acquireLocation() }
        runCurrent()
        val newerAcquisition = async { machine.acquireLocation() }
        runCurrent()
        newerRequest.complete(LocationLookupResult.Success(PRECISE_COORDINATES))
        assertEquals(LocationAcquisitionResult.Success(PRECISE_COORDINATES), newerAcquisition.await())
        olderRequest.complete(LocationLookupResult.Success(PRECISE_COORDINATES))

        assertEquals(LocationAcquisitionResult.Superseded, olderAcquisition.await())
        assertEquals(PRECISE_COORDINATES, machine.state.value.currentCoordinates)
    }

    @Test
    fun `gps disable supersedes in flight location`() = runTest(timeout = 10.seconds) {
        val repository = QueuedLocationRepository()
        val request = repository.enqueueLocation()
        val machine = createMachine(repository)
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        machine.onGpsAvailabilityChanged(true)

        val acquisition = async { machine.acquireLocation() }
        runCurrent()
        machine.onGpsAvailabilityChanged(false)
        request.complete(LocationLookupResult.Success(PRECISE_COORDINATES))

        assertEquals(LocationAcquisitionResult.Superseded, acquisition.await())
        assertNull(machine.state.value.currentCoordinates)
    }

    @Test
    fun `gps off and on still supersedes old location`() = runTest(timeout = 10.seconds) {
        val repository = QueuedLocationRepository()
        val request = repository.enqueueLocation()
        val machine = createMachine(repository)
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        machine.onGpsAvailabilityChanged(true)

        val acquisition = async { machine.acquireLocation() }
        runCurrent()
        machine.onGpsAvailabilityChanged(false)
        machine.onGpsAvailabilityChanged(true)
        request.complete(LocationLookupResult.Success(PRECISE_COORDINATES))

        assertEquals(LocationAcquisitionResult.Superseded, acquisition.await())
        assertNull(machine.state.value.currentCoordinates)
    }

    @Test
    fun `identical gps callback does not supersede location`() = runTest(timeout = 10.seconds) {
        val repository = QueuedLocationRepository()
        val request = repository.enqueueLocation()
        val machine = createMachine(repository)
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        machine.onGpsAvailabilityChanged(true)

        val acquisition = async { machine.acquireLocation() }
        runCurrent()
        machine.onGpsAvailabilityChanged(true)
        request.complete(LocationLookupResult.Success(PRECISE_COORDINATES))

        assertEquals(LocationAcquisitionResult.Success(PRECISE_COORDINATES), acquisition.await())
    }

    @Test
    fun `obsolete provider outcomes are superseded instead of remapped`() = runTest(timeout = 10.seconds) {
        val obsoleteOutcomes = listOf(
            LocationLookupResult.PermissionDenied,
            LocationLookupResult.TimedOut,
            LocationLookupResult.Unavailable,
            LocationLookupResult.Error(IllegalStateException("obsolete")),
        )

        obsoleteOutcomes.forEach { outcome ->
            val repository = QueuedLocationRepository()
            val request = repository.enqueueLocation()
            val machine = createMachine(repository)
            machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
            val acquisition = async { machine.acquireLocation() }
            runCurrent()

            machine.onPermissionChanged(LocationPermissionState.ApproximateGranted)
            machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
            request.complete(outcome)

            assertEquals(LocationAcquisitionResult.Superseded, acquisition.await())
        }
    }

    @Test
    fun `older same-coordinate address cannot overwrite latest label`() = runTest(timeout = 10.seconds) {
        val repository = QueuedLocationRepository()
        repository.enqueueLocation().complete(LocationLookupResult.Success(PRECISE_COORDINATES))
        val olderAddress = repository.enqueueAddress()
        val newerAddress = repository.enqueueAddress()
        val machine = createMachine(repository)
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        machine.acquireLocation()

        val olderResolution = async { machine.resolveAddressLabel(PRECISE_COORDINATES) }
        runCurrent()
        val newerResolution = async { machine.resolveAddressLabel(PRECISE_COORDINATES) }
        runCurrent()
        newerAddress.complete(LocationAddressLookupResult.Success("서울 강남구 최신동 2"))
        newerResolution.await()
        olderAddress.complete(LocationAddressLookupResult.Success("서울 강남구 오래된동 1"))
        olderResolution.await()

        assertEquals("서울 강남구 최신동", machine.state.value.currentAddressLabel)
    }

    @Test
    fun `new location request invalidates prior address before location completes`() = runTest(timeout = 10.seconds) {
        val repository = QueuedLocationRepository()
        repository.enqueueLocation().complete(LocationLookupResult.Success(PRECISE_COORDINATES))
        repository.enqueueAddress().complete(LocationAddressLookupResult.Success("서울 강남구 기존동 1"))
        val staleAddress = repository.enqueueAddress()
        val newerLocation = repository.enqueueLocation()
        val machine = createMachine(repository)
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        machine.acquireLocation()
        machine.resolveAddressLabel(PRECISE_COORDINATES)

        val addressResolution = async { machine.resolveAddressLabel(PRECISE_COORDINATES) }
        runCurrent()
        val locationAcquisition = async { machine.acquireLocation() }
        runCurrent()
        staleAddress.complete(LocationAddressLookupResult.Success("서울 강남구 오래된동 1"))
        addressResolution.await()

        assertEquals("서울 강남구 기존동", machine.state.value.currentAddressLabel)
        newerLocation.complete(LocationLookupResult.Success(NEW_COORDINATES))
        assertEquals(LocationAcquisitionResult.Success(NEW_COORDINATES), locationAcquisition.await())
    }

    @Test
    fun `permission transition invalidates in flight address`() = runTest(timeout = 10.seconds) {
        val repository = QueuedLocationRepository()
        repository.enqueueLocation().complete(LocationLookupResult.Success(PRECISE_COORDINATES))
        repository.enqueueAddress().complete(LocationAddressLookupResult.Success("서울 강남구 기존동 1"))
        val staleAddress = repository.enqueueAddress()
        val machine = createMachine(repository)
        machine.onPermissionChanged(LocationPermissionState.ApproximateGranted)
        machine.acquireLocation()
        machine.resolveAddressLabel(PRECISE_COORDINATES)

        val resolution = async { machine.resolveAddressLabel(PRECISE_COORDINATES) }
        runCurrent()
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        staleAddress.complete(LocationAddressLookupResult.Success("서울 강남구 오래된동 1"))
        resolution.await()

        assertEquals("서울 강남구 기존동", machine.state.value.currentAddressLabel)
    }

    @Test
    fun `gps disable invalidates in flight address`() = runTest(timeout = 10.seconds) {
        val repository = QueuedLocationRepository()
        repository.enqueueLocation().complete(LocationLookupResult.Success(PRECISE_COORDINATES))
        repository.enqueueAddress().complete(LocationAddressLookupResult.Success("서울 강남구 기존동 1"))
        val staleAddress = repository.enqueueAddress()
        val machine = createMachine(repository)
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        machine.onGpsAvailabilityChanged(true)
        machine.acquireLocation()
        machine.resolveAddressLabel(PRECISE_COORDINATES)

        val resolution = async { machine.resolveAddressLabel(PRECISE_COORDINATES) }
        runCurrent()
        machine.onGpsAvailabilityChanged(false)
        staleAddress.complete(LocationAddressLookupResult.Success("서울 강남구 오래된동 1"))
        resolution.await()

        assertEquals("서울 강남구 기존동", machine.state.value.currentAddressLabel)
    }

    @Test
    fun `stale address failures cannot clear newer label`() = runTest(timeout = 10.seconds) {
        val staleOutcomes = listOf(
            LocationAddressLookupResult.Unavailable,
            LocationAddressLookupResult.Error(IllegalStateException("obsolete")),
        )

        staleOutcomes.forEachIndexed { index, staleOutcome ->
            val repository = QueuedLocationRepository()
            repository.enqueueLocation().complete(LocationLookupResult.Success(PRECISE_COORDINATES))
            val staleAddress = repository.enqueueAddress()
            val latestAddress = repository.enqueueAddress()
            val machine = createMachine(repository)
            machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
            machine.acquireLocation()

            val staleResolution = async { machine.resolveAddressLabel(PRECISE_COORDINATES) }
            runCurrent()
            val latestResolution = async { machine.resolveAddressLabel(PRECISE_COORDINATES) }
            runCurrent()
            latestAddress.complete(LocationAddressLookupResult.Success("서울 강남구 최신동 $index"))
            latestResolution.await()
            staleAddress.complete(staleOutcome)
            staleResolution.await()

            assertEquals("서울 강남구 최신동", machine.state.value.currentAddressLabel)
        }
    }

    @Test
    fun `location cancellation propagates and leaves visible state unchanged`() = runTest(timeout = 10.seconds) {
        val repository = QueuedLocationRepository()
        repository.enqueueLocation()
        val machine = createMachine(repository)
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        val before = machine.state.value

        val acquisition = async { machine.acquireLocation() }
        runCurrent()
        acquisition.cancel()

        assertCancellationPropagates { acquisition.await() }
        assertEquals(before, machine.state.value)
    }

    @Test
    fun `address cancellation propagates and leaves visible state unchanged`() = runTest(timeout = 10.seconds) {
        val repository = QueuedLocationRepository()
        repository.enqueueLocation().complete(LocationLookupResult.Success(PRECISE_COORDINATES))
        repository.enqueueAddress().complete(LocationAddressLookupResult.Success("서울 강남구 기존동 1"))
        repository.enqueueAddress()
        val machine = createMachine(repository)
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        machine.acquireLocation()
        machine.resolveAddressLabel(PRECISE_COORDINATES)
        val before = machine.state.value

        val resolution = async { machine.resolveAddressLabel(PRECISE_COORDINATES) }
        runCurrent()
        resolution.cancel()

        assertCancellationPropagates { resolution.await() }
        assertEquals(before, machine.state.value)
    }

    @Test
    fun `location cancellation after provider result cannot commit`() = runTest(timeout = 10.seconds) {
        val machine = createMachine(
            object : LocationRepository {
                override fun observeAvailability(): Flow<Boolean> = MutableStateFlow(true)

                override suspend fun getCurrentLocation(permissionState: LocationPermissionState): LocationLookupResult {
                    currentCoroutineContext().cancel(CancellationException("cancel before location commit"))
                    return LocationLookupResult.Success(PRECISE_COORDINATES)
                }

                override suspend fun getCurrentAddress(coordinates: Coordinates): LocationAddressLookupResult =
                    LocationAddressLookupResult.Unavailable
            },
        )
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        val before = machine.state.value

        val acquisition = async { machine.acquireLocation() }

        assertCancellationPropagates { acquisition.await() }
        assertEquals(before, machine.state.value)
    }

    @Test
    fun `address cancellation after provider result cannot commit`() = runTest(timeout = 10.seconds) {
        val machine = createMachine(
            object : LocationRepository {
                override fun observeAvailability(): Flow<Boolean> = MutableStateFlow(true)

                override suspend fun getCurrentLocation(permissionState: LocationPermissionState): LocationLookupResult =
                    LocationLookupResult.Success(PRECISE_COORDINATES)

                override suspend fun getCurrentAddress(coordinates: Coordinates): LocationAddressLookupResult {
                    currentCoroutineContext().cancel(CancellationException("cancel before address commit"))
                    return LocationAddressLookupResult.Success("서울 강남구 취소동 1")
                }
            },
        )
        machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        machine.acquireLocation()
        val before = machine.state.value

        val resolution = async { machine.resolveAddressLabel(PRECISE_COORDINATES) }

        assertCancellationPropagates { resolution.await() }
        assertEquals(before, machine.state.value)
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

private fun createMachine(repository: LocationRepository = FakeLocationStateMachineRepository()): LocationStateMachine =
    LocationStateMachine(
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
    var locationRequests = 0
        private set

    override fun observeAvailability(): Flow<Boolean> = availability

    override suspend fun getCurrentLocation(permissionState: LocationPermissionState): LocationLookupResult {
        locationRequests += 1
        return result
    }

    override suspend fun getCurrentAddress(coordinates: Coordinates): LocationAddressLookupResult = addressResult
}

private class RecordingPermissionLocationRepository : LocationRepository {
    var locationRequests = 0

    override fun observeAvailability(): Flow<Boolean> = MutableStateFlow(true)

    override suspend fun getCurrentLocation(permissionState: LocationPermissionState): LocationLookupResult {
        locationRequests += 1
        return LocationLookupResult.Success(Coordinates(37.498095, 127.027610))
    }

    override suspend fun getCurrentAddress(coordinates: Coordinates): LocationAddressLookupResult = LocationAddressLookupResult.Unavailable
}

private class QueuedLocationRepository : LocationRepository {
    private val locationResults = ArrayDeque<CompletableDeferred<LocationLookupResult>>()
    private val addressResults = ArrayDeque<CompletableDeferred<LocationAddressLookupResult>>()

    fun enqueueLocation(): CompletableDeferred<LocationLookupResult> =
        CompletableDeferred<LocationLookupResult>().also(locationResults::addLast)

    fun enqueueAddress(): CompletableDeferred<LocationAddressLookupResult> =
        CompletableDeferred<LocationAddressLookupResult>().also(addressResults::addLast)

    override fun observeAvailability(): Flow<Boolean> = MutableStateFlow(true)

    override suspend fun getCurrentLocation(permissionState: LocationPermissionState): LocationLookupResult =
        locationResults.removeFirst().await()

    override suspend fun getCurrentAddress(coordinates: Coordinates): LocationAddressLookupResult = addressResults.removeFirst().await()
}

private val PRECISE_COORDINATES = Coordinates(37.498095, 127.027610)
private val NEW_COORDINATES = Coordinates(37.497927, 127.027583)

private suspend fun assertCancellationPropagates(block: suspend () -> Unit) {
    try {
        block()
        fail("Expected CancellationException")
    } catch (_: CancellationException) {}
}
