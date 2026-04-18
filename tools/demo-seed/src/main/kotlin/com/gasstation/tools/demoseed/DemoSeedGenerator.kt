package com.gasstation.tools.demoseed

import com.gasstation.core.model.Coordinates
import com.gasstation.core.network.di.NetworkModule
import com.gasstation.core.network.station.NetworkStationFetchResult
import com.gasstation.core.network.station.NetworkStationFetcher
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.SearchRadius
import java.io.File

data class DemoSeedRemoteStation(
    val stationId: String,
    val name: String,
    val brandCode: String,
    val priceWon: Int,
    val coordinates: Coordinates,
)

interface DemoSeedStationFetcher {
    suspend fun fetchStations(
        origin: Coordinates,
        radius: SearchRadius,
        fuelType: FuelType,
    ): List<DemoSeedRemoteStation>
}

class DemoSeedGenerator(
    private val fetcher: DemoSeedStationFetcher,
) {
    suspend fun generate(
        outputFile: File,
        origin: Coordinates,
        generatedAtEpochMillis: Long,
        originLabel: String = GangnamStationExit2Label,
    ) {
        val document = createDocument(
            origin = origin,
            generatedAtEpochMillis = generatedAtEpochMillis,
            originLabel = originLabel,
        )

        outputFile.parentFile?.mkdirs()
        outputFile.writeText(DemoSeedJsonWriter.write(document), Charsets.UTF_8)
    }

    suspend fun createDocument(
        origin: Coordinates,
        generatedAtEpochMillis: Long,
        originLabel: String = GangnamStationExit2Label,
    ): DemoSeedDocument {
        val snapshots = DemoSeedQueryMatrix.all().map { query ->
            DemoSeedSnapshot(
                radiusMeters = query.radius.meters,
                fuelType = query.fuelType.name,
                stations = fetcher.fetchStations(
                    origin = origin,
                    radius = query.radius,
                    fuelType = query.fuelType,
                ).map { station ->
                    DemoSeedStation(
                        stationId = station.stationId,
                        brandCode = station.brandCode,
                        name = station.name,
                        priceWon = station.priceWon,
                        latitude = station.coordinates.latitude,
                        longitude = station.coordinates.longitude,
                    )
                },
            )
        }

        val history = snapshots
            .asSequence()
            .flatMap { snapshot ->
                snapshot.stations.asSequence().map { station -> snapshot.fuelType to station }
            }
            .distinctBy { (fuelType, station) -> fuelType to station.stationId }
            .map { (fuelTypeName, station) ->
                DemoSeedStationHistory(
                    stationId = station.stationId,
                    fuelType = fuelTypeName,
                    entries = DemoSeedHistoryFactory.createEntries(
                        stationId = station.stationId,
                        fuelType = FuelType.valueOf(fuelTypeName),
                        latestPriceWon = station.priceWon,
                        generatedAtEpochMillis = generatedAtEpochMillis,
                    ),
                )
            }
            .sortedWith(compareBy(DemoSeedStationHistory::fuelType, DemoSeedStationHistory::stationId))
            .toList()

        return DemoSeedDocument(
            seedVersion = 1,
            generatedAtEpochMillis = generatedAtEpochMillis,
            origin = DemoSeedOriginJson(
                label = originLabel,
                latitude = origin.latitude,
                longitude = origin.longitude,
            ),
            queries = snapshots,
            history = history,
        )
    }

    companion object {
        const val GangnamStationExit2Label: String = "Gangnam Station Exit 2"

        fun fromSystemProperties(): DemoSeedGenerator {
            return DemoSeedGenerator(
                fetcher = SharedNetworkSeedStationFetcher(
                    fetcher = NetworkStationFetcher(
                        opinetService = NetworkModule.provideOpinetService(NetworkModule.provideOpinetBaseUrl()),
                        opinetApiKey = System.getProperty("opinet.apikey").orEmpty(),
                    ),
                ),
            )
        }
    }
}

private class SharedNetworkSeedStationFetcher(
    private val fetcher: NetworkStationFetcher,
) : DemoSeedStationFetcher {
    override suspend fun fetchStations(
        origin: Coordinates,
        radius: SearchRadius,
        fuelType: FuelType,
    ): List<DemoSeedRemoteStation> = when (val result = fetcher.fetchStations(origin, radius, fuelType)) {
        is NetworkStationFetchResult.Success -> result.stations.map { station ->
            DemoSeedRemoteStation(
                stationId = station.stationId,
                name = station.name,
                brandCode = station.brandCode,
                priceWon = station.priceWon,
                coordinates = station.coordinates,
            )
        }
        NetworkStationFetchResult.Failure -> error(
            "Shared network fetcher failed for ${radius.name}/${fuelType.name}.",
        )
    }
}
