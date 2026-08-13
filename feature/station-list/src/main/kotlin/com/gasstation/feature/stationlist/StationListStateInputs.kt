package com.gasstation.feature.stationlist

import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationListEntry
import com.gasstation.domain.station.model.StationSearchResult
import java.time.Instant

internal sealed interface PreferenceLoadState {
    data object Loading : PreferenceLoadState

    data class Ready(val preferences: UserPreferences) : PreferenceLoadState

    data object Failed : PreferenceLoadState
}

internal data class StationListPreferenceMutationState(val pendingPreferenceWrite: Boolean = false)

internal data class StationListSearchProjection(
    val sourceStations: List<StationListEntry> = emptyList(),
    val stations: List<StationListItemUiModel> = emptyList(),
    val freshness: StationFreshness = StationFreshness.Stale,
    val fetchedAt: Instant? = null,
    val hasCachedSnapshot: Boolean = false,
)

internal data class StationListStateInputs(
    val preference: PreferenceLoadState,
    val preferenceMutation: StationListPreferenceMutationState,
    val location: LocationState,
    val refresh: RefreshCoordinatorState,
    val search: StationListSearchProjection,
    val blockingFailure: StationListFailureReason?,
    val pendingCommands: List<StationListUiCommand>,
)

internal data class StationListBaseStateInputs(
    val preference: PreferenceLoadState,
    val preferenceMutation: StationListPreferenceMutationState,
    val location: LocationState,
    val refresh: RefreshCoordinatorState,
    val search: StationListSearchProjection,
)

internal fun projectStationSearchResult(previous: StationListSearchProjection, result: StationSearchResult): StationListSearchProjection {
    val items = if (previous.sourceStations == result.stations) {
        previous.stations
    } else {
        result.stations.map(::StationListItemUiModel)
    }
    return StationListSearchProjection(
        sourceStations = result.stations,
        stations = items,
        freshness = result.freshness,
        fetchedAt = result.fetchedAt,
        hasCachedSnapshot = result.hasCachedSnapshot,
    )
}
