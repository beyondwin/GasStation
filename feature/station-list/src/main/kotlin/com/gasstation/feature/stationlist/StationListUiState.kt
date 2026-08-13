package com.gasstation.feature.stationlist

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.settings.model.UserPreferences
import java.time.Instant

data class StationListUiState(
    val currentCoordinates: Coordinates? = null,
    val currentAddressLabel: String? = null,
    val permissionState: LocationPermissionState = LocationPermissionState.Denied,
    val needsRecoveryRefresh: Boolean = false,
    val isGpsEnabled: Boolean = true,
    val isAvailabilityKnown: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val hasCachedSnapshot: Boolean = false,
    val blockingFailure: StationListFailureReason? = null,
    val stations: List<StationListItemUiModel> = emptyList(),
    val preferences: UserPreferences? = null,
    val preferenceLoadFailed: Boolean = false,
    val pendingPreferenceWrite: Boolean = false,
    val lastUpdatedAt: Instant? = null,
    val pendingCommands: List<StationListUiCommand> = emptyList(),
)
