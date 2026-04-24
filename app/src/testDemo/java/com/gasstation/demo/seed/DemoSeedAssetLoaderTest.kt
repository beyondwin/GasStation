package com.gasstation.demo.seed

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DemoSeedAssetLoaderTest {
    @Test
    fun `parse decodes query snapshots and history rows from demo json`() {
        val document = DemoSeedAssetLoader().parse(
            """
            {
              "seedVersion": 1,
              "generatedAtEpochMillis": 1770000000000,
              "origin": {
                "label": "Gangnam Station Exit 2",
                "latitude": 37.497927,
                "longitude": 127.027583
              },
              "queries": [
                {
                  "radiusMeters": 3000,
                  "fuelType": "GASOLINE",
                  "stations": [
                    {
                      "stationId": "station-1",
                      "brandCode": "GSC",
                      "name": "Gangnam",
                      "priceWon": 1689,
                      "latitude": 37.498,
                      "longitude": 127.028
                    }
                  ]
                }
              ],
              "history": [
                {
                  "stationId": "station-1",
                  "fuelType": "GASOLINE",
                  "entries": [
                    { "priceWon": 1670, "fetchedAtEpochMillis": 1769827200000 },
                    { "priceWon": 1689, "fetchedAtEpochMillis": 1770000000000 }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, document.queries.size)
        assertEquals("station-1", document.history.single().stationId)
    }

    @Test
    fun `load reads packaged asset with approved origin and full radius fuel matrix`() {
        val application = ApplicationProvider.getApplicationContext<Application>()

        val document = DemoSeedAssetLoader().load(application)
        val expectedMatrix = SearchRadius.entries.flatMap { radius ->
            FuelType.entries.map { fuelType -> radius.meters to fuelType.name }
        }.toSet()
        val actualMatrix = document.queries.map { it.radiusMeters to it.fuelType }.toSet()

        assertEquals(DemoSeedOrigin.label, document.origin.label)
        assertEquals(DemoSeedOrigin.coordinates.latitude, document.origin.latitude, 0.0)
        assertEquals(DemoSeedOrigin.coordinates.longitude, document.origin.longitude, 0.0)
        assertEquals(expectedMatrix, actualMatrix)
    }
}
