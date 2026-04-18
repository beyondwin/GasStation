package com.gasstation.demo.seed

data class DemoSeedDocument(
    val seedVersion: Int,
    val generatedAtEpochMillis: Long,
    val origin: DemoSeedOriginDocument,
    val queries: List<DemoSeedQueryDocument>,
    val history: List<DemoSeedHistoryDocument>,
)

data class DemoSeedOriginDocument(
    val label: String,
    val latitude: Double,
    val longitude: Double,
)

data class DemoSeedQueryDocument(
    val radiusMeters: Int,
    val fuelType: String,
    val stations: List<DemoSeedStationDocument>,
)

data class DemoSeedStationDocument(
    val stationId: String,
    val brandCode: String,
    val name: String,
    val priceWon: Int,
    val latitude: Double,
    val longitude: Double,
)

data class DemoSeedHistoryDocument(
    val stationId: String,
    val fuelType: String,
    val entries: List<DemoSeedHistoryEntryDocument>,
)

data class DemoSeedHistoryEntryDocument(
    val priceWon: Int,
    val fetchedAtEpochMillis: Long,
)
