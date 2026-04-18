package com.gasstation.data.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.network.station.NetworkStationFetchResult
import com.gasstation.core.network.station.NetworkStationFetcher
import com.gasstation.domain.station.model.StationQuery
import java.io.IOException
import java.io.InterruptedIOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

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

    data class Failure(
        val reason: StationRefreshFailureReason,
        val cause: Throwable? = null,
    ) : RemoteStationFetchResult
}

class DefaultStationRemoteDataSource @Inject constructor(
    private val networkStationFetcher: NetworkStationFetcher,
) : StationRemoteDataSource {
    override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult {
        return try {
            when (
                val result = networkStationFetcher.fetchStations(
                    origin = query.coordinates,
                    radius = query.radius,
                    fuelType = query.fuelType,
                )
            ) {
                is NetworkStationFetchResult.Success -> RemoteStationFetchResult.Success(
                    result.stations.map { station ->
                        RemoteStation(
                            stationId = station.stationId,
                            name = station.name,
                            brandCode = station.brandCode,
                            priceWon = station.priceWon,
                            coordinates = station.coordinates,
                        )
                    },
                )
                NetworkStationFetchResult.Failure -> RemoteStationFetchResult.Failure(
                    reason = StationRefreshFailureReason.InvalidPayload,
                )
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (timeout: InterruptedIOException) {
            RemoteStationFetchResult.Failure(
                reason = StationRefreshFailureReason.Timeout,
                cause = timeout,
            )
        } catch (ioException: IOException) {
            RemoteStationFetchResult.Failure(
                reason = StationRefreshFailureReason.Network,
                cause = ioException,
            )
        } catch (exception: Exception) {
            RemoteStationFetchResult.Failure(
                reason = exception.toFailureReason(),
                cause = exception,
            )
        }
    }
}

private fun Exception.toFailureReason(): StationRefreshFailureReason = when {
    isPayloadParsingFailure() -> StationRefreshFailureReason.InvalidPayload
    else -> StationRefreshFailureReason.Unknown
}

private fun Throwable.isPayloadParsingFailure(): Boolean = generateSequence(this) { it.cause }
    .map { it::class.java.simpleName }
    .any { simpleName ->
        simpleName == "JsonSyntaxException" ||
            simpleName == "JsonParseException" ||
            simpleName == "MalformedJsonException"
    }
