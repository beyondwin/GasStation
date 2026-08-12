package com.gasstation.core.network.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.network.service.OpinetService
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.CancellationException

class NetworkStationFetcher(private val opinetService: OpinetService, private val opinetApiKey: String) : StationNetworkSource {
    override suspend fun fetchStations(origin: Coordinates, radius: SearchRadius, fuelType: FuelType): NetworkStationFetchResult = try {
        val originInKtm = LocalKoreanCoordinateTransform.wgs84ToKtm(
            latitude = origin.latitude,
            longitude = origin.longitude,
        )

        val response = opinetService.findStations(
            code = opinetApiKey,
            x = originInKtm.x,
            y = originInKtm.y,
            radius = radius.meters,
            sort = OPINET_DISTANCE_SORT,
            fuelType = fuelType.toFuelProductCode(),
        )

        val rawStations = response.result?.stations
            ?: return NetworkStationFetchResult.Failure(NetworkStationFailure.InvalidPayload)
        val normalizedStations = buildList {
            for (station in rawStations) {
                val mapped = station.toNetworkRemoteStation() ?: continue
                add(mapped)
            }
        }

        return when {
            normalizedStations.isNotEmpty() -> NetworkStationFetchResult.Success(normalizedStations)
            rawStations.isEmpty() -> NetworkStationFetchResult.Success(emptyList())
            else -> NetworkStationFetchResult.Failure(NetworkStationFailure.InvalidPayload)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (timeout: InterruptedIOException) {
        NetworkStationFetchResult.Failure(NetworkStationFailure.Timeout, timeout)
    } catch (network: IOException) {
        val reason = if (network.hasJsonParsingCause()) {
            NetworkStationFailure.InvalidPayload
        } else {
            NetworkStationFailure.Network
        }
        NetworkStationFetchResult.Failure(reason, network)
    } catch (exception: Exception) {
        NetworkStationFetchResult.Failure(exception.toNetworkStationFailure(), exception)
    }
}

private fun Exception.toNetworkStationFailure(): NetworkStationFailure = when {
    hasJsonParsingCause() -> NetworkStationFailure.InvalidPayload
    this is HttpException -> NetworkStationFailure.Http(code())
    else -> NetworkStationFailure.Unknown
}

private const val OPINET_DISTANCE_SORT = "2"
