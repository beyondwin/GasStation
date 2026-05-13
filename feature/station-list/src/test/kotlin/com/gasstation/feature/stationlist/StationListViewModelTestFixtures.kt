package com.gasstation.feature.stationlist

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationAddressLookupResult
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.location.LocationRepository
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchedStationSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeStationRepository(
    result: StationSearchResult,
    var refreshFailure: Throwable? = null,
    useObservedResultsFlow: Boolean = false,
    initialObservedResult: StationSearchResult? = result,
) : StationRepository {
    private val state = MutableStateFlow(result)
    private val observedResults =
        if (useObservedResultsFlow) {
            MutableSharedFlow<StationSearchResult>(
                replay = if (initialObservedResult != null) 1 else 0,
                extraBufferCapacity = 1,
            ).also { flow ->
                initialObservedResult?.let(flow::tryEmit)
            }
        } else {
            null
        }

    val refreshedQueries = mutableListOf<StationQuery>()
    val observedQueries = mutableListOf<StationQuery>()
    val watchStateUpdates = mutableListOf<Pair<String, Boolean>>()

    fun emitObservedResult(result: StationSearchResult) {
        checkNotNull(observedResults) { "Observed results flow is not enabled for this fake repository." }
            .tryEmit(result)
    }

    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> {
        observedQueries += query
        return observedResults ?: state
    }

    override fun observeWatchlist(origin: Coordinates): Flow<List<WatchedStationSummary>> = MutableStateFlow(emptyList())

    override suspend fun refreshNearbyStations(query: StationQuery) {
        refreshedQueries += query
        refreshFailure?.let { throw it }
    }

    override suspend fun updateWatchState(station: Station, watched: Boolean) {
        watchStateUpdates += station.id to watched
    }
}

internal class FakeLocationRepository(
    private val availability: Flow<Boolean> = MutableStateFlow(true),
    private val result: LocationLookupResult = LocationLookupResult.Success(
        Coordinates(37.498095, 127.027610),
    ),
    private val addressResult: LocationAddressLookupResult = LocationAddressLookupResult.Unavailable,
    private val resultForPermission: ((LocationPermissionState) -> LocationLookupResult)? = null,
    private val addressResultForCoordinates: ((Coordinates) -> LocationAddressLookupResult)? = null,
) : LocationRepository {
    override fun observeAvailability(): Flow<Boolean> = availability

    override suspend fun getCurrentLocation(permissionState: LocationPermissionState): LocationLookupResult =
        resultForPermission?.invoke(permissionState) ?: result

    override suspend fun getCurrentAddress(coordinates: Coordinates): LocationAddressLookupResult =
        addressResultForCoordinates?.invoke(coordinates) ?: addressResult
}

internal class RecordingStationEventLogger : StationEventLogger {
    val events = mutableListOf<StationEvent>()

    override fun log(event: StationEvent) {
        events += event
    }
}

internal class ThrowingStationEventLogger : StationEventLogger {
    override fun log(event: StationEvent): Unit = throw IllegalStateException("analytics failed")
}
