package com.gasstation.feature.stationlist

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.GetCurrentAddressUseCase
import com.gasstation.domain.location.GetCurrentLocationUseCase
import com.gasstation.domain.location.LocationAddressLookupResult
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.location.ObserveLocationAvailabilityUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LocationStateMachine @Inject constructor(
    private val getCurrentLocation: GetCurrentLocationUseCase,
    private val getCurrentAddress: GetCurrentAddressUseCase,
    private val observeAvailability: ObserveLocationAvailabilityUseCase,
) {
    private val mutableState = MutableStateFlow(LocationState())

    val state = mutableState.asStateFlow()

    fun observeGpsAvailability(): Flow<Boolean> = observeAvailability()

    fun onPermissionChanged(permissionState: LocationPermissionState) {
        mutableState.update {
            it.withLocationRecoveryState(permissionState = permissionState)
        }
    }

    fun onGpsAvailabilityChanged(isEnabled: Boolean) {
        mutableState.update {
            it.withLocationRecoveryState(
                isGpsEnabled = isEnabled,
                isAvailabilityKnown = true,
            )
        }
    }

    suspend fun acquireLocation(): LocationAcquisitionResult =
        when (val result = getCurrentLocation(state.value.permissionState)) {
            is LocationLookupResult.Success -> {
                val coordinates = result.coordinates
                val previousCoordinates = state.value.currentCoordinates
                mutableState.update {
                    it.copy(
                        currentCoordinates = coordinates,
                        currentAddressLabel = if (previousCoordinates == coordinates) {
                            it.currentAddressLabel
                        } else {
                            null
                        },
                        hasDeniedLocationAccess = it.permissionState == LocationPermissionState.Denied,
                        needsRecoveryRefresh = false,
                    )
                }
                LocationAcquisitionResult.Success(coordinates)
            }

            LocationLookupResult.PermissionDenied -> LocationAcquisitionResult.PermissionDenied
            LocationLookupResult.TimedOut -> LocationAcquisitionResult.TimedOut
            LocationLookupResult.Unavailable -> LocationAcquisitionResult.Unavailable
            is LocationLookupResult.Error -> LocationAcquisitionResult.Error(result.throwable)
        }

    suspend fun resolveAddressLabel(coordinates: Coordinates): String? =
        when (val result = getCurrentAddress(coordinates)) {
            is LocationAddressLookupResult.Success -> result.addressLabel
            LocationAddressLookupResult.Unavailable,
            is LocationAddressLookupResult.Error -> null
        }

    fun onAddressResolved(coordinates: Coordinates, addressLabel: String?) {
        mutableState.update { current ->
            if (current.currentCoordinates == coordinates) {
                current.copy(currentAddressLabel = addressLabel)
            } else {
                current
            }
        }
    }
}

data class LocationState(
    val permissionState: LocationPermissionState = LocationPermissionState.Denied,
    val hasDeniedLocationAccess: Boolean = false,
    val needsRecoveryRefresh: Boolean = false,
    val isGpsEnabled: Boolean = true,
    val isAvailabilityKnown: Boolean = false,
    val currentCoordinates: Coordinates? = null,
    val currentAddressLabel: String? = null,
)

sealed interface LocationAcquisitionResult {
    data class Success(val coordinates: Coordinates) : LocationAcquisitionResult
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
        currentCoordinates != null &&
        !hasDeniedLocationAccess
    return updated.copy(
        needsRecoveryRefresh = updated.needsRecoveryRefresh || needsRecoveryRefresh,
    )
}

private fun LocationState.isLocationUsable(): Boolean =
    isGpsEnabled &&
        (
            permissionState != LocationPermissionState.Denied ||
                hasDeniedLocationAccess
            )
