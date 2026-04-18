package com.gasstation.tools.demoseed

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.SearchRadius
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DemoSeedGeneratorTest {
    @Test
    fun `query matrix covers every approved radius and fuel type combination`() {
        val matrix = DemoSeedQueryMatrix.all()

        assertEquals(15, matrix.size)
        assertTrue(matrix.any { it.radius == SearchRadius.KM_3 && it.fuelType == FuelType.GASOLINE })
        assertTrue(matrix.any { it.radius == SearchRadius.KM_4 && it.fuelType == FuelType.DIESEL })
        assertTrue(matrix.any { it.radius == SearchRadius.KM_5 && it.fuelType == FuelType.LPG })
    }

    @Test
    fun `history factory generates stable three-point history for a station`() {
        val entries = DemoSeedHistoryFactory.createEntries(
            stationId = "station-1",
            fuelType = FuelType.GASOLINE,
            latestPriceWon = 1_689,
            generatedAtEpochMillis = 1_770_000_000_000,
        )

        assertEquals(3, entries.size)
        assertEquals(1_689, entries.last().priceWon)
        assertEquals(
            entries,
            DemoSeedHistoryFactory.createEntries(
                stationId = "station-1",
                fuelType = FuelType.GASOLINE,
                latestPriceWon = 1_689,
                generatedAtEpochMillis = 1_770_000_000_000,
            ),
        )
    }

    @Test
    fun `generator writes full query matrix and de-duplicates history per station fuel type`() = runTest {
        val generator = DemoSeedGenerator(
            fetcher = object : DemoSeedStationFetcher {
                override suspend fun fetchStations(
                    origin: Coordinates,
                    radius: SearchRadius,
                    fuelType: FuelType,
                ): List<DemoSeedRemoteStation> {
                    if (fuelType != FuelType.GASOLINE) return emptyList()
                    return when (radius) {
                        SearchRadius.KM_3 -> listOf(
                            DemoSeedRemoteStation(
                                stationId = "station-1",
                                name = "Gangnam One",
                                brandCode = "SK",
                                priceWon = 1_689,
                                coordinates = Coordinates(latitude = 37.498, longitude = 127.028),
                            ),
                        )
                        SearchRadius.KM_4 -> listOf(
                            DemoSeedRemoteStation(
                                stationId = "station-1",
                                name = "Gangnam One",
                                brandCode = "SK",
                                priceWon = 1_689,
                                coordinates = Coordinates(latitude = 37.498, longitude = 127.028),
                            ),
                            DemoSeedRemoteStation(
                                stationId = "station-2",
                                name = "Gangnam Two",
                                brandCode = "HD",
                                priceWon = 1_702,
                                coordinates = Coordinates(latitude = 37.499, longitude = 127.029),
                            ),
                        )
                        SearchRadius.KM_5 -> emptyList()
                    }
                }
            },
        )
        val outputFile = File.createTempFile("demo-seed", ".json")

        outputFile.deleteOnExit()
        generator.generate(
            outputFile = outputFile,
            origin = Coordinates(latitude = 37.497927, longitude = 127.027583),
            generatedAtEpochMillis = 1_770_000_000_000,
        )

        val document = DemoSeedJsonWriter.gson.fromJson(outputFile.readText(), DemoSeedDocument::class.java)
        assertEquals(15, document.queries.size)
        assertEquals("Gangnam Station Exit 2", document.origin.label)
        assertEquals(2, document.history.size)
        assertTrue(document.history.any { it.stationId == "station-1" && it.fuelType == FuelType.GASOLINE.name })
        assertTrue(document.history.any { it.stationId == "station-2" && it.fuelType == FuelType.GASOLINE.name })
    }
}
