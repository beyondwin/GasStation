package com.gasstation.tools.demoseed

import com.google.gson.Gson
import com.google.gson.GsonBuilder

data class DemoSeedDocument(
    val seedVersion: Int,
    val generatedAtEpochMillis: Long,
    val origin: DemoSeedOriginJson,
    val queries: List<DemoSeedSnapshot>,
    val history: List<DemoSeedStationHistory>,
)

data class DemoSeedOriginJson(
    val label: String,
    val latitude: Double,
    val longitude: Double,
)

data class DemoSeedSnapshot(
    val radiusMeters: Int,
    val fuelType: String,
    val stations: List<DemoSeedStation>,
)

data class DemoSeedStation(
    val stationId: String,
    val brandCode: String,
    val name: String,
    val priceWon: Int,
    val latitude: Double,
    val longitude: Double,
)

data class DemoSeedStationHistory(
    val stationId: String,
    val fuelType: String,
    val entries: List<DemoSeedHistoryEntry>,
)

object DemoSeedJsonWriter {
    val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    fun write(document: DemoSeedDocument): String = gson.toJson(document)
}
