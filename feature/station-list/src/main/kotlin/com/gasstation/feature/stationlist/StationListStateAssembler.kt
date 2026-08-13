package com.gasstation.feature.stationlist

import com.gasstation.domain.station.model.StationFreshness

internal object StationListStateAssembler {
    fun assemble(inputs: StationListStateInputs): StationListUiState {
        val readyPreferences = (inputs.preference as? PreferenceLoadState.Ready)?.preferences
        return StationListUiState(
            currentCoordinates = inputs.location.currentCoordinates,
            currentAddressLabel = inputs.location.currentAddressLabel,
            permissionState = inputs.location.permissionState,
            needsRecoveryRefresh = inputs.location.needsRecoveryRefresh,
            isGpsEnabled = inputs.location.isGpsEnabled,
            isAvailabilityKnown = inputs.location.isAvailabilityKnown,
            isLoading = inputs.refresh.isLoading || inputs.preference is PreferenceLoadState.Loading,
            isRefreshing = inputs.refresh.isRefreshing,
            isStale = inputs.search.hasCachedSnapshot &&
                inputs.search.freshness is StationFreshness.Stale,
            blockingFailure = inputs.blockingFailure,
            stations = inputs.search.stations,
            preferences = readyPreferences,
            preferenceLoadFailed = inputs.preference is PreferenceLoadState.Failed,
            pendingPreferenceWrite = inputs.preferenceMutation.pendingPreferenceWrite,
            lastUpdatedAt = inputs.search.fetchedAt,
            hasCachedSnapshot = inputs.search.hasCachedSnapshot,
            pendingCommands = inputs.pendingCommands,
        )
    }
}
