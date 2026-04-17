package com.gasstation.data.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.network.service.KakaoService
import com.gasstation.core.network.service.OpinetService
import com.gasstation.data.station.mapper.toFuelProductCode
import com.gasstation.data.station.mapper.toRemoteStation
import com.gasstation.domain.station.model.StationQuery
import javax.inject.Inject
import javax.inject.Named

interface StationRemoteDataSource {
    suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult
}

data class RemoteStation(
    val stationId: String,
    val name: String,
    val brandCode: String,
    val priceWon: Int,
    val coordinates: Coordinates,
)

sealed interface RemoteStationFetchResult {
    data class Success(
        val stations: List<RemoteStation>,
    ) : RemoteStationFetchResult

    data object Failure : RemoteStationFetchResult
}

class DefaultStationRemoteDataSource @Inject constructor(
    private val opinetService: OpinetService,
    private val kakaoService: KakaoService,
    @Named("opinetApiKey") private val opinetApiKey: String,
    ) : StationRemoteDataSource {
    override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult {
        val originInKtm = kakaoService.transCoord(
            x = query.coordinates.longitude,
            y = query.coordinates.latitude,
            inputCoord = WGS84,
            outputCoord = KTM,
        ).documents.firstOrNull() ?: return RemoteStationFetchResult.Failure

        val response = opinetService.findStations(
            code = opinetApiKey,
            x = originInKtm.x,
            y = originInKtm.y,
            radius = query.radius.meters,
            sort = OPINET_DISTANCE_SORT,
            fuelType = query.fuelType.toFuelProductCode(),
        )
        val rawStations = response.result?.stations ?: return RemoteStationFetchResult.Failure
        val normalizedStations = rawStations.mapNotNullSuspend { station ->
            station.toRemoteStation(kakaoService = kakaoService)
        }

        return when {
            normalizedStations.isNotEmpty() -> RemoteStationFetchResult.Success(normalizedStations)
            rawStations.isEmpty() -> RemoteStationFetchResult.Success(emptyList())
            else -> RemoteStationFetchResult.Failure
        }
    }

    private suspend fun <T, R : Any> Iterable<T>.mapNotNullSuspend(
        transform: suspend (T) -> R?,
    ): List<R> {
        val mapped = mutableListOf<R>()
        for (item in this) {
            val result = transform(item) ?: continue
            mapped += result
        }
        return mapped
    }

    private companion object {
        const val OPINET_DISTANCE_SORT = "2"
        const val WGS84 = "WGS84"
        const val KTM = "KTM"
    }
}
