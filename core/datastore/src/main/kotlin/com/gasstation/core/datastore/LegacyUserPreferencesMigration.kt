package com.gasstation.core.datastore

import android.content.SharedPreferences
import androidx.datastore.core.DataMigration
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder

class LegacyUserPreferencesMigration(
    private val sharedPreferences: SharedPreferences,
) : DataMigration<UserPreferences> {

    override suspend fun shouldMigrate(currentData: UserPreferences): Boolean =
        currentData == UserPreferences.default() &&
            LEGACY_KEYS.any(sharedPreferences::contains)

    override suspend fun migrate(currentData: UserPreferences): UserPreferences =
        currentData.copy(
            searchRadius = sharedPreferences.stringValue(KEY_DISTANCE_TYPE)?.toSearchRadius()
                ?: currentData.searchRadius,
            fuelType = sharedPreferences.stringValue(KEY_OIL_TYPE)?.toFuelType()
                ?: currentData.fuelType,
            brandFilter = sharedPreferences.stringValue(KEY_GAS_STATION_TYPE)?.toBrandFilter()
                ?: currentData.brandFilter,
            sortOrder = sharedPreferences.stringValue(KEY_SORT_TYPE)?.toSortOrder()
                ?: currentData.sortOrder,
            mapProvider = sharedPreferences.stringValue(KEY_MAP_TYPE)?.toMapProvider()
                ?: currentData.mapProvider,
        )

    override suspend fun cleanUp() = Unit

    private fun SharedPreferences.stringValue(key: String): String? =
        getString(key, null)?.trim()?.takeIf(String::isNotEmpty)

    private fun String.toSearchRadius(): SearchRadius? =
        when (this) {
            "3km", SearchRadius.KM_3.name -> SearchRadius.KM_3
            "4km", SearchRadius.KM_4.name -> SearchRadius.KM_4
            "5km", SearchRadius.KM_5.name -> SearchRadius.KM_5
            else -> null
        }

    private fun String.toFuelType(): FuelType? =
        when (this) {
            "휘발유", FuelType.GASOLINE.name -> FuelType.GASOLINE
            "경유", FuelType.DIESEL.name -> FuelType.DIESEL
            "고급휘발유", FuelType.PREMIUM_GASOLINE.name -> FuelType.PREMIUM_GASOLINE
            "실내등유", FuelType.KEROSENE.name -> FuelType.KEROSENE
            "자동차부탄", FuelType.LPG.name -> FuelType.LPG
            else -> null
        }

    private fun String.toBrandFilter(): BrandFilter? =
        when (this) {
            "전체", BrandFilter.ALL.name -> BrandFilter.ALL
            "SK에너지", BrandFilter.SKE.name -> BrandFilter.SKE
            "GS칼텍스", BrandFilter.GSC.name -> BrandFilter.GSC
            "현대오일뱅크", BrandFilter.HDO.name -> BrandFilter.HDO
            "S-OIL", BrandFilter.SOL.name -> BrandFilter.SOL
            "자영알뜰", BrandFilter.RTO.name -> BrandFilter.RTO
            "고속도로알뜰", BrandFilter.RTX.name -> BrandFilter.RTX
            "농협알뜰", BrandFilter.NHO.name -> BrandFilter.NHO
            "자가상표", BrandFilter.ETC.name -> BrandFilter.ETC
            "E1", BrandFilter.E1G.name -> BrandFilter.E1G
            "SK가스", BrandFilter.SKG.name -> BrandFilter.SKG
            else -> null
        }

    private fun String.toSortOrder(): SortOrder? =
        when (this) {
            "거리순 보기", SortOrder.DISTANCE.name -> SortOrder.DISTANCE
            "가격순 보기", SortOrder.PRICE.name -> SortOrder.PRICE
            else -> null
        }

    private fun String.toMapProvider(): MapProvider? =
        when (this) {
            "티맵", "TMAP", MapProvider.TMAP.name -> MapProvider.TMAP
            "카카오네비", "KAKAO", MapProvider.KAKAO_NAVI.name -> MapProvider.KAKAO_NAVI
            "네이버지도", "NAVER", MapProvider.NAVER_MAP.name -> MapProvider.NAVER_MAP
            else -> null
        }

    companion object {
        const val LEGACY_PREFERENCES_FILE_NAME = "gas-station-preference"
        const val KEY_DISTANCE_TYPE = "distanceType"
        const val KEY_OIL_TYPE = "oilType"
        const val KEY_GAS_STATION_TYPE = "gasStationType"
        const val KEY_SORT_TYPE = "sortType"
        const val KEY_MAP_TYPE = "mapType"
        val LEGACY_KEYS = listOf(
            KEY_DISTANCE_TYPE,
            KEY_OIL_TYPE,
            KEY_GAS_STATION_TYPE,
            KEY_SORT_TYPE,
            KEY_MAP_TYPE,
        )
    }
}
