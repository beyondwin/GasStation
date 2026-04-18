package com.gasstation.demo.seed

import android.content.Context
import com.gasstation.data.station.RemoteStation
import com.gasstation.data.station.RemoteStationFetchResult
import com.gasstation.data.station.SeedStationRemoteDataSource
import com.gasstation.domain.station.model.StationQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DemoSeedStationRemoteDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assetLoader: DemoSeedAssetLoader,
) : SeedStationRemoteDataSource {
    override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult {
        val snapshot = assetLoader.load(context).queries.firstOrNull { candidate ->
            candidate.radiusMeters == query.radius.meters &&
                candidate.fuelType == query.fuelType.name
        } ?: return RemoteStationFetchResult.Failure

        return RemoteStationFetchResult.Success(
            snapshot.stations.map { station ->
                RemoteStation(
                    stationId = station.stationId,
                    name = station.name,
                    brandCode = station.brandCode,
                    priceWon = station.priceWon,
                    coordinates = com.gasstation.core.model.Coordinates(
                        latitude = station.latitude,
                        longitude = station.longitude,
                    ),
                )
            },
        )
    }
}
