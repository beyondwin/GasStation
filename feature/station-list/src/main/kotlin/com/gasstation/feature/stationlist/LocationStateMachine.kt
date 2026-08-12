package com.gasstation.feature.stationlist

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.GetCurrentAddressUseCase
import com.gasstation.domain.location.GetCurrentLocationUseCase
import com.gasstation.domain.location.LocationAddressLookupResult
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.location.ObserveLocationAvailabilityUseCase
import com.gasstation.domain.location.normalizeCurrentAddressLabel
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@ViewModelScoped
class LocationStateMachine @Inject constructor(
    private val getCurrentLocation: GetCurrentLocationUseCase,
    private val getCurrentAddress: GetCurrentAddressUseCase,
    private val observeAvailability: ObserveLocationAvailabilityUseCase,
) {
    private val stateLock = Any()
    private val mutableState = MutableStateFlow(LocationState())
    private var permissionGeneration = 0L
    private var gpsGeneration = 0L
    private var locationRequestGeneration = 0L
    private var addressRequestGeneration = 0L

    val state = mutableState.asStateFlow()

    fun observeGpsAvailability(): Flow<Boolean> = observeAvailability()

    fun onPermissionChanged(permissionState: LocationPermissionState) {
        synchronized(stateLock) {
            val current = mutableState.value
            if (current.permissionState == permissionState) return

            permissionGeneration += 1
            mutableState.value = if (
                permissionState == LocationPermissionState.Denied ||
                (
                    current.permissionState == LocationPermissionState.PreciseGranted &&
                        permissionState == LocationPermissionState.ApproximateGranted
                    )
            ) {
                current.copy(
                    permissionState = permissionState,
                    currentCoordinates = null,
                    currentAddressLabel = null,
                    needsRecoveryRefresh = false,
                )
            } else {
                current.withLocationRecoveryState(permissionState = permissionState)
            }
        }
    }

    fun onGpsAvailabilityChanged(isEnabled: Boolean) {
        synchronized(stateLock) {
            val current = mutableState.value
            val gpsChanged = current.isGpsEnabled != isEnabled
            if (!gpsChanged && current.isAvailabilityKnown) return

            if (gpsChanged) gpsGeneration += 1
            mutableState.value = current.withLocationRecoveryState(
                isGpsEnabled = isEnabled,
                isAvailabilityKnown = true,
            )
        }
    }

    suspend fun acquireLocation(): LocationAcquisitionResult {
        val request = synchronized(stateLock) {
            locationRequestGeneration += 1
            val current = mutableState.value
            LocationRequest(
                permissionGeneration = permissionGeneration,
                permissionState = current.permissionState,
                gpsGeneration = gpsGeneration,
                isGpsEnabled = current.isGpsEnabled,
                requestGeneration = locationRequestGeneration,
            )
        }
        if (request.permissionState == LocationPermissionState.Denied) {
            return LocationAcquisitionResult.PermissionDenied
        }

        val result = getCurrentLocation(request.permissionState)
        currentCoroutineContext().ensureActive()
        return synchronized(stateLock) {
            if (!request.isCurrent()) return@synchronized LocationAcquisitionResult.Superseded

            when (result) {
                is LocationLookupResult.Success -> {
                    val coordinates = result.coordinates
                    val current = mutableState.value
                    mutableState.value = current.copy(
                        currentCoordinates = coordinates,
                        currentAddressLabel = if (current.currentCoordinates == coordinates) {
                            current.currentAddressLabel
                        } else {
                            null
                        },
                        needsRecoveryRefresh = false,
                    )
                    LocationAcquisitionResult.Success(coordinates)
                }

                LocationLookupResult.PermissionDenied -> LocationAcquisitionResult.PermissionDenied

                LocationLookupResult.TimedOut -> LocationAcquisitionResult.TimedOut

                LocationLookupResult.Unavailable -> LocationAcquisitionResult.Unavailable

                is LocationLookupResult.Error -> LocationAcquisitionResult.Error(result.throwable)
            }
        }
    }

    suspend fun resolveAddressLabel(coordinates: Coordinates) {
        val request = synchronized(stateLock) {
            addressRequestGeneration += 1
            val current = mutableState.value
            if (
                current.currentCoordinates != coordinates ||
                current.permissionState == LocationPermissionState.Denied ||
                !current.isGpsEnabled
            ) {
                null
            } else {
                AddressRequest(
                    permissionGeneration = permissionGeneration,
                    permissionState = current.permissionState,
                    gpsGeneration = gpsGeneration,
                    isGpsEnabled = current.isGpsEnabled,
                    locationRequestGeneration = locationRequestGeneration,
                    addressRequestGeneration = addressRequestGeneration,
                    coordinates = coordinates,
                )
            }
        } ?: return

        val addressLabel = when (val result = getCurrentAddress(coordinates)) {
            is LocationAddressLookupResult.Success -> normalizeCurrentAddressLabel(result.addressLabel)

            LocationAddressLookupResult.Unavailable,
            is LocationAddressLookupResult.Error,
            -> null
        }
        currentCoroutineContext().ensureActive()

        synchronized(stateLock) {
            if (!request.isCurrent()) return@synchronized
            mutableState.value = mutableState.value.copy(currentAddressLabel = addressLabel)
        }
    }

    private fun LocationRequest.isCurrent(): Boolean {
        val current = mutableState.value
        return permissionGeneration == this@LocationStateMachine.permissionGeneration &&
            permissionState == current.permissionState &&
            gpsGeneration == this@LocationStateMachine.gpsGeneration &&
            isGpsEnabled == current.isGpsEnabled &&
            requestGeneration == locationRequestGeneration
    }

    private fun AddressRequest.isCurrent(): Boolean {
        val current = mutableState.value
        return permissionGeneration == this@LocationStateMachine.permissionGeneration &&
            permissionState == current.permissionState &&
            gpsGeneration == this@LocationStateMachine.gpsGeneration &&
            isGpsEnabled == current.isGpsEnabled &&
            locationRequestGeneration == this@LocationStateMachine.locationRequestGeneration &&
            addressRequestGeneration == this@LocationStateMachine.addressRequestGeneration &&
            coordinates == current.currentCoordinates
    }
}

