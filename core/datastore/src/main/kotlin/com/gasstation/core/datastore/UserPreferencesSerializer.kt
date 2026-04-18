package com.gasstation.core.datastore

import androidx.datastore.core.Serializer
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder
import java.io.InputStream
import java.io.OutputStream

object UserPreferencesSerializer : Serializer<UserPreferences> {
    private const val ENTRY_DELIMITER = "\n"
    private const val KEY_VALUE_DELIMITER = "="
    private const val VERSION = "2"
    private const val KEY_VERSION = "version"
    private const val KEY_SEARCH_RADIUS = "searchRadius"
    private const val KEY_FUEL_TYPE = "fuelType"
    private const val KEY_BRAND_FILTER = "brandFilter"
    private const val KEY_SORT_ORDER = "sortOrder"
    private const val KEY_MAP_PROVIDER = "mapProvider"

    override val defaultValue: UserPreferences = UserPreferences.default()

    override suspend fun readFrom(input: InputStream): UserPreferences {
        val encoded = input.readBytes().decodeToString().trim()
        if (encoded.isBlank()) {
            return defaultValue
        }

        return decodeKeyValueFormat(encoded)
    }

    override suspend fun writeTo(t: UserPreferences, output: OutputStream) {
        output.write(
            listOf(
                KEY_VERSION to VERSION,
                KEY_SEARCH_RADIUS to t.searchRadius.name,
                KEY_FUEL_TYPE to t.fuelType.name,
                KEY_BRAND_FILTER to t.brandFilter.name,
                KEY_SORT_ORDER to t.sortOrder.name,
                KEY_MAP_PROVIDER to t.mapProvider.name,
            ).joinToString(ENTRY_DELIMITER) { (key, value) ->
                "$key$KEY_VALUE_DELIMITER$value"
            }.encodeToByteArray(),
        )
    }

    private fun decodeKeyValueFormat(encoded: String): UserPreferences {
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

        return UserPreferences(
            searchRadius = decodeEnumOrDefault(values[KEY_SEARCH_RADIUS], defaultValue.searchRadius),
            fuelType = decodeEnumOrDefault(values[KEY_FUEL_TYPE], defaultValue.fuelType),
            brandFilter = decodeEnumOrDefault(values[KEY_BRAND_FILTER], defaultValue.brandFilter),
            sortOrder = decodeEnumOrDefault(values[KEY_SORT_ORDER], defaultValue.sortOrder),
            mapProvider = decodeEnumOrDefault(values[KEY_MAP_PROVIDER], defaultValue.mapProvider),
        )
    }

    private inline fun <reified T : Enum<T>> decodeEnum(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)

    private inline fun <reified T : Enum<T>> decodeEnumOrDefault(value: String?, default: T): T =
        value?.let { decodeEnum(it, default) } ?: default
}
