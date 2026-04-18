package com.gasstation.demo.seed

import android.content.Context
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

class DemoSeedAssetLoader @Inject constructor() {
    fun load(context: Context): DemoSeedDocument =
        context.assets.open(ASSET_FILE_NAME).bufferedReader().use { reader ->
            parse(reader.readText())
        }

    fun parse(rawJson: String): DemoSeedDocument {
        val root = JSONObject(rawJson)
        return DemoSeedDocument(
            seedVersion = root.getInt("seedVersion"),
            generatedAtEpochMillis = root.getLong("generatedAtEpochMillis"),
            origin = root.getJSONObject("origin").toOrigin(),
            queries = root.getJSONArray("queries").mapObjects { it.toQuery() },
            history = root.getJSONArray("history").mapObjects { it.toHistory() },
        )
    }

    private fun JSONObject.toOrigin(): DemoSeedOriginDocument = DemoSeedOriginDocument(
        label = getString("label"),
        latitude = getDouble("latitude"),
        longitude = getDouble("longitude"),
    )

    private fun JSONObject.toQuery(): DemoSeedQueryDocument = DemoSeedQueryDocument(
        radiusMeters = getInt("radiusMeters"),
        fuelType = getString("fuelType"),
        stations = getJSONArray("stations").mapObjects { station ->
            DemoSeedStationDocument(
                stationId = station.getString("stationId"),
                brandCode = station.getString("brandCode"),
                name = station.getString("name"),
                priceWon = station.getInt("priceWon"),
                latitude = station.getDouble("latitude"),
                longitude = station.getDouble("longitude"),
            )
        },
    )

    private fun JSONObject.toHistory(): DemoSeedHistoryDocument = DemoSeedHistoryDocument(
        stationId = getString("stationId"),
        fuelType = getString("fuelType"),
        entries = getJSONArray("entries").mapObjects { entry ->
            DemoSeedHistoryEntryDocument(
                priceWon = entry.getInt("priceWon"),
                fetchedAtEpochMillis = entry.getLong("fetchedAtEpochMillis"),
            )
        },
    )

    private companion object {
        const val ASSET_FILE_NAME = "demo-station-seed.json"
    }
}

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    List(length()) { index -> transform(getJSONObject(index)) }