private data class LocationRequest(
    val permissionGeneration: Long,
    val permissionState: LocationPermissionState,
    val gpsGeneration: Long,
    val isGpsEnabled: Boolean,
    val requestGeneration: Long,
)

private data class AddressRequest(
    val permissionGeneration: Long,
    val permissionState: LocationPermissionState,
    val gpsGeneration: Long,
    val isGpsEnabled: Boolean,
    val locationRequestGeneration: Long,
    val addressRequestGeneration: Long,
    val coordinates: Coordinates,
)

data class LocationState(
    val permissionState: LocationPermissionState = LocationPermissionState.Denied,
    val needsRecoveryRefresh: Boolean = false,
    val isGpsEnabled: Boolean = true,
    val isAvailabilityKnown: Boolean = false,
    val currentCoordinates: Coordinates? = null,
    val currentAddressLabel: String? = null,
)

sealed interface LocationAcquisitionResult {
    data class Success(val coordinates: Coordinates) : LocationAcquisitionResult
    data object Superseded : LocationAcquisitionResult
    data object PermissionDenied : LocationAcquisitionResult
    data object TimedOut : LocationAcquisitionResult
    data object Unavailable : LocationAcquisitionResult
    data class Error(val throwable: Throwable) : LocationAcquisitionResult
}

private fun LocationState.withLocationRecoveryState(
    permissionState: LocationPermissionState = this.permissionState,
    isGpsEnabled: Boolean = this.isGpsEnabled,
    isAvailabilityKnown: Boolean = this.isAvailabilityKnown,
): LocationState {
    val updated = copy(
        permissionState = permissionState,
        isGpsEnabled = isGpsEnabled,
        isAvailabilityKnown = isAvailabilityKnown,
    )
    val needsRecoveryRefresh = !isLocationUsable() &&
        updated.isLocationUsable() &&
        currentCoordinates != null
    return updated.copy(
        needsRecoveryRefresh = updated.needsRecoveryRefresh || needsRecoveryRefresh,
    )
}

private fun LocationState.isLocationUsable(): Boolean = isGpsEnabled && permissionState != LocationPermissionState.Denied
