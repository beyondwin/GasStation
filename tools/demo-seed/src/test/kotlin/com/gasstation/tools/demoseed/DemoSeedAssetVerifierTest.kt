package com.gasstation.tools.demoseed

import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DemoSeedAssetVerifierTest {
    @Test
    fun `accepts a document created by the generator`() = runTest {
        DemoSeedAssetVerifier.verify(validDocument())
    }

    @Test
    fun `rejects a document with no approved query matrix`() = runTest {
        val failure = assertFailsWith<IllegalArgumentException> {
            DemoSeedAssetVerifier.verify(validDocument().copy(queries = emptyList(), history = emptyList()))
        }

        assertTrue(failure.message.orEmpty().contains("query matrix"))
    }

    @Test
    fun `rejects duplicate stations inside one query snapshot`() = runTest {
        val document = validDocument()
        val snapshot = document.queries.first { it.stations.isNotEmpty() }
        val invalidSnapshot = snapshot.copy(stations = snapshot.stations + snapshot.stations.first())

        val failure = assertFailsWith<IllegalArgumentException> {
            DemoSeedAssetVerifier.verify(
                document.copy(queries = document.queries.map { if (it === snapshot) invalidSnapshot else it }),
            )
        }

        assertTrue(failure.message.orEmpty().contains("Duplicate station id"))
    }

    @Test
    fun `rejects history whose latest price does not match query projection`() = runTest {
        val document = validDocument()
        val history = document.history.first()
        val invalidHistory = history.copy(
            entries = history.entries.dropLast(1) + history.entries.last().copy(priceWon = history.entries.last().priceWon + 1),
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            DemoSeedAssetVerifier.verify(
                document.copy(history = document.history.map { if (it === history) invalidHistory else it }),
            )
        }

        assertTrue(failure.message.orEmpty().contains("latest price"))
    }

    private suspend fun validDocument(): DemoSeedDocument = DemoSeedGenerator(
        fetcher = object : DemoSeedStationFetcher {
            override suspend fun fetchStations(
                origin: Coordinates,
                radius: SearchRadius,
                fuelType: FuelType,
            ): List<DemoSeedRemoteStation> = emptyList()
        },
    ).createDocument(
        origin = Coordinates(latitude = 37.497927, longitude = 127.027583),
        generatedAtEpochMillis = 1_770_000_000_000,
    )
}
