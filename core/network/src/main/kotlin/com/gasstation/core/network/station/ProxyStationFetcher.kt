package com.gasstation.core.network.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.network.model.ProxyStationDto
import com.gasstation.core.network.model.ProxyStationSearchRequestDto
import com.gasstation.core.network.service.ProxyStationService
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.CancellationException

class ProxyStationFetcher(private val proxyStationService: ProxyStationService) : StationNetworkSource {
    override suspend fun fetchStations(origin: Coordinates, radius: SearchRadius, fuelType: FuelType): NetworkStationFetchResult = try {
        val response = proxyStationService.findStations(
            ProxyStationSearchRequestDto(
                latitude = origin.latitude,
                longitude = origin.longitude,
                radiusMeters = radius.meters,
                fuelType = fuelType.name,
            ),
        )
        val rawStations = response.stations
        val mappedStations = rawStations.mapNotNull { station ->
            station.toNetworkRemoteStation(expectedFuelType = fuelType)
        }

        return when {
            mappedStations.isNotEmpty() -> NetworkStationFetchResult.Success(mappedStations)
            rawStations.isEmpty() -> NetworkStationFetchResult.Success(emptyList())
            else -> NetworkStationFetchResult.Failure(NetworkStationFailure.InvalidPayload)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (timeout: InterruptedIOException) {
        NetworkStationFetchResult.Failure(NetworkStationFailure.Timeout, timeout)
    } catch (network: IOException) {
        val reason = if (network.hasProxyJsonParsingCause()) {
            NetworkStationFailure.InvalidPayload
        } else {
            NetworkStationFailure.Network
        }
        NetworkStationFetchResult.Failure(reason, network)
    } catch (exception: Exception) {
        val reason = when {
            exception.hasProxyJsonParsingCause() -> NetworkStationFailure.InvalidPayload
            exception is HttpException -> NetworkStationFailure.Http(exception.code())
            else -> NetworkStationFailure.Unknown
        }
        NetworkStationFetchResult.Failure(reason, exception)
    }
}

private fun ProxyStationDto.toNetworkRemoteStation(expectedFuelType: FuelType): NetworkRemoteStation? {
    if (fuelType != expectedFuelType.name) return null
    val id = stationId?.takeIf(String::isNotBlank) ?: return null
    val stationName = name?.takeIf(String::isNotBlank) ?: return null
    val brand = brandCode?.takeIf(String::isNotBlank) ?: return null
    val price = priceWon?.takeIf { it > 0 } ?: return null
    val lat = latitude ?: return null
    val lon = longitude ?: return null

    return NetworkRemoteStation(
        stationId = id,
        name = stationName,
        brandCode = brand,
        priceWon = price,
        coordinates = Coordinates.ofOrNull(latitude = lat, longitude = lon) ?: return null,
    )
}

private fun Throwable.hasProxyJsonParsingCause(): Boolean = generateSequence(this) { it.cause }
    .map { it::class.java.simpleName }
    .any { simpleName ->
        simpleName == "JsonSyntaxException" ||
            simpleName == "JsonParseException" ||
            simpleName == "MalformedJsonException"
    }
