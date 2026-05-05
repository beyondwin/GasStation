package com.gasstation.core.datastore

import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream

object UserPreferencesSerializer : Serializer<StoredUserPreferences> {
    private const val ENTRY_DELIMITER = "\n"
    private const val KEY_VALUE_DELIMITER = "="
    private const val VERSION = "2"
    private const val KEY_VERSION = "version"
    private const val KEY_SEARCH_RADIUS = "searchRadius"
    private const val KEY_FUEL_TYPE = "fuelType"
    private const val KEY_BRAND_FILTER = "brandFilter"
    private const val KEY_SORT_ORDER = "sortOrder"
    private const val KEY_MAP_PROVIDER = "mapProvider"

    override val defaultValue: StoredUserPreferences = StoredUserPreferences.Default

    override suspend fun readFrom(input: InputStream): StoredUserPreferences {
        val encoded = input.readBytes().decodeToString().trim()
        if (encoded.isBlank()) {
            return defaultValue
        }

        return decodeKeyValueFormat(encoded)
    }

    override suspend fun writeTo(t: StoredUserPreferences, output: OutputStream) {
        output.write(
            listOf(
                KEY_VERSION to VERSION,
                KEY_SEARCH_RADIUS to t.searchRadiusName,
                KEY_FUEL_TYPE to t.fuelTypeName,
                KEY_BRAND_FILTER to t.brandFilterName,
                KEY_SORT_ORDER to t.sortOrderName,
                KEY_MAP_PROVIDER to t.mapProviderName,
            ).joinToString(ENTRY_DELIMITER) { (key, value) ->
                "$key$KEY_VALUE_DELIMITER$value"
            }.encodeToByteArray(),
        )
    }

    private fun decodeKeyValueFormat(encoded: String): StoredUserPreferences {
        val values = encoded.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull { line ->
                val separatorIndex = line.indexOf(KEY_VALUE_DELIMITER)
                if (separatorIndex <= 0) {
                    return@mapNotNull null
                }

                line.substring(0, separatorIndex) to line.substring(separatorIndex + 1)
            }.toMap()

        return StoredUserPreferences(
            searchRadiusName = values[KEY_SEARCH_RADIUS] ?: defaultValue.searchRadiusName,
            fuelTypeName = values[KEY_FUEL_TYPE] ?: defaultValue.fuelTypeName,
            brandFilterName = values[KEY_BRAND_FILTER] ?: defaultValue.brandFilterName,
            sortOrderName = values[KEY_SORT_ORDER] ?: defaultValue.sortOrderName,
            mapProviderName = values[KEY_MAP_PROVIDER] ?: defaultValue.mapProviderName,
        )
    }
}
