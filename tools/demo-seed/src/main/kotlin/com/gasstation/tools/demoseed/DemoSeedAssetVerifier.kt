package com.gasstation.tools.demoseed

import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius

object DemoSeedAssetVerifier {
    private const val EXPECTED_SEED_VERSION = 1
    private val approvedOrigin = Coordinates(latitude = 37.497927, longitude = 127.027583)

    fun verify(document: DemoSeedDocument) {
        require(document.seedVersion == EXPECTED_SEED_VERSION) {
            "Unexpected demo seed version ${document.seedVersion}."
        }
        require(document.origin.label == DemoSeedGenerator.GANGNAM_STATION_EXIT_2_LABEL) {
            "Unexpected demo seed origin label ${document.origin.label}."
        }
        require(
            document.origin.latitude == approvedOrigin.latitude &&
                document.origin.longitude == approvedOrigin.longitude,
        ) {
            "Unexpected demo seed origin coordinates ${document.origin.latitude},${document.origin.longitude}."
        }

        val expectedQueryKeys = DemoSeedQueryMatrix.all()
            .map { query -> query.radius.meters to query.fuelType.name }
            .toSet()
        val actualQueryKeys = document.queries.map { snapshot -> snapshot.radiusMeters to snapshot.fuelType }
        require(actualQueryKeys.size == expectedQueryKeys.size && actualQueryKeys.toSet() == expectedQueryKeys) {
            "Demo seed query matrix must contain every approved combination exactly once."
        }

        document.queries.forEach { snapshot ->
            val stationIds = snapshot.stations.map(DemoSeedStation::stationId)
            require(stationIds.size == stationIds.distinct().size) {
                "Duplicate station id in ${snapshot.radiusMeters}/${snapshot.fuelType}."
            }
        }

        val expectedHistory = linkedMapOf<Pair<String, String>, DemoSeedStation>()
        document.queries.forEach { snapshot ->
            snapshot.stations.forEach { station ->
                expectedHistory.putIfAbsent(snapshot.fuelType to station.stationId, station)
            }
        }
        val actualHistoryKeys = document.history.map { history -> history.fuelType to history.stationId }
        require(actualHistoryKeys.size == actualHistoryKeys.distinct().size) {
            "Duplicate demo seed history key."
        }
        require(actualHistoryKeys.toSet() == expectedHistory.keys) {
            "Demo seed history keys do not match query stations."
        }

        val historyByKey = document.history.associateBy { history -> history.fuelType to history.stationId }
        expectedHistory.forEach { (key, station) ->
            val history = requireNotNull(historyByKey[key])
            val latestEntry = history.entries.lastOrNull()
            require(history.entries.size == 3) {
                "History $key must contain three points."
            }
            require(latestEntry?.priceWon == station.priceWon) {
                "History $key latest price does not match the query projection."
            }
            require(latestEntry.fetchedAtEpochMillis == document.generatedAtEpochMillis) {
                "History $key latest timestamp does not match generatedAtEpochMillis."
            }
            require(
                history.entries == DemoSeedHistoryFactory.createEntries(
                    stationId = station.stationId,
                    fuelType = FuelType.valueOf(history.fuelType),
                    latestPriceWon = station.priceWon,
                    generatedAtEpochMillis = document.generatedAtEpochMillis,
                ),
            ) {
                "History $key does not match deterministic generator output."
            }
        }

        val portfolioSnapshot = document.queries.single {
            it.radiusMeters == SearchRadius.KM_3.meters && it.fuelType == FuelType.GASOLINE.name
        }
        DemoPortfolioStations.forQuery(SearchRadius.KM_3, FuelType.GASOLINE).forEach { expected ->
            val actual = portfolioSnapshot.stations.singleOrNull { it.stationId == expected.stationId }
            require(
                actual != null &&
                    actual.brandCode == expected.brandCode &&
                    actual.name == expected.name &&
                    actual.priceWon == expected.priceWon &&
                    actual.latitude == expected.coordinates.latitude &&
                    actual.longitude == expected.coordinates.longitude,
            ) {
                "Missing or invalid approved portfolio station ${expected.stationId}."
            }
        }
    }
}
